package com.reliefnet.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "disaster_reports")
public class DisasterReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String location;
    private double latitude;
    private double longitude;
    private String disasterType;
    private int affectedPeople;
    private int casualties;
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "report_resources", joinColumns = @JoinColumn(name = "report_id"))
    @Column(name = "resource")
    private List<String> resourcesRequired;

    @Enumerated(EnumType.STRING)
    private SeverityLevel severityLevel;

    @Enumerated(EnumType.STRING)
    private ReportStatus status = ReportStatus.PENDING;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reported_by_id")
    private User reportedBy;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "report_volunteers",
        joinColumns = @JoinColumn(name = "report_id"),
        inverseJoinColumns = @JoinColumn(name = "volunteer_id"))
    private List<Volunteer> assignedVolunteers;

    @OneToMany(mappedBy = "disasterReport", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<ResourceAllocation> allocations;

    private LocalDateTime reportedAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        reportedAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    public enum SeverityLevel { LOW, MEDIUM, HIGH, CRITICAL }
    public enum ReportStatus { PENDING, UNDER_REVIEW, ACTIVE, RESOLVED, CLOSED }

    // ── Constructors ──
    public DisasterReport() {}

    // ── Getters & Setters ──
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getDisasterType() { return disasterType; }
    public void setDisasterType(String disasterType) { this.disasterType = disasterType; }

    public int getAffectedPeople() { return affectedPeople; }
    public void setAffectedPeople(int affectedPeople) { this.affectedPeople = affectedPeople; }

    public int getCasualties() { return casualties; }
    public void setCasualties(int casualties) { this.casualties = casualties; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getResourcesRequired() { return resourcesRequired; }
    public void setResourcesRequired(List<String> resourcesRequired) { this.resourcesRequired = resourcesRequired; }

    public SeverityLevel getSeverityLevel() { return severityLevel; }
    public void setSeverityLevel(SeverityLevel severityLevel) { this.severityLevel = severityLevel; }

    public ReportStatus getStatus() { return status; }
    public void setStatus(ReportStatus status) { this.status = status; }

    public User getReportedBy() { return reportedBy; }
    public void setReportedBy(User reportedBy) { this.reportedBy = reportedBy; }

    public List<Volunteer> getAssignedVolunteers() { return assignedVolunteers; }
    public void setAssignedVolunteers(List<Volunteer> assignedVolunteers) { this.assignedVolunteers = assignedVolunteers; }

    public List<ResourceAllocation> getAllocations() { return allocations; }
    public void setAllocations(List<ResourceAllocation> allocations) { this.allocations = allocations; }

    public LocalDateTime getReportedAt() { return reportedAt; }
    public void setReportedAt(LocalDateTime reportedAt) { this.reportedAt = reportedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
