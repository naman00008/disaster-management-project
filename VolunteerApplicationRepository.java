package com.reliefnet.repository;

import com.reliefnet.model.Volunteer;
import com.reliefnet.model.VolunteerApplication;
import com.reliefnet.model.DisasterReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VolunteerApplicationRepository extends JpaRepository<VolunteerApplication, Long> {
    List<VolunteerApplication> findByAssignedVolunteer(Volunteer volunteer);
    List<VolunteerApplication> findByReport(DisasterReport report);
    List<VolunteerApplication> findByAssignedVolunteerAndApplicationStatus(Volunteer volunteer, VolunteerApplication.ApplicationStatus status);
    Optional<VolunteerApplication> findByAssignedVolunteerAndReport(Volunteer volunteer, DisasterReport report);
    boolean existsByAssignedVolunteerAndReport(Volunteer volunteer, DisasterReport report);
    long countByAssignedVolunteerAndApplicationStatus(Volunteer volunteer, VolunteerApplication.ApplicationStatus status);
}
