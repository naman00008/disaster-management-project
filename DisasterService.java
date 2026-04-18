package com.reliefnet.service;

import com.reliefnet.exception.ResourceNotFoundException;
import com.reliefnet.model.*;
import com.reliefnet.repository.*;
import com.reliefnet.websocket.RealtimeEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core business logic service.
 *
 * ARCHITECTURE: Controller → DisasterService → Repositories
 *               DisasterService → RealtimeEventService (WebSocket broadcasts)
 *
 * ROOT CAUSE FIXES:
 *  - Status update now broadcasts via WebSocket so UI updates live
 *  - Auto-allocate triggers real-time map pin update
 *  - All mutations emit WebSocket events
 */
@Service
@Transactional
public class DisasterService {

    @Autowired private VolunteerRepository       volunteerRepository;
    @Autowired private DisasterReportRepository  reportRepository;
    @Autowired private ResourceAllocationRepository allocationRepository;
    @Autowired private RealtimeEventService       realtimeEventService;

    // ── SCORING: Score = (ExpertiseMatch + Proximity) / CurrentWorkload ───
    public List<Map<String, Object>> rankVolunteersForReport(DisasterReport report) {
        List<Volunteer> available = volunteerRepository.findAvailableVolunteers();
        String requiredExpertise  = inferRequiredExpertise(report.getDisasterType());

        return available.stream()
            .map(v -> {
                double score = v.computeScore(requiredExpertise, report.getLatitude(), report.getLongitude());
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("volunteerId",  v.getId());
                entry.put("name",         v.getUser().getName());
                entry.put("skills",       v.getSkills());
                entry.put("expertise",    v.getExpertise());
                entry.put("location",     v.getCurrentLocation());
                entry.put("workload",     v.getCurrentWorkload());
                entry.put("status",       v.getAvailabilityStatus().name());
                entry.put("latitude",     v.getLatitude());
                entry.put("longitude",    v.getLongitude());
                entry.put("score",        Math.round(score * 100.0) / 100.0);
                return entry;
            })
            .sorted((a, b) -> Double.compare((double) b.get("score"), (double) a.get("score")))
            .collect(Collectors.toList());
    }

    // ── AUTO ALLOCATE: pick best volunteer, assign, broadcast ─────────────
    public Optional<ResourceAllocation> autoAllocate(DisasterReport report) {
        List<Map<String, Object>> ranked = rankVolunteersForReport(report);
        if (ranked.isEmpty()) return Optional.empty();

        Long volunteerId = (Long) ranked.get(0).get("volunteerId");
        return volunteerRepository.findById(volunteerId).map(volunteer -> {
            // Create allocation record
            ResourceAllocation allocation = new ResourceAllocation();
            allocation.setReport(report);
            allocation.setVolunteer(volunteer);
            allocation.setTaskDescription("Auto-assigned: " + inferTaskDescription(report));
            allocation.setResourceType(inferRequiredExpertise(report.getDisasterType()));
            allocation.setQuantity(1);
            allocation.setMatchScore((double) ranked.get(0).get("score"));
            allocation.setAllocationStatus(ResourceAllocation.AllocationStatus.ASSIGNED);

            // Update volunteer state
            volunteer.setCurrentWorkload(volunteer.getCurrentWorkload() + 1);
            if (volunteer.getCurrentWorkload() >= 3) {
                volunteer.setAvailabilityStatus(Volunteer.AvailabilityStatus.BUSY);
            } else {
                volunteer.setAvailabilityStatus(Volunteer.AvailabilityStatus.ON_MISSION);
            }
            volunteerRepository.save(volunteer);

            // Update report state
            report.setStatus(DisasterReport.Status.IN_PROGRESS);
            report.setHelpDispatched(true);
            report.setUpdatedAt(LocalDateTime.now());
            reportRepository.save(report);

            ResourceAllocation saved = allocationRepository.save(allocation);

            // === REAL-TIME: broadcast report update to all connected clients ===
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status",          report.getStatus().name());
            payload.put("helpDispatched",  true);
            payload.put("volunteerName",   volunteer.getUser().getName());
            payload.put("progressPercent", report.getProgressPercent());
            realtimeEventService.broadcastReportUpdate("UPDATED", report.getId(), payload);

            // === REAL-TIME: broadcast volunteer location for map pin ===
            realtimeEventService.broadcastLocationUpdate(
                "VOLUNTEER", volunteer.getId(),
                volunteer.getLatitude(), volunteer.getLongitude(),
                volunteer.getUser().getName() + " → " + report.getArea()
            );

            // Refresh dashboard stats broadcast
            realtimeEventService.broadcastStats(getDashboardStats());
            return saved;
        });
    }

