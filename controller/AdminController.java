package com.reliefnet.controller;

import com.reliefnet.model.*;
import com.reliefnet.repository.*;
import com.reliefnet.service.DisasterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminController {

    @Autowired private UserRepository userRepo;
    @Autowired private VolunteerRepository volRepo;
    @Autowired private DisasterReportRepository reportRepo;
    @Autowired private ResourceAllocationRepository allocRepo;
    @Autowired private DisasterService disasterService;
    @Autowired private PasswordEncoder encoder;

    // ════════════════════════════════════════════════════
    // STATS / DASHBOARD
    // ════════════════════════════════════════════════════
    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalReports",     reportRepo.count());
        stats.put("activeDisasters",  reportRepo.countByStatus(DisasterReport.ReportStatus.ACTIVE));
        stats.put("totalVolunteers",  volRepo.count());
        stats.put("availableVols",    volRepo.countByAvailabilityStatus(Volunteer.AvailabilityStatus.AVAILABLE));
        stats.put("totalUsers",       userRepo.countByRole(User.Role.USER));
        stats.put("totalAllocations", allocRepo.count());

        // Disaster type breakdown
        Map<String,Long> typeMap = new LinkedHashMap<>();
        for (Object[] row : reportRepo.countByDisasterType())
            typeMap.put(String.valueOf(row[0]), ((Number)row[1]).longValue());
        stats.put("byType", typeMap);

        // Severity breakdown
        Map<String,Long> sevMap = new LinkedHashMap<>();
        for (Object[] row : reportRepo.countBySeverityLevel())
            sevMap.put(String.valueOf(row[0]), ((Number)row[1]).longValue());
        stats.put("bySeverity", sevMap);

        // Status breakdown
        Map<String,Long> stMap = new LinkedHashMap<>();
        for (Object[] row : reportRepo.countByStatusGrouped())
            stMap.put(String.valueOf(row[0]), ((Number)row[1]).longValue());
        stats.put("byStatus", stMap);

        return ResponseEntity.ok(stats);
    }

    // ════════════════════════════════════════════════════
    // DISASTER REPORTS
    // ════════════════════════════════════════════════════
    @GetMapping("/reports")
    public ResponseEntity<?> getAllReports(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type) {

        List<DisasterReport> reports = reportRepo.findAllByOrderByReportedAtDesc();

        if (severity != null && !severity.isBlank())
            reports = reports.stream()
                .filter(r -> r.getSeverityLevel() != null && r.getSeverityLevel().name().equals(severity))
                .toList();
        if (status != null && !status.isBlank())
            reports = reports.stream()
                .filter(r -> r.getStatus().name().equals(status))
                .toList();
        if (type != null && !type.isBlank())
            reports = reports.stream()
                .filter(r -> r.getDisasterType() != null && r.getDisasterType().equals(type))
                .toList();

        return ResponseEntity.ok(reports.stream().map(this::toReportMap).toList());
    }

    @GetMapping("/reports/{id}")
    public ResponseEntity<?> getReport(@PathVariable Long id) {
        return reportRepo.findById(id)
            .map(r -> ResponseEntity.ok(toReportMap(r)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/reports/{id}/status")
    public ResponseEntity<?> updateReportStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return reportRepo.findById(id).map(r -> {
            try {
                r.setStatus(DisasterReport.ReportStatus.valueOf(body.get("status")));
                reportRepo.save(r);
                return ResponseEntity.ok(toReportMap(r));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid status"));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/reports/{id}/severity")
    public ResponseEntity<?> updateSeverity(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return reportRepo.findById(id).map(r -> {
            try {
                r.setSeverityLevel(DisasterReport.SeverityLevel.valueOf(body.get("severity")));
                reportRepo.save(r);
                return ResponseEntity.ok(toReportMap(r));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid severity"));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/reports/{id}/auto-severity")
    public ResponseEntity<?> autoSeverity(@PathVariable Long id) {
        return reportRepo.findById(id).map(r -> {
            r.setSeverityLevel(disasterService.calculateSeverity(r));
            reportRepo.save(r);
            return ResponseEntity.ok(toReportMap(r));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/reports/{id}")
    public ResponseEntity<?> deleteReport(@PathVariable Long id) {
        if (!reportRepo.existsById(id)) return ResponseEntity.notFound().build();
        reportRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Report deleted successfully."));
    }

    // ════════════════════════════════════════════════════
    // VOLUNTEER ASSIGNMENT
    // ════════════════════════════════════════════════════
    @PostMapping("/reports/{id}/assign-volunteers")
    public ResponseEntity<?> assignVolunteers(
            @PathVariable Long id,
            @RequestBody Map<String, List<Long>> body) {
        return reportRepo.findById(id).map(r -> {
            List<Long> volIds = body.get("volunteerIds");
            if (volIds == null || volIds.isEmpty())
                return ResponseEntity.badRequest().body(Map.of("error", "No volunteer IDs provided."));
            List<Volunteer> vols = volRepo.findAllById(volIds);
            r.setAssignedVolunteers(vols);
            vols.forEach(v -> {
                v.setAvailabilityStatus(Volunteer.AvailabilityStatus.ON_MISSION);
                volRepo.save(v);
            });
            if (r.getStatus() == DisasterReport.ReportStatus.PENDING ||
                r.getStatus() == DisasterReport.ReportStatus.UNDER_REVIEW)
                r.setStatus(DisasterReport.ReportStatus.ACTIVE);
            reportRepo.save(r);
            return ResponseEntity.ok(toReportMap(r));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ════════════════════════════════════════════════════
    // RESOURCE ALLOCATIONS
    // ════════════════════════════════════════════════════
    @GetMapping("/reports/{id}/allocations")
    public ResponseEntity<?> getAllocations(@PathVariable Long id) {
        List<ResourceAllocation> allocs = allocRepo.findByDisasterReport_Id(id);
        return ResponseEntity.ok(allocs.stream().map(this::toAllocMap).toList());
    }

    @PostMapping("/reports/{id}/allocations")
    public ResponseEntity<?> addAllocation(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return reportRepo.findById(id).map(r -> {
            ResourceAllocation a = new ResourceAllocation();
            a.setDisasterReport(r);
            a.setResourceType((String) body.get("resourceType"));
            a.setQuantity(Integer.parseInt(String.valueOf(body.getOrDefault("quantity", 0))));
            a.setUnit((String) body.getOrDefault("unit", "units"));
            a.setNotes((String) body.getOrDefault("notes", ""));
            allocRepo.save(a);
            return ResponseEntity.ok(toAllocMap(a));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/reports/{id}/auto-allocate")
    public ResponseEntity<?> autoAllocate(@PathVariable Long id) {
        return reportRepo.findById(id).map(r -> {
            List<ResourceAllocation> allocs = disasterService.autoAllocate(r);
            return ResponseEntity.ok(allocs.stream().map(this::toAllocMap).toList());
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/allocations/{id}")
    public ResponseEntity<?> deleteAllocation(@PathVariable Long id) {
        if (!allocRepo.existsById(id)) return ResponseEntity.notFound().build();
        allocRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Allocation deleted."));
    }

    // ════════════════════════════════════════════════════
    // VOLUNTEERS MANAGEMENT
    // ════════════════════════════════════════════════════
    @GetMapping("/volunteers")
    public ResponseEntity<?> getAllVolunteers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) String search) {

        List<Volunteer> vols = volRepo.findAll();

        if (status != null && !status.isBlank()) {
            try {
                Volunteer.AvailabilityStatus s = Volunteer.AvailabilityStatus.valueOf(status);
                vols = vols.stream().filter(v -> v.getAvailabilityStatus() == s).toList();
            } catch (Exception ignored) {}
        }
        if (skill != null && !skill.isBlank()) {
            final String sk = skill;
            vols = vols.stream()
                .filter(v -> v.getSkills() != null && v.getSkills().contains(sk))
                .toList();
        }
        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase();
            vols = vols.stream()
                .filter(v -> v.getUser().getFullName().toLowerCase().contains(q)
                    || (v.getOrganization() != null && v.getOrganization().toLowerCase().contains(q))
                    || (v.getCurrentLocation() != null && v.getCurrentLocation().toLowerCase().contains(q)))
                .toList();
        }

        return ResponseEntity.ok(vols.stream().map(this::toVolMap).toList());
    }

    @GetMapping("/volunteers/{id}")
    public ResponseEntity<?> getVolunteer(@PathVariable Long id) {
        return volRepo.findById(id)
            .map(v -> ResponseEntity.ok(toVolMap(v)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/volunteers/{id}/status")
    public ResponseEntity<?> updateVolStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        return volRepo.findById(id).map(v -> {
            try {
                v.setAvailabilityStatus(Volunteer.AvailabilityStatus.valueOf(body.get("status")));
                volRepo.save(v);
                return ResponseEntity.ok(toVolMap(v));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error","Invalid status"));
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/volunteers/{id}")
    public ResponseEntity<?> deleteVolunteer(@PathVariable Long id) {
        return volRepo.findById(id).map(v -> {
            User u = v.getUser();
            volRepo.delete(v);
            userRepo.delete(u);
            return ResponseEntity.ok(Map.of("message", "Volunteer removed."));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ════════════════════════════════════════════════════
    // USERS MANAGEMENT
    // ════════════════════════════════════════════════════
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        return ResponseEntity.ok(
            userRepo.findByRole(User.Role.USER).stream().map(this::toUserMap).toList());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<?> getUser(@PathVariable Long id) {
        return userRepo.findById(id)
            .map(u -> ResponseEntity.ok(toUserMap(u)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email","").toLowerCase().trim();
        if (userRepo.existsByEmail(email))
            return ResponseEntity.badRequest().body(Map.of("error","Email already exists."));
        User u = new User();
        u.setFullName(body.get("fullName"));
        u.setEmail(email);
        u.setPassword(encoder.encode(body.getOrDefault("password","password123")));
        u.setPhone(body.getOrDefault("phone",""));
        u.setAddress(body.getOrDefault("address",""));
        u.setRole(User.Role.USER);
        userRepo.save(u);
        return ResponseEntity.ok(toUserMap(u));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody Map<String,String> body) {
        return userRepo.findById(id).map(u -> {
            if (body.containsKey("fullName")) u.setFullName(body.get("fullName"));
            if (body.containsKey("phone"))    u.setPhone(body.get("phone"));
            if (body.containsKey("address"))  u.setAddress(body.get("address"));
            if (body.containsKey("enabled"))  u.setEnabled(Boolean.parseBoolean(body.get("enabled")));
            userRepo.save(u);
            return ResponseEntity.ok(toUserMap(u));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (!userRepo.existsById(id)) return ResponseEntity.notFound().build();
        userRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("message","User deleted."));
    }

    // ════════════════════════════════════════════════════
    // HELPERS — DTO mappers
    // ════════════════════════════════════════════════════
    Map<String, Object> toReportMap(DisasterReport r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("location", r.getLocation());
        m.put("latitude", r.getLatitude());
        m.put("longitude", r.getLongitude());
        m.put("disasterType", r.getDisasterType());
        m.put("affectedPeople", r.getAffectedPeople());
        m.put("casualties", r.getCasualties());
        m.put("description", r.getDescription());
        m.put("resourcesRequired", r.getResourcesRequired());
        m.put("severityLevel", r.getSeverityLevel() != null ? r.getSeverityLevel().name() : null);
        m.put("status", r.getStatus() != null ? r.getStatus().name() : null);
        m.put("reportedAt", r.getReportedAt() != null ? r.getReportedAt().toString() : null);
        m.put("updatedAt",  r.getUpdatedAt()  != null ? r.getUpdatedAt().toString()  : null);
        if (r.getReportedBy() != null) {
            m.put("reportedBy", Map.of(
                "id", r.getReportedBy().getId(),
                "fullName", r.getReportedBy().getFullName(),
                "email", r.getReportedBy().getEmail()
            ));
        }
        if (r.getAssignedVolunteers() != null) {
            m.put("assignedVolunteers", r.getAssignedVolunteers().stream()
                .map(v -> Map.of(
                    "id", v.getId(),
                    "name", v.getUser().getFullName(),
                    "organization", v.getOrganization() != null ? v.getOrganization() : "",
                    "currentLocation", v.getCurrentLocation() != null ? v.getCurrentLocation() : ""
                )).toList());
        }
        if (r.getAllocations() != null) {
            m.put("allocations", r.getAllocations().stream().map(this::toAllocMap).toList());
        }
        return m;
    }

    Map<String, Object> toVolMap(Volunteer v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("userId", v.getUser().getId());
        m.put("name", v.getUser().getFullName());
        m.put("email", v.getUser().getEmail());
        m.put("phone", v.getUser().getPhone());
        m.put("organization", v.getOrganization());
        m.put("currentLocation", v.getCurrentLocation());
        m.put("availabilityStatus", v.getAvailabilityStatus().name());
        m.put("experienceYears", v.getExperienceYears());
        m.put("skills", v.getSkills());
        m.put("bio", v.getBio());
        m.put("registeredAt", v.getRegisteredAt() != null ? v.getRegisteredAt().toString() : null);
        return m;
    }

    Map<String, Object> toUserMap(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("fullName", u.getFullName());
        m.put("email", u.getEmail());
        m.put("phone", u.getPhone());
        m.put("address", u.getAddress());
        m.put("role", u.getRole().name().toLowerCase());
        m.put("enabled", u.isEnabled());
        m.put("createdAt", u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
        return m;
    }

    Map<String, Object> toAllocMap(ResourceAllocation a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("resourceType", a.getResourceType());
        m.put("quantity", a.getQuantity());
        m.put("unit", a.getUnit());
        m.put("notes", a.getNotes());
        m.put("status", a.getStatus().name());
        m.put("allocatedAt", a.getAllocatedAt() != null ? a.getAllocatedAt().toString() : null);
        return m;
    }
}
