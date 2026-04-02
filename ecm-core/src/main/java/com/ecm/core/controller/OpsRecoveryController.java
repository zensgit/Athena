package com.ecm.core.controller;

import com.ecm.core.asynctask.AsyncTaskSummaryAdapters;
import com.ecm.core.asynctask.AsyncTaskSummarySnapshot;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.AuditLog;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.preview.PreviewDeadLetterRegistry;
import com.ecm.core.preview.PreviewFailureClassifier;
import com.ecm.core.preview.PreviewQueueService;
import com.ecm.core.repository.AuditLogRepository;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.RenditionResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/ops/recovery")
@RequiredArgsConstructor
@Tag(name = "Ops Recovery", description = "Unified admin recovery control-plane for operational workflows")
@PreAuthorize("hasRole('ADMIN')")
public class OpsRecoveryController {

    private static final int DEFAULT_WINDOW_DAYS = 7;
    private static final int MAX_WINDOW_DAYS = 365;
    private static final int DEFAULT_BATCH_LIMIT = 100;
    private static final int MAX_BATCH_LIMIT = 500;
    private static final int DEFAULT_HISTORY_LIMIT = 20;
    private static final int MAX_HISTORY_LIMIT = 200;
    private static final int DEFAULT_HISTORY_EXPORT_LIMIT = 500;
    private static final int MAX_HISTORY_EXPORT_LIMIT = 2000;
    private static final int DEFAULT_HISTORY_EXPORT_ASYNC_LIST_LIMIT = 20;
    private static final int MAX_HISTORY_EXPORT_ASYNC_LIST_LIMIT = 100;
    private static final int MAX_HISTORY_EXPORT_ASYNC_TASKS = 100;
    private static final int DEFAULT_HISTORY_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT = 20;
    private static final int MAX_HISTORY_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT = 200;
    private static final int MAX_HISTORY_EXPORT_ASYNC_RETRY_SELECTED_IDS = 200;
    private static final int HISTORY_TREND_PAGE_LIMIT = 500;
    private static final int MAX_HISTORY_TREND_SCAN = 20000;
    private static final int DEFAULT_COMPARE_BREAKDOWN_LIMIT = 10;
    private static final int MAX_COMPARE_BREAKDOWN_LIMIT = 100;
    private static final String DEFAULT_COMPARE_BREAKDOWN_SORT = "DELTA_ABS_DESC";
    private static final int DEFAULT_COMPARE_ACTOR_LIMIT = 10;
    private static final int MAX_COMPARE_ACTOR_LIMIT = 100;
    private static final String DEFAULT_COMPARE_ACTOR_SORT = "DELTA_ABS_DESC";
    private static final int SCAN_LIMIT = 2000;
    private static final long ASYNC_TASK_ACTIVE_TIMEOUT_SECONDS = 1800L;
    private static final long ASYNC_TASK_RETENTION_SECONDS = 172800L;
    private static final String ASYNC_TASK_TIMED_OUT_MESSAGE = "Task timed out by lifecycle policy";
    private static final String ASYNC_TASK_EXPIRED_MESSAGE = "Task expired by retention policy";
    private static final String OPS_RECOVERY_EVENT_PREFIX = "OPS_RECOVERY_";
    private static final Set<String> SUPPORTED_HISTORY_MODES = Set.of(
        "QUEUE_BY_REASON",
        "QUEUE_BY_WINDOW",
        "CLEAR_BATCH",
        "CLEAR_BY_FILTER",
        "REPLAY_BATCH",
        "REPLAY_BY_FILTER",
        "DRY_RUN"
    );
    private static final Set<String> SUPPORTED_COMPARE_BREAKDOWN_SORTS = Set.of(
        "DELTA_ABS_DESC",
        "DELTA_DESC",
        "DELTA_ASC",
        "CURRENT_DESC",
        "PREVIOUS_DESC",
        "EVENT_TYPE_ASC"
    );
    private static final Set<String> SUPPORTED_COMPARE_ACTOR_SORTS = Set.of(
        "DELTA_ABS_DESC",
        "DELTA_DESC",
        "DELTA_ASC",
        "CURRENT_DESC",
        "PREVIOUS_DESC",
        "ACTOR_ASC"
    );

    private final DocumentRepository documentRepository;
    private final PreviewQueueService previewQueueService;
    private final PreviewDeadLetterRegistry previewDeadLetterRegistry;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final RenditionResourceService renditionResourceService;
    private final Map<String, RecoveryHistoryExportAsyncTask> historyExportAsyncTasks = new ConcurrentHashMap<>();
    private final Map<String, RecoveryHistoryExportAsyncSnapshot> historyExportAsyncTaskSnapshots = new ConcurrentHashMap<>();
    private final Deque<String> historyExportAsyncTaskOrder = new ArrayDeque<>();
    private final Object historyExportAsyncTaskLock = new Object();

    @GetMapping("/history")
    @Operation(
        summary = "List recent ops recovery execution history",
        description = "Returns recent admin recovery executions from audit logs."
    )
    public ResponseEntity<RecoveryHistoryResponseDto> listHistory(
        @RequestParam(required = false, defaultValue = "20") Integer limit,
        @RequestParam(required = false, defaultValue = "7") Integer days,
        @RequestParam(required = false, defaultValue = "0") Integer page,
        @RequestParam(required = false) String mode,
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String eventType
    ) {
        int normalizedLimit = clamp(limit != null ? limit : DEFAULT_HISTORY_LIMIT, 1, MAX_HISTORY_LIMIT);
        int normalizedDays = normalizeWindowDays(days != null ? days : DEFAULT_WINDOW_DAYS);
        int normalizedPage = Math.max(0, page != null ? page : 0);
        LocalDateTime from = resolveUpdatedSince(normalizedDays);
        String normalizedMode = normalizeHistoryMode(mode);
        String normalizedActor = normalizeActor(actor);
        String normalizedEventType = normalizeHistoryEventType(eventType);
        Page<AuditLog> historyPage = queryHistoryPage(
            normalizedLimit,
            from,
            normalizedMode,
            normalizedActor,
            normalizedEventType,
            normalizedPage
        );

        List<RecoveryHistoryItemDto> items = historyPage.getContent().stream()
            .map(this::toRecoveryHistoryItem)
            .toList();

        return ResponseEntity.ok(new RecoveryHistoryResponseDto(
            "PREVIEW",
            normalizedDays,
            normalizedLimit,
            normalizedPage,
            historyPage.getTotalPages(),
            historyPage.getTotalElements(),
            normalizedMode,
            normalizedActor,
            normalizedEventType,
            items
        ));
    }

    @GetMapping("/history/summary")
    @Operation(
        summary = "Summarize ops recovery execution history",
        description = "Returns grouped history counts by event type for current filters."
    )
    public ResponseEntity<RecoveryHistorySummaryResponseDto> summaryHistory(
        @RequestParam(required = false, defaultValue = "7") Integer days,
        @RequestParam(required = false) String mode,
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String eventType
    ) {
        return ResponseEntity.ok(queryHistorySummary(days, mode, actor, eventType));
    }

    @GetMapping("/history/summary/compare")
    @Operation(
        summary = "Compare current vs previous ops recovery summary window",
        description = "Returns KPI deltas between current and previous time windows under identical filters."
    )
    public ResponseEntity<RecoveryHistorySummaryCompareResponseDto> compareHistorySummary(
        @RequestParam(required = false, defaultValue = "7") Integer days,
        @RequestParam(required = false) String mode,
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String eventType
    ) {
        return ResponseEntity.ok(queryHistorySummaryCompare(days, mode, actor, eventType));
    }