    // ── UPDATE REPORT STATUS (fixes status button) ─────────────────────────
    public DisasterReport updateStatus(Long reportId, DisasterReport.Status newStatus) {
        DisasterReport r = reportRepository.findById(reportId)
            .orElseThrow(() -> new ResourceNotFoundException("Report not found: " + reportId));

        r.setStatus(newStatus);
        r.setUpdatedAt(LocalDateTime.now());
        if (newStatus == DisasterReport.Status.RESOLVED) {
            r.setResolvedAt(LocalDateTime.now());
            r.setVolunteerArrived(true);
        } else if (newStatus == DisasterReport.Status.IN_PROGRESS) {
            r.setHelpDispatched(true);
        }
        DisasterReport saved = reportRepository.save(r);

        // === REAL-TIME broadcast ===
        Map<String, Object> payload = Map.of(
            "status",          saved.getStatus().name(),
            "progressPercent", saved.getProgressPercent(),
            "updatedAt",       saved.getUpdatedAt().toString()
        );
        realtimeEventService.broadcastReportUpdate("STATUS_CHANGED", saved.getId(), payload);
        realtimeEventService.broadcastStats(getDashboardStats());

        return saved;
    }

    // ── DASHBOARD STATS (fixes missing real-time overview) ─────────────────
    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalReports",       reportRepository.count());
        stats.put("pendingReports",     reportRepository.countByStatus(DisasterReport.Status.PENDING));
        stats.put("inProgressReports",  reportRepository.countByStatus(DisasterReport.Status.IN_PROGRESS));
        stats.put("resolvedReports",    reportRepository.countByStatus(DisasterReport.Status.RESOLVED));
        stats.put("criticalReports",    reportRepository.countBySeverity(DisasterReport.Severity.CRITICAL));
        stats.put("totalVolunteers",    volunteerRepository.count());
        stats.put("availableVolunteers",volunteerRepository.findAvailableVolunteers().size());
        stats.put("activeAllocations",  allocationRepository
            .findByAllocationStatus(ResourceAllocation.AllocationStatus.IN_PROGRESS).size());
        // Breakdown by disaster type for dashboard modules
        stats.put("floodCount",     reportRepository.countByDisasterType(DisasterReport.DisasterType.FLOOD));
        stats.put("earthquakeCount",reportRepository.countByDisasterType(DisasterReport.DisasterType.EARTHQUAKE));
        stats.put("fireCount",      reportRepository.countByDisasterType(DisasterReport.DisasterType.FIRE));
        stats.put("cycloneCount",   reportRepository.countByDisasterType(DisasterReport.DisasterType.CYCLONE));
        stats.put("timestamp",      LocalDateTime.now().toString());
        return stats;
    }

    // ── LOCATION UPDATE: update lat/lon and broadcast to live map ──────────
    public void updateDisasterLocation(Long reportId, double lat, double lon, String location) {
        reportRepository.findById(reportId).ifPresent(r -> {
            r.setLatitude(lat);
            r.setLongitude(lon);
            r.setLocation(location);
            r.setUpdatedAt(LocalDateTime.now());
            reportRepository.save(r);

            // === REAL-TIME: push new pin to live map ===
            realtimeEventService.broadcastLocationUpdate(
                "DISASTER", reportId, lat, lon, r.getTitle()
            );
        });
    }

    public void updateVolunteerLocation(Long volunteerId, double lat, double lon, String location) {
        volunteerRepository.findById(volunteerId).ifPresent(v -> {
            v.setLatitude(lat);
            v.setLongitude(lon);
            v.setCurrentLocation(location);
            v.setLastActive(LocalDateTime.now());
            volunteerRepository.save(v);

            realtimeEventService.broadcastLocationUpdate(
                "VOLUNTEER", volunteerId, lat, lon, v.getUser().getName()
            );
        });
    }

    // ── HELPERS ────────────────────────────────────────────────────────────
    private String inferRequiredExpertise(DisasterReport.DisasterType type) {
        if (type == null) return "General";
        return switch (type) {
            case FLOOD, TSUNAMI -> "Rescue";
            case EARTHQUAKE, LANDSLIDE -> "Engineering";
            case MEDICAL_EMERGENCY -> "Medical";
            case FIRE -> "Rescue";
            case DROUGHT -> "Logistics";
            default -> "General";
        };
    }

    private String inferTaskDescription(DisasterReport report) {
        String type = report.getDisasterType() != null
            ? report.getDisasterType().name().toLowerCase().replace("_", " ") : "disaster";
        return "Respond to " + type + " at " + report.getLocation();
    }
}
