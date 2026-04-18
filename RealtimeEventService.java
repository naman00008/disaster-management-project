package com.reliefnet.websocket;

import com.reliefnet.model.DisasterReport;
import com.reliefnet.model.Volunteer;
import com.reliefnet.model.VolunteerApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Service that broadcasts real-time events to all connected WebSocket clients.
 *
 * TOPICS:
 *   /topic/reports      → disaster report created/updated/deleted
 *   /topic/volunteers   → volunteer status changed
 *   /topic/applications → application accepted/rejected
 *   /topic/stats        → dashboard stat numbers refreshed
 *   /topic/locations    → volunteer/disaster location update
 */
@Service
public class RealtimeEventService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // ── Broadcast a disaster report event ─────────────────────────────────
    public void broadcastReportUpdate(String action, Long reportId, Map<String, Object> payload) {
        Map<String, Object> event = Map.of(
            "event", "REPORT_" + action.toUpperCase(),   // e.g. REPORT_CREATED
            "reportId", reportId,
            "timestamp", LocalDateTime.now().toString(),
            "data", payload
        );
        messagingTemplate.convertAndSend("/topic/reports", event);
    }

    // ── Broadcast a volunteer status change ───────────────────────────────
    public void broadcastVolunteerUpdate(String action, Long volunteerId, Map<String, Object> payload) {
        Map<String, Object> event = Map.of(
            "event", "VOLUNTEER_" + action.toUpperCase(),
            "volunteerId", volunteerId,
            "timestamp", LocalDateTime.now().toString(),
            "data", payload
        );
        messagingTemplate.convertAndSend("/topic/volunteers", event);
    }

    // ── Broadcast application decision ────────────────────────────────────
    public void broadcastApplicationUpdate(Long appId, String status, String adminNote) {
        Map<String, Object> event = Map.of(
            "event", "APPLICATION_RESPONDED",
            "applicationId", appId,
            "status", status,
            "adminNote", adminNote != null ? adminNote : "",
            "timestamp", LocalDateTime.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/applications", event);
    }

    // ── Broadcast a location pin update (for live map) ────────────────────
    public void broadcastLocationUpdate(String entityType, Long id, double lat, double lon, String label) {
        Map<String, Object> event = Map.of(
            "event", "LOCATION_UPDATE",
            "entityType", entityType,   // "DISASTER" or "VOLUNTEER"
            "id", id,
            "latitude", lat,
            "longitude", lon,
            "label", label,
            "timestamp", LocalDateTime.now().toString()
        );
        messagingTemplate.convertAndSend("/topic/locations", event);
    }

    // ── Broadcast refreshed dashboard stats ───────────────────────────────
    public void broadcastStats(Map<String, Object> stats) {
        messagingTemplate.convertAndSend("/topic/stats", stats);
    }
}
