package com.ecm.core.controller;

import com.ecm.core.asynctask.AsyncTaskAcknowledgementService;
import com.ecm.core.asynctask.AsyncTaskGovernanceDomainSnapshot;
import com.ecm.core.asynctask.AsyncTaskGovernanceOverviewSnapshot;
import com.ecm.core.asynctask.AsyncTaskGovernanceService;
import com.ecm.core.asynctask.AsyncTaskLifecycleListSnapshot;
import com.ecm.core.asynctask.AsyncTaskLifecycleService;
import com.ecm.core.asynctask.AsyncTaskStatusSnapshot;
import com.ecm.core.asynctask.AsyncTaskSummarySnapshot;
import com.ecm.core.entity.AsyncTaskAcknowledgement;
import com.ecm.core.entity.AuditCategory;
import com.ecm.core.entity.AuditCategorySetting;
import com.ecm.core.entity.AuditLog;
import com.ecm.core.service.AnalyticsService;
import com.ecm.core.service.AnalyticsService.DailyActivityStats;
import com.ecm.core.service.AnalyticsService.MimeTypeStats;
import com.ecm.core.service.AnalyticsService.SystemSummaryStats;
import com.ecm.core.service.AnalyticsService.UserActivityStats;
import com.ecm.core.service.AuditExportAsyncTaskRegistry;
import com.ecm.core.service.AuditExportAsyncTaskRegistry.AuditExportAsyncStatus;
import com.ecm.core.service.AuditExportAsyncTaskRegistry.AuditExportAsyncSummary;
import com.ecm.core.service.AuditExportAsyncTaskRegistry.AuditExportAsyncTask;
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
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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

    private static final int MAX_AUDIT_EXPORT_ASYNC_LIST_LIMIT = 100;

    private final AnalyticsService analyticsService;
    private final AuditService auditService;
    private final AuditExportAsyncTaskRegistry auditExportAsyncTaskRegistry;
    private final AsyncTaskGovernanceService asyncTaskGovernanceService;
    private final AsyncTaskLifecycleService asyncTaskLifecycleService;
    private final AsyncTaskAcknowledgementService asyncTaskAcknowledgementService;

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
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID nodeId,
            @RequestParam(defaultValue = "30") int days) {
        AuditExportRequest request = resolveAuditExportRequest(from, to, preset, username, eventType, category, nodeId, days);
        AuditExportPayload payload = generateAuditExportPayload(request);
        return buildAuditExportCsvResponse(payload.csvContent(), payload.filename(), payload.rowCount());
    }

    @PostMapping("/audit/export-async")
    @Operation(summary = "Start Async Audit Logs Export", description = "Create an asynchronous audit logs CSV export task")
    public ResponseEntity<AuditExportAsyncCreateResponse> startAuditLogsExportAsync(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID nodeId,
            @RequestParam(defaultValue = "30") int days) {
        AuditExportRequest requestSnapshot = resolveAuditExportRequest(from, to, preset, username, eventType, category, nodeId, days);
        AuditExportAsyncTask task = auditExportAsyncTaskRegistry.createTask();

        CompletableFuture.runAsync(() -> runAuditExportAsyncTask(task.taskId(), requestSnapshot));

        return ResponseEntity.ok(new AuditExportAsyncCreateResponse(
            task.taskId(),
            task.status().name(),
            task.createdAt()
        ));
    }

    @GetMapping("/audit/export-async")
    @Operation(summary = "List Async Audit Export Tasks", description = "List recent asynchronous audit export tasks")
    public ResponseEntity<AuditExportAsyncListResponse> listAuditLogsExportAsyncTasks(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status) {
        int boundedLimit = clamp(limit, 1, MAX_AUDIT_EXPORT_ASYNC_LIST_LIMIT);
        AuditExportAsyncStatus statusFilter = parseAuditExportAsyncStatus(status);
        List<AuditExportAsyncStatusResponse> items = auditExportAsyncTaskRegistry.list(boundedLimit, statusFilter).stream()
            .map(this::toAuditExportAsyncStatusResponse)
            .toList();
        return ResponseEntity.ok(new AuditExportAsyncListResponse(items.size(), items));
    }

    @GetMapping("/audit/export-async/{taskId}")
    @Operation(summary = "Get Async Audit Export Task Status", description = "Get status/details for an asynchronous audit export task")
    public ResponseEntity<AuditExportAsyncStatusResponse> getAuditLogsExportAsyncTaskStatus(
            @PathVariable String taskId) {
        AuditExportAsyncTask task = auditExportAsyncTaskRegistry.get(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toAuditExportAsyncStatusResponse(task));
    }

    @GetMapping("/audit/export-async/summary")
    @Operation(
        summary = "Audit Async Export Task Summary",
        description = "Get aggregate async audit export task counts by status and task lifecycle class."
    )
    public ResponseEntity<AuditExportAsyncSummaryResponse> getAuditLogsExportAsyncTaskSummary(
            @RequestParam(required = false) String status) {
        AuditExportAsyncStatus statusFilter = parseAuditExportAsyncStatus(status);
        return ResponseEntity.ok(toAuditExportAsyncSummaryResponse(auditExportAsyncTaskRegistry.summary(statusFilter)));
    }

    @PostMapping("/audit/export-async/{taskId}/cancel")
    @Operation(summary = "Cancel Async Audit Export Task", description = "Cancel a queued/running asynchronous audit export task")
    public ResponseEntity<AuditExportAsyncStatusResponse> cancelAuditLogsExportAsyncTask(
            @PathVariable String taskId) {
        AuditExportAsyncTask existing = auditExportAsyncTaskRegistry.get(taskId);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (existing.isTerminal()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(toAuditExportAsyncStatusResponse(existing));
        }
        AuditExportAsyncTask updated = auditExportAsyncTaskRegistry.cancel(taskId, "Cancelled by user");
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toAuditExportAsyncStatusResponse(updated));
    }

    @PostMapping("/audit/export-async/cancel-active")
    @Operation(
        summary = "Cancel Active Async Audit Export Tasks",
        description = "Cancel queued/running async export tasks, optionally filtered by QUEUED or RUNNING."
    )
    public ResponseEntity<AuditExportAsyncCancelActiveResponse> cancelActiveAuditLogsExportAsyncTasks(
            @RequestParam(required = false) String status) {
        AuditExportAsyncStatus statusFilter = parseAuditExportAsyncStatus(status);
        if (statusFilter != null
            && statusFilter != AuditExportAsyncStatus.QUEUED
            && statusFilter != AuditExportAsyncStatus.RUNNING) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "status filter only supports active states: QUEUED, RUNNING"
            );
        }

        long cancelledCount = auditExportAsyncTaskRegistry.cancelActive(statusFilter, "Cancelled by user");
        long remainingActiveCount = auditExportAsyncTaskRegistry.activeCount();

        String statusFilterName = statusFilter != null ? statusFilter.name() : null;
        String message = cancelledCount > 0
            ? statusFilterName == null
                ? String.format("Cancelled %d active async audit export tasks", cancelledCount)
                : String.format("Cancelled %d async audit export tasks with status %s", cancelledCount, statusFilterName)
            : statusFilterName == null
                ? "No active async audit export tasks to cancel"
                : String.format("No active async audit export tasks with status %s to cancel", statusFilterName);

        return ResponseEntity.ok(new AuditExportAsyncCancelActiveResponse(
            cancelledCount,
            remainingActiveCount,
            statusFilterName,
            message
        ));
    }

    @GetMapping(value = "/audit/export-async/{taskId}/download", produces = "text/csv")
    @Operation(summary = "Download Async Audit Export Result", description = "Download CSV attachment for a completed asynchronous export task")
    public ResponseEntity<byte[]> downloadAuditLogsExportAsyncTaskResult(
            @PathVariable String taskId) {
        AuditExportAsyncTask task = auditExportAsyncTaskRegistry.get(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        if (task.status() != AuditExportAsyncStatus.COMPLETED
            || task.csvContent() == null
            || task.filename() == null
            || task.rowCount() == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        return buildAuditExportCsvResponse(task.csvContent().clone(), task.filename(), task.rowCount());
    }

    @PostMapping("/audit/export-async/cleanup")
    @Operation(
        summary = "Cleanup Async Audit Export Tasks",
        description = "Delete async export tasks by terminal state or by a specific terminal status."
    )
    public ResponseEntity<AuditExportAsyncCleanupResponse> cleanupAuditLogsExportAsyncTasks(
        @RequestParam(required = false) String status
    ) {
        AuditExportAsyncStatus statusFilter = parseAuditExportAsyncStatus(status);
        if (statusFilter == AuditExportAsyncStatus.QUEUED || statusFilter == AuditExportAsyncStatus.RUNNING) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "status filter only supports terminal states: COMPLETED, CANCELLED, FAILED"
            );
        }

        long deletedCount = auditExportAsyncTaskRegistry.cleanupTerminal(statusFilter);
        int remainingCount = auditExportAsyncTaskRegistry.size();

        String statusFilterName = statusFilter != null ? statusFilter.name() : null;
        String message = deletedCount > 0
            ? statusFilterName == null
                ? String.format("Deleted %d terminal async audit export tasks", deletedCount)
                : String.format("Deleted %d async audit export tasks with status %s", deletedCount, statusFilterName)
            : statusFilterName == null
                ? "No terminal async audit export tasks to delete"
                : String.format("No async audit export tasks with status %s to delete", statusFilterName);

        return ResponseEntity.ok(new AuditExportAsyncCleanupResponse(
            deletedCount,
            remainingCount,
            statusFilterName,
            message
        ));
    }

    @GetMapping("/audit/search")
    @Operation(summary = "Search Audit Logs", description = "Search audit logs with optional filters")
    public ResponseEntity<org.springframework.data.domain.Page<AuditLog>> searchAuditLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) UUID nodeId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        LocalDateTime fromTime = parseOptionalAuditDateTime(from, "from");
        LocalDateTime toTime = parseOptionalAuditDateTime(to, "to");
        var pageable = org.springframework.data.domain.PageRequest.of(page, size);
        AuditCategory categoryFilter = AuditCategory.fromString(category);
        return ResponseEntity.ok(analyticsService.searchAuditLogs(username, eventType, categoryFilter, nodeId, fromTime, toTime, pageable));
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

    @GetMapping("/audit/event-types")
    @Operation(summary = "Audit Event Types", description = "List event types with counts for filtering")
    public ResponseEntity<List<AnalyticsService.AuditEventTypeCount>> getAuditEventTypes(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(analyticsService.getAuditEventTypes(limit));
    }

    @GetMapping("/audit/report")
    @Operation(summary = "Audit Report Summary", description = "Get audit summary counts grouped by category")
    public ResponseEntity<Map<String, Object>> getAuditReportSummary(
            @RequestParam(name = "days", defaultValue = "30") int days) {
        AnalyticsService.AuditReportSummary summary = analyticsService.getAuditReportSummary(days);
        Map<String, Long> categoryCounts = new java.util.LinkedHashMap<>();
        summary.countsByCategory().forEach((category, count) ->
            categoryCounts.put(category.name(), count));
        return ResponseEntity.ok(Map.of(
            "windowDays", summary.windowDays(),
            "totalEvents", summary.totalEvents(),
            "countsByCategory", categoryCounts
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

    private AuditExportRequest resolveAuditExportRequest(
            String from,
            String to,
            String preset,
            String username,
            String eventType,
            String category,
            UUID nodeId,
            int days) {
        validateAuditExportPreset(preset, username, eventType);

        AuditExportRange range = resolveAuditExportRange(from, to, preset, days);
        validateAuditExportRange(range.from(), range.to());

        return new AuditExportRequest(
            range.from(),
            range.to(),
            username,
            eventType,
            nodeId,
            AuditCategory.fromString(category)
        );
    }

    private void validateAuditExportPreset(String preset, String username, String eventType) {
        if (preset == null) {
            return;
        }
        if ("user".equalsIgnoreCase(preset) && (username == null || username.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username is required for user preset");
        }
        if ("event".equalsIgnoreCase(preset) && (eventType == null || eventType.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventType is required for event preset");
        }
    }

    private void validateAuditExportRange(LocalDateTime fromTime, LocalDateTime toTime) {
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
    }

    private AuditExportPayload generateAuditExportPayload(AuditExportRequest request) {
        AnalyticsService.AuditExportResult exportResult = analyticsService.exportAuditLogsCsv(
            request.from(),
            request.to(),
            request.username(),
            request.eventType(),
            request.nodeId(),
            request.category()
        );
        String filename = String.format("audit_logs_%s_to_%s.csv",
            request.from().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
            request.to().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        return new AuditExportPayload(
            filename,
            exportResult.csvContent().getBytes(StandardCharsets.UTF_8),
            exportResult.rowCount()
        );
    }

    private ResponseEntity<byte[]> buildAuditExportCsvResponse(byte[] csvContent, String filename, long rowCount) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(filename, StandardCharsets.UTF_8)
            .build());
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.add("X-Audit-Export-Count", String.valueOf(rowCount));

        return ResponseEntity.ok()
            .headers(headers)
            .body(csvContent);
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

    private void runAuditExportAsyncTask(String taskId, AuditExportRequest request) {
        AuditExportAsyncTask current = auditExportAsyncTaskRegistry.markRunning(taskId);
        if (current == null || current.status() == AuditExportAsyncStatus.CANCELLED) {
            return;
        }
        try {
            AuditExportPayload payload = generateAuditExportPayload(request);
            auditExportAsyncTaskRegistry.complete(taskId, payload.filename(), payload.csvContent(), payload.rowCount());
        } catch (Exception e) {
            auditExportAsyncTaskRegistry.fail(taskId, resolveAsyncAuditExportErrorMessage(e));
        }
    }

    private AuditExportAsyncSummaryResponse toAuditExportAsyncSummaryResponse(AuditExportAsyncSummary summary) {
        return new AuditExportAsyncSummaryResponse(
            summary.totalCount(),
            summary.queuedCount(),
            summary.runningCount(),
            summary.completedCount(),
            summary.cancelledCount(),
            summary.failedCount(),
            summary.activeCount(),
            summary.terminalCount()
        );
    }

    private AuditExportAsyncStatusResponse toAuditExportAsyncStatusResponse(AuditExportAsyncTask task) {
        return new AuditExportAsyncStatusResponse(
            task.taskId(),
            task.status().name(),
            task.error(),
            task.createdAt(),
            task.finishedAt(),
            task.status() == AuditExportAsyncStatus.COMPLETED ? task.filename() : null,
            task.status() == AuditExportAsyncStatus.COMPLETED ? task.rowCount() : null
        );
    }

    private static String resolveAsyncAuditExportErrorMessage(Exception e) {
        if (e == null) {
            return "Unknown export error";
        }
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private AuditExportAsyncStatus parseAuditExportAsyncStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return AuditExportAsyncStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown async export status: " + status);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @GetMapping("/async-governance/overview")
    @Operation(
        summary = "Async Task Governance Overview",
        description = "Get a cross-center async task governance overview for audit, ops, search, preview, and batch download domains."
    )
    public ResponseEntity<AsyncExportGovernanceOverviewResponse> getAsyncExportGovernanceOverview() {
        return ResponseEntity.ok(
            toAsyncExportGovernanceOverviewResponse(asyncTaskGovernanceService.buildOverview())
        );
    }

    @GetMapping("/async-governance/tasks")
    @Operation(
        summary = "Recent async task lifecycle list",
        description = "List recent async tasks across audit, ops, search, preview, and batch download domains using a shared lifecycle contract."
    )
    public ResponseEntity<AsyncTaskLifecycleListResponse> listRecentAsyncGovernanceTasks(
        @RequestParam(required = false) Integer maxItems,
        @RequestParam(required = false) Integer skipCount,
        @RequestParam(required = false) String domain,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "false") boolean includeAcknowledged
    ) {
        return ResponseEntity.ok(
            toAsyncTaskLifecycleListResponse(
                asyncTaskLifecycleService.listRecentTasks(maxItems, skipCount, domain, status, includeAcknowledged)
            )
        );
    }

    @PostMapping("/async-governance/tasks/acknowledge")
    @Operation(
        summary = "Acknowledge recent async task",
        description = "Persist an acknowledgement for a recent async task so operator views can hide or restore it."
    )
    public ResponseEntity<AsyncTaskLifecycleAcknowledgementResponse> acknowledgeRecentAsyncGovernanceTask(
        @RequestBody(required = false) AsyncTaskLifecycleAcknowledgementRequest request
    ) {
        AsyncTaskStatusSnapshot task = requireRecentAsyncGovernanceTask(request);
        AsyncTaskAcknowledgement acknowledgement = asyncTaskAcknowledgementService.acknowledge(task);
        AsyncTaskStatusSnapshot acknowledgedTask = asyncTaskAcknowledgementService.applyAcknowledgement(task, acknowledgement);
        return ResponseEntity.ok(
            new AsyncTaskLifecycleAcknowledgementResponse(
                acknowledgedTask.domainKey(),
                acknowledgedTask.taskId(),
                acknowledgedTask.fingerprint(),
                acknowledgedTask.acknowledged(),
                acknowledgedTask.acknowledgedAt() != null ? acknowledgedTask.acknowledgedAt().toString() : null,
                !task.acknowledged()
            )
        );
    }

    @PostMapping("/async-governance/tasks/unacknowledge")
    @Operation(
        summary = "Restore acknowledged async task",
        description = "Remove a persisted acknowledgement so a recent async task is visible in operator views again."
    )
    public ResponseEntity<AsyncTaskLifecycleAcknowledgementResponse> unacknowledgeRecentAsyncGovernanceTask(
        @RequestBody(required = false) AsyncTaskLifecycleAcknowledgementRequest request
    ) {
        String fingerprint = resolveRecentAsyncGovernanceFingerprint(request);
        boolean changed = asyncTaskAcknowledgementService.unacknowledge(fingerprint);
        return ResponseEntity.ok(
            new AsyncTaskLifecycleAcknowledgementResponse(
                request != null ? request.domainKey() : null,
                request != null ? request.taskId() : null,
                fingerprint,
                false,
                null,
                changed
            )
        );
    }

    private AsyncExportGovernanceOverviewResponse toAsyncExportGovernanceOverviewResponse(
        AsyncTaskGovernanceOverviewSnapshot overview
    ) {
        AsyncTaskSummarySnapshot summary = overview.summary();
        List<AsyncExportGovernanceDomainResponse> domains = overview.domains().stream()
            .map(this::toAsyncExportGovernanceDomainResponse)
            .toList();
        return new AsyncExportGovernanceOverviewResponse(
            overview.generatedAt(),
            overview.overallStatus().name(),
            overview.overallRiskLevel().name(),
            overview.totalDomains(),
            overview.degradedDomainCount(),
            summary.totalCount(),
            summary.activeCount(),
            summary.terminalCount(),
            summary.queuedCount(),
            summary.runningCount(),
            summary.completedCount(),
            summary.cancelledCount(),
            summary.failedCount(),
            summary.timedOutCount(),
            summary.expiredCount(),
            summary.failureRate(),
            domains
        );
    }

    private AsyncExportGovernanceDomainResponse toAsyncExportGovernanceDomainResponse(
        AsyncTaskGovernanceDomainSnapshot domain
    ) {
        AsyncTaskSummarySnapshot summary = domain.summary();
        return new AsyncExportGovernanceDomainResponse(
            domain.key(),
            domain.label(),
            domain.status().name(),
            domain.riskLevel().name(),
            domain.error(),
            summary.totalCount(),
            summary.activeCount(),
            summary.terminalCount(),
            summary.queuedCount(),
            summary.runningCount(),
            summary.completedCount(),
            summary.cancelledCount(),
            summary.failedCount(),
            summary.timedOutCount(),
            summary.expiredCount(),
            summary.failureRate()
        );
    }

    private AsyncTaskLifecycleListResponse toAsyncTaskLifecycleListResponse(
        AsyncTaskLifecycleListSnapshot snapshot
    ) {
        return new AsyncTaskLifecycleListResponse(
            snapshot.generatedAt() != null ? snapshot.generatedAt().toString() : null,
            snapshot.domainFilter(),
            snapshot.statusFilter(),
            snapshot.count(),
            snapshot.totalCount(),
            new AsyncTaskLifecyclePagingResponse(
                snapshot.skipCount(),
                snapshot.maxItems(),
                snapshot.totalCount(),
                snapshot.hasMoreItems()
            ),
            snapshot.items().stream()
                .map(this::toAsyncTaskLifecycleItemResponse)
                .toList()
        );
    }

    private AsyncTaskLifecycleItemResponse toAsyncTaskLifecycleItemResponse(AsyncTaskStatusSnapshot task) {
        return new AsyncTaskLifecycleItemResponse(
            task.domainKey(),
            task.domainLabel(),
            task.taskId(),
            task.status(),
            task.error(),
            task.createdAt() != null ? task.createdAt().toString() : null,
            task.startedAt() != null ? task.startedAt().toString() : null,
            task.updatedAt() != null ? task.updatedAt().toString() : null,
            task.timeoutAt() != null ? task.timeoutAt().toString() : null,
            task.expiresAt() != null ? task.expiresAt().toString() : null,
            task.finishedAt() != null ? task.finishedAt().toString() : null,
            task.filename(),
            task.createdBy(),
            task.updatedBy(),
            task.fingerprint(),
            task.acknowledged(),
            task.acknowledgedAt() != null ? task.acknowledgedAt().toString() : null,
            task.actions() != null ? task.actions().cancelUrl() : null,
            task.actions() != null ? task.actions().downloadUrl() : null,
            task.actions() != null ? task.actions().cleanupUrl() : null,
            task.actions() != null && task.actions().cancellable(),
            task.actions() != null && task.actions().cleanupEligible(),
            task.actions() != null && task.actions().downloadReady()
        );
    }

    private AsyncTaskStatusSnapshot requireRecentAsyncGovernanceTask(AsyncTaskLifecycleAcknowledgementRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Async lifecycle acknowledgement request body is required");
        }
        return asyncTaskLifecycleService.findRecentTask(request.domainKey(), request.taskId(), request.fingerprint());
    }

    private String resolveRecentAsyncGovernanceFingerprint(AsyncTaskLifecycleAcknowledgementRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Async lifecycle acknowledgement request body is required");
        }
        if (request.fingerprint() != null && !request.fingerprint().isBlank()) {
            return request.fingerprint().trim();
        }
        return requireRecentAsyncGovernanceTask(request).fingerprint();
    }

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

    private record AuditExportRequest(
        LocalDateTime from,
        LocalDateTime to,
        String username,
        String eventType,
        UUID nodeId,
        AuditCategory category
    ) {}

    private record AuditExportPayload(
        String filename,
        byte[] csvContent,
        long rowCount
    ) {}

    private record AuditExportAsyncCreateResponse(
        String taskId,
        String status,
        LocalDateTime createdAt
    ) {}

    private record AuditExportAsyncListResponse(
        int count,
        List<AuditExportAsyncStatusResponse> items
    ) {}

    private record AuditExportAsyncStatusResponse(
        String taskId,
        String status,
        String error,
        LocalDateTime createdAt,
        LocalDateTime finishedAt,
        String filename,
        Long rowCount
    ) {}

    private record AuditExportAsyncSummaryResponse(
        long totalCount,
        long queuedCount,
        long runningCount,
        long completedCount,
        long cancelledCount,
        long failedCount,
        long activeCount,
        long terminalCount
    ) {}

    private record AuditExportAsyncCleanupResponse(
        long deletedCount,
        long remainingCount,
        String statusFilter,
        String message
    ) {}

    private record AuditExportAsyncCancelActiveResponse(
        long cancelledCount,
        long remainingActiveCount,
        String statusFilter,
        String message
    ) {}

    private record AsyncExportGovernanceOverviewResponse(
        LocalDateTime generatedAt,
        String overallStatus,
        String overallRiskLevel,
        int totalDomains,
        int degradedDomainCount,
        long totalCount,
        long activeCount,
        long terminalCount,
        long queuedCount,
        long runningCount,
        long completedCount,
        long cancelledCount,
        long failedCount,
        long timedOutCount,
        long expiredCount,
        double failureRate,
        List<AsyncExportGovernanceDomainResponse> domains
    ) {}

    private record AsyncExportGovernanceDomainResponse(
        String key,
        String label,
        String status,
        String riskLevel,
        String error,
        long totalCount,
        long activeCount,
        long terminalCount,
        long queuedCount,
        long runningCount,
        long completedCount,
        long cancelledCount,
        long failedCount,
        long timedOutCount,
        long expiredCount,
        double failureRate
    ) {}

    private record AsyncTaskLifecycleListResponse(
        String generatedAt,
        String domainFilter,
        String statusFilter,
        int count,
        long totalCount,
        AsyncTaskLifecyclePagingResponse paging,
        List<AsyncTaskLifecycleItemResponse> items
    ) {}

    private record AsyncTaskLifecyclePagingResponse(
        int skipCount,
        int maxItems,
        long totalItems,
        boolean hasMoreItems
    ) {}

    private record AsyncTaskLifecycleItemResponse(
        String domainKey,
        String domainLabel,
        String taskId,
        String status,
        String error,
        String createdAt,
        String startedAt,
        String updatedAt,
        String timeoutAt,
        String expiresAt,
        String finishedAt,
        String filename,
        String createdBy,
        String updatedBy,
        String fingerprint,
        boolean acknowledged,
        String acknowledgedAt,
        String cancelUrl,
        String downloadUrl,
        String cleanupUrl,
        boolean cancellable,
        boolean cleanupEligible,
        boolean downloadReady
    ) {}

    private record AsyncTaskLifecycleAcknowledgementRequest(
        String domainKey,
        String taskId,
        String fingerprint
    ) {}

    private record AsyncTaskLifecycleAcknowledgementResponse(
        String domainKey,
        String taskId,
        String fingerprint,
        boolean acknowledged,
        String acknowledgedAt,
        boolean changed
    ) {}
}
