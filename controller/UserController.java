package com.reliefnet.controller;

import com.reliefnet.model.*;
import com.reliefnet.repository.*;
import com.reliefnet.service.DisasterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
public class UserController {

    @Autowired private UserRepository userRepo;
    @Autowired private DisasterReportRepository reportRepo;
    @Autowired private DisasterService disasterService;

    // ── My Reports ───────────────────────────────────────────────────────────
    @GetMapping("/reports")
    public ResponseEntity<?> getMyReports(@AuthenticationPrincipal String email) {
        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error","Not authenticated"));
        List<DisasterReport> reports = reportRepo.findByReportedBy(user);
        return ResponseEntity.ok(reports.stream().map(r -> toReportMap(r)).toList());
    }

    @GetMapping("/reports/{id}")
    public ResponseEntity<?> getReport(@PathVariable Long id,
                                       @AuthenticationPrincipal String email) {
        User user = userRepo.findByEmail(email).orElse(null);
        return reportRepo.findById(id).map(r -> {
            // Admin can see all; user can only see own
            if (user != null && user.getRole() == User.Role.ADMIN) {
                return ResponseEntity.ok(toReportMap(r));
            }
            if (r.getReportedBy() != null && r.getReportedBy().getId().equals(
                    user != null ? user.getId() : -1L)) {
                return ResponseEntity.ok(toReportMap(r));
            }
            return ResponseEntity.status(403).<Object>body(Map.of("error","Access denied"));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ── Submit Report ────────────────────────────────────────────────────────
    @PostMapping("/reports")
    public ResponseEntity<?> submitReport(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal String email) {

        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.status(401).body(Map.of("error","Not authenticated"));

        DisasterReport r = new DisasterReport();
        r.setLocation((String) body.get("location"));
        r.setDisasterType((String) body.get("disasterType"));
        r.setAffectedPeople(Integer.parseInt(String.valueOf(body.getOrDefault("affectedPeople", 0))));
        r.setCasualties(Integer.parseInt(String.valueOf(body.getOrDefault("casualties", 0))));
        r.setDescription((String) body.getOrDefault("description", ""));
        r.setLatitude(Double.parseDouble(String.valueOf(body.getOrDefault("latitude", 0.0))));
        r.setLongitude(Double.parseDouble(String.valueOf(body.getOrDefault("longitude", 0.0))));

        Object resObj = body.get("resourcesRequired");
        if (resObj instanceof List) r.setResourcesRequired((List<String>) resObj);

        r.setReportedBy(user);
        r.setStatus(DisasterReport.ReportStatus.PENDING);
        r.setSeverityLevel(disasterService.calculateSeverity(r));
        r.setAssignedVolunteers(new ArrayList<>());

        reportRepo.save(r);
        return ResponseEntity.ok(toReportMap(r));
    }

    // ── Profile ──────────────────────────────────────────────────────────────
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@AuthenticationPrincipal String email) {
        return userRepo.findByEmail(email)
            .map(u -> ResponseEntity.ok(toUserMap(u)))
            .orElse(ResponseEntity.status(401).build());
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal String email) {
        return userRepo.findByEmail(email).map(u -> {
            if (body.containsKey("fullName")) u.setFullName(body.get("fullName"));
            if (body.containsKey("phone"))    u.setPhone(body.get("phone"));
            if (body.containsKey("address"))  u.setAddress(body.get("address"));
            userRepo.save(u);
            return ResponseEntity.ok(toUserMap(u));
        }).orElse(ResponseEntity.status(401).build());
    }

    // ── Stats ────────────────────────────────────────────────────────────────
    @GetMapping("/stats")
    public ResponseEntity<?> getStats(@AuthenticationPrincipal String email) {
        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.status(401).build();
        List<DisasterReport> reports = reportRepo.findByReportedBy(user);
        return ResponseEntity.ok(Map.of(
            "total",    reports.size(),
            "active",   reports.stream().filter(r -> r.getStatus() == DisasterReport.ReportStatus.ACTIVE).count(),
            "resolved", reports.stream().filter(r -> r.getStatus() == DisasterReport.ReportStatus.RESOLVED).count(),
            "pending",  reports.stream().filter(r -> r.getStatus() == DisasterReport.ReportStatus.PENDING).count()
        ));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private Map<String, Object> toReportMap(DisasterReport r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("location", r.getLocation());
        m.put("disasterType", r.getDisasterType());
        m.put("affectedPeople", r.getAffectedPeople());
        m.put("casualties", r.getCasualties());
        m.put("description", r.getDescription());
        m.put("resourcesRequired", r.getResourcesRequired());
        m.put("severityLevel", r.getSeverityLevel() != null ? r.getSeverityLevel().name() : null);
        m.put("status", r.getStatus() != null ? r.getStatus().name() : null);
        m.put("reportedAt", r.getReportedAt() != null ? r.getReportedAt().toString() : null);
        if (r.getAssignedVolunteers() != null) {
            m.put("assignedVolunteers", r.getAssignedVolunteers().stream()
                .map(v -> Map.of("name", v.getUser().getFullName(), "org", v.getOrganization() != null ? v.getOrganization() : ""))
                .toList());
        }
        return m;
    }

    private Map<String, Object> toUserMap(User u) {
        return Map.of(
            "id", u.getId(),
            "fullName", u.getFullName(),
            "email", u.getEmail(),
            "phone", u.getPhone() != null ? u.getPhone() : "",
            "address", u.getAddress() != null ? u.getAddress() : "",
            "role", u.getRole().name().toLowerCase()
        );
    }
}
