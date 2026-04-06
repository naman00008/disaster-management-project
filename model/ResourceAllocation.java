package com.reliefnet.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "resource_allocations")
public class ResourceAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disaster_report_id")
    private DisasterReport disasterReport;

    private String resourceType;
    private int quantity;
    private String unit;
    private String notes;

    @Enumerated(EnumType.STRING)
    private AllocationStatus status = AllocationStatus.ALLOCATED;

    private LocalDateTime allocatedAt;

    @PrePersist
    protected void onCreate() { allocatedAt = LocalDateTime.now(); }

    public enum AllocationStatus { ALLOCATED, IN_TRANSIT, DELIVERED, RETURNED }

    // ── Constructors ──
    public ResourceAllocation() {}

    public ResourceAllocation(DisasterReport report, String resourceType, int quantity, String unit) {
        this.disasterReport = report;
        this.resourceType = resourceType;
        this.quantity = quantity;
        this.unit = unit;
    }

    // ── Getters & Setters ──
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public DisasterReport getDisasterReport() { return disasterReport; }
    public void setDisasterReport(DisasterReport disasterReport) { this.disasterReport = disasterReport; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public AllocationStatus getStatus() { return status; }
    public void setStatus(AllocationStatus status) { this.status = status; }

    public LocalDateTime getAllocatedAt() { return allocatedAt; }
    public void setAllocatedAt(LocalDateTime allocatedAt) { this.allocatedAt = allocatedAt; }
}
