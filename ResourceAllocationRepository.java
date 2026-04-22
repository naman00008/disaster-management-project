package com.reliefnet.repository;

import com.reliefnet.model.ResourceAllocation;
import com.reliefnet.model.DisasterReport;
import com.reliefnet.model.Volunteer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceAllocationRepository extends JpaRepository<ResourceAllocation, Long> {
    List<ResourceAllocation> findByReport(DisasterReport report);
    List<ResourceAllocation> findByVolunteer(Volunteer volunteer);
    List<ResourceAllocation> findByAllocationStatus(ResourceAllocation.AllocationStatus status);
    long countByVolunteerAndAllocationStatus(Volunteer volunteer, ResourceAllocation.AllocationStatus status);
}
