package com.ecm.core.controller;

import com.ecm.core.entity.AuditLog;
import com.ecm.core.service.AnalyticsService;
import com.ecm.core.service.AnalyticsService.DailyActivityStats;
import com.ecm.core.service.AnalyticsService.MimeTypeStats;
import com.ecm.core.service.AnalyticsService.SystemSummaryStats;
import com.ecm.core.service.AnalyticsService.UserActivityStats;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Analytics and Monitoring Controller
 *
 * Provides endpoints for system usage statistics, storage analysis,
 * and user activity reports.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "System statistics and reports")
@PreAuthorize("hasRole('ADMIN')")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    @Operation(summary = "System Summary", description = "Get overall system usage statistics")
    public ResponseEntity<SystemSummaryStats> getSummary() {
        return ResponseEntity.ok(analyticsService.getSystemSummary());
    }

    @GetMapping("/storage/mimetype")
    @Operation(summary = "Storage by Type", description = "Get storage usage breakdown by MIME type")
    public ResponseEntity<List<MimeTypeStats>> getStorageByMimeType() {
        return ResponseEntity.ok(analyticsService.getStorageByMimeType());
    }

    @GetMapping("/activity/daily")
    @Operation(summary = "Daily Activity", description = "Get daily activity volume for the last 30 days")
    public ResponseEntity<List<DailyActivityStats>> getDailyActivity(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(analyticsService.getDailyActivity(days));
    }

    @GetMapping("/users/top")
    @Operation(summary = "Top Users", description = "Get most active users")
    public ResponseEntity<List<UserActivityStats>> getTopUsers(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(analyticsService.getTopUsers(limit));
    }

    @GetMapping("/audit/recent")
    @Operation(summary = "Recent Audit Logs", description = "Get recent system activity logs")
    public ResponseEntity<List<AuditLog>> getRecentActivity(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(analyticsService.getRecentActivity(limit));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Full Dashboard", description = "Get aggregated dashboard data")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        return ResponseEntity.ok(Map.of(
            "summary", analyticsService.getSystemSummary(),
            "storage", analyticsService.getStorageByMimeType(),
            "activity", analyticsService.getDailyActivity(14), // Last 2 weeks
            "topUsers", analyticsService.getTopUsers(5)
        ));
    }
}
