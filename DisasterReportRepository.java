package com.reliefnet.repository;

import com.reliefnet.model.DisasterReport;
import com.reliefnet.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisasterReportRepository extends JpaRepository<DisasterReport, Long> {
    List<DisasterReport>  findByReporter(User reporter);
    List<DisasterReport>  findByStatus(DisasterReport.Status status);
    List<DisasterReport>  findBySeverity(DisasterReport.Severity severity);
    List<DisasterReport>  findByArea(String area);
    List<DisasterReport>  findByStatusAndSeverity(DisasterReport.Status status, DisasterReport.Severity severity);
    List<DisasterReport>  findByDisasterType(DisasterReport.DisasterType type);

    @Query("SELECT COUNT(r) FROM DisasterReport r WHERE r.status = :status")
    long countByStatus(@Param("status") DisasterReport.Status status);

    @Query("SELECT COUNT(r) FROM DisasterReport r WHERE r.severity = :severity")
    long countBySeverity(@Param("severity") DisasterReport.Severity severity);

    // FIX: Added countByDisasterType for dashboard modules (Flood/Earthquake/Fire/Cyclone)
    @Query("SELECT COUNT(r) FROM DisasterReport r WHERE r.disasterType = :type")
    long countByDisasterType(@Param("type") DisasterReport.DisasterType type);

    @Query("SELECT r FROM DisasterReport r ORDER BY r.reportedAt DESC")
    List<DisasterReport> findAllOrderByReportedAtDesc();

    // FIX: Fetch only active reports (not DISMISSED) for map display
    @Query("SELECT r FROM DisasterReport r WHERE r.status NOT IN ('RESOLVED','DISMISSED') ORDER BY r.reportedAt DESC")
    List<DisasterReport> findActiveReports();
}
