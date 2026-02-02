package com.ecm.core.controller;

import com.ecm.core.entity.AuditCategory;
import com.ecm.core.entity.AuditCategorySetting;
import com.ecm.core.entity.AuditLog;
import com.ecm.core.service.AnalyticsService;
import com.ecm.core.service.AnalyticsService.DailyActivityStats;
import com.ecm.core.service.AnalyticsService.MimeTypeStats;
import com.ecm.core.service.AnalyticsService.SystemSummaryStats;
import com.ecm.core.service.AnalyticsService.UserActivityStats;
import com.ecm.core.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final AuditService auditService;

    @Value("${ecm.audit.export.max-range-days:90}")
    private int auditExportMaxRangeDays;

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
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "30") int days) {

        if (preset != null) {
            if ("user".equalsIgnoreCase(preset) && (username == null || username.isBlank())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username is required for user preset");
            }
            if ("event".equalsIgnoreCase(preset) && (eventType == null || eventType.isBlank())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventType is required for event preset");
            }
        }

        AuditExportRange range = resolveAuditExportRange(from, to, preset, days);
        LocalDateTime fromTime = range.from();
        LocalDateTime toTime = range.to();
        if (!fromTime.isBefore(toTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to");
        }
        if (auditExportMaxRangeDays > 0
            && Duration.between(fromTime, toTime).compareTo(Duration.ofDays(auditExportMaxRangeDays)) > 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Range exceeds maximum of " + auditExportMaxRangeDays + " days"
            );
        }

        AnalyticsService.AuditExportResult exportResult = analyticsService.exportAuditLogsCsv(fromTime, toTime, username, eventType);
        String filename = String.format("audit_logs_%s_to_%s.csv",
            fromTime.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
            toTime.format(DateTimeFormatter.ofPattern("yyyyMMdd")));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(filename, StandardCharsets.UTF_8)
            .build());
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.add("X-Audit-Export-Count", String.valueOf(exportResult.rowCount()));

        return ResponseEntity.ok()
            .headers(headers)
            .body(exportResult.csvContent().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/audit/search")
    @Operation(summary = "Search Audit Logs", description = "Search audit logs with optional filters")
    public ResponseEntity<org.springframework.data.domain.Page<AuditLog>> searchAuditLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        LocalDateTime fromTime = parseOptionalAuditDateTime(from, "from");
        LocalDateTime toTime = parseOptionalAuditDateTime(to, "to");
        var pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return ResponseEntity.ok(analyticsService.searchAuditLogs(username, eventType, fromTime, toTime, pageable));
    }

    @GetMapping("/audit/presets")
    @Operation(summary = "Audit Export Presets", description = "List available audit export presets")
    public ResponseEntity<List<Map<String, Object>>> getAuditExportPresets() {
        return ResponseEntity.ok(List.of(
            Map.of("id", "last24h", "label", "Last 24 hours", "requiresUser", false, "requiresEventType", false),
            Map.of("id", "last7d", "label", "Last 7 days", "requiresUser", false, "requiresEventType", false),
            Map.of("id", "last30d", "label", "Last 30 days", "requiresUser", false, "requiresEventType", false),
            Map.of("id", "user", "label", "User activity (last N days)", "requiresUser", true, "requiresEventType", false),
            Map.of("id", "event", "label", "Event type (last N days)", "requiresUser", false, "requiresEventType", true)
        ));
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

    private LocalDateTime parseOptionalAuditDateTime(String value, String paramName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseAuditExportDateTime(value, paramName);
    }

    private AuditExportRange resolveAuditExportRange(String from, String to, String preset, int days) {
        if (preset == null || preset.isBlank()) {
            LocalDateTime fromTime = parseAuditExportDateTime(from, "from");
            LocalDateTime toTime = parseAuditExportDateTime(to, "to");
            return new AuditExportRange(fromTime, toTime);
        }

        LocalDateTime now = LocalDateTime.now();
        switch (preset.toLowerCase()) {
            case "last24h" -> {
                return new AuditExportRange(now.minusHours(24), now);
            }
            case "last7d" -> {
                return new AuditExportRange(now.minusDays(7), now);
            }
            case "last30d" -> {
                return new AuditExportRange(now.minusDays(30), now);
            }
            case "user", "event" -> {
                int safeDays = Math.max(1, days);
                return new AuditExportRange(now.minusDays(safeDays), now);
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown preset: " + preset);
        }
    }

    private record AuditExportRange(LocalDateTime from, LocalDateTime to) {}

    @GetMapping("/audit/retention")
    @Operation(summary = "Audit Retention Info", description = "Get audit log retention policy information")
    public ResponseEntity<Map<String, Object>> getAuditRetentionInfo() {
        return ResponseEntity.ok(Map.of(
            "retentionDays", analyticsService.getAuditRetentionDays(),
            "expiredLogCount", analyticsService.getExpiredAuditLogCount(),
            "exportMaxRangeDays", auditExportMaxRangeDays
        ));
    }

    @GetMapping("/audit/categories")
    @Operation(summary = "Audit Categories", description = "List audit category toggles")
    public ResponseEntity<List<AuditCategoryResponse>> getAuditCategories() {
        return ResponseEntity.ok(toAuditCategoryResponse(auditService.getCategorySettings()));
    }

    @PutMapping("/audit/categories")
    @Operation(summary = "Update Audit Categories", description = "Enable or disable audit categories")
    public ResponseEntity<List<AuditCategoryResponse>> updateAuditCategories(
            @RequestBody List<AuditCategoryRequest> updates) {
        Map<AuditCategory, Boolean> updateMap = new java.util.EnumMap<>(AuditCategory.class);
        if (updates != null) {
            for (AuditCategoryRequest update : updates) {
                AuditCategory category = AuditCategory.fromString(update.category());
                if (category == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown audit category: " + update.category());
                }
                updateMap.put(category, update.enabled());
            }
        }
        List<AuditCategorySetting> updated = auditService.updateCategorySettings(updateMap);
        return ResponseEntity.ok(toAuditCategoryResponse(updated));
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

    private static List<AuditCategoryResponse> toAuditCategoryResponse(List<AuditCategorySetting> settings) {
        if (settings == null) {
            return List.of();
        }
        return settings.stream()
            .sorted(Comparator.comparingInt(setting -> setting.getCategory().ordinal()))
            .map(setting -> new AuditCategoryResponse(setting.getCategory().name(), setting.isEnabled()))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private record AuditCategoryRequest(String category, boolean enabled) {}

    private record AuditCategoryResponse(String category, boolean enabled) {}
}
