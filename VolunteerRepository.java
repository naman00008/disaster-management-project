package com.reliefnet.repository;

import com.reliefnet.model.Volunteer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VolunteerRepository extends JpaRepository<Volunteer, Long> {
    Optional<Volunteer> findByUserId(Long userId);
    List<Volunteer> findByAvailabilityStatus(Volunteer.AvailabilityStatus status);

    @Query("SELECT v FROM Volunteer v WHERE v.availabilityStatus = 'AVAILABLE' AND v.currentWorkload < 3")
    List<Volunteer> findAvailableVolunteers();

    @Query("SELECT v FROM Volunteer v WHERE LOWER(v.skills) LIKE LOWER(CONCAT('%', :skill, '%'))")
    List<Volunteer> findBySkillsContainingIgnoreCase(String skill);
}
