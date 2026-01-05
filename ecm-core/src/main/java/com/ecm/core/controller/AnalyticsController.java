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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

    @GetMapping("/rules/recent")
    @Operation(summary = "Recent Rule Activity", description = "Get recent rule execution audit logs")
    public ResponseEntity<List<AuditLog>> getRecentRuleActivity(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(analyticsService.getRecentRuleActivity(limit));
    }

    @GetMapping("/rules/summary")
    @Operation(summary = "Rule Execution Summary", description = "Get rule execution statistics for a time window")
    public ResponseEntity<AnalyticsService.RuleExecutionSummary> getRuleExecutionSummary(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(analyticsService.getRuleExecutionSummary(days));
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

    @GetMapping("/audit/export")
    @Operation(summary = "Export Audit Logs", description = "Export audit logs as CSV within a time range")
    public ResponseEntity<byte[]> exportAuditLogs(
            @RequestParam String from,
            @RequestParam String to) {

        LocalDateTime fromTime = parseAuditExportDateTime(from, "from");
        LocalDateTime toTime = parseAuditExportDateTime(to, "to");
        if (fromTime.isAfter(toTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
        }

        String csvContent = analyticsService.exportAuditLogsCsv(fromTime, toTime);
        String filename = String.format("audit_logs_%s_to_%s.csv",
            fromTime.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
            toTime.format(DateTimeFormatter.ofPattern("yyyyMMdd")));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(filename, StandardCharsets.UTF_8)
            .build());
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));

        return ResponseEntity.ok()
            .headers(headers)
            .body(csvContent.getBytes(StandardCharsets.UTF_8));
    }

    private LocalDateTime parseAuditExportDateTime(String value, String paramName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, paramName + " is required");
        }

        try {
            OffsetDateTime offsetDateTime = OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            return LocalDateTime.ofInstant(offsetDateTime.toInstant(), ZoneId.systemDefault());
        } catch (DateTimeParseException ignored) {
            // Fall back to local datetime without offset.
        }

        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + paramName + " datetime");
        }
    }

    @GetMapping("/audit/retention")
    @Operation(summary = "Audit Retention Info", description = "Get audit log retention policy information")
    public ResponseEntity<Map<String, Object>> getAuditRetentionInfo() {
        return ResponseEntity.ok(Map.of(
            "retentionDays", analyticsService.getAuditRetentionDays(),
            "expiredLogCount", analyticsService.getExpiredAuditLogCount()
        ));
    }

    @PostMapping("/audit/cleanup")
    @Operation(summary = "Trigger Audit Cleanup", description = "Manually trigger audit log cleanup based on retention policy")
    public ResponseEntity<Map<String, Object>> triggerAuditCleanup() {
        long deletedCount = analyticsService.manualCleanupExpiredAuditLogs();
        return ResponseEntity.ok(Map.of(
            "deletedCount", deletedCount,
            "retentionDays", analyticsService.getAuditRetentionDays(),
            "message", deletedCount > 0
                ? String.format("Deleted %d expired audit logs", deletedCount)
                : "No expired audit logs to delete"
        ));
    }
}