    @GetMapping("/history/summary/compare/breakdown")
    @Operation(
        summary = "Compare current vs previous summary breakdown by mode",
        description = "Returns per-event compare deltas between current and previous windows under identical filters."
    )
    public ResponseEntity<RecoveryHistorySummaryCompareBreakdownResponseDto> compareHistorySummaryBreakdown(
        @RequestParam(required = false, defaultValue = "7") Integer days,
        @RequestParam(required = false) String mode,
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false, defaultValue = "10") Integer limit,
        @RequestParam(required = false, defaultValue = "DELTA_ABS_DESC") String sort
    ) {
        return ResponseEntity.ok(queryHistorySummaryCompareBreakdown(days, mode, actor, eventType, limit, sort));
    }

    @GetMapping("/history/summary/compare/actors")
    @Operation(
        summary = "Compare current vs previous summary by actor",
        description = "Returns per-actor compare deltas between current and previous windows under identical filters."
    )
    public ResponseEntity<RecoveryHistorySummaryCompareActorsResponseDto> compareHistorySummaryActors(
        @RequestParam(required = false, defaultValue = "7") Integer days,
        @RequestParam(required = false) String mode,
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false, defaultValue = "10") Integer limit,
        @RequestParam(required = false, defaultValue = "DELTA_ABS_DESC") String sort
    ) {
        return ResponseEntity.ok(queryHistorySummaryCompareActors(days, mode, actor, eventType, limit, sort));
    }

    @GetMapping("/history/summary/compare/actors/export")
    @Operation(
        summary = "Export current vs previous summary by actor",
        description = "Export actor compare metrics as CSV."
    )
    public ResponseEntity<byte[]> exportCompareHistorySummaryActorsCsv(
        @RequestParam(required = false, defaultValue = "7") Integer days,
        @RequestParam(required = false) String mode,
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false, defaultValue = "10") Integer limit,
        @RequestParam(required = false, defaultValue = "DELTA_ABS_DESC") String sort
    ) {
        RecoveryHistorySummaryCompareActorsResponseDto compareActors = queryHistorySummaryCompareActors(
            days,
            mode,
            actor,
            eventType,
            limit,
            sort
        );
        String csv = buildHistoryCompareActorsCsv(compareActors);
        String filename = String.format(
            "ops_recovery_history_compare_actors_%s.csv",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.add("X-Ops-Recovery-Compare-Actors-Count", String.valueOf(compareActors.items().size()));
        auditHistoryCompareActorsExport(
            compareActors.windowDays(),
            compareActors.modeFilter(),
            compareActors.actorFilter(),
            compareActors.eventTypeFilter(),
            compareActors.sortBy(),
            compareActors.requestedLimit(),
            compareActors.items().size()
        );
        return ResponseEntity.ok()
            .headers(headers)
            .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/history/summary/compare/breakdown/export")
    @Operation(
        summary = "Export current vs previous summary breakdown by mode",
        description = "Export compare breakdown metrics as CSV."
    )
    public ResponseEntity<byte[]> exportCompareHistorySummaryBreakdownCsv(
        @RequestParam(required = false, defaultValue = "7") Integer days,
        @RequestParam(required = false) String mode,
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false, defaultValue = "10") Integer limit,
        @RequestParam(required = false, defaultValue = "DELTA_ABS_DESC") String sort
    ) {
        RecoveryHistorySummaryCompareBreakdownResponseDto compareBreakdown = queryHistorySummaryCompareBreakdown(
            days,
            mode,
            actor,
            eventType,
            limit,
            sort
        );
        String csv = buildHistoryCompareBreakdownCsv(compareBreakdown);
        String filename = String.format(
            "ops_recovery_history_compare_breakdown_%s.csv",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.add("X-Ops-Recovery-Compare-Breakdown-Count", String.valueOf(compareBreakdown.items().size()));
        auditHistoryCompareBreakdownExport(
            compareBreakdown.windowDays(),
            compareBreakdown.modeFilter(),
            compareBreakdown.actorFilter(),
            compareBreakdown.eventTypeFilter(),
            compareBreakdown.items().size()
        );
        return ResponseEntity.ok()
            .headers(headers)
            .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/history/summary/compare/export")
    @Operation(
        summary = "Export current vs previous summary compare",
        description = "Export compare KPI metrics as CSV."
    )
    public ResponseEntity<byte[]> exportCompareHistorySummaryCsv(
        @RequestParam(required = false, defaultValue = "7") Integer days,
        @RequestParam(required = false) String mode,
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String eventType
    ) {
        RecoveryHistorySummaryCompareResponseDto compare = queryHistorySummaryCompare(days, mode, actor, eventType);
        String csv = buildHistoryCompareCsv(compare);
        String filename = String.format(
            "ops_recovery_history_compare_%s.csv",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.add("X-Ops-Recovery-Compare-Count", "1");
        auditHistoryCompareExport(
            compare.windowDays(),
            compare.modeFilter(),
            compare.actorFilter(),
            compare.eventTypeFilter(),
            compare.currentTotal(),
            compare.previousTotal(),
            compare.delta()
        );
        return ResponseEntity.ok()
            .headers(headers)
            .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/history/summary/trend")
    @Operation(
        summary = "Trend ops recovery history summary by day",
        description = "Returns daily history counts for current summary filters."
    )
    public ResponseEntity<RecoveryHistoryTrendResponseDto> summaryHistoryTrend(
        @RequestParam(required = false, defaultValue = "7") Integer days,
        @RequestParam(required = false) String mode,
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String eventType
    ) {
        return ResponseEntity.ok(queryHistoryTrend(days, mode, actor, eventType));
    }

    @GetMapping("/history/summary/trend/export")
    @Operation(
        summary = "Export ops recovery history trend",
        description = "Export daily grouped history trend as CSV."
    )
    public ResponseEntity<byte[]> exportHistorySummaryTrendCsv(
        @RequestParam(required = false, defaultValue = "7") Integer days,
        @RequestParam(required = false) String mode,
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String eventType
    ) {
        RecoveryHistoryTrendResponseDto trend = queryHistoryTrend(days, mode, actor, eventType);
        String csv = buildHistoryTrendCsv(trend.items());
        String filename = String.format(
            "ops_recovery_history_trend_%s.csv",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.add("X-Ops-Recovery-Trend-Count", String.valueOf(trend.items().size()));
        auditHistoryTrendExport(
            trend.windowDays(),
            trend.modeFilter(),
            trend.actorFilter(),
            trend.eventTypeFilter(),
            trend.items().size()
        );
        return ResponseEntity.ok()
            .headers(headers)
            .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/history/summary/export")
    @Operation(
        summary = "Export ops recovery history summary",
        description = "Export grouped history summary as CSV."
    )
    public ResponseEntity<byte[]> exportHistorySummaryCsv(
        @RequestParam(required = false, defaultValue = "7") Integer days,
        @RequestParam(required = false) String mode,
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String eventType
    ) {
        RecoveryHistorySummaryResponseDto summary = queryHistorySummary(days, mode, actor, eventType);
        String csv = buildHistorySummaryCsv(summary.items(), summary.actorItems());
        String filename = String.format(
            "ops_recovery_history_summary_%s.csv",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        int exportedRows = summary.items().size() + summary.actorItems().size();
        headers.add("X-Ops-Recovery-Summary-Count", String.valueOf(exportedRows));
        auditHistorySummaryExport(
            summary.windowDays(),
            summary.modeFilter(),
            summary.actorFilter(),
            summary.eventTypeFilter(),
            exportedRows
        );
        return ResponseEntity.ok()
            .headers(headers)
            .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/history/export")
    @Operation(
        summary = "Export ops recovery execution history",
        description = "Export recent ops recovery history as CSV."
    )
    public ResponseEntity<byte[]> exportHistoryCsv(
        @RequestParam(required = false, defaultValue = "500") Integer limit,
        @RequestParam(required = false, defaultValue = "7") Integer days,
        @RequestParam(required = false) String mode,
        @RequestParam(required = false) String actor,
        @RequestParam(required = false) String eventType
    ) {
        int normalizedLimit = clamp(limit != null ? limit : DEFAULT_HISTORY_EXPORT_LIMIT, 1, MAX_HISTORY_EXPORT_LIMIT);
        int normalizedDays = normalizeWindowDays(days != null ? days : DEFAULT_WINDOW_DAYS);
        LocalDateTime from = resolveUpdatedSince(normalizedDays);
        String normalizedMode = normalizeHistoryMode(mode);
        String normalizedActor = normalizeActor(actor);
        String normalizedEventType = normalizeHistoryEventType(eventType);
        Page<AuditLog> page = queryHistoryPage(
            normalizedLimit,
            from,
            normalizedMode,
            normalizedActor,
            normalizedEventType,
            0
        );

        String csv = buildHistoryCsv(page.getContent());
        String filename = String.format(
            "ops_recovery_history_%s.csv",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.add("X-Ops-Recovery-Count", String.valueOf(page.getNumberOfElements()));
        auditHistoryExport(
            normalizedLimit,
            normalizedDays,
            normalizedMode,
            normalizedActor,
            normalizedEventType,
            page.getNumberOfElements()
        );
        return ResponseEntity.ok()
            .headers(headers)
            .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/history/export-async")
    @Operation(
        summary = "Start ops recovery history async export task",
        description = "Create an asynchronous CSV export task for ops recovery history/summary/trend/compare reports."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202",
            description = "Async export task accepted (new or deduplicated active task).",
            headers = {
                @Header(
                    name = HttpHeaders.LOCATION,
                    description = "Task status endpoint.",
                    schema = @Schema(type = "string")
                )
            }
        )
    })
    public ResponseEntity<RecoveryHistoryExportAsyncCreateResponseDto> startHistoryExportAsync(
        @RequestBody(required = false) RecoveryHistoryExportAsyncRequestDto request
    ) {
        RecoveryHistoryExportAsyncSnapshot snapshot = copyHistoryExportAsyncRequest(request);
        RecoveryHistoryExportAsyncTask task;
        boolean deduplicated;
        String deduplicatedFromTaskId;
        synchronized (historyExportAsyncTaskLock) {
            refreshHistoryExportAsyncTasksLifecycleLocked();
            RecoveryHistoryExportAsyncTask activeTask = findActiveHistoryExportAsyncTaskBySnapshotLocked(snapshot);
            deduplicated = activeTask != null;
            task = deduplicated ? activeTask : createHistoryExportAsyncTask(snapshot);
            deduplicatedFromTaskId = deduplicated && activeTask != null ? activeTask.taskId() : null;
        }
        if (!deduplicated) {
            CompletableFuture.runAsync(() -> runHistoryExportAsyncTask(task));
        }
        return acceptedHistoryExportAsyncCreateResponse(toHistoryExportAsyncCreateResponse(
            task,
            deduplicated,
            deduplicatedFromTaskId,
            deduplicated
                ? "Reused active async export task with same request snapshot"
                : "Started async export task"
        ), task.taskId());
    }

    @GetMapping("/history/export-async")
    @Operation(
        summary = "List ops recovery history async export tasks",
        description = "List recent asynchronous ops recovery history export tasks."
    )
    public ResponseEntity<RecoveryHistoryExportAsyncListResponseDto> listHistoryExportAsyncTasks(
        @RequestParam(required = false, defaultValue = "20") Integer limit,
        @RequestParam(required = false) Integer maxItems,
        @RequestParam(defaultValue = "0") Integer skipCount,
        @RequestParam(required = false) String exportType,
        @RequestParam(required = false) String status
    ) {
        try {
            int normalizedMaxItems = clamp(
                maxItems != null
                    ? maxItems
                    : (limit != null ? limit : DEFAULT_HISTORY_EXPORT_ASYNC_LIST_LIMIT),
                1,
                MAX_HISTORY_EXPORT_ASYNC_LIST_LIMIT
            );
            int normalizedSkipCount = Math.max(skipCount != null ? skipCount : 0, 0);
            HistoryExportAsyncType exportTypeFilter = normalizeHistoryExportAsyncTypeFilter(exportType);
            RecoveryHistoryExportAsyncStatus statusFilter = normalizeHistoryExportAsyncStatusFilter(status);
            RecoveryHistoryExportAsyncListPage page = listHistoryExportAsyncTasksInternal(
                normalizedSkipCount,
                normalizedMaxItems,
                exportTypeFilter,
                statusFilter
            );
            boolean hasMoreItems = normalizedSkipCount + page.items().size() < page.totalItems();
            return ResponseEntity.ok(new RecoveryHistoryExportAsyncListResponseDto(
                page.items().size(),
                new RecoveryTaskCenterPagingDto(
                    normalizedSkipCount,
                    normalizedMaxItems,
                    page.totalItems(),
                    hasMoreItems
                ),
                page.items()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/history/export-async/summary")
    @Operation(
        summary = "Summarize ops recovery history async export tasks",
        description = "Return aggregate counters for async export tasks by current status."
    )
    public ResponseEntity<RecoveryHistoryExportAsyncSummaryResponseDto> summarizeHistoryExportAsyncTasks(
        @RequestParam(required = false) String exportType,
        @RequestParam(required = false) String status
    ) {
        try {
            return ResponseEntity.ok(
                AsyncTaskSummaryAdapters.toOpsRecovery(
                    summarizeHistoryExportAsyncTaskSnapshot(exportType, status)
                )
            );
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    public AsyncTaskSummarySnapshot summarizeHistoryExportAsyncTaskSnapshot(String exportType, String status) {
        HistoryExportAsyncType exportTypeFilter = normalizeHistoryExportAsyncTypeFilter(exportType);
        RecoveryHistoryExportAsyncStatus statusFilter = normalizeHistoryExportAsyncStatusFilter(status);
        return buildHistoryExportAsyncSummarySnapshot(exportTypeFilter, statusFilter);
    }

    private AsyncTaskSummarySnapshot buildHistoryExportAsyncSummarySnapshot(
        HistoryExportAsyncType exportTypeFilter,
        RecoveryHistoryExportAsyncStatus statusFilter
    ) {
        int totalCount = 0;
        int queuedCount = 0;
        int runningCount = 0;
        int completedCount = 0;
        int cancelledCount = 0;
        int failedCount = 0;
        int timedOutCount = 0;
        int expiredCount = 0;
        synchronized (historyExportAsyncTaskLock) {
            refreshHistoryExportAsyncTasksLifecycleLocked();
            for (RecoveryHistoryExportAsyncTask task : historyExportAsyncTasks.values()) {
                if (task == null) {
                    continue;
                }
                if (exportTypeFilter != null && task.exportType() != exportTypeFilter) {
                    continue;
                }
                if (statusFilter != null && task.status() != statusFilter) {
                    continue;
                }
                totalCount += 1;
                switch (task.status()) {
                    case QUEUED -> queuedCount += 1;
                    case RUNNING -> runningCount += 1;
                    case COMPLETED -> completedCount += 1;
                    case CANCELLED -> cancelledCount += 1;
                    case FAILED -> failedCount += 1;
                    case TIMED_OUT -> timedOutCount += 1;
                    case EXPIRED -> expiredCount += 1;
                }
            }
        }
        int activeCount = queuedCount + runningCount;
        int terminalCount = completedCount + cancelledCount + failedCount + timedOutCount + expiredCount;
        return new AsyncTaskSummarySnapshot(
            totalCount,
            activeCount,
            terminalCount,
            queuedCount,
            runningCount,
            completedCount,
            cancelledCount,
            failedCount,
            timedOutCount,
            expiredCount
        );
    }

    @PostMapping("/history/export-async/cleanup")
    @Operation(
        summary = "Cleanup ops recovery history async export tasks",
        description = "Cleanup terminal tasks by default, or cleanup specific terminal status with optional exportType filter."
    )
    public ResponseEntity<RecoveryHistoryExportAsyncCleanupResponseDto> cleanupHistoryExportAsyncTasks(
        @RequestParam(required = false) String exportType,
        @RequestParam(required = false) String status
    ) {
        try {
            HistoryExportAsyncType exportTypeFilter = normalizeHistoryExportAsyncTypeFilter(exportType);
            RecoveryHistoryExportAsyncStatus statusFilter = normalizeHistoryExportAsyncCleanupStatusFilter(status);
            int deletedCount = cleanupHistoryExportAsyncTasksInternal(exportTypeFilter, statusFilter);
            int remainingCount = historyExportAsyncTasks.size();
            String normalizedExportTypeFilter = exportTypeFilter != null ? exportTypeFilter.name() : null;
            String normalizedStatusFilter = statusFilter != null ? statusFilter.name() : null;
            String message = deletedCount > 0
                ? String.format(Locale.ROOT, "Deleted %d async export task(s).", deletedCount)
                : "No async export tasks matched cleanup filters.";
            auditHistoryExportAsyncCleanup(normalizedExportTypeFilter, normalizedStatusFilter, deletedCount, remainingCount);
            return ResponseEntity.ok(new RecoveryHistoryExportAsyncCleanupResponseDto(
                deletedCount,
                remainingCount,
                normalizedExportTypeFilter,
                normalizedStatusFilter,
                message
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new RecoveryHistoryExportAsyncCleanupResponseDto(
                0,
                historyExportAsyncTasks.size(),
                exportType != null && !exportType.isBlank() ? exportType.trim().toUpperCase(Locale.ROOT) : null,
                status != null && !status.isBlank() ? status.trim().toUpperCase(Locale.ROOT) : null,
                ex.getMessage()
            ));
        }
    }

    @PostMapping("/history/export-async/cancel-active")
    @Operation(
        summary = "Cancel active ops recovery history async export tasks",
        description = "Cancel queued/running async export tasks by default, optionally filtered by exportType/status."
    )
    public ResponseEntity<RecoveryHistoryExportAsyncCancelActiveResponseDto> cancelActiveHistoryExportAsyncTasks(
        @RequestParam(required = false) String exportType,
        @RequestParam(required = false) String status
    ) {
        try {
            HistoryExportAsyncType exportTypeFilter = normalizeHistoryExportAsyncTypeFilter(exportType);
            RecoveryHistoryExportAsyncStatus statusFilter = normalizeHistoryExportAsyncCancelActiveStatusFilter(status);
            int cancelledCount = cancelActiveHistoryExportAsyncTasksInternal(exportTypeFilter, statusFilter);
            int remainingActiveCount = countActiveHistoryExportAsyncTasks(exportTypeFilter, statusFilter);
            String normalizedExportTypeFilter = exportTypeFilter != null ? exportTypeFilter.name() : null;
            String normalizedStatusFilter = statusFilter != null ? statusFilter.name() : "ACTIVE";
            String message = cancelledCount > 0
                ? String.format(Locale.ROOT, "Cancelled %d active async export task(s).", cancelledCount)
                : "No active async export tasks matched cancel-active filters.";
            auditHistoryExportAsyncCancelActive(
                normalizedExportTypeFilter,
                normalizedStatusFilter,
                cancelledCount,
                remainingActiveCount
            );
            return ResponseEntity.ok(new RecoveryHistoryExportAsyncCancelActiveResponseDto(
                cancelledCount,
                remainingActiveCount,
                normalizedExportTypeFilter,
                normalizedStatusFilter,
                message
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new RecoveryHistoryExportAsyncCancelActiveResponseDto(
                0,
                countActiveHistoryExportAsyncTasks(null, null),
                exportType != null && !exportType.isBlank() ? exportType.trim().toUpperCase(Locale.ROOT) : null,
                status != null && !status.isBlank() ? status.trim().toUpperCase(Locale.ROOT) : "ACTIVE",
                ex.getMessage()
            ));
        }
    }

    @GetMapping("/history/export-async/{taskId}")
    @Operation(
        summary = "Get ops recovery history async export task",
        description = "Get status/details for a specific asynchronous ops recovery history export task."
    )
    public ResponseEntity<RecoveryHistoryExportAsyncStatusResponseDto> getHistoryExportAsyncTask(
        @PathVariable String taskId
    ) {
        RecoveryHistoryExportAsyncTask task;
        synchronized (historyExportAsyncTaskLock) {
            refreshHistoryExportAsyncTasksLifecycleLocked();
            task = historyExportAsyncTasks.get(taskId);
        }
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toHistoryExportAsyncStatusResponse(task));
    }

    @PostMapping("/history/export-async/{taskId}/cancel")
    @Operation(
        summary = "Cancel ops recovery history async export task",
        description = "Cancel a queued/running asynchronous ops recovery history export task."
    )
    public ResponseEntity<RecoveryHistoryExportAsyncStatusResponseDto> cancelHistoryExportAsyncTask(
        @PathVariable String taskId
    ) {
        RecoveryHistoryExportAsyncTask updated;
        synchronized (historyExportAsyncTaskLock) {
            refreshHistoryExportAsyncTasksLifecycleLocked();
            RecoveryHistoryExportAsyncTask existing = historyExportAsyncTasks.get(taskId);
            if (existing == null) {
                return ResponseEntity.notFound().build();
            }
            if (existing.isTerminal()) {
                return ResponseEntity.status(409).body(toHistoryExportAsyncStatusResponse(existing));
            }
            String actor = resolveAuditUsername();
            updated = historyExportAsyncTasks.computeIfPresent(taskId, (key, current) -> {
                if (current.isTerminal()) {
                    return current;
                }
                return current.cancel("Cancelled by user", actor);
            });
        }
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toHistoryExportAsyncStatusResponse(updated));
    }

    @PostMapping("/history/export-async/{taskId}/retry")
    @Operation(
        summary = "Retry ops recovery history async export task",
        description = "Retry a terminal async export task with its original request snapshot."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202",
            description = "Async export retry accepted (new or deduplicated active task).",
            headers = {
                @Header(
                    name = HttpHeaders.LOCATION,
                    description = "Task status endpoint.",
                    schema = @Schema(type = "string")
                )
            }
        )
    })
    public ResponseEntity<RecoveryHistoryExportAsyncCreateResponseDto> retryHistoryExportAsyncTask(
        @PathVariable String taskId
    ) {
        RecoveryHistoryExportAsyncSnapshot retrySnapshot;
        RecoveryHistoryExportAsyncTask retryTask;
        boolean deduplicated;
        String deduplicatedFromTaskId;
        synchronized (historyExportAsyncTaskLock) {
            refreshHistoryExportAsyncTasksLifecycleLocked();
            RecoveryHistoryExportAsyncTask sourceTask = historyExportAsyncTasks.get(taskId);
            if (sourceTask == null) {
                return ResponseEntity.notFound().build();
            }
            if (!sourceTask.isTerminal()) {
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "task is not terminal and cannot be retried"
                );
            }
            retrySnapshot = historyExportAsyncTaskSnapshots.get(taskId);
            if (retrySnapshot == null) {
                retrySnapshot = copyHistoryExportAsyncRequest(null);
            }
            RecoveryHistoryExportAsyncTask activeTask = findActiveHistoryExportAsyncTaskBySnapshotLocked(retrySnapshot);
            deduplicated = activeTask != null;
            retryTask = deduplicated ? activeTask : createHistoryExportAsyncTask(retrySnapshot);
            deduplicatedFromTaskId = deduplicated && activeTask != null ? activeTask.taskId() : null;
        }

        if (!deduplicated) {
            CompletableFuture.runAsync(() -> runHistoryExportAsyncTask(retryTask));
        }
        return acceptedHistoryExportAsyncCreateResponse(toHistoryExportAsyncCreateResponse(
            retryTask,
            deduplicated,
            deduplicatedFromTaskId,
            deduplicated
                ? "Reused active async export task with same request snapshot"
                : "Retried terminal async export task"
        ), retryTask.taskId());
    }

    @PostMapping("/history/export-async/retry-terminal")
    @Operation(
        summary = "Retry terminal ops recovery history async export tasks",
        description = "Retry terminal asynchronous ops recovery history export tasks in bulk."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202",
            description = "Bulk retry accepted.",
            headers = {
                @Header(
                    name = HttpHeaders.LOCATION,
                    description = "Task-center list endpoint for polling newly created/reused tasks.",
                    schema = @Schema(type = "string")
                )
            }
        ),
        @ApiResponse(responseCode = "400", description = "Invalid filters.")
    })
    public ResponseEntity<RecoveryHistoryExportAsyncRetryTerminalResponseDto> retryTerminalHistoryExportAsyncTasks(
        @RequestParam(required = false) String exportType,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "20") Integer limit
    ) {
        try {
            HistoryExportAsyncType exportTypeFilter = normalizeHistoryExportAsyncTypeFilter(exportType);
            RecoveryHistoryExportAsyncStatus statusFilter = normalizeHistoryExportAsyncRetryTerminalStatusFilter(status);
            int boundedLimit = clamp(
                limit != null && limit > 0 ? limit : DEFAULT_HISTORY_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT,
                1,
                MAX_HISTORY_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT
            );
            List<RecoveryHistoryExportAsyncTask> candidates = listHistoryExportAsyncRetryTerminalCandidates(
                boundedLimit,
                exportTypeFilter,
                statusFilter
            );

            int requested = candidates.size();
            int retried = 0;
            int reused = 0;
            int skipped = 0;
            int failed = 0;
            List<RecoveryHistoryExportAsyncRetryTerminalItemDto> results = new ArrayList<>();

            for (RecoveryHistoryExportAsyncTask candidate : candidates) {
                if (candidate == null || !candidate.isTerminal()) {
                    skipped += 1;
                    results.add(new RecoveryHistoryExportAsyncRetryTerminalItemDto(
                        candidate != null ? candidate.taskId() : null,
                        null,
                        candidate != null && candidate.exportType() != null ? candidate.exportType().name() : "UNKNOWN",
                        candidate != null && candidate.status() != null ? candidate.status().name() : "UNKNOWN",
                        "SKIPPED",
                        "Source task is missing or not terminal"
                    ));
                    continue;
                }
                try {
                    RecoveryHistoryExportAsyncTask sourceTask;
                    RecoveryHistoryExportAsyncSnapshot retrySnapshot;
                    RecoveryHistoryExportAsyncTask retryTask;
                    boolean deduplicated;
                    synchronized (historyExportAsyncTaskLock) {
                        refreshHistoryExportAsyncTasksLifecycleLocked();
                        sourceTask = historyExportAsyncTasks.get(candidate.taskId());
                        if (sourceTask == null || !sourceTask.isTerminal()) {
                            throw new IllegalStateException("Source task is missing or not terminal");
                        }
                        retrySnapshot = historyExportAsyncTaskSnapshots.get(sourceTask.taskId());
                        if (retrySnapshot == null) {
                            retrySnapshot = copyHistoryExportAsyncRequest(null);
                        }
                        RecoveryHistoryExportAsyncTask activeTask =
                            findActiveHistoryExportAsyncTaskBySnapshotLocked(retrySnapshot);
                        deduplicated = activeTask != null;
                        retryTask = deduplicated ? activeTask : createHistoryExportAsyncTask(retrySnapshot);
                    }

                    retried += 1;
                    if (deduplicated) {
                        reused += 1;
                    }
                    results.add(new RecoveryHistoryExportAsyncRetryTerminalItemDto(
                        sourceTask.taskId(),
                        retryTask.taskId(),
                        sourceTask.exportType().name(),
                        sourceTask.status().name(),
                        deduplicated ? "REUSED" : "RETRIED",
                        deduplicated
                            ? "Reused active async export task with same request snapshot"
                            : "Retried terminal async export task"
                    ));
                    if (!deduplicated) {
                        CompletableFuture.runAsync(() -> runHistoryExportAsyncTask(retryTask));
                    }
                } catch (Exception ex) {
                    failed += 1;
                    results.add(new RecoveryHistoryExportAsyncRetryTerminalItemDto(
                        candidate.taskId(),
                        null,
                        candidate.exportType() != null ? candidate.exportType().name() : "UNKNOWN",
                        candidate.status() != null ? candidate.status().name() : "UNKNOWN",
                        "FAILED",
                        resolveErrorMessage(ex)
                    ));
                }
            }

            String normalizedExportTypeFilter = exportTypeFilter != null ? exportTypeFilter.name() : "ALL";
            String normalizedStatusFilter = statusFilter != null ? statusFilter.name() : "FAILED|CANCELLED|COMPLETED|TIMED_OUT|EXPIRED";
            String message = requested == 0
                ? "No terminal async ops recovery export tasks matched retry filters"
                : String.format(
                    Locale.ROOT,
                    "Retried %d/%d terminal async ops recovery export tasks (reused=%d, skipped=%d, failed=%d)",
                    retried,
                    requested,
                    reused,
                    skipped,
                    failed
                );

            return acceptedHistoryExportAsyncRetryTerminalResponse(new RecoveryHistoryExportAsyncRetryTerminalResponseDto(
                requested,
                retried,
                reused,
                skipped,
                failed,
                boundedLimit,
                normalizedExportTypeFilter,
                normalizedStatusFilter,
                message,
                results
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new RecoveryHistoryExportAsyncRetryTerminalResponseDto(
                0,
                0,
                0,
                0,
                0,
                0,
                exportType != null && !exportType.isBlank() ? exportType.trim().toUpperCase(Locale.ROOT) : "ALL",
                status != null && !status.isBlank() ? status.trim().toUpperCase(Locale.ROOT) : null,
                ex.getMessage(),
                List.of()
            ));
        }
    }

    @PostMapping("/history/export-async/retry-terminal/dry-run")
    @Operation(
        summary = "Dry-run retry terminal ops recovery history async export tasks",
        description = "Evaluate terminal asynchronous ops recovery history export tasks for retry eligibility without executing retries."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Dry-run computed."),
        @ApiResponse(responseCode = "400", description = "Invalid filters.")
    })
    public ResponseEntity<RecoveryHistoryExportAsyncRetryTerminalDryRunResponseDto> dryRunRetryTerminalHistoryExportAsyncTasks(
        @RequestParam(required = false) String exportType,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "20") Integer limit
    ) {
        try {
            HistoryExportAsyncType exportTypeFilter = normalizeHistoryExportAsyncTypeFilter(exportType);
            RecoveryHistoryExportAsyncStatus statusFilter = normalizeHistoryExportAsyncRetryTerminalStatusFilter(status);
            int boundedLimit = clamp(
                limit != null && limit > 0 ? limit : DEFAULT_HISTORY_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT,
                1,
                MAX_HISTORY_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT
            );
            RecoveryHistoryExportAsyncRetryTerminalDryRunComputation dryRun =
                computeHistoryExportAsyncRetryTerminalDryRun(exportTypeFilter, statusFilter, boundedLimit);
            return ResponseEntity.ok(new RecoveryHistoryExportAsyncRetryTerminalDryRunResponseDto(
                dryRun.requested(),
                dryRun.retryable(),
                dryRun.skipped(),
                dryRun.limit(),
                dryRun.exportTypeFilter(),
                dryRun.statusFilter(),
                dryRun.message(),
                dryRun.results(),
                dryRun.reasonBreakdown()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new RecoveryHistoryExportAsyncRetryTerminalDryRunResponseDto(
                0,
                0,
                0,
                0,
                exportType != null && !exportType.isBlank() ? exportType.trim().toUpperCase(Locale.ROOT) : "ALL",
                status != null && !status.isBlank() ? status.trim().toUpperCase(Locale.ROOT) : null,
                ex.getMessage(),
                List.of(),
                List.of()
            ));
        }
    }

    @GetMapping(value = "/history/export-async/retry-terminal/dry-run/export", produces = "text/csv")
    @Operation(
        summary = "Export dry-run retry terminal ops recovery history async export diagnostics CSV",
        description = "Export dry-run retry-terminal diagnostics rows and reason breakdown as CSV."
    )
    public ResponseEntity<byte[]> exportDryRunRetryTerminalHistoryExportAsyncTasks(
        @RequestParam(required = false) String exportType,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "20") Integer limit
    ) {
        try {
            HistoryExportAsyncType exportTypeFilter = normalizeHistoryExportAsyncTypeFilter(exportType);
            RecoveryHistoryExportAsyncStatus statusFilter = normalizeHistoryExportAsyncRetryTerminalStatusFilter(status);
            int boundedLimit = clamp(
                limit != null && limit > 0 ? limit : DEFAULT_HISTORY_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT,
                1,
                MAX_HISTORY_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT
            );
            RecoveryHistoryExportAsyncRetryTerminalDryRunComputation dryRun =
                computeHistoryExportAsyncRetryTerminalDryRun(exportTypeFilter, statusFilter, boundedLimit);
            String csv = buildHistoryExportAsyncRetryTerminalDryRunCsv(dryRun);
            String filename = String.format(
                Locale.ROOT,
                "ops_recovery_history_async_retry_dry_run_%s.csv",
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
            headers.setContentDisposition(
                ContentDisposition.attachment()
                    .filename(filename, StandardCharsets.UTF_8)
                    .build()
            );
            headers.add("X-Ops-Recovery-Async-Retry-Dry-Run-Count", String.valueOf(dryRun.results().size()));
            return ResponseEntity.ok()
                .headers(headers)
                .body(csv.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                ex.getMessage()
            );
        }
    }

    @PostMapping("/history/export-async/retry-terminal/by-task-ids")
    @Operation(
        summary = "Retry selected terminal ops recovery history async export tasks",
        description = "Retry terminal asynchronous ops recovery history export tasks by source task ids."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202",
            description = "Selected-task retry accepted.",
            headers = {
                @Header(
                    name = HttpHeaders.LOCATION,
                    description = "Task-center list endpoint for polling newly created/reused tasks.",
                    schema = @Schema(type = "string")
                )
            }
        )
    })
    public ResponseEntity<RecoveryHistoryExportAsyncRetryTerminalResponseDto> retrySelectedTerminalHistoryExportAsyncTasks(
        @RequestParam(required = false) String exportType,
        @RequestBody(required = false) RecoveryHistoryExportAsyncRetryTerminalByTaskIdsRequestDto request
    ) {
        try {
            HistoryExportAsyncType exportTypeFilter = normalizeHistoryExportAsyncTypeFilter(exportType);
            List<String> sourceTaskIds = normalizeHistoryExportAsyncRetrySourceTaskIds(
                request != null ? request.sourceTaskIds() : null
            );
            int requested = sourceTaskIds.size();
            int retried = 0;
            int reused = 0;
            int skipped = 0;
            int failed = 0;
            List<RecoveryHistoryExportAsyncRetryTerminalItemDto> results = new ArrayList<>();

            for (String sourceTaskId : sourceTaskIds) {
                try {
                    RecoveryHistoryExportAsyncTask sourceTask;
                    RecoveryHistoryExportAsyncSnapshot retrySnapshot;
                    RecoveryHistoryExportAsyncTask retryTask;
                    boolean deduplicated;
                    String sourceStatus;
                    String sourceExportType;
                    synchronized (historyExportAsyncTaskLock) {
                        refreshHistoryExportAsyncTasksLifecycleLocked();
                        sourceTask = historyExportAsyncTasks.get(sourceTaskId);
                        if (sourceTask == null) {
                            skipped += 1;
                            results.add(new RecoveryHistoryExportAsyncRetryTerminalItemDto(
                                sourceTaskId,
                                null,
                                "UNKNOWN",
                                "NOT_FOUND",
                                "SKIPPED",
                                "Source task not found"
                            ));
                            continue;
                        }
                        if (exportTypeFilter != null && sourceTask.exportType() != exportTypeFilter) {
                            skipped += 1;
                            results.add(new RecoveryHistoryExportAsyncRetryTerminalItemDto(
                                sourceTask.taskId(),
                                null,
                                sourceTask.exportType().name(),
                                sourceTask.status().name(),
                                "SKIPPED",
                                String.format(
                                    Locale.ROOT,
                                    "Source task export type mismatch: expected=%s actual=%s",
                                    exportTypeFilter.name(),
                                    sourceTask.exportType().name()
                                )
                            ));
                            continue;
                        }
                        if (!sourceTask.isTerminal()) {
                            skipped += 1;
                            results.add(new RecoveryHistoryExportAsyncRetryTerminalItemDto(
                                sourceTask.taskId(),
                                null,
                                sourceTask.exportType().name(),
                                sourceTask.status().name(),
                                "SKIPPED",
                                "Source task is not terminal"
                            ));
                            continue;
                        }
                        sourceStatus = sourceTask.status().name();
                        sourceExportType = sourceTask.exportType().name();
                        retrySnapshot = historyExportAsyncTaskSnapshots.get(sourceTask.taskId());
                        if (retrySnapshot == null) {
                            retrySnapshot = copyHistoryExportAsyncRequest(null);
                        }
                        RecoveryHistoryExportAsyncTask activeTask =
                            findActiveHistoryExportAsyncTaskBySnapshotLocked(retrySnapshot);
                        deduplicated = activeTask != null;
                        retryTask = deduplicated ? activeTask : createHistoryExportAsyncTask(retrySnapshot);
                    }

                    retried += 1;
                    if (deduplicated) {
                        reused += 1;
                    }
                    results.add(new RecoveryHistoryExportAsyncRetryTerminalItemDto(
                        sourceTaskId,
                        retryTask.taskId(),
                        sourceExportType,
                        sourceStatus,
                        deduplicated ? "REUSED" : "RETRIED",
                        deduplicated
                            ? "Reused active async export task with same request snapshot"
                            : "Retried selected terminal async export task"
                    ));
                    if (!deduplicated) {
                        CompletableFuture.runAsync(() -> runHistoryExportAsyncTask(retryTask));
                    }
                } catch (Exception ex) {
                    failed += 1;
                    results.add(new RecoveryHistoryExportAsyncRetryTerminalItemDto(
                        sourceTaskId,
                        null,
                        "UNKNOWN",
                        "UNKNOWN",
                        "FAILED",
                        resolveErrorMessage(ex)
                    ));
                }
            }

            String normalizedExportTypeFilter = exportTypeFilter != null ? exportTypeFilter.name() : "ALL";
            String message = requested == 0
                ? "No selected terminal async ops recovery export tasks to retry"
                : String.format(
                    Locale.ROOT,
                    "Retried %d/%d selected terminal async ops recovery export tasks (reused=%d, skipped=%d, failed=%d)",
                    retried,
                    requested,
                    reused,
                    skipped,
                    failed
                );

            return acceptedHistoryExportAsyncRetryTerminalResponse(new RecoveryHistoryExportAsyncRetryTerminalResponseDto(
                requested,
                retried,
                reused,
                skipped,
                failed,
                requested,
                normalizedExportTypeFilter,
                "BY_TASK_IDS",
                message,
                results
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new RecoveryHistoryExportAsyncRetryTerminalResponseDto(
                0,
                0,
                0,
                0,
                0,
                0,
                exportType != null && !exportType.isBlank() ? exportType.trim().toUpperCase(Locale.ROOT) : "ALL",
                "BY_TASK_IDS",
                ex.getMessage(),
                List.of()
            ));
        }
    }

    @GetMapping(value = "/history/export-async/{taskId}/download", produces = "text/csv")
    @Operation(
        summary = "Download ops recovery history async export result",
        description = "Download CSV payload for a completed asynchronous ops recovery history export task."
    )
    public ResponseEntity<byte[]> downloadHistoryExportAsyncTask(
        @PathVariable String taskId
    ) {
        RecoveryHistoryExportAsyncTask task;
        synchronized (historyExportAsyncTaskLock) {
            refreshHistoryExportAsyncTasksLifecycleLocked();
            task = historyExportAsyncTasks.get(taskId);
        }
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        if (task.status() != RecoveryHistoryExportAsyncStatus.COMPLETED
            || task.csvContent() == null
            || task.filename() == null) {
            return ResponseEntity.status(409).build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(task.filename(), StandardCharsets.UTF_8)
            .build());
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        return ResponseEntity.ok()
            .headers(headers)
            .body(task.csvContent().clone());
    }

    @PostMapping("/queue-by-reason")
    @Operation(
        summary = "Queue recovery by failure reason",
        description = "Queue preview recovery jobs for matched failed documents in a reason scope."
    )
    public ResponseEntity<RecoveryBatchResponseDto> queueByReason(@RequestBody RecoveryByReasonRequestDto request) {
        try {
            String domain = normalizeDomain(request != null ? request.domain() : null);
            String reason = normalizeReason(request != null ? request.reason() : null);
            String category = normalizedUpperOrDefault(request != null ? request.category() : null, "ANY");
            Boolean retryable = request != null ? request.retryable() : null;
            boolean force = request != null && Boolean.TRUE.equals(request.force());
            int days = normalizeWindowDays(request != null && request.days() != null ? request.days() : DEFAULT_WINDOW_DAYS);
            int maxDocuments = clamp(request != null && request.maxDocuments() != null ? request.maxDocuments() : DEFAULT_BATCH_LIMIT, 1, MAX_BATCH_LIMIT);
            LocalDateTime updatedSince = resolveUpdatedSince(days);

            long totalCandidates = documentRepository.countPreviewFailuresByReasonAndWindow(
                failureStatuses(),
                updatedSince,
                reason
            );
            ScanResult scan = scanFailuresByReason(reason, category, retryable, updatedSince, maxDocuments);
            QueueBatchResult queue = queueDocuments(
                scan.matchedDocumentIds(),
                force,
                "ANY".equals(category) ? null : toFailureCategory(category)
            );
            auditRecovery("QUEUE_BY_REASON", domain, queue, force, reason, category, retryable, days, maxDocuments);

            return ResponseEntity.ok(new RecoveryBatchResponseDto(
                domain,
                "QUEUE_BY_REASON",
                days,
                maxDocuments,
                totalCandidates,
                scan.scanned(),
                scan.matched(),
                scan.truncated(),
                queue.requested(),
                queue.deduplicated(),
                queue.queued(),
                queue.skipped(),
                queue.failed(),
                queue.results(),
                null
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorBatch("QUEUE_BY_REASON", ex.getMessage()));
        }
    }

    @PostMapping("/queue-by-window")
    @Operation(
        summary = "Queue recovery by diagnostics window",
        description = "Queue preview recovery jobs for matched failures in a selected diagnostics window."
    )
    public ResponseEntity<RecoveryBatchResponseDto> queueByWindow(@RequestBody RecoveryByWindowRequestDto request) {
        try {
            String domain = normalizeDomain(request != null ? request.domain() : null);
            String reason = normalizeOptionalReason(request != null ? request.reason() : null);
            String category = normalizedUpperOrDefault(request != null ? request.category() : null, "ANY");
            Boolean retryable = request != null ? request.retryable() : null;
            boolean force = request != null && Boolean.TRUE.equals(request.force());
            int days = normalizeWindowDays(request != null && request.days() != null ? request.days() : DEFAULT_WINDOW_DAYS);
            int maxDocuments = clamp(request != null && request.maxDocuments() != null ? request.maxDocuments() : DEFAULT_BATCH_LIMIT, 1, MAX_BATCH_LIMIT);
            LocalDateTime updatedSince = resolveUpdatedSince(days);

            long totalCandidates = reason == null
                ? documentRepository.countPreviewFailuresByWindow(failureStatuses(), updatedSince)
                : documentRepository.countPreviewFailuresByReasonAndWindow(failureStatuses(), updatedSince, reason);

            ScanResult scan = scanFailuresByWindow(reason, category, retryable, updatedSince, maxDocuments);
            QueueBatchResult queue = queueDocuments(
                scan.matchedDocumentIds(),
                force,
                "ANY".equals(category) ? null : toFailureCategory(category)
            );
            auditRecovery("QUEUE_BY_WINDOW", domain, queue, force, reason, category, retryable, days, maxDocuments);

            return ResponseEntity.ok(new RecoveryBatchResponseDto(
                domain,
                "QUEUE_BY_WINDOW",
                days,
                maxDocuments,
                totalCandidates,
                scan.scanned(),
                scan.matched(),
                scan.truncated(),
                queue.requested(),
                queue.deduplicated(),
                queue.queued(),
                queue.skipped(),
                queue.failed(),
                queue.results(),
                null
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorBatch("QUEUE_BY_WINDOW", ex.getMessage()));
        }
    }

    @PostMapping("/replay-batch")
    @Operation(
        summary = "Replay recovery batch",
        description = "Replay selected preview dead-letter entries in batch."
    )
    public ResponseEntity<RecoveryBatchResponseDto> replayBatch(@RequestBody RecoveryReplayBatchRequestDto request) {
        try {
            String domain = normalizeDomain(request != null ? request.domain() : null);
            boolean force = request == null || request.force() == null || Boolean.TRUE.equals(request.force());
            QueueBatchResult queue = replayBatchInternal(
                request != null ? request.documentIds() : List.of(),
                request != null ? request.entryKeys() : List.of(),
                force
            );
            auditRecovery("REPLAY_BATCH", domain, queue, force, null, "ANY", null, 0, queue.deduplicated());
            return ResponseEntity.ok(new RecoveryBatchResponseDto(
                domain,
                "REPLAY_BATCH",
                0,
                queue.deduplicated(),
                queue.deduplicated(),
                queue.deduplicated(),
                queue.deduplicated(),
                false,
                queue.requested(),
                queue.deduplicated(),
                queue.queued(),
                queue.skipped(),
                queue.failed(),
                queue.results(),
                null
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorBatch("REPLAY_BATCH", ex.getMessage()));
        }
    }

    @PostMapping("/clear-batch")
    @Operation(
        summary = "Clear recovery batch",
        description = "Clear selected preview dead-letter entries in batch without enqueuing preview jobs."
    )
    public ResponseEntity<RecoveryBatchResponseDto> clearBatch(@RequestBody RecoveryClearBatchRequestDto request) {
        try {
            String domain = normalizeDomain(request != null ? request.domain() : null);
            QueueBatchResult queue = clearBatchInternal(
                request != null ? request.documentIds() : List.of(),
                request != null ? request.entryKeys() : List.of()
            );
            auditRecovery("CLEAR_BATCH", domain, queue, false, null, "ANY", null, 0, queue.deduplicated());
            return ResponseEntity.ok(new RecoveryBatchResponseDto(
                domain,
                "CLEAR_BATCH",
                0,
                queue.deduplicated(),
                queue.deduplicated(),
                queue.deduplicated(),
                queue.deduplicated(),
                false,
                queue.requested(),
                queue.deduplicated(),
                queue.queued(),
                queue.skipped(),
                queue.failed(),
                queue.results(),
                null
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorBatch("CLEAR_BATCH", ex.getMessage()));
        }
    }

    @PostMapping("/clear-by-filter")
    @Operation(
        summary = "Clear dead-letter by filter",
        description = "Clear matched preview dead-letter entries by reason/category/retryable in a diagnostics window."
    )
    public ResponseEntity<RecoveryBatchResponseDto> clearByFilter(@RequestBody RecoveryClearByFilterRequestDto request) {
        try {
            String domain = normalizeDomain(request != null ? request.domain() : null);
            String reason = normalizeOptionalReason(request != null ? request.reason() : null);
            String category = normalizedUpperOrDefault(request != null ? request.category() : null, "ANY");
            Boolean retryable = request != null ? request.retryable() : null;
            int days = normalizeWindowDays(request != null && request.days() != null ? request.days() : DEFAULT_WINDOW_DAYS);
            int maxDocuments = clamp(request != null && request.maxDocuments() != null ? request.maxDocuments() : DEFAULT_BATCH_LIMIT, 1, MAX_BATCH_LIMIT);

            DeadLetterScanResult scan = scanDeadLetterByFilter(reason, category, retryable, days, maxDocuments);
            QueueBatchResult queue = clearBatchInternal(List.of(), scan.entryKeys());
            auditRecovery("CLEAR_BY_FILTER", domain, queue, false, reason, category, retryable, days, maxDocuments);
            return ResponseEntity.ok(new RecoveryBatchResponseDto(
                domain,
                "CLEAR_BY_FILTER",
                days,
                maxDocuments,
                scan.totalMatched(),
                scan.scanned(),
                scan.matched(),
                scan.truncated(),
                queue.requested(),
                queue.deduplicated(),
                queue.queued(),
                queue.skipped(),
                queue.failed(),
                queue.results(),
                null
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorBatch("CLEAR_BY_FILTER", ex.getMessage()));
        }
    }

    @PostMapping("/replay-by-filter")
    @Operation(
        summary = "Replay dead-letter by filter",
        description = "Replay matched preview dead-letter entries by reason/category/retryable in a diagnostics window."
    )
    public ResponseEntity<RecoveryBatchResponseDto> replayByFilter(@RequestBody RecoveryReplayByFilterRequestDto request) {
        try {
            String domain = normalizeDomain(request != null ? request.domain() : null);
            String reason = normalizeOptionalReason(request != null ? request.reason() : null);
            String category = normalizedUpperOrDefault(request != null ? request.category() : null, "ANY");
            Boolean retryable = request != null ? request.retryable() : null;
            int days = normalizeWindowDays(request != null && request.days() != null ? request.days() : DEFAULT_WINDOW_DAYS);
            int maxDocuments = clamp(request != null && request.maxDocuments() != null ? request.maxDocuments() : DEFAULT_BATCH_LIMIT, 1, MAX_BATCH_LIMIT);
            boolean force = request == null || request.force() == null || Boolean.TRUE.equals(request.force());

            DeadLetterScanResult scan = scanDeadLetterByFilter(reason, category, retryable, days, maxDocuments);
            QueueBatchResult queue = replayBatchInternal(List.of(), scan.entryKeys(), force);
            auditRecovery("REPLAY_BY_FILTER", domain, queue, force, reason, category, retryable, days, maxDocuments);
            return ResponseEntity.ok(new RecoveryBatchResponseDto(
                domain,
                "REPLAY_BY_FILTER",
                days,
                maxDocuments,
                scan.totalMatched(),
                scan.scanned(),
                scan.matched(),
                scan.truncated(),
                queue.requested(),
                queue.deduplicated(),
                queue.queued(),
                queue.skipped(),
                queue.failed(),
                queue.results(),
                null
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(errorBatch("REPLAY_BY_FILTER", ex.getMessage()));
        }
    }

    @PostMapping("/dry-run")
    @Operation(
        summary = "Dry-run recovery impact",
        description = "Estimate queue/skip/failure outcomes without enqueuing actual jobs."
    )
    public ResponseEntity<RecoveryDryRunResponseDto> dryRun(@RequestBody RecoveryDryRunRequestDto request) {
        try {
            DryRunComputation computation = computeDryRun(request);
            auditDryRun(
                computation.domain(),
                computation.mode().name(),
                computation.force(),
                computation.reason(),
                computation.category(),
                computation.retryable(),
                computation.days(),
                computation.maxDocuments(),
                computation.totalCandidates(),
                computation.scanned(),
                computation.matched(),
                computation.truncated(),
                computation.evaluation()
            );
            return ResponseEntity.ok(computation.response());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new RecoveryDryRunResponseDto(
                "PREVIEW",
                "QUEUE_BY_WINDOW",
                DEFAULT_WINDOW_DAYS,
                DEFAULT_BATCH_LIMIT,
                0,
                0,
                0,
                false,
                0,
                0,
                0,
                List.of(),
                ex.getMessage()
            ));
        }
    }

    @PostMapping(value = "/dry-run/export", produces = "text/csv")
    @Operation(
        summary = "Export dry-run recovery diagnostics CSV",
        description = "Export dry-run recovery samples with effective preview summary fields as CSV."
    )
    public ResponseEntity<byte[]> exportDryRunCsv(@RequestBody RecoveryDryRunRequestDto request) {
        try {
            DryRunComputation computation = computeDryRun(request);
            String csv = buildRecoveryDryRunCsv(computation.response());
            String filename = String.format(
                Locale.ROOT,
                "ops_recovery_dry_run_%s.csv",
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
            headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
            headers.add("X-Ops-Recovery-Dry-Run-Count", String.valueOf(computation.response().samples().size()));
            auditDryRunExport(computation);
            return ResponseEntity.ok()
                .headers(headers)
                .body(csv.getBytes(StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                ex.getMessage()
            );
        }
    }

    private DryRunComputation computeDryRun(RecoveryDryRunRequestDto request) {
        String domain = normalizeDomain(request != null ? request.domain() : null);
        DryRunMode mode = resolveMode(request != null ? request.mode() : null);
        boolean force = request != null && Boolean.TRUE.equals(request.force());
        int maxDocuments = clamp(request != null && request.maxDocuments() != null ? request.maxDocuments() : DEFAULT_BATCH_LIMIT, 1, MAX_BATCH_LIMIT);
        int days = normalizeWindowDays(request != null && request.days() != null ? request.days() : DEFAULT_WINDOW_DAYS);
        LocalDateTime updatedSince = resolveUpdatedSince(days);
        String category = normalizedUpperOrDefault(request != null ? request.category() : null, "ANY");
        Boolean retryable = request != null ? request.retryable() : null;
        String reason = normalizeOptionalReason(request != null ? request.reason() : null);

        long totalCandidates;
        int scanned;
        int matched;
        boolean truncated;
        DryRunEvaluation evaluation;
        if (mode == DryRunMode.REPLAY_BATCH) {
            List<UUID> ids = request != null && request.documentIds() != null ? request.documentIds() : List.of();
            LinkedHashSet<UUID> deduplicated = new LinkedHashSet<>();
            ids.stream().filter(id -> id != null).limit(MAX_BATCH_LIMIT).forEach(deduplicated::add);
            List<Document> candidates = deduplicated.isEmpty() ? List.of() : documentRepository.findAllById(deduplicated);
            totalCandidates = deduplicated.size();
            scanned = deduplicated.size();
            matched = candidates.size();
            truncated = ids.size() > deduplicated.size();
            evaluation = evaluateDryRun(candidates, force);
        } else if (mode == DryRunMode.QUEUE_BY_REASON) {
            String normalizedReason = normalizeReason(request != null ? request.reason() : null);
            totalCandidates = documentRepository.countPreviewFailuresByReasonAndWindow(failureStatuses(), updatedSince, normalizedReason);
            ScanResult scan = scanFailuresByReason(normalizedReason, category, retryable, updatedSince, maxDocuments);
            List<Document> candidates = scan.documents();
            scanned = scan.scanned();
            matched = scan.matched();
            truncated = scan.truncated();
            reason = normalizedReason;
            evaluation = evaluateDryRun(candidates, force);
        } else if (mode == DryRunMode.QUEUE_BY_WINDOW) {
            totalCandidates = reason == null
                ? documentRepository.countPreviewFailuresByWindow(failureStatuses(), updatedSince)
                : documentRepository.countPreviewFailuresByReasonAndWindow(failureStatuses(), updatedSince, reason);
            ScanResult scan = scanFailuresByWindow(reason, category, retryable, updatedSince, maxDocuments);
            List<Document> candidates = scan.documents();
            scanned = scan.scanned();
            matched = scan.matched();
            truncated = scan.truncated();
            evaluation = evaluateDryRun(candidates, force);
        } else {
            DeadLetterScanResult scan = scanDeadLetterByFilter(reason, category, retryable, days, maxDocuments);
            totalCandidates = scan.totalMatched();
            scanned = scan.scanned();
            matched = scan.matched();
            truncated = scan.truncated();
            evaluation = mode == DryRunMode.CLEAR_BY_FILTER
                ? evaluateDeadLetterClearDryRun(scan.entryKeys())
                : evaluateDeadLetterReplayDryRun(scan.entryKeys(), force);
        }

        RecoveryDryRunResponseDto response = new RecoveryDryRunResponseDto(
            domain,
            mode.name(),
            days,
            maxDocuments,
            totalCandidates,
            scanned,
            matched,
            truncated,
            evaluation.estimatedQueued(),
            evaluation.estimatedSkipped(),
            evaluation.estimatedFailed(),
            evaluation.samples(),
            null
        );
        return new DryRunComputation(
            response,
            domain,
            mode,
            force,
            reason,
            category,
            retryable,
            days,
            maxDocuments,
            totalCandidates,
            scanned,
            matched,
            truncated,
            evaluation
        );
    }

    private ScanResult scanFailuresByReason(
        String reason,
        String category,
        Boolean retryable,
        LocalDateTime updatedSince,
        int maxDocuments
    ) {
        List<Document> matched = new ArrayList<>();
        int scanned = 0;
        int pageSize = Math.min(200, Math.max(1, maxDocuments));
        int page = 0;
        boolean truncated = false;

        while (matched.size() < maxDocuments && scanned < SCAN_LIMIT) {
            Pageable pageable = PageRequest.of(page, pageSize);
            Page<Document> result = documentRepository.findPreviewFailuresByReasonAndWindow(
                failureStatuses(),
                updatedSince,
                reason,
                pageable
            );
            if (result.isEmpty()) {
                break;
            }
            for (Document document : result.getContent()) {
                scanned += 1;
                if (matchesFilters(document, category, retryable, null)) {
                    matched.add(document);
                    if (matched.size() >= maxDocuments) {
                        break;
                    }
                }
                if (scanned >= SCAN_LIMIT) {
                    truncated = true;
                    break;
                }
            }
            if (!result.hasNext()) {
                break;
            }
            page += 1;
        }

        if (matched.size() >= maxDocuments) {
            truncated = true;
        }
        return new ScanResult(scanned, matched.size(), truncated, matched);
    }

    private ScanResult scanFailuresByWindow(
        String reason,
        String category,
        Boolean retryable,
        LocalDateTime updatedSince,
        int maxDocuments
    ) {
        List<Document> matched = new ArrayList<>();
        int scanned = 0;
        int pageSize = Math.min(200, Math.max(1, maxDocuments));
        int page = 0;
        boolean truncated = false;
        String normalizedReason = reason != null ? normalizeReason(reason) : null;

        while (matched.size() < maxDocuments && scanned < SCAN_LIMIT) {
            Pageable pageable = PageRequest.of(page, pageSize);
            Page<Document> result;
            if (normalizedReason != null) {
                result = documentRepository.findPreviewFailuresByReasonAndWindow(
                    failureStatuses(),
                    updatedSince,
                    normalizedReason,
                    pageable
                );
            } else {
                result = documentRepository.findRecentPreviewFailuresByWindow(
                    failureStatuses(),
                    updatedSince,
                    pageable
                );
            }
            if (result.isEmpty()) {
                break;
            }
            for (Document document : result.getContent()) {
                scanned += 1;
                if (matchesFilters(document, category, retryable, normalizedReason)) {
                    matched.add(document);
                    if (matched.size() >= maxDocuments) {
                        break;
                    }
                }
                if (scanned >= SCAN_LIMIT) {
                    truncated = true;
                    break;
                }
            }
            if (!result.hasNext()) {
                break;
            }
            page += 1;
        }

        if (matched.size() >= maxDocuments) {
            truncated = true;
        }
        return new ScanResult(scanned, matched.size(), truncated, matched);
    }

    private boolean matchesFilters(Document document, String normalizedCategory, Boolean retryable, String normalizedReason) {
        if (document == null) {
            return false;
        }
        String status = Objects.requireNonNullElse(resolveEffectivePreviewStatus(document), "");
        String category = resolveFailureCategory(document).name();
        boolean categoryMatch = "ANY".equals(normalizedCategory) || normalizedCategory.equals(normalizedUpperOrDefault(category, "UNKNOWN"));
        boolean retryableMatch = retryable == null
            || retryable == FailureCategory.TEMPORARY.name().equalsIgnoreCase(category);
        if (!categoryMatch || !retryableMatch) {
            return false;
        }
        if (normalizedReason == null) {
            return true;
        }
        return normalizeReason(resolveEffectivePreviewFailureReason(document)).equals(normalizedReason);
    }

    private QueueBatchResult queueDocuments(
        List<UUID> inputIds,
        boolean force,
        FailureCategory fallbackFailureCategory
    ) {
        List<UUID> rawIds = inputIds != null ? inputIds : List.of();
        int requested = rawIds.size();
        LinkedHashSet<UUID> deduplicated = new LinkedHashSet<>();
        rawIds.stream().filter(id -> id != null).limit(MAX_BATCH_LIMIT).forEach(deduplicated::add);

        Map<UUID, Document> documents = documentRepository.findAllById(deduplicated).stream()
            .collect(Collectors.toMap(Document::getId, value -> value));

        List<RecoveryBatchItemDto> results = new ArrayList<>();
        int queued = 0;
        int skipped = 0;
        int failed = 0;

        for (UUID documentId : deduplicated) {
            Document document = documents.get(documentId);
            try {
                PreviewQueueService.PreviewQueueStatus status = previewQueueService.enqueue(documentId, force);
                boolean wasQueued = status.queued();
                if (wasQueued) {
                    queued += 1;
                } else {
                    skipped += 1;
                }
                results.add(buildRecoveryBatchItem(
                    documentId,
                    wasQueued ? JobState.QUEUED : JobState.SKIPPED,
                    wasQueued ? "QUEUED" : "SKIPPED",
                    status.message(),
                    document,
                    status,
                    fallbackFailureCategory
                ));
            } catch (Exception ex) {
                failed += 1;
                results.add(buildRecoveryBatchItem(
                    documentId,
                    JobState.FAILED,
                    "FAILED",
                    ex.getMessage(),
                    document,
                    null,
                    fallbackFailureCategory
                ));
            }
        }

        return new QueueBatchResult(requested, deduplicated.size(), queued, skipped, failed, results);
    }

    private QueueBatchResult replayBatchInternal(List<UUID> inputIds, List<String> inputEntryKeys, boolean force) {
        List<UUID> rawIds = inputIds != null ? inputIds : List.of();
        List<String> rawEntryKeys = inputEntryKeys != null ? inputEntryKeys : List.of();
        int requested = rawIds.size() + rawEntryKeys.size();

        LinkedHashSet<String> deduplicatedEntryKeys = new LinkedHashSet<>();
        rawEntryKeys.stream()
            .filter(value -> value != null && !value.isBlank())
            .limit(MAX_BATCH_LIMIT)
            .forEach(value -> deduplicatedEntryKeys.add(value.trim()));
        rawIds.stream()
            .filter(id -> id != null)
            .limit(MAX_BATCH_LIMIT)
            .map(id -> PreviewDeadLetterRegistry.buildEntryKey(id, PreviewDeadLetterRegistry.defaultRenditionKey()))
            .forEach(deduplicatedEntryKeys::add);

        LinkedHashSet<UUID> deduplicatedDocumentIds = new LinkedHashSet<>();
        for (String entryKey : deduplicatedEntryKeys) {
            UUID documentId = extractDocumentIdFromEntryKey(entryKey);
            if (documentId != null) {
                deduplicatedDocumentIds.add(documentId);
            }
        }

        Map<UUID, Document> documents = documentRepository.findAllById(deduplicatedDocumentIds).stream()
            .collect(Collectors.toMap(Document::getId, value -> value));

        List<RecoveryBatchItemDto> results = new ArrayList<>();
        int queued = 0;
        int skipped = 0;
        int failed = 0;
        for (String entryKey : deduplicatedEntryKeys) {
            UUID documentId = extractDocumentIdFromEntryKey(entryKey);
            if (documentId == null) {
                failed += 1;
                results.add(buildFailedBatchItem("Invalid dead-letter entry key: " + entryKey));
                continue;
            }
            Document document = documents.get(documentId);
            String renditionKey = extractRenditionKeyFromEntryKey(entryKey);
            try {
                PreviewQueueService.PreviewQueueStatus status = previewQueueService.enqueue(documentId, force);
                boolean wasQueued = status.queued();
                if (wasQueued) {
                    queued += 1;
                    previewDeadLetterRegistry.remove(documentId, renditionKey);
                } else {
                    skipped += 1;
                    previewDeadLetterRegistry.markReplayAttempt(documentId, renditionKey, Instant.now());
                }
                results.add(buildRecoveryBatchItem(
                    documentId,
                    wasQueued ? JobState.QUEUED : JobState.SKIPPED,
                    wasQueued ? "QUEUED" : "SKIPPED",
                    status.message(),
                    document,
                    status,
                    null
                ));
            } catch (Exception ex) {
                failed += 1;
                results.add(buildRecoveryBatchItem(
                    documentId,
                    JobState.FAILED,
                    "FAILED",
                    ex.getMessage(),
                    document,
                    null,
                    null
                ));
            }
        }
        return new QueueBatchResult(requested, deduplicatedEntryKeys.size(), queued, skipped, failed, results);
    }

    private QueueBatchResult clearBatchInternal(List<UUID> inputIds, List<String> inputEntryKeys) {
        List<UUID> rawIds = inputIds != null ? inputIds : List.of();
        List<String> rawEntryKeys = inputEntryKeys != null ? inputEntryKeys : List.of();
        int requested = rawIds.size() + rawEntryKeys.size();

        LinkedHashSet<String> deduplicatedEntryKeys = new LinkedHashSet<>();
        rawEntryKeys.stream()
            .filter(value -> value != null && !value.isBlank())
            .limit(MAX_BATCH_LIMIT)
            .forEach(value -> deduplicatedEntryKeys.add(value.trim()));
        rawIds.stream()
            .filter(id -> id != null)
            .limit(MAX_BATCH_LIMIT)
            .map(id -> PreviewDeadLetterRegistry.buildEntryKey(id, PreviewDeadLetterRegistry.defaultRenditionKey()))
            .forEach(deduplicatedEntryKeys::add);

        LinkedHashSet<UUID> deduplicatedDocumentIds = new LinkedHashSet<>();
        for (String entryKey : deduplicatedEntryKeys) {
            UUID documentId = extractDocumentIdFromEntryKey(entryKey);
            if (documentId != null) {
                deduplicatedDocumentIds.add(documentId);
            }
        }

        Map<UUID, Document> documents = documentRepository.findAllById(deduplicatedDocumentIds).stream()
            .collect(Collectors.toMap(Document::getId, value -> value));

        List<RecoveryBatchItemDto> results = new ArrayList<>();
        int cleared = 0;
        int skipped = 0;
        int failed = 0;
        for (String entryKey : deduplicatedEntryKeys) {
            UUID documentId = extractDocumentIdFromEntryKey(entryKey);
            if (documentId == null) {
                failed += 1;
                results.add(buildFailedBatchItem("Invalid dead-letter entry key: " + entryKey));
                continue;
            }
            Document document = documents.get(documentId);
            String renditionKey = extractRenditionKeyFromEntryKey(entryKey);
            try {
                PreviewDeadLetterRegistry.DeadLetterEntry existing = previewDeadLetterRegistry.findByEntryKey(entryKey);
                previewDeadLetterRegistry.remove(documentId, renditionKey);
                boolean removed = existing != null;
                if (removed) {
                    cleared += 1;
                } else {
                    skipped += 1;
                }
                results.add(buildRecoveryBatchItem(
                    documentId,
                    removed ? JobState.CLEARED : JobState.SKIPPED,
                    removed ? "CLEARED" : "SKIPPED",
                    removed ? "Dead-letter entry cleared" : "Dead-letter entry not found",
                    document,
                    null,
                    null
                ));
            } catch (Exception ex) {
                failed += 1;
                results.add(buildRecoveryBatchItem(
                    documentId,
                    JobState.FAILED,
                    "FAILED",
                    ex.getMessage(),
                    document,
                    null,
                    null
                ));
            }
        }
        return new QueueBatchResult(requested, deduplicatedEntryKeys.size(), cleared, skipped, failed, results);
    }

    /**
     * Build a failed batch item with no document context — used for invalid entry keys,
     * missing documents, or pre-validation failures where no Document is available.
     */
    private static RecoveryBatchItemDto buildFailedBatchItem(String message) {
        return new RecoveryBatchItemDto(
            null,
            JobState.FAILED,
            "FAILED",
            message,
            null,
            FailureCategory.UNKNOWN,
            null,
            null,
            null,
            0,
            null
        );
    }

    private RecoveryBatchItemDto buildRecoveryBatchItem(
        UUID documentId,
        JobState jobState,
        String outcome,
        String message,
        Document document,
        PreviewQueueService.PreviewQueueStatus status,
        FailureCategory fallbackFailureCategory
    ) {
        EffectivePreviewSummary previewSummary = resolvePreviewMutationSummary(document, status);
        FailureCategory failureCategory = previewSummary.failureCategory();
        if (status != null && status.previewStatus() != null && status.previewFailureCategory() == null && fallbackFailureCategory != null) {
            failureCategory = fallbackFailureCategory;
        }
        return new RecoveryBatchItemDto(
            documentId,
            jobState,
            outcome,
            message,
            previewSummary.previewStatus(),
            failureCategory,
            previewSummary.previewFailureReason(),
            previewSummary.previewFailureCategory(),
            previewSummary.previewLastUpdated(),
            status != null ? status.attempts() : 0,
            status != null ? status.nextAttemptAt() : null
        );
    }

    private DeadLetterScanResult scanDeadLetterByFilter(
        String reason,
        String category,
        Boolean retryable,
        int days,
        int maxDocuments
    ) {
        List<PreviewDeadLetterRegistry.DeadLetterEntry> entries = previewDeadLetterRegistry.list(MAX_BATCH_LIMIT);
        Instant since = days > 0 ? Instant.now().minusSeconds(days * 24L * 60L * 60L) : null;
        int scanned = 0;
        int totalMatched = 0;
        boolean truncated = false;
        List<String> matchedEntryKeys = new ArrayList<>();

        for (PreviewDeadLetterRegistry.DeadLetterEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            scanned += 1;
            if (since != null && entry.failedAt() != null && entry.failedAt().isBefore(since)) {
                continue;
            }

            String normalizedCategory = normalizedUpperOrDefault(entry.category(), "UNKNOWN");
            boolean entryRetryable = PreviewFailureClassifier.CATEGORY_TEMPORARY.equalsIgnoreCase(normalizedCategory);
            boolean categoryMatch = "ANY".equals(category) || category.equals(normalizedCategory);
            boolean retryableMatch = retryable == null || retryable == entryRetryable;
            String normalizedReason = normalizeReason(entry.reason());
            boolean reasonMatch = reason == null || normalizedReason.equals(reason);
            if (!categoryMatch || !retryableMatch || !reasonMatch) {
                continue;
            }

            totalMatched += 1;
            if (matchedEntryKeys.size() < maxDocuments) {
                matchedEntryKeys.add(entry.entryKey());
            } else {
                truncated = true;
            }
        }

        if (!truncated && previewDeadLetterRegistry.getItemCount() > entries.size() && totalMatched >= maxDocuments) {
            truncated = true;
        }

        return new DeadLetterScanResult(totalMatched, scanned, matchedEntryKeys.size(), truncated, matchedEntryKeys);
    }

    private DryRunEvaluation evaluateDryRun(List<Document> candidates, boolean force) {
        List<RecoveryDryRunItemDto> samples = new ArrayList<>();
        int queued = 0;
        int skipped = 0;
        int failed = 0;

        for (Document document : candidates) {
            EffectivePreviewSummary previewSummary = resolveEffectivePreviewSummary(document);
            DryRunPrediction prediction = predict(document, force);
            if (prediction.jobState() == JobState.QUEUED) {
                queued += 1;
            } else if (prediction.jobState() == JobState.SKIPPED) {
                skipped += 1;
            } else if (prediction.jobState() == JobState.FAILED) {
                failed += 1;
            }
            samples.add(buildRecoveryDryRunItem(document.getId(), document, previewSummary, prediction));
        }
        return new DryRunEvaluation(queued, skipped, failed, samples);
    }

    private DryRunEvaluation evaluateDeadLetterClearDryRun(List<String> entryKeys) {
        List<String> rawEntryKeys = entryKeys != null ? entryKeys : List.of();
        LinkedHashSet<String> deduplicatedEntryKeys = new LinkedHashSet<>();
        rawEntryKeys.stream()
            .filter(value -> value != null && !value.isBlank())
            .limit(MAX_BATCH_LIMIT)
            .forEach(value -> deduplicatedEntryKeys.add(value.trim()));

        LinkedHashSet<UUID> deduplicatedDocumentIds = new LinkedHashSet<>();
        for (String entryKey : deduplicatedEntryKeys) {
            UUID documentId = extractDocumentIdFromEntryKey(entryKey);
            if (documentId != null) {
                deduplicatedDocumentIds.add(documentId);
            }
        }
        Map<UUID, Document> documents = documentRepository.findAllById(deduplicatedDocumentIds).stream()
            .collect(Collectors.toMap(Document::getId, value -> value));

        List<RecoveryDryRunItemDto> samples = new ArrayList<>();
        int cleared = 0;
        int skipped = 0;
        int failed = 0;

        for (String entryKey : deduplicatedEntryKeys) {
            UUID documentId = extractDocumentIdFromEntryKey(entryKey);
            if (documentId == null) {
                failed += 1;
                samples.add(buildRecoveryDryRunItem(
                    null,
                    null,
                    unknownEffectivePreviewSummary(),
                    new DryRunPrediction(
                        JobState.FAILED,
                        "FAILED",
                        "Invalid dead-letter entry key"
                    )
                ));
                continue;
            }

            Document document = documents.get(documentId);
            EffectivePreviewSummary previewSummary = resolveEffectivePreviewSummary(document);
            PreviewDeadLetterRegistry.DeadLetterEntry existing = previewDeadLetterRegistry.findByEntryKey(entryKey);
            if (existing != null) {
                cleared += 1;
                samples.add(buildRecoveryDryRunItem(
                    documentId,
                    document,
                    previewSummary,
                    new DryRunPrediction(
                        JobState.CLEARED,
                        "CLEARED",
                        "Would clear dead-letter entry"
                    )
                ));
            } else {
                skipped += 1;
                samples.add(buildRecoveryDryRunItem(
                    documentId,
                    document,
                    previewSummary,
                    new DryRunPrediction(
                        JobState.SKIPPED,
                        "SKIPPED",
                        "Dead-letter entry not found"
                    )
                ));
            }
        }

        return new DryRunEvaluation(cleared, skipped, failed, samples);
    }

    private DryRunEvaluation evaluateDeadLetterReplayDryRun(List<String> entryKeys, boolean force) {
        List<String> rawEntryKeys = entryKeys != null ? entryKeys : List.of();
        LinkedHashSet<String> deduplicatedEntryKeys = new LinkedHashSet<>();
        rawEntryKeys.stream()
            .filter(value -> value != null && !value.isBlank())
            .limit(MAX_BATCH_LIMIT)
            .forEach(value -> deduplicatedEntryKeys.add(value.trim()));

        LinkedHashSet<UUID> deduplicatedDocumentIds = new LinkedHashSet<>();
        for (String entryKey : deduplicatedEntryKeys) {
            UUID documentId = extractDocumentIdFromEntryKey(entryKey);
            if (documentId != null) {
                deduplicatedDocumentIds.add(documentId);
            }
        }
        Map<UUID, Document> documents = documentRepository.findAllById(deduplicatedDocumentIds).stream()
            .collect(Collectors.toMap(Document::getId, value -> value));

        List<RecoveryDryRunItemDto> samples = new ArrayList<>();
        int queued = 0;
        int skipped = 0;
        int failed = 0;

        for (String entryKey : deduplicatedEntryKeys) {
            UUID documentId = extractDocumentIdFromEntryKey(entryKey);
            if (documentId == null) {
                failed += 1;
                samples.add(buildRecoveryDryRunItem(
                    null,
                    null,
                    unknownEffectivePreviewSummary(),
                    new DryRunPrediction(
                        JobState.FAILED,
                        "FAILED",
                        "Invalid dead-letter entry key"
                    )
                ));
                continue;
            }

            Document document = documents.get(documentId);
            EffectivePreviewSummary previewSummary = resolveEffectivePreviewSummary(document);
            DryRunPrediction prediction = predict(document, force);
            if (prediction.jobState() == JobState.QUEUED) {
                queued += 1;
            } else if (prediction.jobState() == JobState.SKIPPED) {
                skipped += 1;
            } else if (prediction.jobState() == JobState.FAILED) {
                failed += 1;
            }
            samples.add(buildRecoveryDryRunItem(documentId, document, previewSummary, prediction));
        }

        return new DryRunEvaluation(queued, skipped, failed, samples);
    }

    private DryRunPrediction predict(Document document, boolean force) {
        if (document == null) {
            return new DryRunPrediction(JobState.FAILED, "FAILED", "Document not found");
        }
        String effectiveStatus = resolveEffectivePreviewStatus(document);
        PreviewStatus status = effectiveStatus != null ? PreviewStatus.valueOf(effectiveStatus) : document.getPreviewStatus();
        FailureCategory failureCategory = resolveFailureCategory(document);
        if (!force) {
            if (status == PreviewStatus.READY) {
                return new DryRunPrediction(JobState.SKIPPED, "SKIPPED", "Preview already up to date");
            }
            if (status == PreviewStatus.UNSUPPORTED) {
                return new DryRunPrediction(JobState.SKIPPED, "SKIPPED", "Preview unsupported");
            }
            if (failureCategory == FailureCategory.UNSUPPORTED) {
                return new DryRunPrediction(JobState.SKIPPED, "SKIPPED", "Preview unsupported");
            }
            if (status == PreviewStatus.FAILED && failureCategory == FailureCategory.PERMANENT) {
                return new DryRunPrediction(JobState.SKIPPED, "SKIPPED", "Preview failed permanently; force required");
            }
        }
        return new DryRunPrediction(JobState.QUEUED, "QUEUED", "Would queue preview recovery");
    }

    private RecoveryBatchResponseDto errorBatch(String mode, String error) {
        return new RecoveryBatchResponseDto(
            "PREVIEW",
            mode,
            DEFAULT_WINDOW_DAYS,
            DEFAULT_BATCH_LIMIT,
            0,
            0,
            0,
            false,
            0,
            0,
            0,
            0,
            0,
            List.of(),
            error
        );
    }

    private void auditRecovery(
        String mode,
        String domain,
        QueueBatchResult queue,
        boolean force,
        String reason,
        String category,
        Boolean retryable,
        int days,
        int maxDocuments
    ) {
        auditService.logEvent(
            "OPS_RECOVERY_" + mode,
            null,
            "OPS_RECOVERY",
            "admin",
            String.format(
                Locale.ROOT,
                "domain=%s mode=%s force=%s reason=%s category=%s retryable=%s days=%d maxDocuments=%d requested=%d deduplicated=%d queued=%d skipped=%d failed=%d",
                domain,
                mode,
                force,
                reason,
                category,
                retryable,
                days,
                maxDocuments,
                queue.requested(),
                queue.deduplicated(),
                queue.queued(),
                queue.skipped(),
                queue.failed()
            )
        );
    }

    private void auditDryRun(
        String domain,
        String mode,
        boolean force,
        String reason,
        String category,
        Boolean retryable,
        int days,
        int maxDocuments,
        long totalCandidates,
        int scanned,
        int matched,
        boolean truncated,
        DryRunEvaluation evaluation
    ) {
        auditService.logEvent(
            "OPS_RECOVERY_DRY_RUN",
            null,
            "OPS_RECOVERY",
            "admin",
            String.format(
                Locale.ROOT,
                "domain=%s mode=%s force=%s reason=%s category=%s retryable=%s days=%d maxDocuments=%d totalCandidates=%d scanned=%d matched=%d truncated=%s estimatedQueued=%d estimatedSkipped=%d estimatedFailed=%d",
                domain,
                mode,
                force,
                reason,
                category,
                retryable,
                days,
                maxDocuments,
                totalCandidates,
                scanned,
                matched,
                truncated,
                evaluation.estimatedQueued(),
                evaluation.estimatedSkipped(),
                evaluation.estimatedFailed()
            )
        );
    }

    private void auditDryRunExport(DryRunComputation computation) {
        RecoveryDryRunResponseDto response = computation.response();
        auditService.logEvent(
            "OPS_RECOVERY_DRY_RUN_EXPORT",
            null,
            "OPS_RECOVERY",
            "admin",
            String.format(
                Locale.ROOT,
                "domain=%s mode=%s force=%s reason=%s category=%s retryable=%s days=%d maxDocuments=%d totalCandidates=%d scanned=%d matched=%d truncated=%s estimatedQueued=%d estimatedSkipped=%d estimatedFailed=%d exportedRows=%d",
                computation.domain(),
                computation.mode().name(),
                computation.force(),
                computation.reason(),
                computation.category(),
                computation.retryable(),
                computation.days(),
                computation.maxDocuments(),
                computation.totalCandidates(),
                computation.scanned(),
                computation.matched(),
                computation.truncated(),
                response.estimatedQueued(),
                response.estimatedSkipped(),
                response.estimatedFailed(),
                response.samples().size()
            )
        );
    }

    private RecoveryHistoryItemDto toRecoveryHistoryItem(AuditLog entry) {
        if (entry == null) {
            return new RecoveryHistoryItemDto(null, null, null, null, "UNKNOWN", null, null, null, null, null, null, null);
        }
        String eventType = entry.getEventType();
        String mode = "UNKNOWN";
        if (eventType != null && eventType.startsWith(OPS_RECOVERY_EVENT_PREFIX)) {
            mode = eventType.substring(OPS_RECOVERY_EVENT_PREFIX.length());
        }
        Document document = null;
        if (entry.getNodeId() != null) {
            document = documentRepository.findById(entry.getNodeId())
                .filter(candidate -> !candidate.isDeleted())
                .orElse(null);
        }
        return buildRecoveryHistoryItem(
            entry.getId(),
            eventType,
            mode,
            entry,
            document
        );
    }

    private RecoveryDryRunItemDto buildRecoveryDryRunItem(
        UUID documentId,
        Document document,
        EffectivePreviewSummary previewSummary,
        DryRunPrediction prediction
    ) {
        EffectivePreviewSummary safePreviewSummary = previewSummary != null
            ? previewSummary
            : unknownEffectivePreviewSummary();
        return new RecoveryDryRunItemDto(
            documentId,
            document != null ? document.getName() : null,
            document != null ? document.getPath() : null,
            document != null ? normalizeMimeType(document.getMimeType()) : null,
            safePreviewSummary.previewStatus(),
            safePreviewSummary.failureCategory(),
            safePreviewSummary.previewFailureReason(),
            safePreviewSummary.previewFailureCategory(),
            safePreviewSummary.previewLastUpdated(),
            prediction.jobState(),
            prediction.outcome(),
            prediction.reason()
        );
    }

    private RecoveryHistoryItemDto buildRecoveryHistoryItem(
        UUID id,
        String eventType,
        String mode,
        AuditLog entry,
        Document document
    ) {
        EffectivePreviewSummary previewSummary = resolveEffectivePreviewSummary(document);
        return new RecoveryHistoryItemDto(
            id,
            entry.getNodeId(),
            entry.getNodeName(),
            eventType,
            mode,
            entry.getUsername(),
            entry.getEventTime(),
            entry.getDetails(),
            previewSummary.previewStatus(),
            previewSummary.previewFailureReason(),
            previewSummary.previewFailureCategory(),
            previewSummary.previewLastUpdated()
        );
    }

    private static EffectivePreviewSummary unknownEffectivePreviewSummary() {
        return new EffectivePreviewSummary(
            null,
            null,
            FailureCategory.UNKNOWN,
            null,
            null
        );
    }

    private RecoveryHistorySummaryItemDto toRecoveryHistorySummaryItem(Object[] row) {
        String eventType = row != null && row.length > 0 && row[0] != null
            ? String.valueOf(row[0])
            : "UNKNOWN";
        long count = 0L;
        if (row != null && row.length > 1 && row[1] instanceof Number number) {
            count = number.longValue();
        } else if (row != null && row.length > 1 && row[1] != null) {
            try {
                count = Long.parseLong(String.valueOf(row[1]));
            } catch (NumberFormatException ignore) {
                count = 0L;
            }
        }
        String mode = eventType.startsWith(OPS_RECOVERY_EVENT_PREFIX)
            ? eventType.substring(OPS_RECOVERY_EVENT_PREFIX.length())
            : "UNKNOWN";
        return new RecoveryHistorySummaryItemDto(eventType, mode, count);
    }

    private RecoveryHistoryActorSummaryItemDto toRecoveryHistoryActorSummaryItem(Object[] row) {
        String actor = row != null && row.length > 0 && row[0] != null
            ? String.valueOf(row[0])
            : "unknown";
        long count = 0L;
        if (row != null && row.length > 1 && row[1] instanceof Number number) {
            count = number.longValue();
        } else if (row != null && row.length > 1 && row[1] != null) {
            try {
                count = Long.parseLong(String.valueOf(row[1]));
            } catch (NumberFormatException ignore) {
                count = 0L;
            }
        }
        return new RecoveryHistoryActorSummaryItemDto(actor, count);
    }

    private RecoveryHistorySummaryResponseDto queryHistorySummary(
        Integer days,
        String mode,
        String actor,
        String eventType
    ) {
        int normalizedDays = normalizeWindowDays(days != null ? days : DEFAULT_WINDOW_DAYS);
        LocalDateTime from = resolveUpdatedSince(normalizedDays);
        String normalizedMode = normalizeHistoryMode(mode);
        String normalizedActor = normalizeActor(actor);
        String normalizedEventType = normalizeHistoryEventType(eventType);
        String exactEventType = resolveHistoryExactEventType(normalizedMode, normalizedEventType);

        List<RecoveryHistorySummaryItemDto> items = auditLogRepository.countByEventTypePrefixWithFilters(
                OPS_RECOVERY_EVENT_PREFIX,
                from,
                normalizedActor,
                exactEventType
            ).stream()
            .map(this::toRecoveryHistorySummaryItem)
            .toList();
        long total = items.stream().mapToLong(RecoveryHistorySummaryItemDto::count).sum();
        List<RecoveryHistoryActorSummaryItemDto> actorItems = auditLogRepository.countByUsernamePrefixWithFilters(
                OPS_RECOVERY_EVENT_PREFIX,
                from,
                normalizedActor,
                exactEventType
            ).stream()
            .map(this::toRecoveryHistoryActorSummaryItem)
            .toList();

        return new RecoveryHistorySummaryResponseDto(
            "PREVIEW",
            normalizedDays,
            normalizedMode,
            normalizedActor,
            normalizedEventType,
            total,
            items,
            actorItems
        );
    }

    private RecoveryHistoryTrendResponseDto queryHistoryTrend(
        Integer days,
        String mode,
        String actor,
        String eventType
    ) {
        int normalizedDays = normalizeWindowDays(days != null ? days : DEFAULT_WINDOW_DAYS);
        LocalDateTime from = resolveUpdatedSince(normalizedDays);
        String normalizedMode = normalizeHistoryMode(mode);
        String normalizedActor = normalizeActor(actor);
        String normalizedEventType = normalizeHistoryEventType(eventType);

        Map<LocalDate, Long> dayCounter = new TreeMap<>();
        int scanned = 0;
        int page = 0;
        while (scanned < MAX_HISTORY_TREND_SCAN) {
            Page<AuditLog> historyPage = queryHistoryPage(
                HISTORY_TREND_PAGE_LIMIT,
                from,
                normalizedMode,
                normalizedActor,
                normalizedEventType,
                page
            );
            if (historyPage.isEmpty()) {
                break;
            }
            for (AuditLog entry : historyPage.getContent()) {
                if (entry == null || entry.getEventTime() == null) {
                    continue;
                }
                LocalDate day = entry.getEventTime().toLocalDate();
                dayCounter.merge(day, 1L, Long::sum);
                scanned += 1;
                if (scanned >= MAX_HISTORY_TREND_SCAN) {
                    break;
                }
            }
            if (!historyPage.hasNext()) {
                break;
            }
            page += 1;
        }
        List<RecoveryHistoryTrendItemDto> items = dayCounter.entrySet().stream()
            .sorted(Map.Entry.<LocalDate, Long>comparingByKey().reversed())
            .map(entry -> new RecoveryHistoryTrendItemDto(entry.getKey().toString(), entry.getValue()))
            .toList();
        long total = items.stream().mapToLong(RecoveryHistoryTrendItemDto::count).sum();
        boolean truncated = scanned >= MAX_HISTORY_TREND_SCAN;
        return new RecoveryHistoryTrendResponseDto(
            "PREVIEW",
            normalizedDays,
            normalizedMode,
            normalizedActor,
            normalizedEventType,
            total,
            truncated,
            items
        );
    }

    private RecoveryHistorySummaryCompareResponseDto queryHistorySummaryCompare(
        Integer days,
        String mode,
        String actor,
        String eventType
    ) {
        RecoveryHistorySummaryResponseDto current = queryHistorySummary(days, mode, actor, eventType);
        int normalizedDays = current.windowDays();
        if (normalizedDays <= 0) {
            return new RecoveryHistorySummaryCompareResponseDto(
                current.domain(),
                current.windowDays(),
                0,
                current.modeFilter(),
                current.actorFilter(),
                current.eventTypeFilter(),
                current.total(),
                0L,
                current.total(),
                null,
                false,
                false
            );
        }

        LocalDateTime windowBoundary = LocalDateTime.now().minusDays(normalizedDays);
        LocalDateTime previousFrom = windowBoundary.minusDays(normalizedDays);
        CountRangeResult previous = countHistoryEntriesInRange(
            previousFrom,
            windowBoundary,
            current.modeFilter(),
            current.actorFilter(),
            current.eventTypeFilter()
        );
        long delta = current.total() - previous.total();
        Double deltaPercent = previous.total() > 0
            ? (delta * 100.0d) / previous.total()
            : null;
        return new RecoveryHistorySummaryCompareResponseDto(
            current.domain(),
            current.windowDays(),
            current.windowDays(),
            current.modeFilter(),
            current.actorFilter(),
            current.eventTypeFilter(),
            current.total(),
            previous.total(),
            delta,
            deltaPercent,
            true,
            previous.truncated()
        );
    }

    private RecoveryHistorySummaryCompareBreakdownResponseDto queryHistorySummaryCompareBreakdown(
        Integer days,
        String mode,
        String actor,
        String eventType,
        Integer limit,
        String sort
    ) {
        RecoveryHistorySummaryResponseDto current = queryHistorySummary(days, mode, actor, eventType);
        int normalizedLimit = normalizeCompareBreakdownLimit(limit != null ? limit : DEFAULT_COMPARE_BREAKDOWN_LIMIT);
        String normalizedSort = normalizeCompareBreakdownSort(sort);
        Map<String, Long> currentCountsByEventType = current.items().stream()
            .collect(Collectors.toMap(
                RecoveryHistorySummaryItemDto::eventType,
                RecoveryHistorySummaryItemDto::count,
                Long::sum
            ));
        LinkedHashSet<String> allEventTypes = current.items().stream()
            .map(RecoveryHistorySummaryItemDto::eventType)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        int normalizedDays = current.windowDays();
        if (normalizedDays <= 0) {
            List<RecoveryHistorySummaryCompareBreakdownItemDto> items = allEventTypes.stream()
                .map(eventTypeKey -> {
                    long currentCount = currentCountsByEventType.getOrDefault(eventTypeKey, 0L);
                    return new RecoveryHistorySummaryCompareBreakdownItemDto(
                        eventTypeKey,
                        toRecoveryMode(eventTypeKey),
                        currentCount,
                        0L,
                        currentCount,
                        null
                    );
                })
                .toList();
            List<RecoveryHistorySummaryCompareBreakdownItemDto> normalizedItems = sortAndLimitCompareBreakdownItems(
                items,
                normalizedSort,
                normalizedLimit
            );
            return new RecoveryHistorySummaryCompareBreakdownResponseDto(
                current.domain(),
                current.windowDays(),
                0,
                current.modeFilter(),
                current.actorFilter(),
                current.eventTypeFilter(),
                false,
                false,
                normalizedSort,
                normalizedLimit,
                items.size(),
                normalizedItems.size() < items.size(),
                normalizedItems
            );
        }

        LocalDateTime windowBoundary = LocalDateTime.now().minusDays(normalizedDays);
        LocalDateTime previousFrom = windowBoundary.minusDays(normalizedDays);
        GroupedCountRangeResult previous = countHistoryEntriesByEventTypeInRange(
            previousFrom,
            windowBoundary,
            current.modeFilter(),
            current.actorFilter(),
            current.eventTypeFilter()
        );
        allEventTypes.addAll(previous.countByEventType().keySet());
        List<RecoveryHistorySummaryCompareBreakdownItemDto> items = allEventTypes.stream()
            .map(eventTypeKey -> {
                long currentCount = currentCountsByEventType.getOrDefault(eventTypeKey, 0L);
                long previousCount = previous.countByEventType().getOrDefault(eventTypeKey, 0L);
                long delta = currentCount - previousCount;
                Double deltaPercent = previousCount > 0 ? (delta * 100.0d) / previousCount : null;
                return new RecoveryHistorySummaryCompareBreakdownItemDto(
                    eventTypeKey,
                    toRecoveryMode(eventTypeKey),
                    currentCount,
                    previousCount,
                    delta,
                    deltaPercent
                );
            })
            .toList();
        List<RecoveryHistorySummaryCompareBreakdownItemDto> normalizedItems = sortAndLimitCompareBreakdownItems(
            items,
            normalizedSort,
            normalizedLimit
        );
        return new RecoveryHistorySummaryCompareBreakdownResponseDto(
            current.domain(),
            current.windowDays(),
            current.windowDays(),
            current.modeFilter(),
            current.actorFilter(),
            current.eventTypeFilter(),
            true,
            previous.truncated(),
            normalizedSort,
            normalizedLimit,
            items.size(),
            normalizedItems.size() < items.size(),
            normalizedItems
        );
    }

    private RecoveryHistorySummaryCompareActorsResponseDto queryHistorySummaryCompareActors(
        Integer days,
        String mode,
        String actor,
        String eventType,
        Integer limit,
        String sort
    ) {
        RecoveryHistorySummaryResponseDto current = queryHistorySummary(days, mode, actor, eventType);
        int normalizedLimit = normalizeCompareActorLimit(limit != null ? limit : DEFAULT_COMPARE_ACTOR_LIMIT);
        String normalizedSort = normalizeCompareActorSort(sort);

        Map<String, Long> currentCountsByActor = current.actorItems().stream()
            .collect(Collectors.toMap(
                item -> item.actor() != null ? item.actor() : "unknown",
                RecoveryHistoryActorSummaryItemDto::count,
                Long::sum
            ));
        LinkedHashSet<String> allActors = current.actorItems().stream()
            .map(item -> item.actor() != null ? item.actor() : "unknown")
            .collect(Collectors.toCollection(LinkedHashSet::new));
        int normalizedDays = current.windowDays();
        if (normalizedDays <= 0) {
            List<RecoveryHistorySummaryCompareActorItemDto> items = allActors.stream()
                .map(actorKey -> {
                    long currentCount = currentCountsByActor.getOrDefault(actorKey, 0L);
                    return new RecoveryHistorySummaryCompareActorItemDto(
                        actorKey,
                        currentCount,
                        0L,
                        currentCount,
                        null
                    );
                })
                .toList();
            List<RecoveryHistorySummaryCompareActorItemDto> normalizedItems = sortAndLimitCompareActorItems(
                items,
                normalizedSort,
                normalizedLimit
            );
            return new RecoveryHistorySummaryCompareActorsResponseDto(
                current.domain(),
                current.windowDays(),
                0,
                current.modeFilter(),
                current.actorFilter(),
                current.eventTypeFilter(),
                false,
                false,
                normalizedSort,
                normalizedLimit,
                items.size(),
                normalizedItems.size() < items.size(),
                normalizedItems
            );
        }

        LocalDateTime windowBoundary = LocalDateTime.now().minusDays(normalizedDays);
        LocalDateTime previousFrom = windowBoundary.minusDays(normalizedDays);
        GroupedCountRangeResult previous = countHistoryEntriesByActorInRange(
            previousFrom,
            windowBoundary,
            current.modeFilter(),
            current.actorFilter(),
            current.eventTypeFilter()
        );
        allActors.addAll(previous.countByEventType().keySet());
        List<RecoveryHistorySummaryCompareActorItemDto> items = allActors.stream()
            .map(actorKey -> {
                long currentCount = currentCountsByActor.getOrDefault(actorKey, 0L);
                long previousCount = previous.countByEventType().getOrDefault(actorKey, 0L);
                long delta = currentCount - previousCount;
                Double deltaPercent = previousCount > 0 ? (delta * 100.0d) / previousCount : null;
                return new RecoveryHistorySummaryCompareActorItemDto(
                    actorKey,
                    currentCount,
                    previousCount,
                    delta,
                    deltaPercent
                );
            })
            .toList();
        List<RecoveryHistorySummaryCompareActorItemDto> normalizedItems = sortAndLimitCompareActorItems(
            items,
            normalizedSort,
            normalizedLimit
        );
        return new RecoveryHistorySummaryCompareActorsResponseDto(
            current.domain(),
            current.windowDays(),
            current.windowDays(),
            current.modeFilter(),
            current.actorFilter(),
            current.eventTypeFilter(),
            true,
            previous.truncated(),
            normalizedSort,
            normalizedLimit,
            items.size(),
            normalizedItems.size() < items.size(),
            normalizedItems
        );
    }

    private GroupedCountRangeResult countHistoryEntriesByEventTypeInRange(
        LocalDateTime fromInclusive,
        LocalDateTime toExclusive,
        String modeFilter,
        String actorFilter,
        String eventTypeFilter
    ) {
        String normalizedMode = normalizeHistoryMode(modeFilter);
        String normalizedActor = normalizeActor(actorFilter);
        String normalizedEventType = normalizeHistoryEventType(eventTypeFilter);
        Map<String, Long> counts = new TreeMap<>();
        int scanned = 0;
        int page = 0;
        while (scanned < MAX_HISTORY_TREND_SCAN) {
            Page<AuditLog> historyPage = queryHistoryPage(
                HISTORY_TREND_PAGE_LIMIT,
                fromInclusive,
                normalizedMode,
                normalizedActor,
                normalizedEventType,
                page
            );
            if (historyPage.isEmpty()) {
                break;
            }
            for (AuditLog entry : historyPage.getContent()) {
                if (entry == null || entry.getEventTime() == null) {
                    continue;
                }
                if (entry.getEventTime().isBefore(toExclusive)) {
                    String key = entry.getEventType() != null ? entry.getEventType() : "UNKNOWN";
                    counts.merge(key, 1L, Long::sum);
                }
                scanned += 1;
                if (scanned >= MAX_HISTORY_TREND_SCAN) {
                    break;
                }
            }
            if (!historyPage.hasNext()) {
                break;
            }
            page += 1;
        }
        return new GroupedCountRangeResult(counts, scanned >= MAX_HISTORY_TREND_SCAN);
    }

    private GroupedCountRangeResult countHistoryEntriesByActorInRange(
        LocalDateTime fromInclusive,
        LocalDateTime toExclusive,
        String modeFilter,
        String actorFilter,
        String eventTypeFilter
    ) {
        String normalizedMode = normalizeHistoryMode(modeFilter);
        String normalizedActor = normalizeActor(actorFilter);
        String normalizedEventType = normalizeHistoryEventType(eventTypeFilter);
        Map<String, Long> counts = new TreeMap<>();
        int scanned = 0;
        int page = 0;
        while (scanned < MAX_HISTORY_TREND_SCAN) {
            Page<AuditLog> historyPage = queryHistoryPage(
                HISTORY_TREND_PAGE_LIMIT,
                fromInclusive,
                normalizedMode,
                normalizedActor,
                normalizedEventType,
                page
            );
            if (historyPage.isEmpty()) {
                break;
            }
            for (AuditLog entry : historyPage.getContent()) {
                if (entry == null || entry.getEventTime() == null) {
                    continue;
                }
                if (entry.getEventTime().isBefore(toExclusive)) {
                    String key = (entry.getUsername() == null || entry.getUsername().isBlank())
                        ? "unknown"
                        : entry.getUsername();
                    counts.merge(key, 1L, Long::sum);
                }
                scanned += 1;
                if (scanned >= MAX_HISTORY_TREND_SCAN) {
                    break;
                }
            }
            if (!historyPage.hasNext()) {
                break;
            }
            page += 1;
        }
        return new GroupedCountRangeResult(counts, scanned >= MAX_HISTORY_TREND_SCAN);
    }

    private static String toRecoveryMode(String eventType) {
        if (eventType == null) {
            return "UNKNOWN";
        }
        return eventType.startsWith(OPS_RECOVERY_EVENT_PREFIX)
            ? eventType.substring(OPS_RECOVERY_EVENT_PREFIX.length())
            : "UNKNOWN";
    }

    private static int normalizeCompareBreakdownLimit(int rawLimit) {
        return clamp(rawLimit, 1, MAX_COMPARE_BREAKDOWN_LIMIT);
    }

    private static String normalizeCompareBreakdownSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_COMPARE_BREAKDOWN_SORT;
        }
        String normalized = sort.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED_COMPARE_BREAKDOWN_SORTS.contains(normalized)
            ? normalized
            : DEFAULT_COMPARE_BREAKDOWN_SORT;
    }

    private static int normalizeCompareActorLimit(int rawLimit) {
        return clamp(rawLimit, 1, MAX_COMPARE_ACTOR_LIMIT);
    }

    private static String normalizeCompareActorSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_COMPARE_ACTOR_SORT;
        }
        String normalized = sort.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED_COMPARE_ACTOR_SORTS.contains(normalized)
            ? normalized
            : DEFAULT_COMPARE_ACTOR_SORT;
    }

    private static List<RecoveryHistorySummaryCompareBreakdownItemDto> sortAndLimitCompareBreakdownItems(
        List<RecoveryHistorySummaryCompareBreakdownItemDto> items,
        String normalizedSort,
        int normalizedLimit
    ) {
        Comparator<RecoveryHistorySummaryCompareBreakdownItemDto> comparator = compareBreakdownComparator(normalizedSort);
        List<RecoveryHistorySummaryCompareBreakdownItemDto> sorted = items.stream()
            .sorted(comparator)
            .toList();
        if (sorted.size() <= normalizedLimit) {
            return sorted;
        }
        return new ArrayList<>(sorted.subList(0, normalizedLimit));
    }

    private static Comparator<RecoveryHistorySummaryCompareBreakdownItemDto> compareBreakdownComparator(String normalizedSort) {
        Comparator<RecoveryHistorySummaryCompareBreakdownItemDto> eventTypeAsc =
            Comparator.comparing(item -> item.eventType() != null ? item.eventType() : "", String.CASE_INSENSITIVE_ORDER);
        return switch (normalizedSort) {
            case "DELTA_DESC" -> Comparator.comparingLong(RecoveryHistorySummaryCompareBreakdownItemDto::delta)
                .reversed()
                .thenComparing(eventTypeAsc);
            case "DELTA_ASC" -> Comparator.comparingLong(RecoveryHistorySummaryCompareBreakdownItemDto::delta)
                .thenComparing(eventTypeAsc);
            case "CURRENT_DESC" -> Comparator.comparingLong(RecoveryHistorySummaryCompareBreakdownItemDto::currentCount)
                .reversed()
                .thenComparing(eventTypeAsc);
            case "PREVIOUS_DESC" -> Comparator.comparingLong(RecoveryHistorySummaryCompareBreakdownItemDto::previousCount)
                .reversed()
                .thenComparing(eventTypeAsc);
            case "EVENT_TYPE_ASC" -> eventTypeAsc;
            default -> Comparator.comparingLong((RecoveryHistorySummaryCompareBreakdownItemDto item) -> Math.abs(item.delta()))
                .reversed()
                .thenComparing(Comparator.comparingLong(RecoveryHistorySummaryCompareBreakdownItemDto::delta).reversed())
                .thenComparing(eventTypeAsc);
        };
    }

    private static List<RecoveryHistorySummaryCompareActorItemDto> sortAndLimitCompareActorItems(
        List<RecoveryHistorySummaryCompareActorItemDto> items,
        String normalizedSort,
        int normalizedLimit
    ) {
        Comparator<RecoveryHistorySummaryCompareActorItemDto> comparator = compareActorComparator(normalizedSort);
        List<RecoveryHistorySummaryCompareActorItemDto> sorted = items.stream()
            .sorted(comparator)
            .toList();
        if (sorted.size() <= normalizedLimit) {
            return sorted;
        }
        return new ArrayList<>(sorted.subList(0, normalizedLimit));
    }

    private static Comparator<RecoveryHistorySummaryCompareActorItemDto> compareActorComparator(String normalizedSort) {
        Comparator<RecoveryHistorySummaryCompareActorItemDto> actorAsc =
            Comparator.comparing(item -> item.actor() != null ? item.actor() : "", String.CASE_INSENSITIVE_ORDER);
        return switch (normalizedSort) {
            case "DELTA_DESC" -> Comparator.comparingLong(RecoveryHistorySummaryCompareActorItemDto::delta)
                .reversed()
                .thenComparing(actorAsc);
            case "DELTA_ASC" -> Comparator.comparingLong(RecoveryHistorySummaryCompareActorItemDto::delta)
                .thenComparing(actorAsc);
            case "CURRENT_DESC" -> Comparator.comparingLong(RecoveryHistorySummaryCompareActorItemDto::currentCount)
                .reversed()
                .thenComparing(actorAsc);
            case "PREVIOUS_DESC" -> Comparator.comparingLong(RecoveryHistorySummaryCompareActorItemDto::previousCount)
                .reversed()
                .thenComparing(actorAsc);
            case "ACTOR_ASC" -> actorAsc;
            default -> Comparator.comparingLong((RecoveryHistorySummaryCompareActorItemDto item) -> Math.abs(item.delta()))
                .reversed()
                .thenComparing(Comparator.comparingLong(RecoveryHistorySummaryCompareActorItemDto::delta).reversed())
                .thenComparing(actorAsc);
        };
    }

    private CountRangeResult countHistoryEntriesInRange(
        LocalDateTime fromInclusive,
        LocalDateTime toExclusive,
        String modeFilter,
        String actorFilter,
        String eventTypeFilter
    ) {
        String normalizedMode = normalizeHistoryMode(modeFilter);
        String normalizedActor = normalizeActor(actorFilter);
        String normalizedEventType = normalizeHistoryEventType(eventTypeFilter);
        long total = 0L;
        int scanned = 0;
        int page = 0;
        while (scanned < MAX_HISTORY_TREND_SCAN) {
            Page<AuditLog> historyPage = queryHistoryPage(
                HISTORY_TREND_PAGE_LIMIT,
                fromInclusive,
                normalizedMode,
                normalizedActor,
                normalizedEventType,
                page
            );
            if (historyPage.isEmpty()) {
                break;
            }
            for (AuditLog entry : historyPage.getContent()) {
                if (entry == null || entry.getEventTime() == null) {
                    continue;
                }
                if (entry.getEventTime().isBefore(toExclusive)) {
                    total += 1;
                }
                scanned += 1;
                if (scanned >= MAX_HISTORY_TREND_SCAN) {
                    break;
                }
            }
            if (!historyPage.hasNext()) {
                break;
            }
            page += 1;
        }
        return new CountRangeResult(total, scanned >= MAX_HISTORY_TREND_SCAN);
    }

    private Page<AuditLog> queryHistoryPage(
        int limit,
        LocalDateTime from,
        String normalizedMode,
        String normalizedActor,
        String normalizedEventType,
        int page
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), limit);
        boolean hasActor = normalizedActor != null;
        if (normalizedEventType != null) {
            if (!hasActor) {
                return from == null
                    ? auditLogRepository.findByEventTypeOrderByEventTimeDesc(normalizedEventType, pageable)
                    : auditLogRepository.findByEventTypeAndEventTimeGreaterThanEqualOrderByEventTimeDesc(
                        normalizedEventType,
                        from,
                        pageable
                    );
            }
            return from == null
                ? auditLogRepository.findByEventTypeAndUsernameOrderByEventTimeDesc(
                    normalizedEventType,
                    normalizedActor,
                    pageable
                )
                : auditLogRepository.findByEventTypeAndUsernameAndEventTimeGreaterThanEqualOrderByEventTimeDesc(
                    normalizedEventType,
                    normalizedActor,
                    from,
                    pageable
                );
        }
        if (normalizedMode == null) {
            if (!hasActor) {
                return from == null
                    ? auditLogRepository.findByEventTypePrefix(OPS_RECOVERY_EVENT_PREFIX, pageable)
                    : auditLogRepository.findByEventTypePrefixSince(OPS_RECOVERY_EVENT_PREFIX, from, pageable);
            }
            return from == null
                ? auditLogRepository.findByEventTypePrefixAndUsername(OPS_RECOVERY_EVENT_PREFIX, normalizedActor, pageable)
                : auditLogRepository.findByEventTypePrefixSinceAndUsername(OPS_RECOVERY_EVENT_PREFIX, from, normalizedActor, pageable);
        }
        String eventType = OPS_RECOVERY_EVENT_PREFIX + normalizedMode;
        if (!hasActor) {
            return from == null
                ? auditLogRepository.findByEventTypeOrderByEventTimeDesc(eventType, pageable)
                : auditLogRepository.findByEventTypeAndEventTimeGreaterThanEqualOrderByEventTimeDesc(eventType, from, pageable);
        }
        return from == null
            ? auditLogRepository.findByEventTypeAndUsernameOrderByEventTimeDesc(eventType, normalizedActor, pageable)
            : auditLogRepository.findByEventTypeAndUsernameAndEventTimeGreaterThanEqualOrderByEventTimeDesc(
                eventType,
                normalizedActor,
                from,
                pageable
            );
    }

    private static String resolveHistoryExactEventType(String normalizedMode, String normalizedEventType) {
        if (normalizedEventType != null) {
            return normalizedEventType;
        }
        if (normalizedMode != null) {
            return OPS_RECOVERY_EVENT_PREFIX + normalizedMode;
        }
        return null;
    }

    private RecoveryHistoryExportAsyncSnapshot copyHistoryExportAsyncRequest(RecoveryHistoryExportAsyncRequestDto request) {
        String rawExportType = request != null ? request.exportType() : null;
        HistoryExportAsyncType exportType = normalizeHistoryExportAsyncType(rawExportType);
        int days = normalizeWindowDays(request != null && request.days() != null ? request.days() : DEFAULT_WINDOW_DAYS);
        int limit = clamp(
            request != null && request.limit() != null ? request.limit() : DEFAULT_HISTORY_EXPORT_LIMIT,
            1,
            MAX_HISTORY_EXPORT_LIMIT
        );
        String mode = normalizeHistoryMode(request != null ? request.mode() : null);
        String actor = normalizeActor(request != null ? request.actor() : null);
        String eventType = normalizeHistoryEventType(request != null ? request.eventType() : null);
        int compareBreakdownLimit = normalizeCompareBreakdownLimit(
            request != null && request.compareBreakdownLimit() != null
                ? request.compareBreakdownLimit()
                : DEFAULT_COMPARE_BREAKDOWN_LIMIT
        );
        String compareBreakdownSort = normalizeCompareBreakdownSort(request != null ? request.compareBreakdownSort() : null);
        int compareActorLimit = normalizeCompareActorLimit(
            request != null && request.compareActorLimit() != null
                ? request.compareActorLimit()
                : DEFAULT_COMPARE_ACTOR_LIMIT
        );
        String compareActorSort = normalizeCompareActorSort(request != null ? request.compareActorSort() : null);
        return new RecoveryHistoryExportAsyncSnapshot(
            exportType,
            limit,
            days,
            mode,
            actor,
            eventType,
            compareBreakdownLimit,
            compareBreakdownSort,
            compareActorLimit,
            compareActorSort
        );
    }

    private static HistoryExportAsyncType normalizeHistoryExportAsyncType(String rawExportType) {
        if (rawExportType == null || rawExportType.isBlank()) {
            return HistoryExportAsyncType.HISTORY;
        }
        try {
            return HistoryExportAsyncType.valueOf(rawExportType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported exportType: " + rawExportType);
        }
    }

    private static HistoryExportAsyncType normalizeHistoryExportAsyncTypeFilter(String rawExportType) {
        if (rawExportType == null || rawExportType.isBlank()) {
            return null;
        }
        if ("ALL".equalsIgnoreCase(rawExportType.trim())) {
            return null;
        }
        return normalizeHistoryExportAsyncType(rawExportType);
    }

    private static RecoveryHistoryExportAsyncStatus normalizeHistoryExportAsyncCleanupStatusFilter(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        RecoveryHistoryExportAsyncStatus normalizedStatus = normalizeHistoryExportAsyncStatus(
            rawStatus,
            "Unsupported cleanup status filter: "
        );
        if (normalizedStatus == RecoveryHistoryExportAsyncStatus.QUEUED
            || normalizedStatus == RecoveryHistoryExportAsyncStatus.RUNNING) {
            throw new IllegalArgumentException("Cleanup status filter cannot be QUEUED or RUNNING");
        }
        return normalizedStatus;
    }

    private static RecoveryHistoryExportAsyncStatus normalizeHistoryExportAsyncRetryTerminalStatusFilter(
        String rawStatus
    ) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        RecoveryHistoryExportAsyncStatus normalizedStatus = normalizeHistoryExportAsyncStatus(
            rawStatus,
            "Unsupported retry-terminal status filter: "
        );
        if (isHistoryExportAsyncActiveStatus(normalizedStatus)) {
            throw new IllegalArgumentException(
                "Retry-terminal status filter only supports terminal states: COMPLETED, CANCELLED, FAILED, TIMED_OUT, EXPIRED"
            );
        }
        return normalizedStatus;
    }

    private static RecoveryHistoryExportAsyncStatus normalizeHistoryExportAsyncCancelActiveStatusFilter(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        RecoveryHistoryExportAsyncStatus normalizedStatus = normalizeHistoryExportAsyncStatus(
            rawStatus,
            "Unsupported cancel-active status filter: "
        );
        if (!isHistoryExportAsyncActiveStatus(normalizedStatus)) {
            throw new IllegalArgumentException("Cancel-active status filter must be QUEUED or RUNNING");
        }
        return normalizedStatus;
    }

    private static RecoveryHistoryExportAsyncStatus normalizeHistoryExportAsyncStatusFilter(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        return normalizeHistoryExportAsyncStatus(rawStatus, "Unsupported status filter: ");
    }

    private static RecoveryHistoryExportAsyncStatus normalizeHistoryExportAsyncStatus(
        String rawStatus,
        String errorPrefix
    ) {
        try {
            return RecoveryHistoryExportAsyncStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(errorPrefix + rawStatus);
        }
    }

    private static boolean isHistoryExportAsyncActiveStatus(RecoveryHistoryExportAsyncStatus status) {
        return status == RecoveryHistoryExportAsyncStatus.QUEUED
            || status == RecoveryHistoryExportAsyncStatus.RUNNING;
    }

    private RecoveryHistoryExportAsyncTask createHistoryExportAsyncTask(RecoveryHistoryExportAsyncSnapshot snapshot) {
        String taskId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String actor = resolveAuditUsername();
        RecoveryHistoryExportAsyncSnapshot normalizedSnapshot = snapshot != null
            ? snapshot
            : copyHistoryExportAsyncRequest(null);
        RecoveryHistoryExportAsyncTask task = new RecoveryHistoryExportAsyncTask(
            taskId,
            normalizedSnapshot.exportType(),
            now,
            null,
            now,
            resolveAsyncTaskTimeoutAt(now),
            resolveAsyncTaskExpiresAt(now),
            resolveTaskActor(actor),
            resolveTaskActor(actor),
            RecoveryHistoryExportAsyncStatus.QUEUED,
            null,
            null,
            null,
            null
        );
        synchronized (historyExportAsyncTaskLock) {
            historyExportAsyncTasks.put(taskId, task);
            historyExportAsyncTaskSnapshots.put(taskId, normalizedSnapshot);
            historyExportAsyncTaskOrder.addLast(taskId);
            trimHistoryExportAsyncTasksLocked();
        }
        return task;
    }

    private RecoveryHistoryExportAsyncTask findActiveHistoryExportAsyncTaskBySnapshotLocked(
        RecoveryHistoryExportAsyncSnapshot snapshot
    ) {
        RecoveryHistoryExportAsyncSnapshot normalizedSnapshot = snapshot != null
            ? snapshot
            : copyHistoryExportAsyncRequest(null);
        Iterator<String> iterator = historyExportAsyncTaskOrder.descendingIterator();
        while (iterator.hasNext()) {
            String candidateTaskId = iterator.next();
            RecoveryHistoryExportAsyncTask candidateTask = historyExportAsyncTasks.get(candidateTaskId);
            if (candidateTask == null || candidateTask.isTerminal()) {
                continue;
            }
            RecoveryHistoryExportAsyncSnapshot candidateSnapshot = historyExportAsyncTaskSnapshots.get(candidateTaskId);
            if (Objects.equals(candidateSnapshot, normalizedSnapshot)) {
                return candidateTask;
            }
        }
        return null;
    }

    private RecoveryHistoryExportAsyncCreateResponseDto toHistoryExportAsyncCreateResponse(
        RecoveryHistoryExportAsyncTask task,
        boolean deduplicated,
        String deduplicatedFromTaskId,
        String message
    ) {
        if (task == null) {
            return null;
        }
        RecoveryHistoryExportAsyncRequestSnapshotDto request = toHistoryExportAsyncRequestSnapshotDto(
            historyExportAsyncTaskSnapshots.get(task.taskId())
        );
        return new RecoveryHistoryExportAsyncCreateResponseDto(
            task.taskId(),
            task.exportType().name(),
            task.status().name(),
            request,
            task.createdAt(),
            task.timeoutAt(),
            task.expiresAt(),
            task.createdBy(),
            task.updatedBy(),
            deduplicated,
            deduplicatedFromTaskId,
            message
        );
    }

    private ResponseEntity<RecoveryHistoryExportAsyncCreateResponseDto> acceptedHistoryExportAsyncCreateResponse(
        RecoveryHistoryExportAsyncCreateResponseDto payload,
        String taskId
    ) {
        String normalizedTaskId = taskId != null ? taskId.trim() : "";
        String location = normalizedTaskId.isEmpty()
            ? historyExportAsyncTaskListLocation()
            : "/api/v1/ops/recovery/history/export-async/" + normalizedTaskId;
        return ResponseEntity.accepted()
            .header(HttpHeaders.LOCATION, location)
            .body(payload);
    }

    private ResponseEntity<RecoveryHistoryExportAsyncRetryTerminalResponseDto> acceptedHistoryExportAsyncRetryTerminalResponse(
        RecoveryHistoryExportAsyncRetryTerminalResponseDto payload
    ) {
        return ResponseEntity.accepted()
            .header(HttpHeaders.LOCATION, historyExportAsyncTaskListLocation())
            .body(payload);
    }

    private static String historyExportAsyncTaskListLocation() {
        return "/api/v1/ops/recovery/history/export-async";
    }

    private void runHistoryExportAsyncTask(RecoveryHistoryExportAsyncTask initialTask) {
        historyExportAsyncTasks.computeIfPresent(initialTask.taskId(), (key, current) ->
            current.status() == RecoveryHistoryExportAsyncStatus.QUEUED
                ? current.withStatus(RecoveryHistoryExportAsyncStatus.RUNNING, resolveAuditUsername())
                : current
        );
        RecoveryHistoryExportAsyncTask current = historyExportAsyncTasks.get(initialTask.taskId());
        if (current == null) {
            return;
        }
        if (current.isTerminal()) {
            return;
        }
        try {
            RecoveryHistoryExportAsyncSnapshot request = historyExportAsyncTaskSnapshots.get(initialTask.taskId());
            if (request == null) {
                request = copyHistoryExportAsyncRequest(null);
            }
            RecoveryHistoryExportPayload payload = buildHistoryExportAsyncPayload(request);
            historyExportAsyncTasks.computeIfPresent(initialTask.taskId(), (key, runningTask) ->
                runningTask.isTerminal()
                    ? runningTask
                    : runningTask.complete(payload.filename(), payload.csvContent(), resolveAuditUsername())
            );
        } catch (Exception ex) {
            historyExportAsyncTasks.computeIfPresent(initialTask.taskId(), (key, runningTask) ->
                runningTask.isTerminal()
                    ? runningTask
                    : runningTask.fail(resolveErrorMessage(ex), resolveAuditUsername())
            );
        } finally {
            synchronized (historyExportAsyncTaskLock) {
                trimHistoryExportAsyncTasksLocked();
            }
        }
    }

    private RecoveryHistoryExportPayload buildHistoryExportAsyncPayload(RecoveryHistoryExportAsyncSnapshot request) {
        return switch (request.exportType()) {
            case HISTORY -> {
                LocalDateTime from = resolveUpdatedSince(request.days());
                Page<AuditLog> page = queryHistoryPage(
                    request.limit(),
                    from,
                    request.mode(),
                    request.actor(),
                    request.eventType(),
                    0
                );
                String csv = buildHistoryCsv(page.getContent());
                auditHistoryExport(
                    request.limit(),
                    request.days(),
                    request.mode(),
                    request.actor(),
                    request.eventType(),
                    page.getNumberOfElements()
                );
                yield new RecoveryHistoryExportPayload(
                    buildHistoryExportAsyncFilename("ops_recovery_history"),
                    csv.getBytes(StandardCharsets.UTF_8)
                );
            }
            case HISTORY_SUMMARY -> {
                RecoveryHistorySummaryResponseDto summary = queryHistorySummary(
                    request.days(),
                    request.mode(),
                    request.actor(),
                    request.eventType()
                );
                String csv = buildHistorySummaryCsv(summary.items(), summary.actorItems());
                int exportedRows = summary.items().size() + summary.actorItems().size();
                auditHistorySummaryExport(
                    summary.windowDays(),
                    summary.modeFilter(),
                    summary.actorFilter(),
                    summary.eventTypeFilter(),
                    exportedRows
                );
                yield new RecoveryHistoryExportPayload(
                    buildHistoryExportAsyncFilename("ops_recovery_history_summary"),
                    csv.getBytes(StandardCharsets.UTF_8)
                );
            }
            case HISTORY_TREND -> {
                RecoveryHistoryTrendResponseDto trend = queryHistoryTrend(
                    request.days(),
                    request.mode(),
                    request.actor(),
                    request.eventType()
                );
                String csv = buildHistoryTrendCsv(trend.items());
                auditHistoryTrendExport(
                    trend.windowDays(),
                    trend.modeFilter(),
                    trend.actorFilter(),
                    trend.eventTypeFilter(),
                    trend.items().size()
                );
                yield new RecoveryHistoryExportPayload(
                    buildHistoryExportAsyncFilename("ops_recovery_history_trend"),
                    csv.getBytes(StandardCharsets.UTF_8)
                );
            }
            case HISTORY_COMPARE -> {
                RecoveryHistorySummaryCompareResponseDto compare = queryHistorySummaryCompare(
                    request.days(),
                    request.mode(),
                    request.actor(),
                    request.eventType()
                );
                String csv = buildHistoryCompareCsv(compare);
                auditHistoryCompareExport(
                    compare.windowDays(),
                    compare.modeFilter(),
                    compare.actorFilter(),
                    compare.eventTypeFilter(),
                    compare.currentTotal(),
                    compare.previousTotal(),
                    compare.delta()
                );
                yield new RecoveryHistoryExportPayload(
                    buildHistoryExportAsyncFilename("ops_recovery_history_compare"),
                    csv.getBytes(StandardCharsets.UTF_8)
                );
            }
            case HISTORY_COMPARE_BREAKDOWN -> {
                RecoveryHistorySummaryCompareBreakdownResponseDto compareBreakdown = queryHistorySummaryCompareBreakdown(
                    request.days(),
                    request.mode(),
                    request.actor(),
                    request.eventType(),
                    request.compareBreakdownLimit(),
                    request.compareBreakdownSort()
                );
                String csv = buildHistoryCompareBreakdownCsv(compareBreakdown);
                auditHistoryCompareBreakdownExport(
                    compareBreakdown.windowDays(),
                    compareBreakdown.modeFilter(),
                    compareBreakdown.actorFilter(),
                    compareBreakdown.eventTypeFilter(),
                    compareBreakdown.items().size()
                );
                yield new RecoveryHistoryExportPayload(
                    buildHistoryExportAsyncFilename("ops_recovery_history_compare_breakdown"),
                    csv.getBytes(StandardCharsets.UTF_8)
                );
            }
            case HISTORY_COMPARE_ACTORS -> {
                RecoveryHistorySummaryCompareActorsResponseDto compareActors = queryHistorySummaryCompareActors(
                    request.days(),
                    request.mode(),
                    request.actor(),
                    request.eventType(),
                    request.compareActorLimit(),
                    request.compareActorSort()
                );
                String csv = buildHistoryCompareActorsCsv(compareActors);
                auditHistoryCompareActorsExport(
                    compareActors.windowDays(),
                    compareActors.modeFilter(),
                    compareActors.actorFilter(),
                    compareActors.eventTypeFilter(),
                    compareActors.sortBy(),
                    compareActors.requestedLimit(),
                    compareActors.items().size()
                );
                yield new RecoveryHistoryExportPayload(
                    buildHistoryExportAsyncFilename("ops_recovery_history_compare_actors"),
                    csv.getBytes(StandardCharsets.UTF_8)
                );
            }
        };
    }

    private static String buildHistoryExportAsyncFilename(String prefix) {
        return prefix + "_" + Instant.now().toEpochMilli() + ".csv";
    }

    private void trimHistoryExportAsyncTasksLocked() {
        if (historyExportAsyncTasks.size() <= MAX_HISTORY_EXPORT_ASYNC_TASKS) {
            return;
        }
        Iterator<String> iterator = historyExportAsyncTaskOrder.iterator();
        while (historyExportAsyncTasks.size() > MAX_HISTORY_EXPORT_ASYNC_TASKS && iterator.hasNext()) {
            String candidateTaskId = iterator.next();
            RecoveryHistoryExportAsyncTask candidate = historyExportAsyncTasks.get(candidateTaskId);
            if (candidate == null) {
                iterator.remove();
                continue;
            }
            if (!candidate.isTerminal()) {
                continue;
            }
            if (historyExportAsyncTasks.remove(candidateTaskId, candidate)) {
                historyExportAsyncTaskSnapshots.remove(candidateTaskId);
                iterator.remove();
            }
        }
        Iterator<String> fallbackIterator = historyExportAsyncTaskOrder.iterator();
        while (historyExportAsyncTasks.size() > MAX_HISTORY_EXPORT_ASYNC_TASKS && fallbackIterator.hasNext()) {
            String candidateTaskId = fallbackIterator.next();
            if (historyExportAsyncTasks.remove(candidateTaskId) != null) {
                historyExportAsyncTaskSnapshots.remove(candidateTaskId);
                fallbackIterator.remove();
            }
        }
    }

    private RecoveryHistoryExportAsyncListPage listHistoryExportAsyncTasksInternal(
        int skipCount,
        int maxItems,
        HistoryExportAsyncType exportTypeFilter,
        RecoveryHistoryExportAsyncStatus statusFilter
    ) {
        List<RecoveryHistoryExportAsyncStatusResponseDto> items = new ArrayList<>();
        int matchedCount = 0;
        synchronized (historyExportAsyncTaskLock) {
            refreshHistoryExportAsyncTasksLifecycleLocked();
            Iterator<String> iterator = historyExportAsyncTaskOrder.descendingIterator();
            while (iterator.hasNext()) {
                String taskId = iterator.next();
                RecoveryHistoryExportAsyncTask task = historyExportAsyncTasks.get(taskId);
                if (task == null) {
                    continue;
                }
                if (exportTypeFilter != null && task.exportType() != exportTypeFilter) {
                    continue;
                }
                if (statusFilter != null && task.status() != statusFilter) {
                    continue;
                }
                if (matchedCount >= skipCount && items.size() < maxItems) {
                    items.add(toHistoryExportAsyncStatusResponse(task));
                }
                matchedCount += 1;
            }
        }
        return new RecoveryHistoryExportAsyncListPage(matchedCount, items);
    }

    private List<RecoveryHistoryExportAsyncTask> listHistoryExportAsyncRetryTerminalCandidates(
        int limit,
        HistoryExportAsyncType exportTypeFilter,
        RecoveryHistoryExportAsyncStatus statusFilter
    ) {
        List<RecoveryHistoryExportAsyncTask> candidates = new ArrayList<>();
        synchronized (historyExportAsyncTaskLock) {
            refreshHistoryExportAsyncTasksLifecycleLocked();
            Iterator<String> iterator = historyExportAsyncTaskOrder.descendingIterator();
            while (iterator.hasNext() && candidates.size() < limit) {
                String taskId = iterator.next();
                RecoveryHistoryExportAsyncTask task = historyExportAsyncTasks.get(taskId);
                if (task == null || !task.isTerminal()) {
                    continue;
                }
                if (exportTypeFilter != null && task.exportType() != exportTypeFilter) {
                    continue;
                }
                if (statusFilter != null && task.status() != statusFilter) {
                    continue;
                }
                candidates.add(task);
            }
        }
        return candidates;
    }

    private RecoveryHistoryExportAsyncRetryTerminalDryRunComputation computeHistoryExportAsyncRetryTerminalDryRun(
        HistoryExportAsyncType exportTypeFilter,
        RecoveryHistoryExportAsyncStatus statusFilter,
        int limit
    ) {
        List<RecoveryHistoryExportAsyncTask> candidates = listHistoryExportAsyncRetryTerminalCandidates(
            limit,
            exportTypeFilter,
            statusFilter
        );
        int requested = candidates.size();
        int retryable = 0;
        int skipped = 0;
        List<RecoveryHistoryExportAsyncRetryTerminalDryRunItemDto> results = new ArrayList<>();
        Map<String, Long> reasonCounter = new HashMap<>();

        for (RecoveryHistoryExportAsyncTask candidate : candidates) {
            if (candidate == null) {
                skipped += 1;
                String reasonCode = "SOURCE_TASK_MISSING";
                String outcome = "SKIPPED";
                reasonCounter.merge(reasonCode + "|" + outcome, 1L, Long::sum);
                results.add(new RecoveryHistoryExportAsyncRetryTerminalDryRunItemDto(
                    null,
                    "UNKNOWN",
                    "UNKNOWN",
                    outcome,
                    reasonCode,
                    "Source task not found"
                ));
                continue;
            }

            synchronized (historyExportAsyncTaskLock) {
                refreshHistoryExportAsyncTasksLifecycleLocked();
                RecoveryHistoryExportAsyncTask sourceTask = historyExportAsyncTasks.get(candidate.taskId());
                if (sourceTask == null) {
                    skipped += 1;
                    String reasonCode = "SOURCE_TASK_MISSING";
                    String outcome = "SKIPPED";
                    reasonCounter.merge(reasonCode + "|" + outcome, 1L, Long::sum);
                    results.add(new RecoveryHistoryExportAsyncRetryTerminalDryRunItemDto(
                        candidate.taskId(),
                        candidate.exportType() != null ? candidate.exportType().name() : "UNKNOWN",
                        "NOT_FOUND",
                        outcome,
                        reasonCode,
                        "Source task not found"
                    ));
                    continue;
                }
                if (!sourceTask.isTerminal()) {
                    skipped += 1;
                    String reasonCode = "SOURCE_TASK_NOT_TERMINAL";
                    String outcome = "SKIPPED";
                    reasonCounter.merge(reasonCode + "|" + outcome, 1L, Long::sum);
                    results.add(new RecoveryHistoryExportAsyncRetryTerminalDryRunItemDto(
                        sourceTask.taskId(),
                        sourceTask.exportType() != null ? sourceTask.exportType().name() : "UNKNOWN",
                        sourceTask.status() != null ? sourceTask.status().name() : "UNKNOWN",
                        outcome,
                        reasonCode,
                        "Source task is not terminal"
                    ));
                    continue;
                }

                RecoveryHistoryExportAsyncSnapshot sourceSnapshot = historyExportAsyncTaskSnapshots.get(sourceTask.taskId());
                RecoveryHistoryExportAsyncSnapshot retrySnapshot = sourceSnapshot != null
                    ? sourceSnapshot
                    : copyHistoryExportAsyncRequest(null);
                RecoveryHistoryExportAsyncTask activeTask =
                    findActiveHistoryExportAsyncTaskBySnapshotLocked(retrySnapshot);

                retryable += 1;
                String reasonCode = activeTask != null
                    ? "ACTIVE_TASK_WILL_BE_REUSED"
                    : "TERMINAL_TASK_RETRYABLE";
                String message = activeTask != null
                    ? "Retry would reuse active async export task with same request snapshot"
                    : "Terminal async export task can be retried";
                String outcome = "RETRYABLE";
                reasonCounter.merge(reasonCode + "|" + outcome, 1L, Long::sum);
                results.add(new RecoveryHistoryExportAsyncRetryTerminalDryRunItemDto(
                    sourceTask.taskId(),
                    sourceTask.exportType() != null ? sourceTask.exportType().name() : "UNKNOWN",
                    sourceTask.status() != null ? sourceTask.status().name() : "UNKNOWN",
                    outcome,
                    reasonCode,
                    message
                ));
            }
        }

        List<RecoveryHistoryExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown = reasonCounter.entrySet()
            .stream()
            .map((entry) -> {
                String[] parts = entry.getKey().split("\\|", 2);
                String reasonCode = parts.length > 0 ? parts[0] : "UNKNOWN";
                String outcome = parts.length > 1 ? parts[1] : "UNKNOWN";
                return new RecoveryHistoryExportAsyncRetryTerminalDryRunReasonCountDto(
                    reasonCode,
                    outcome,
                    entry.getValue() != null ? entry.getValue() : 0L
                );
            })
            .sorted(Comparator.comparingLong(RecoveryHistoryExportAsyncRetryTerminalDryRunReasonCountDto::count)
                .reversed()
                .thenComparing(RecoveryHistoryExportAsyncRetryTerminalDryRunReasonCountDto::reasonCode))
            .toList();

        String normalizedExportTypeFilter = exportTypeFilter != null ? exportTypeFilter.name() : "ALL";
        String normalizedStatusFilter = statusFilter != null
            ? statusFilter.name()
            : "FAILED|CANCELLED|COMPLETED|TIMED_OUT|EXPIRED";
        String message = requested == 0
            ? "No terminal async ops recovery export tasks matched dry-run filters"
            : String.format(
                Locale.ROOT,
                "Dry-run identified %d/%d retryable terminal async ops recovery export tasks (skipped=%d)",
                retryable,
                requested,
                skipped
            );
        return new RecoveryHistoryExportAsyncRetryTerminalDryRunComputation(
            requested,
            retryable,
            skipped,
            limit,
            normalizedExportTypeFilter,
            normalizedStatusFilter,
            message,
            results,
            reasonBreakdown
        );
    }

    private String buildHistoryExportAsyncRetryTerminalDryRunCsv(
        RecoveryHistoryExportAsyncRetryTerminalDryRunComputation dryRun
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("exportTypeFilter,statusFilter,limit,requested,retryable,skipped,sourceTaskId,exportType,sourceStatus,outcome,reasonCode,message\n");
        List<RecoveryHistoryExportAsyncRetryTerminalDryRunItemDto> results = dryRun != null
            ? dryRun.results()
            : List.of();
        if (results.isEmpty()) {
            sb.append(csv(dryRun != null ? dryRun.exportTypeFilter() : "ALL"))
                .append(',')
                .append(csv(dryRun != null ? dryRun.statusFilter() : "FAILED|CANCELLED|COMPLETED|TIMED_OUT|EXPIRED"))
                .append(',')
                .append(dryRun != null ? dryRun.limit() : 0)
                .append(',')
                .append(dryRun != null ? dryRun.requested() : 0)
                .append(',')
                .append(dryRun != null ? dryRun.retryable() : 0)
                .append(',')
                .append(dryRun != null ? dryRun.skipped() : 0)
                .append(",,,,,,")
                .append('\n');
        } else {
            for (RecoveryHistoryExportAsyncRetryTerminalDryRunItemDto item : results) {
                sb.append(csv(dryRun.exportTypeFilter()))
                    .append(',')
                    .append(csv(dryRun.statusFilter()))
                    .append(',')
                    .append(dryRun.limit())
                    .append(',')
                    .append(dryRun.requested())
                    .append(',')
                    .append(dryRun.retryable())
                    .append(',')
                    .append(dryRun.skipped())
                    .append(',')
                    .append(csv(item.sourceTaskId()))
                    .append(',')
                    .append(csv(item.exportType()))
                    .append(',')
                    .append(csv(item.sourceStatus()))
                    .append(',')
                    .append(csv(item.outcome()))
                    .append(',')
                    .append(csv(item.reasonCode()))
                    .append(',')
                    .append(csv(item.message()))
                    .append('\n');
            }
        }

        sb.append('\n');
        sb.append("reasonCode,outcome,count\n");
        List<RecoveryHistoryExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown = dryRun != null
            ? dryRun.reasonBreakdown()
            : List.of();
        if (reasonBreakdown.isEmpty()) {
            sb.append("NONE,UNKNOWN,0\n");
            return sb.toString();
        }
        for (RecoveryHistoryExportAsyncRetryTerminalDryRunReasonCountDto item : reasonBreakdown) {
            sb.append(csv(item.reasonCode()))
                .append(',')
                .append(csv(item.outcome()))
                .append(',')
                .append(item.count())
                .append('\n');
        }
        return sb.toString();
    }

    private String buildRecoveryDryRunCsv(RecoveryDryRunResponseDto dryRun) {
        StringBuilder sb = new StringBuilder();
        sb.append("domain,mode,windowDays,maxDocuments,totalCandidates,scanned,matched,truncated,estimatedQueued,estimatedSkipped,estimatedFailed,documentId,name,path,mimeType,previewStatus,failureCategory,previewFailureReason,previewFailureCategory,previewLastUpdated,predictedState,predictedOutcome,predictedReason\n");
        List<RecoveryDryRunItemDto> samples = dryRun != null ? dryRun.samples() : List.of();
        if (samples.isEmpty()) {
            sb.append(csv(dryRun != null ? dryRun.domain() : "PREVIEW"))
                .append(',')
                .append(csv(dryRun != null ? dryRun.mode() : "QUEUE_BY_WINDOW"))
                .append(',')
                .append(dryRun != null ? dryRun.windowDays() : 0)
                .append(',')
                .append(dryRun != null ? dryRun.maxDocuments() : 0)
                .append(',')
                .append(dryRun != null ? dryRun.totalCandidates() : 0)
                .append(',')
                .append(dryRun != null ? dryRun.scanned() : 0)
                .append(',')
                .append(dryRun != null ? dryRun.matched() : 0)
                .append(',')
                .append(dryRun != null && dryRun.truncated())
                .append(',')
                .append(dryRun != null ? dryRun.estimatedQueued() : 0)
                .append(',')
                .append(dryRun != null ? dryRun.estimatedSkipped() : 0)
                .append(',')
                .append(dryRun != null ? dryRun.estimatedFailed() : 0)
                .append(",,,,,,,,,,,")
                .append('\n');
            return sb.toString();
        }
        for (RecoveryDryRunItemDto item : samples) {
            sb.append(csv(dryRun.domain()))
                .append(',')
                .append(csv(dryRun.mode()))
                .append(',')
                .append(dryRun.windowDays())
                .append(',')
                .append(dryRun.maxDocuments())
                .append(',')
                .append(dryRun.totalCandidates())
                .append(',')
                .append(dryRun.scanned())
                .append(',')
                .append(dryRun.matched())
                .append(',')
                .append(dryRun.truncated())
                .append(',')
                .append(dryRun.estimatedQueued())
                .append(',')
                .append(dryRun.estimatedSkipped())
                .append(',')
                .append(dryRun.estimatedFailed())
                .append(',')
                .append(csv(item.documentId()))
                .append(',')
                .append(csv(item.name()))
                .append(',')
                .append(csv(item.path()))
                .append(',')
                .append(csv(item.mimeType()))
                .append(',')
                .append(csv(item.previewStatus()))
                .append(',')
                .append(csv(item.failureCategory()))
                .append(',')
                .append(csv(item.previewFailureReason()))
                .append(',')
                .append(csv(item.previewFailureCategory()))
                .append(',')
                .append(csv(item.previewLastUpdated()))
                .append(',')
                .append(csv(item.predictedState()))
                .append(',')
                .append(csv(item.predictedOutcome()))
                .append(',')
                .append(csv(item.predictedReason()))
                .append('\n');
        }
        return sb.toString();
    }

    private List<String> normalizeHistoryExportAsyncRetrySourceTaskIds(List<String> sourceTaskIds) {
        LinkedHashSet<String> deduplicated = new LinkedHashSet<>();
        if (sourceTaskIds == null) {
            return List.of();
        }
        for (String sourceTaskId : sourceTaskIds) {
            if (sourceTaskId == null) {
                continue;
            }
            String normalized = sourceTaskId.trim();
            if (normalized.isEmpty()) {
                continue;
            }
            deduplicated.add(normalized);
            if (deduplicated.size() >= MAX_HISTORY_EXPORT_ASYNC_RETRY_SELECTED_IDS) {
                break;
            }
        }
        return new ArrayList<>(deduplicated);
    }

    private int cleanupHistoryExportAsyncTasksInternal(
        HistoryExportAsyncType exportTypeFilter,
        RecoveryHistoryExportAsyncStatus statusFilter
    ) {
        int deletedCount = 0;
        synchronized (historyExportAsyncTaskLock) {
            refreshHistoryExportAsyncTasksLifecycleLocked();
            Iterator<String> iterator = historyExportAsyncTaskOrder.iterator();
            while (iterator.hasNext()) {
                String taskId = iterator.next();
                RecoveryHistoryExportAsyncTask task = historyExportAsyncTasks.get(taskId);
                if (task == null) {
                    iterator.remove();
                    continue;
                }
                if (exportTypeFilter != null && task.exportType() != exportTypeFilter) {
                    continue;
                }
                boolean deletable = statusFilter != null
                    ? task.status() == statusFilter
                    : task.isTerminal();
                if (!deletable) {
                    continue;
                }
                if (historyExportAsyncTasks.remove(taskId, task)) {
                    historyExportAsyncTaskSnapshots.remove(taskId);
                    iterator.remove();
                    deletedCount += 1;
                }
            }
        }
        return deletedCount;
    }

    private int cancelActiveHistoryExportAsyncTasksInternal(
        HistoryExportAsyncType exportTypeFilter,
        RecoveryHistoryExportAsyncStatus statusFilter
    ) {
        int cancelledCount = 0;
        synchronized (historyExportAsyncTaskLock) {
            refreshHistoryExportAsyncTasksLifecycleLocked();
            Iterator<String> iterator = historyExportAsyncTaskOrder.iterator();
            String actor = resolveAuditUsername();
            while (iterator.hasNext()) {
                String taskId = iterator.next();
                RecoveryHistoryExportAsyncTask task = historyExportAsyncTasks.get(taskId);
                if (task == null) {
                    iterator.remove();
                    continue;
                }
                if (exportTypeFilter != null && task.exportType() != exportTypeFilter) {
                    continue;
                }
                boolean cancellable = statusFilter != null
                    ? task.status() == statusFilter
                    : isHistoryExportAsyncActiveStatus(task.status());
                if (!cancellable) {
                    continue;
                }
                boolean[] cancelled = {false};
                historyExportAsyncTasks.computeIfPresent(taskId, (key, current) -> {
                    if (exportTypeFilter != null && current.exportType() != exportTypeFilter) {
                        return current;
                    }
                    boolean shouldCancel = statusFilter != null
                        ? current.status() == statusFilter
                        : isHistoryExportAsyncActiveStatus(current.status());
                    if (!shouldCancel) {
                        return current;
                    }
                    cancelled[0] = true;
                    return current.cancel("Cancelled by cancel-active", actor);
                });
                if (cancelled[0]) {
                    cancelledCount += 1;
                }
            }
        }
        return cancelledCount;
    }

    private int countActiveHistoryExportAsyncTasks(
        HistoryExportAsyncType exportTypeFilter,
        RecoveryHistoryExportAsyncStatus statusFilter
    ) {
        int activeCount = 0;
        synchronized (historyExportAsyncTaskLock) {
            refreshHistoryExportAsyncTasksLifecycleLocked();
            for (RecoveryHistoryExportAsyncTask task : historyExportAsyncTasks.values()) {
                if (task == null) {
                    continue;
                }
                if (exportTypeFilter != null && task.exportType() != exportTypeFilter) {
                    continue;
                }
                boolean active = statusFilter != null
                    ? task.status() == statusFilter
                    : isHistoryExportAsyncActiveStatus(task.status());
                if (active) {
                    activeCount += 1;
                }
            }
        }
        return activeCount;
    }

    private RecoveryHistoryExportAsyncStatusResponseDto toHistoryExportAsyncStatusResponse(
        RecoveryHistoryExportAsyncTask task
    ) {
        RecoveryHistoryExportAsyncRequestSnapshotDto request = toHistoryExportAsyncRequestSnapshotDto(
            historyExportAsyncTaskSnapshots.get(task.taskId())
        );
        return new RecoveryHistoryExportAsyncStatusResponseDto(
            task.taskId(),
            task.exportType().name(),
            task.status().name(),
            task.error(),
            request,
            task.createdAt(),
            task.startedAt(),
            task.updatedAt(),
            task.timeoutAt(),
            task.expiresAt(),
            task.finishedAt(),
            task.status() == RecoveryHistoryExportAsyncStatus.COMPLETED ? task.filename() : null,
            task.createdBy(),
            task.updatedBy()
        );
    }

    private RecoveryHistoryExportAsyncRequestSnapshotDto toHistoryExportAsyncRequestSnapshotDto(
        RecoveryHistoryExportAsyncSnapshot snapshot
    ) {
        if (snapshot == null) {
            return null;
        }
        return new RecoveryHistoryExportAsyncRequestSnapshotDto(
            snapshot.exportType() != null ? snapshot.exportType().name() : null,
            snapshot.limit(),
            snapshot.days(),
            snapshot.mode(),
            snapshot.actor(),
            snapshot.eventType(),
            snapshot.compareBreakdownLimit(),
            snapshot.compareBreakdownSort(),
            snapshot.compareActorLimit(),
            snapshot.compareActorSort()
        );
    }

    private void refreshHistoryExportAsyncTasksLifecycleLocked() {
        Instant now = Instant.now();
        List<String> taskIds = new ArrayList<>(historyExportAsyncTaskOrder);
        for (String taskId : taskIds) {
            historyExportAsyncTasks.computeIfPresent(taskId, (key, task) ->
                applyHistoryExportAsyncTaskLifecycle(task, now)
            );
        }
        historyExportAsyncTaskOrder.removeIf(taskId -> !historyExportAsyncTasks.containsKey(taskId));
    }

    private RecoveryHistoryExportAsyncTask applyHistoryExportAsyncTaskLifecycle(
        RecoveryHistoryExportAsyncTask task,
        Instant now
    ) {
        if (task == null) {
            return null;
        }
        RecoveryHistoryExportAsyncTask current = task;
        if (isHistoryExportAsyncActiveStatus(current.status())
            && current.timeoutAt() != null
            && now != null
            && now.isAfter(current.timeoutAt())) {
            current = current.timeout(now, "system");
        }
        if (current.status() != RecoveryHistoryExportAsyncStatus.EXPIRED
            && current.expiresAt() != null
            && now != null
            && now.isAfter(current.expiresAt())
            && current.isTerminal()) {
            current = current.expire(now, "system");
        }
        return current;
    }

    private static String resolveErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }

    private static String resolveAuditUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "system";
        }
        return authentication.getName();
    }

    private static String resolveTaskActor(String actor) {
        if (actor == null || actor.isBlank()) {
            return "system";
        }
        return actor.trim();
    }

    private static Instant resolveAsyncTaskTimeoutAt(Instant startedOrUpdatedAt) {
        if (startedOrUpdatedAt == null) {
            return null;
        }
        return startedOrUpdatedAt.plusSeconds(ASYNC_TASK_ACTIVE_TIMEOUT_SECONDS);
    }

    private static Instant resolveAsyncTaskExpiresAt(Instant createdAt) {
        if (createdAt == null) {
            return null;
        }
        return createdAt.plusSeconds(ASYNC_TASK_RETENTION_SECONDS);
    }

    private void auditHistoryExport(
        int limit,
        int days,
        String mode,
        String actor,
        String eventType,
        int count
    ) {
        auditService.logEvent(
            "OPS_RECOVERY_HISTORY_EXPORT",
            null,
            "OPS_RECOVERY",
            "admin",
            String.format(
                Locale.ROOT,
                "limit=%d days=%d mode=%s actor=%s eventType=%s count=%d",
                limit,
                days,
                mode,
                actor,
                eventType,
                count
            )
        );
    }

    private void auditHistoryExportAsyncCleanup(
        String exportTypeFilter,
        String statusFilter,
        int deletedCount,
        int remainingCount
    ) {
        auditService.logEvent(
            "OPS_RECOVERY_HISTORY_EXPORT_ASYNC_CLEANUP",
            null,
            "OPS_RECOVERY",
            "admin",
            String.format(
                Locale.ROOT,
                "exportType=%s status=%s deleted=%d remaining=%d",
                exportTypeFilter,
                statusFilter,
                deletedCount,
                remainingCount
            )
        );
    }

    private void auditHistoryExportAsyncCancelActive(
        String exportTypeFilter,
        String statusFilter,
        int cancelledCount,
        int remainingActiveCount
    ) {
        auditService.logEvent(
            "OPS_RECOVERY_HISTORY_EXPORT_ASYNC_CANCEL_ACTIVE",
            null,
            "OPS_RECOVERY",
            "admin",
            String.format(
                Locale.ROOT,
                "exportType=%s status=%s cancelled=%d remainingActive=%d",
                exportTypeFilter,
                statusFilter,
                cancelledCount,
                remainingActiveCount
            )
        );
    }

    private void auditHistorySummaryExport(
        int days,
        String mode,
        String actor,
        String eventType,
        int count
    ) {
        auditService.logEvent(
            "OPS_RECOVERY_HISTORY_SUMMARY_EXPORT",
            null,
            "OPS_RECOVERY",
            "admin",
            String.format(
                Locale.ROOT,
                "days=%d mode=%s actor=%s eventType=%s count=%d",
                days,
                mode,
                actor,
                eventType,
                count
            )
        );
    }

    private void auditHistoryTrendExport(
        int days,
        String mode,
        String actor,
        String eventType,
        int count
    ) {
        auditService.logEvent(
            "OPS_RECOVERY_HISTORY_TREND_EXPORT",
            null,
            "OPS_RECOVERY",
            "admin",
            String.format(
                Locale.ROOT,
                "days=%d mode=%s actor=%s eventType=%s count=%d",
                days,
                mode,
                actor,
                eventType,
                count
            )
        );
    }

    private void auditHistoryCompareExport(
        int days,
        String mode,
        String actor,
        String eventType,
        long currentTotal,
        long previousTotal,
        long delta
    ) {
        auditService.logEvent(
            "OPS_RECOVERY_HISTORY_COMPARE_EXPORT",
            null,
            "OPS_RECOVERY",
            "admin",
            String.format(
                Locale.ROOT,
                "days=%d mode=%s actor=%s eventType=%s current=%d previous=%d delta=%d",
                days,
                mode,
                actor,
                eventType,
                currentTotal,
                previousTotal,
                delta
            )
        );
    }

    private void auditHistoryCompareBreakdownExport(
        int days,
        String mode,
        String actor,
        String eventType,
        int count
    ) {
        auditService.logEvent(
            "OPS_RECOVERY_HISTORY_COMPARE_BREAKDOWN_EXPORT",
            null,
            "OPS_RECOVERY",
            "admin",
            String.format(
                Locale.ROOT,
                "days=%d mode=%s actor=%s eventType=%s count=%d",
                days,
                mode,
                actor,
                eventType,
                count
            )
        );
    }

    private void auditHistoryCompareActorsExport(
        int days,
        String mode,
        String actor,
        String eventType,
        String sort,
        int limit,
        int count
    ) {
        auditService.logEvent(
            "OPS_RECOVERY_HISTORY_ACTOR_COMPARE_EXPORT",
            null,
            "OPS_RECOVERY",
            "admin",
            String.format(
                Locale.ROOT,
                "days=%d mode=%s actor=%s eventType=%s sort=%s limit=%d count=%d",
                days,
                mode,
                actor,
                eventType,
                sort,
                limit,
                count
            )
        );
    }

    private String buildHistoryCsv(List<AuditLog> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,nodeId,nodeName,eventTime,eventType,mode,actor,previewStatus,previewFailureReason,previewFailureCategory,previewLastUpdated,details\n");
        for (AuditLog entry : entries) {
            RecoveryHistoryItemDto item = toRecoveryHistoryItem(entry);
            sb.append(csv(item.id()))
                .append(',')
                .append(csv(item.nodeId()))
                .append(',')
                .append(csv(item.nodeName()))
                .append(',')
                .append(csv(item.eventTime()))
                .append(',')
                .append(csv(item.eventType()))
                .append(',')
                .append(csv(item.mode()))
                .append(',')
                .append(csv(item.actor()))
                .append(',')
                .append(csv(item.previewStatus()))
                .append(',')
                .append(csv(item.previewFailureReason()))
                .append(',')
                .append(csv(item.previewFailureCategory()))
                .append(',')
                .append(csv(item.previewLastUpdated()))
                .append(',')
                .append(csv(item.details()))
                .append('\n');
        }
        return sb.toString();
    }

    private String buildHistoryCompareBreakdownCsv(RecoveryHistorySummaryCompareBreakdownResponseDto compareBreakdown) {
        StringBuilder sb = new StringBuilder();
        sb.append("domain,windowDays,previousWindowDays,modeFilter,actorFilter,eventTypeFilter,compareAvailable,truncated,sortBy,requestedLimit,totalItems,limited,eventType,mode,currentCount,previousCount,delta,deltaPercent\n");
        for (RecoveryHistorySummaryCompareBreakdownItemDto item : compareBreakdown.items()) {
            sb.append(csv(compareBreakdown.domain()))
                .append(',')
                .append(csv(compareBreakdown.windowDays()))
                .append(',')
                .append(csv(compareBreakdown.previousWindowDays()))
                .append(',')
                .append(csv(compareBreakdown.modeFilter()))
                .append(',')
                .append(csv(compareBreakdown.actorFilter()))
                .append(',')
                .append(csv(compareBreakdown.eventTypeFilter()))
                .append(',')
                .append(csv(compareBreakdown.compareAvailable()))
                .append(',')
                .append(csv(compareBreakdown.truncated()))
                .append(',')
                .append(csv(compareBreakdown.sortBy()))
                .append(',')
                .append(csv(compareBreakdown.requestedLimit()))
                .append(',')
                .append(csv(compareBreakdown.totalItems()))
                .append(',')
                .append(csv(compareBreakdown.limited()))
                .append(',')
                .append(csv(item.eventType()))
                .append(',')
                .append(csv(item.mode()))
                .append(',')
                .append(csv(item.currentCount()))
                .append(',')
                .append(csv(item.previousCount()))
                .append(',')
                .append(csv(item.delta()))
                .append(',')
                .append(csv(item.deltaPercent()))
                .append('\n');
        }
        if (compareBreakdown.items().isEmpty()) {
            sb.append(csv(compareBreakdown.domain()))
                .append(',')
                .append(csv(compareBreakdown.windowDays()))
                .append(',')
                .append(csv(compareBreakdown.previousWindowDays()))
                .append(',')
                .append(csv(compareBreakdown.modeFilter()))
                .append(',')
                .append(csv(compareBreakdown.actorFilter()))
                .append(',')
                .append(csv(compareBreakdown.eventTypeFilter()))
                .append(',')
                .append(csv(compareBreakdown.compareAvailable()))
                .append(',')
                .append(csv(compareBreakdown.truncated()))
                .append(',')
                .append(csv(compareBreakdown.sortBy()))
                .append(',')
                .append(csv(compareBreakdown.requestedLimit()))
                .append(',')
                .append(csv(compareBreakdown.totalItems()))
                .append(',')
                .append(csv(compareBreakdown.limited()))
                .append(",,,,,,\n");
        }
        return sb.toString();
    }

    private String buildHistoryCompareActorsCsv(RecoveryHistorySummaryCompareActorsResponseDto compareActors) {
        StringBuilder sb = new StringBuilder();
        sb.append("domain,windowDays,previousWindowDays,modeFilter,actorFilter,eventTypeFilter,compareAvailable,truncated,sortBy,requestedLimit,totalItems,limited,actor,currentCount,previousCount,delta,deltaPercent\n");
        for (RecoveryHistorySummaryCompareActorItemDto item : compareActors.items()) {
            sb.append(csv(compareActors.domain()))
                .append(',')
                .append(csv(compareActors.windowDays()))
                .append(',')
                .append(csv(compareActors.previousWindowDays()))
                .append(',')
                .append(csv(compareActors.modeFilter()))
                .append(',')
                .append(csv(compareActors.actorFilter()))
                .append(',')
                .append(csv(compareActors.eventTypeFilter()))
                .append(',')
                .append(csv(compareActors.compareAvailable()))
                .append(',')
                .append(csv(compareActors.truncated()))
                .append(',')
                .append(csv(compareActors.sortBy()))
                .append(',')
                .append(csv(compareActors.requestedLimit()))
                .append(',')
                .append(csv(compareActors.totalItems()))
                .append(',')
                .append(csv(compareActors.limited()))
                .append(',')
                .append(csv(item.actor()))
                .append(',')
                .append(csv(item.currentCount()))
                .append(',')
                .append(csv(item.previousCount()))
                .append(',')
                .append(csv(item.delta()))
                .append(',')
                .append(csv(item.deltaPercent()))
                .append('\n');
        }
        if (compareActors.items().isEmpty()) {
            sb.append(csv(compareActors.domain()))
                .append(',')
                .append(csv(compareActors.windowDays()))
                .append(',')
                .append(csv(compareActors.previousWindowDays()))
                .append(',')
                .append(csv(compareActors.modeFilter()))
                .append(',')
                .append(csv(compareActors.actorFilter()))
                .append(',')
                .append(csv(compareActors.eventTypeFilter()))
                .append(',')
                .append(csv(compareActors.compareAvailable()))
                .append(',')
                .append(csv(compareActors.truncated()))
                .append(',')
                .append(csv(compareActors.sortBy()))
                .append(',')
                .append(csv(compareActors.requestedLimit()))
                .append(',')
                .append(csv(compareActors.totalItems()))
                .append(',')
                .append(csv(compareActors.limited()))
                .append(",,,,,\n");
        }
        return sb.toString();
    }

    private String buildHistoryCompareCsv(RecoveryHistorySummaryCompareResponseDto compare) {
        StringBuilder sb = new StringBuilder();
        sb.append("domain,windowDays,previousWindowDays,modeFilter,actorFilter,eventTypeFilter,currentTotal,previousTotal,delta,deltaPercent,compareAvailable,truncated\n");
        sb.append(csv(compare.domain()))
            .append(',')
            .append(csv(compare.windowDays()))
            .append(',')
            .append(csv(compare.previousWindowDays()))
            .append(',')
            .append(csv(compare.modeFilter()))
            .append(',')
            .append(csv(compare.actorFilter()))
            .append(',')
            .append(csv(compare.eventTypeFilter()))
            .append(',')
            .append(csv(compare.currentTotal()))
            .append(',')
            .append(csv(compare.previousTotal()))
            .append(',')
            .append(csv(compare.delta()))
            .append(',')
            .append(csv(compare.deltaPercent()))
            .append(',')
            .append(csv(compare.compareAvailable()))
            .append(',')
            .append(csv(compare.truncated()))
            .append('\n');
        return sb.toString();
    }

    private String buildHistoryTrendCsv(List<RecoveryHistoryTrendItemDto> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("day,count\n");
        for (RecoveryHistoryTrendItemDto item : items) {
            sb.append(csv(item.day()))
                .append(',')
                .append(csv(item.count()))
                .append('\n');
        }
        return sb.toString();
    }

    private String buildHistorySummaryCsv(
        List<RecoveryHistorySummaryItemDto> items,
        List<RecoveryHistoryActorSummaryItemDto> actorItems
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("section,key,mode,count\n");
        for (RecoveryHistorySummaryItemDto item : items) {
            sb.append(csv("EVENT_TYPE"))
                .append(',')
                .append(csv(item.eventType()))
                .append(',')
                .append(csv(item.mode()))
                .append(',')
                .append(csv(item.count()))
                .append('\n');
        }
        for (RecoveryHistoryActorSummaryItemDto item : actorItems) {
            sb.append(csv("ACTOR"))
                .append(',')
                .append(csv(item.actor()))
                .append(',')
                .append(csv(""))
                .append(',')
                .append(csv(item.count()))
                .append('\n');
        }
        return sb.toString();
    }

    private static String csv(Object value) {
        if (value == null) {
            return "";
        }
        String raw = String.valueOf(value).replace("\"", "\"\"");
        return "\"" + raw + "\"";
    }

    private static String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return "PREVIEW";
        }
        String normalized = domain.trim().toUpperCase(Locale.ROOT);
        if (!"PREVIEW".equals(normalized)) {
            throw new IllegalArgumentException("Unsupported recovery domain: " + normalized);
        }
        return normalized;
    }

    private static String normalizeHistoryMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return null;
        }
        String normalized = mode.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED_HISTORY_MODES.contains(normalized) ? normalized : null;
    }

    private static String normalizeActor(String actor) {
        if (actor == null) {
            return null;
        }
        String normalized = actor.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeHistoryEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return null;
        }
        String normalized = eventType.trim().toUpperCase(Locale.ROOT);
        if (!normalized.startsWith(OPS_RECOVERY_EVENT_PREFIX)) {
            return null;
        }
        return normalized;
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "UNSPECIFIED";
        }
        return reason.replaceAll("\\s+", " ").trim();
    }

    private static UUID extractDocumentIdFromEntryKey(String entryKey) {
        if (entryKey == null || entryKey.isBlank()) {
            return null;
        }
        String[] parts = entryKey.trim().split("\\|", 2);
        if (parts.length == 0 || parts[0].isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(parts[0].trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String extractRenditionKeyFromEntryKey(String entryKey) {
        if (entryKey == null || entryKey.isBlank()) {
            return PreviewDeadLetterRegistry.defaultRenditionKey();
        }
        String[] parts = entryKey.trim().split("\\|", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            return PreviewDeadLetterRegistry.defaultRenditionKey();
        }
        return parts[1].trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeOptionalReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return normalizeReason(reason);
    }

    private static String normalizedUpperOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static int normalizeWindowDays(int rawDays) {
        if (rawDays == 0) {
            return 0;
        }
        if (rawDays < 0) {
            return DEFAULT_WINDOW_DAYS;
        }
        return clamp(rawDays, 1, MAX_WINDOW_DAYS);
    }

    private static LocalDateTime resolveUpdatedSince(int days) {
        if (days <= 0) {
            return null;
        }
        return LocalDateTime.now().minusDays(days);
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private static List<PreviewStatus> failureStatuses() {
        return List.of(PreviewStatus.FAILED, PreviewStatus.UNSUPPORTED);
    }

    private static String normalizeMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return "";
        }
        return mimeType.split(";")[0].trim().toLowerCase(Locale.ROOT);
    }

    private FailureCategory resolveFailureCategory(Document document) {
        return resolveEffectivePreviewSummary(document).failureCategory();
    }

    private EffectivePreviewSummary resolveEffectivePreviewSummary(Document document) {
        return resolveEffectivePreviewSummary(document, null);
    }

    private EffectivePreviewSummary resolvePreviewMutationSummary(
        Document document,
        PreviewQueueService.PreviewQueueStatus status
    ) {
        EffectivePreviewSummary snapshotSummary = resolveEffectivePreviewSummary(document);
        boolean hasExplicitPreviewStatus = status != null && status.previewStatus() != null;
        String previewStatus = hasExplicitPreviewStatus
            ? status.previewStatus().name()
            : snapshotSummary.previewStatus();
        String previewFailureReason = hasExplicitPreviewStatus
            ? firstNonBlank(status.previewFailureReason(), snapshotSummary.previewFailureReason())
            : snapshotSummary.previewFailureReason();
        String previewFailureCategory = hasExplicitPreviewStatus
            ? normalizedUpperOrDefault(
                firstNonBlank(status.previewFailureCategory(), snapshotSummary.previewFailureCategory()),
                "UNKNOWN"
            )
            : snapshotSummary.previewFailureCategory();
        LocalDateTime previewLastUpdated = status != null && status.previewLastUpdated() != null
            ? status.previewLastUpdated()
            : snapshotSummary.previewLastUpdated();
        FailureCategory failureCategory = hasExplicitPreviewStatus
            ? toFailureCategory(previewFailureCategory)
            : snapshotSummary.failureCategory();
        return new EffectivePreviewSummary(
            previewStatus,
            previewFailureReason,
            failureCategory,
            previewLastUpdated,
            previewFailureCategory
        );
    }

    private EffectivePreviewSummary resolveEffectivePreviewSummary(Document document, String fallbackPreviewStatus) {
        RenditionResourceService.EffectivePreviewSnapshot sharedSnapshot =
            renditionResourceService.resolveEffectivePreviewSnapshot(document, fallbackPreviewStatus, null, null, null);
        if (sharedSnapshot != null) {
            String normalizedCategory = normalizedUpperOrDefault(sharedSnapshot.previewFailureCategory(), "UNKNOWN");
            return new EffectivePreviewSummary(
                sharedSnapshot.previewStatus(),
                sharedSnapshot.previewFailureReason(),
                toFailureCategory(normalizedCategory),
                sharedSnapshot.previewLastUpdated(),
                normalizedCategory
            );
        }
        if (document == null) {
            return new EffectivePreviewSummary(
                fallbackPreviewStatus,
                null,
                FailureCategory.UNKNOWN,
                null,
                "UNKNOWN"
            );
        }
        RenditionResourceService.RenditionSummary renditionSummary = renditionResourceService.summarizeDocument(document);
        String previewStatus = renditionSummary != null && renditionSummary.document() && renditionSummary.previewStatus() != null
            ? renditionSummary.previewStatus()
            : document.getPreviewStatus() != null ? document.getPreviewStatus().name() : fallbackPreviewStatus;
        String previewFailureReason = renditionSummary != null && renditionSummary.document() && renditionSummary.previewFailureReason() != null
            ? renditionSummary.previewFailureReason()
            : document.getPreviewFailureReason();
        LocalDateTime previewLastUpdated = renditionSummary != null && renditionSummary.document() && renditionSummary.previewLastUpdated() != null
            ? renditionSummary.previewLastUpdated()
            : document.getPreviewLastUpdated();
        String classified = renditionSummary != null && renditionSummary.document() && renditionSummary.previewFailureCategory() != null
            ? renditionSummary.previewFailureCategory()
            : PreviewFailureClassifier.classify(
                Objects.requireNonNullElse(previewStatus, ""),
                document.getMimeType(),
                previewFailureReason
            );
        String normalized = normalizedUpperOrDefault(classified, "UNKNOWN");
        FailureCategory failureCategory = toFailureCategory(normalized);
        return new EffectivePreviewSummary(
            previewStatus,
            previewFailureReason,
            failureCategory,
            previewLastUpdated,
            normalized
        );
    }

    private String resolveEffectivePreviewStatus(Document document) {
        return resolveEffectivePreviewStatus(document, null);
    }

    private String resolveEffectivePreviewStatus(Document document, String fallbackPreviewStatus) {
        RenditionResourceService.EffectivePreviewSnapshot sharedSnapshot =
            renditionResourceService.resolveEffectivePreviewSnapshot(document, fallbackPreviewStatus, null, null, null);
        if (sharedSnapshot != null && sharedSnapshot.previewStatus() != null) {
            return sharedSnapshot.previewStatus();
        }
        if (document == null) {
            return fallbackPreviewStatus;
        }
        RenditionResourceService.RenditionSummary renditionSummary = renditionResourceService.summarizeDocument(document);
        if (renditionSummary != null && renditionSummary.document() && renditionSummary.previewStatus() != null) {
            return renditionSummary.previewStatus();
        }
        if (document.getPreviewStatus() != null) {
            return document.getPreviewStatus().name();
        }
        return fallbackPreviewStatus;
    }

    private String resolveEffectivePreviewFailureReason(Document document) {
        RenditionResourceService.EffectivePreviewSnapshot sharedSnapshot =
            renditionResourceService.resolveEffectivePreviewSnapshot(document, null, null, null, null);
        if (sharedSnapshot != null && sharedSnapshot.previewFailureReason() != null) {
            return sharedSnapshot.previewFailureReason();
        }
        if (document == null) {
            return null;
        }
        RenditionResourceService.RenditionSummary renditionSummary = renditionResourceService.summarizeDocument(document);
        if (renditionSummary != null && renditionSummary.document() && renditionSummary.previewFailureReason() != null) {
            return renditionSummary.previewFailureReason();
        }
        return document.getPreviewFailureReason();
    }

    private FailureCategory toFailureCategory(String normalizedCategory) {
        return switch (normalizedCategory) {
            case "TEMPORARY" -> FailureCategory.TEMPORARY;
            case "PERMANENT" -> FailureCategory.PERMANENT;
            case "UNSUPPORTED" -> FailureCategory.UNSUPPORTED;
            default -> FailureCategory.UNKNOWN;
        };
    }

    private static DryRunMode resolveMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return DryRunMode.QUEUE_BY_WINDOW;
        }
        String normalized = mode.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "QUEUE_BY_REASON" -> DryRunMode.QUEUE_BY_REASON;
            case "QUEUE_BY_WINDOW" -> DryRunMode.QUEUE_BY_WINDOW;
            case "CLEAR_BY_FILTER" -> DryRunMode.CLEAR_BY_FILTER;
            case "REPLAY_BY_FILTER" -> DryRunMode.REPLAY_BY_FILTER;
            case "REPLAY_BATCH" -> DryRunMode.REPLAY_BATCH;
            default -> throw new IllegalArgumentException("Unsupported dry-run mode: " + normalized);
        };
    }

    private record ScanResult(
        int scanned,
        int matched,
        boolean truncated,
        List<Document> documents
    ) {
        List<UUID> matchedDocumentIds() {
            return documents.stream().map(Document::getId).toList();
        }
    }

    private record DeadLetterScanResult(
        int totalMatched,
        int scanned,
        int matched,
        boolean truncated,
        List<String> entryKeys
    ) {}

    private record QueueBatchResult(
        int requested,
        int deduplicated,
        int queued,
        int skipped,
        int failed,
        List<RecoveryBatchItemDto> results
    ) {}

    private record DryRunPrediction(JobState jobState, String outcome, String reason) {}

    private record DryRunEvaluation(
        int estimatedQueued,
        int estimatedSkipped,
        int estimatedFailed,
        List<RecoveryDryRunItemDto> samples
    ) {}

    private record DryRunComputation(
        RecoveryDryRunResponseDto response,
        String domain,
        DryRunMode mode,
        boolean force,
        String reason,
        String category,
        Boolean retryable,
        int days,
        int maxDocuments,
        long totalCandidates,
        int scanned,
        int matched,
        boolean truncated,
        DryRunEvaluation evaluation
    ) {}

    private record CountRangeResult(
        long total,
        boolean truncated
    ) {}

    private record GroupedCountRangeResult(
        Map<String, Long> countByEventType,
        boolean truncated
    ) {}

    private record RecoveryHistoryExportAsyncSnapshot(
        HistoryExportAsyncType exportType,
        int limit,
        int days,
        String mode,
        String actor,
        String eventType,
        int compareBreakdownLimit,
        String compareBreakdownSort,
        int compareActorLimit,
        String compareActorSort
    ) {}

    private record RecoveryHistoryExportPayload(
        String filename,
        byte[] csvContent
    ) {}

    private enum HistoryExportAsyncType {
        HISTORY,
        HISTORY_SUMMARY,
        HISTORY_TREND,
        HISTORY_COMPARE,
        HISTORY_COMPARE_BREAKDOWN,
        HISTORY_COMPARE_ACTORS
    }

    private enum RecoveryHistoryExportAsyncStatus {
        QUEUED,
        RUNNING,
        COMPLETED,
        CANCELLED,
        FAILED,
        TIMED_OUT,
        EXPIRED
    }

    private record RecoveryHistoryExportAsyncTask(
        String taskId,
        HistoryExportAsyncType exportType,
        Instant createdAt,
        Instant startedAt,
        Instant updatedAt,
        Instant timeoutAt,
        Instant expiresAt,
        String createdBy,
        String updatedBy,
        RecoveryHistoryExportAsyncStatus status,
        String error,
        Instant finishedAt,
        String filename,
        byte[] csvContent
    ) {
        private RecoveryHistoryExportAsyncTask withStatus(
            RecoveryHistoryExportAsyncStatus nextStatus,
            String actor
        ) {
            Instant now = Instant.now();
            String nextActor = resolveTaskActor(actor);
            Instant nextStartedAt = startedAt;
            Instant nextTimeoutAt = timeoutAt;
            if (nextStatus == RecoveryHistoryExportAsyncStatus.RUNNING) {
                if (nextStartedAt == null) {
                    nextStartedAt = now;
                }
                nextTimeoutAt = resolveAsyncTaskTimeoutAt(now);
            }
            return new RecoveryHistoryExportAsyncTask(
                taskId,
                exportType,
                createdAt,
                nextStartedAt,
                now,
                nextTimeoutAt,
                expiresAt,
                createdBy,
                nextActor,
                nextStatus,
                null,
                null,
                null,
                null
            );
        }

        private RecoveryHistoryExportAsyncTask complete(
            String completedFilename,
            byte[] payload,
            String actor
        ) {
            Instant now = Instant.now();
            return new RecoveryHistoryExportAsyncTask(
                taskId,
                exportType,
                createdAt,
                startedAt,
                now,
                timeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                RecoveryHistoryExportAsyncStatus.COMPLETED,
                null,
                now,
                completedFilename,
                payload != null ? payload.clone() : null
            );
        }

        private RecoveryHistoryExportAsyncTask fail(String errorMessage, String actor) {
            Instant now = Instant.now();
            return new RecoveryHistoryExportAsyncTask(
                taskId,
                exportType,
                createdAt,
                startedAt,
                now,
                timeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                RecoveryHistoryExportAsyncStatus.FAILED,
                errorMessage,
                now,
                null,
                null
            );
        }

        private RecoveryHistoryExportAsyncTask cancel(String reason, String actor) {
            Instant now = Instant.now();
            return new RecoveryHistoryExportAsyncTask(
                taskId,
                exportType,
                createdAt,
                startedAt,
                now,
                timeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                RecoveryHistoryExportAsyncStatus.CANCELLED,
                reason,
                now,
                null,
                null
            );
        }

        private RecoveryHistoryExportAsyncTask timeout(Instant at, String actor) {
            Instant now = at != null ? at : Instant.now();
            String timeoutMessage = (error == null || error.isBlank()) ? ASYNC_TASK_TIMED_OUT_MESSAGE : error;
            return new RecoveryHistoryExportAsyncTask(
                taskId,
                exportType,
                createdAt,
                startedAt,
                now,
                timeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                RecoveryHistoryExportAsyncStatus.TIMED_OUT,
                timeoutMessage,
                now,
                null,
                null
            );
        }

        private RecoveryHistoryExportAsyncTask expire(Instant at, String actor) {
            Instant now = at != null ? at : Instant.now();
            String expireMessage = (error == null || error.isBlank()) ? ASYNC_TASK_EXPIRED_MESSAGE : error;
            return new RecoveryHistoryExportAsyncTask(
                taskId,
                exportType,
                createdAt,
                startedAt,
                now,
                timeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                RecoveryHistoryExportAsyncStatus.EXPIRED,
                expireMessage,
                finishedAt != null ? finishedAt : now,
                null,
                null
            );
        }

        private boolean isTerminal() {
            return status == RecoveryHistoryExportAsyncStatus.COMPLETED
                || status == RecoveryHistoryExportAsyncStatus.CANCELLED
                || status == RecoveryHistoryExportAsyncStatus.FAILED
                || status == RecoveryHistoryExportAsyncStatus.TIMED_OUT
                || status == RecoveryHistoryExportAsyncStatus.EXPIRED;
        }
    }

    private enum DryRunMode {
        QUEUE_BY_REASON,
        QUEUE_BY_WINDOW,
        CLEAR_BY_FILTER,
        REPLAY_BY_FILTER,
        REPLAY_BATCH
    }

    public enum JobState {
        READY,
        PROCESSING,
        FAILED,
        QUEUED,
        CLEARED,
        PENDING,
        SKIPPED,
        UNSUPPORTED,
        UNKNOWN
    }

    public enum FailureCategory {
        TEMPORARY,
        PERMANENT,
        UNSUPPORTED,
        UNKNOWN
    }

    public record RecoveryByReasonRequestDto(
        String domain,
        String reason,
        String category,
        Boolean retryable,
        Integer maxDocuments,
        Integer days,
        Boolean force
    ) {}

    public record RecoveryByWindowRequestDto(
        String domain,
        String reason,
        String category,
        Boolean retryable,
        Integer maxDocuments,
        Integer days,
        Boolean force
    ) {}

    public record RecoveryReplayBatchRequestDto(
        String domain,
        List<UUID> documentIds,
        List<String> entryKeys,
        Boolean force
    ) {}

    public record RecoveryClearBatchRequestDto(
        String domain,
        List<UUID> documentIds,
        List<String> entryKeys
    ) {}

    public record RecoveryClearByFilterRequestDto(
        String domain,
        String reason,
        String category,
        Boolean retryable,
        Integer maxDocuments,
        Integer days
    ) {}

    public record RecoveryReplayByFilterRequestDto(
        String domain,
        String reason,
        String category,
        Boolean retryable,
        Integer maxDocuments,
        Integer days,
        Boolean force
    ) {}

    public record RecoveryDryRunRequestDto(
        String domain,
        String mode,
        String reason,
        String category,
        Boolean retryable,
        Integer maxDocuments,
        Integer days,
        Boolean force,
        List<UUID> documentIds
    ) {}

    public record RecoveryHistoryExportAsyncRequestDto(
        String exportType,
        Integer limit,
        Integer days,
        String mode,
        String actor,
        String eventType,
        Integer compareBreakdownLimit,
        String compareBreakdownSort,
        Integer compareActorLimit,
        String compareActorSort
    ) {}

    public record RecoveryHistoryExportAsyncCreateResponseDto(
        String taskId,
        String exportType,
        String status,
        RecoveryHistoryExportAsyncRequestSnapshotDto request,
        Instant createdAt,
        Instant timeoutAt,
        Instant expiresAt,
        String createdBy,
        String updatedBy,
        boolean deduplicated,
        String deduplicatedFromTaskId,
        String message
    ) {}

    public record RecoveryHistoryExportAsyncStatusResponseDto(
        String taskId,
        String exportType,
        String status,
        String error,
        RecoveryHistoryExportAsyncRequestSnapshotDto request,
        Instant createdAt,
        Instant startedAt,
        Instant updatedAt,
        Instant timeoutAt,
        Instant expiresAt,
        Instant finishedAt,
        String filename,
        String createdBy,
        String updatedBy
    ) {}

    public record RecoveryHistoryExportAsyncRequestSnapshotDto(
        String exportType,
        int limit,
        int days,
        String mode,
        String actor,
        String eventType,
        int compareBreakdownLimit,
        String compareBreakdownSort,
        int compareActorLimit,
        String compareActorSort
    ) {}

    public record RecoveryHistoryExportAsyncListResponseDto(
        int count,
        RecoveryTaskCenterPagingDto paging,
        List<RecoveryHistoryExportAsyncStatusResponseDto> items
    ) {}

    public record RecoveryTaskCenterPagingDto(
        int skipCount,
        int maxItems,
        int totalItems,
        boolean hasMoreItems
    ) {}

    public record RecoveryHistoryExportAsyncSummaryResponseDto(
        int totalCount,
        int queuedCount,
        int runningCount,
        int completedCount,
        int cancelledCount,
        int failedCount,
        int timedOutCount,
        int expiredCount,
        int activeCount,
        int terminalCount
    ) {}

    public record RecoveryHistoryExportAsyncCleanupResponseDto(
        int deletedCount,
        int remainingCount,
        String exportTypeFilter,
        String statusFilter,
        String message
    ) {}

    public record RecoveryHistoryExportAsyncCancelActiveResponseDto(
        int cancelledCount,
        int remainingActiveCount,
        String exportTypeFilter,
        String statusFilter,
        String message
    ) {}

    public record RecoveryHistoryExportAsyncRetryTerminalByTaskIdsRequestDto(
        List<String> sourceTaskIds
    ) {}

    public record RecoveryHistoryExportAsyncRetryTerminalResponseDto(
        int requested,
        int retried,
        int reused,
        int skipped,
        int failed,
        int limit,
        String exportTypeFilter,
        String statusFilter,
        String message,
        List<RecoveryHistoryExportAsyncRetryTerminalItemDto> results
    ) {}

    public record RecoveryHistoryExportAsyncRetryTerminalItemDto(
        String sourceTaskId,
        String retriedTaskId,
        String exportType,
        String sourceStatus,
        String outcome,
        String message
    ) {}

    public record RecoveryHistoryExportAsyncRetryTerminalDryRunResponseDto(
        int requested,
        int retryable,
        int skipped,
        int limit,
        String exportTypeFilter,
        String statusFilter,
        String message,
        List<RecoveryHistoryExportAsyncRetryTerminalDryRunItemDto> results,
        List<RecoveryHistoryExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown
    ) {}

    public record RecoveryHistoryExportAsyncRetryTerminalDryRunItemDto(
        String sourceTaskId,
        String exportType,
        String sourceStatus,
        String outcome,
        String reasonCode,
        String message
    ) {}

    public record RecoveryHistoryExportAsyncRetryTerminalDryRunReasonCountDto(
        String reasonCode,
        String outcome,
        long count
    ) {}

    public record RecoveryBatchResponseDto(
        String domain,
        String mode,
        int windowDays,
        int maxDocuments,
        long totalCandidates,
        int scanned,
        int matched,
        boolean truncated,
        int requested,
        int deduplicated,
        int queued,
        int skipped,
        int failed,
        List<RecoveryBatchItemDto> results,
        String error
    ) {}

    public record RecoveryBatchItemDto(
        UUID documentId,
        JobState jobState,
        String outcome,
        String message,
        String previewStatus,
        FailureCategory failureCategory,
        String previewFailureReason,
        String previewFailureCategory,
        LocalDateTime previewLastUpdated,
        int attempts,
        Instant nextAttemptAt
    ) {}

    private record EffectivePreviewSummary(
        String previewStatus,
        String previewFailureReason,
        FailureCategory failureCategory,
        LocalDateTime previewLastUpdated,
        String previewFailureCategory
    ) {}

    public record RecoveryDryRunResponseDto(
        String domain,
        String mode,
        int windowDays,
        int maxDocuments,
        long totalCandidates,
        int scanned,
        int matched,
        boolean truncated,
        int estimatedQueued,
        int estimatedSkipped,
        int estimatedFailed,
        List<RecoveryDryRunItemDto> samples,
        String error
    ) {}

    public record RecoveryDryRunItemDto(
        UUID documentId,
        String name,
        String path,
        String mimeType,
        String previewStatus,
        FailureCategory failureCategory,
        String previewFailureReason,
        String previewFailureCategory,
        LocalDateTime previewLastUpdated,
        JobState predictedState,
        String predictedOutcome,
        String predictedReason
    ) {}

    public record RecoveryHistoryResponseDto(
        String domain,
        int windowDays,
        int limit,
        int page,
        int totalPages,
        long total,
        String modeFilter,
        String actorFilter,
        String eventTypeFilter,
        List<RecoveryHistoryItemDto> items
    ) {}

    public record RecoveryHistorySummaryResponseDto(
        String domain,
        int windowDays,
        String modeFilter,
        String actorFilter,
        String eventTypeFilter,
        long total,
        List<RecoveryHistorySummaryItemDto> items,
        List<RecoveryHistoryActorSummaryItemDto> actorItems
    ) {}

    public record RecoveryHistoryTrendResponseDto(
        String domain,
        int windowDays,
        String modeFilter,
        String actorFilter,
        String eventTypeFilter,
        long total,
        boolean truncated,
        List<RecoveryHistoryTrendItemDto> items
    ) {}

    public record RecoveryHistorySummaryCompareResponseDto(
        String domain,
        int windowDays,
        int previousWindowDays,
        String modeFilter,
        String actorFilter,
        String eventTypeFilter,
        long currentTotal,
        long previousTotal,
        long delta,
        Double deltaPercent,
        boolean compareAvailable,
        boolean truncated
    ) {}

    public record RecoveryHistorySummaryCompareBreakdownResponseDto(
        String domain,
        int windowDays,
        int previousWindowDays,
        String modeFilter,
        String actorFilter,
        String eventTypeFilter,
        boolean compareAvailable,
        boolean truncated,
        String sortBy,
        int requestedLimit,
        int totalItems,
        boolean limited,
        List<RecoveryHistorySummaryCompareBreakdownItemDto> items
    ) {}

    public record RecoveryHistorySummaryCompareActorsResponseDto(
        String domain,
        int windowDays,
        int previousWindowDays,
        String modeFilter,
        String actorFilter,
        String eventTypeFilter,
        boolean compareAvailable,
        boolean truncated,
        String sortBy,
        int requestedLimit,
        int totalItems,
        boolean limited,
        List<RecoveryHistorySummaryCompareActorItemDto> items
    ) {}

    public record RecoveryHistorySummaryCompareActorItemDto(
        String actor,
        long currentCount,
        long previousCount,
        long delta,
        Double deltaPercent
    ) {}

    public record RecoveryHistorySummaryCompareBreakdownItemDto(
        String eventType,
        String mode,
        long currentCount,
        long previousCount,
        long delta,
        Double deltaPercent
    ) {}

    public record RecoveryHistorySummaryItemDto(
        String eventType,
        String mode,
        long count
    ) {}

    public record RecoveryHistoryActorSummaryItemDto(
        String actor,
        long count
    ) {}

    public record RecoveryHistoryTrendItemDto(
        String day,
        long count
    ) {}

    public record RecoveryHistoryItemDto(
        UUID id,
        UUID nodeId,
        String nodeName,
        String eventType,
        String mode,
        String actor,
        LocalDateTime eventTime,
        String details,
        String previewStatus,
        String previewFailureReason,
        String previewFailureCategory,
        LocalDateTime previewLastUpdated
    ) {}

    private record RecoveryHistoryExportAsyncListPage(
        int totalItems,
        List<RecoveryHistoryExportAsyncStatusResponseDto> items
    ) {}

    private record RecoveryHistoryExportAsyncRetryTerminalDryRunComputation(
        int requested,
        int retryable,
        int skipped,
        int limit,
        String exportTypeFilter,
        String statusFilter,
        String message,
        List<RecoveryHistoryExportAsyncRetryTerminalDryRunItemDto> results,
        List<RecoveryHistoryExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown
    ) {}
}
