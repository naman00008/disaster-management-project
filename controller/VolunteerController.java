package com.reliefnet.controller;

import com.reliefnet.model.*;
import com.reliefnet.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/volunteer")
@CrossOrigin(origins = "*")
public class VolunteerController {

    @Autowired private UserRepository userRepo;
    @Autowired private VolunteerRepository volRepo;
    @Autowired private DisasterReportRepository reportRepo;
    @Autowired private ResourceAllocationRepository allocRepo;

    // ── Dashboard / Profile ──────────────────────────────────────────────────
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal String email) {
        return volRepo.findByUser_Email(email)
            .map(v -> ResponseEntity.ok(toVolMap(v)))
            .orElse(ResponseEntity.status(404).body(Map.of("error", "Volunteer profile not found")));
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal String email) {
        return volRepo.findByUser_Email(email).map(v -> {
            if (body.containsKey("currentLocation")) v.setCurrentLocation((String) body.get("currentLocation"));
            if (body.containsKey("organization"))    v.setOrganization((String) body.get("organization"));
            if (body.containsKey("bio"))             v.setBio((String) body.get("bio"));
            if (body.containsKey("experienceYears"))
                v.setExperienceYears(Integer.parseInt(String.valueOf(body.get("experienceYears"))));
            if (body.containsKey("skills") && body.get("skills") instanceof List)
                v.setSkills((List<String>) body.get("skills"));
            volRepo.save(v);
            return ResponseEntity.ok(toVolMap(v));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Availability ─────────────────────────────────────────────────────────
    @PatchMapping("/status")
    public ResponseEntity<?> updateStatus(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal String email) {
        return volRepo.findByUser_Email(email).map(v -> {
            try {
                v.setAvailabilityStatus(Volunteer.AvailabilityStatus.valueOf(body.get("status")));
                if (body.containsKey("currentLocation"))
                    v.setCurrentLocation(body.get("currentLocation"));
                volRepo.save(v);
                return ResponseEntity.ok(toVolMap(v));
            } catch (Exception e) {
                return ResponseEntity.badRequest().<Object>body(Map.of("error", "Invalid status"));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Missions ─────────────────────────────────────────────────────────────
    @GetMapping("/missions")
    public ResponseEntity<?> getMyMissions(@AuthenticationPrincipal String email) {
        return volRepo.findByUser_Email(email).map(v -> {
            List<DisasterReport> missions = reportRepo.findAll().stream()
                .filter(r -> r.getAssignedVolunteers() != null &&
                             r.getAssignedVolunteers().stream().anyMatch(av -> av.getId().equals(v.getId())))
                .toList();
            return ResponseEntity.ok(missions.stream().map(r -> toMissionMap(r, v)).toList());
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/missions/{id}")
    public ResponseEntity<?> getMissionDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal String email) {
        return volRepo.findByUser_Email(email).map(v ->
            reportRepo.findById(id).map(r -> {
                Map<String, Object> m = toMissionMap(r, v);
                m.put("allocations", allocRepo.findByDisasterReport_Id(id).stream()
                    .map(a -> Map.of(
                        "id", a.getId(),
                        "resourceType", a.getResourceType(),
                        "quantity", a.getQuantity(),
                        "unit", a.getUnit(),
                        "status", a.getStatus().name()
                    )).toList());
                return ResponseEntity.ok(m);
            }).orElse(ResponseEntity.notFound().build())
        ).orElse(ResponseEntity.notFound().build());
    }

    // ── Stats ─────────────────────────────────────────────────────────────────
    @GetMapping("/stats")
    public ResponseEntity<?> getStats(@AuthenticationPrincipal String email) {
        return volRepo.findByUser_Email(email).map(v -> {
            List<DisasterReport> missions = reportRepo.findAll().stream()
                .filter(r -> r.getAssignedVolunteers() != null &&
                             r.getAssignedVolunteers().stream().anyMatch(av -> av.getId().equals(v.getId())))
                .toList();
            return ResponseEntity.ok(Map.of(
                "totalMissions",     missions.size(),
                "activeMissions",    missions.stream().filter(r -> r.getStatus() == DisasterReport.ReportStatus.ACTIVE).count(),
                "completedMissions", missions.stream().filter(r -> r.getStatus() == DisasterReport.ReportStatus.RESOLVED).count(),
                "availabilityStatus", v.getAvailabilityStatus().name(),
                "skills", v.getSkills() != null ? v.getSkills() : List.of()
            ));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    Map<String, Object> toVolMap(Volunteer v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("userId", v.getUser().getId());
        m.put("name", v.getUser().getFullName());
        m.put("email", v.getUser().getEmail());
        m.put("phone", v.getUser().getPhone() != null ? v.getUser().getPhone() : "");
        m.put("organization", v.getOrganization() != null ? v.getOrganization() : "");
        m.put("currentLocation", v.getCurrentLocation() != null ? v.getCurrentLocation() : "");
        m.put("availabilityStatus", v.getAvailabilityStatus().name());
        m.put("experienceYears", v.getExperienceYears());
        m.put("skills", v.getSkills() != null ? v.getSkills() : List.of());
        m.put("bio", v.getBio() != null ? v.getBio() : "");
        m.put("registeredAt", v.getRegisteredAt() != null ? v.getRegisteredAt().toString() : null);
        return m;
    }

    private Map<String, Object> toMissionMap(DisasterReport r, Volunteer v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("location", r.getLocation());
        m.put("disasterType", r.getDisasterType());
        m.put("affectedPeople", r.getAffectedPeople());
        m.put("casualties", r.getCasualties());
        m.put("description", r.getDescription());
        m.put("severityLevel", r.getSeverityLevel() != null ? r.getSeverityLevel().name() : null);
        m.put("status", r.getStatus() != null ? r.getStatus().name() : null);
        m.put("reportedAt", r.getReportedAt() != null ? r.getReportedAt().toString() : null);
        return m;
    }
}
