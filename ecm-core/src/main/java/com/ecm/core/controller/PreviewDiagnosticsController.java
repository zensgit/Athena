package com.ecm.core.controller;

import com.ecm.core.asynctask.AsyncTaskSummaryAdapters;
import com.ecm.core.asynctask.AsyncTaskSummarySnapshot;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.preview.CadRenderEndpointRegistry;
import com.ecm.core.preview.CadRenderFailoverTracker;
import com.ecm.core.preview.PreviewDeadLetterRegistry;
import com.ecm.core.preview.PreviewFailureClassifier;
import com.ecm.core.preview.PreviewFailurePolicyRegistry;
import com.ecm.core.preview.PreviewPreflightResolver;
import com.ecm.core.preview.PreviewQueueService;
import com.ecm.core.preview.PreviewRenditionPreventionRegistry;
import com.ecm.core.preview.PreviewStatusSemantics;
import com.ecm.core.preview.PreviewTransformTraceBuffer;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/preview/diagnostics")
@RequiredArgsConstructor
@Tag(name = "Preview Diagnostics", description = "Admin-only diagnostics endpoints for preview generation")
@PreAuthorize("hasRole('ADMIN')")
public class PreviewDiagnosticsController {

    private final DocumentRepository documentRepository;
    private final PreviewQueueService previewQueueService;
    private final CadRenderEndpointRegistry cadRenderEndpointRegistry;
    private final CadRenderFailoverTracker cadRenderFailoverTracker;
    private final PreviewTransformTraceBuffer previewTransformTraceBuffer;
    private final PreviewFailurePolicyRegistry previewFailurePolicyRegistry;
    private final PreviewPreflightResolver previewPreflightResolver;
    private final PreviewRenditionPreventionRegistry previewRenditionPreventionRegistry;
    private final PreviewDeadLetterRegistry previewDeadLetterRegistry;
    private final AuditService auditService;
    private final RenditionResourceService renditionResourceService;
    private static final int MAX_FAILURE_LIST_LIMIT = 200;
    private static final int DEFAULT_TRACE_LIST_LIMIT = 20;
    private static final int MAX_TRACE_LIST_LIMIT = 200;
    private static final int DEFAULT_SUMMARY_SAMPLE_LIMIT = 500;
    private static final int MAX_SUMMARY_SAMPLE_LIMIT = 2000;
    private static final int DEFAULT_QUEUE_DIAGNOSTICS_LIMIT = 20;
    private static final int MAX_QUEUE_DIAGNOSTICS_LIMIT = 100;
    private static final int DEFAULT_QUEUE_DIAGNOSTICS_EXPORT_LIMIT = 200;
    private static final int MAX_QUEUE_DIAGNOSTICS_EXPORT_LIMIT = 1000;
    private static final int DEFAULT_QUEUE_CANCEL_LIMIT = 200;
    private static final int MAX_QUEUE_CANCEL_LIMIT = 1000;
    private static final int DEFAULT_QUEUE_DECLINED_LIMIT = 50;
    private static final int MAX_QUEUE_DECLINED_LIMIT = 500;
    private static final int DEFAULT_QUEUE_DECLINED_EXPORT_LIMIT = 500;
    private static final int MAX_QUEUE_DECLINED_EXPORT_LIMIT = 2000;
    private static final int MAX_QUEUE_DECLINED_EXPORT_ASYNC_TASKS = 100;
    private static final int MAX_QUEUE_DECLINED_EXPORT_ASYNC_LIST_LIMIT = 100;
    private static final int DEFAULT_QUEUE_DECLINED_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT = 20;
    private static final int MAX_QUEUE_DECLINED_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT = 200;
    private static final int MAX_QUEUE_DECLINED_EXPORT_ASYNC_RETRY_SELECTED_IDS = 200;
    private static final String QUEUE_DECLINED_EXPORT_ASYNC_STATUS_ERROR_TEMPLATE = "Unknown async export status: %s";
    private static final int DEFAULT_QUEUE_DECLINED_ACTION_LIMIT = 200;
    private static final int MAX_QUEUE_DECLINED_ACTION_LIMIT = 1000;
    private static final int MAX_QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_ASYNC_TASKS = 100;
    private static final int MAX_QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_ASYNC_LIST_LIMIT = 100;
    private static final int DEFAULT_QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT = 20;
    private static final int MAX_QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT = 200;
    private static final int MAX_QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_ASYNC_RETRY_SELECTED_IDS = 200;
    private static final String QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_ASYNC_STATUS_ERROR_TEMPLATE =
        "Unknown async requeue dry-run export status: %s";
    private static final long ASYNC_TASK_ACTIVE_TIMEOUT_SECONDS = 1800L;
    private static final long ASYNC_TASK_RETENTION_SECONDS = 172800L;
    private static final String ASYNC_TASK_TIMEOUT_MESSAGE = "Task timed out before completion";
    private static final String ASYNC_TASK_EXPIRED_MESSAGE = "Task expired by retention policy";
    private static final int MAX_QUEUE_DECLINED_WINDOW_HOURS = 720;
    private static final int DEFAULT_RENDITION_RESOURCE_LIMIT = 100;
    private static final int MAX_RENDITION_RESOURCE_LIMIT = 500;
    private static final int DEFAULT_RENDITION_RESOURCE_EXPORT_LIMIT = 500;
    private static final int MAX_RENDITION_RESOURCE_EXPORT_LIMIT = 2000;
    private static final int MAX_RENDITION_RESOURCE_EXPORT_ASYNC_TASKS = 100;
    private static final int MAX_RENDITION_RESOURCE_EXPORT_ASYNC_LIST_LIMIT = 100;
    private static final int DEFAULT_RENDITION_RESOURCE_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT = 20;
    private static final int MAX_RENDITION_RESOURCE_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT = 200;
    private static final int MAX_RENDITION_RESOURCE_EXPORT_ASYNC_RETRY_SELECTED_IDS = 200;
    private static final String RENDITION_RESOURCES_EXPORT_ASYNC_STATUS_ERROR_TEMPLATE = "Unknown async export status: %s";
    private static final int MAX_TOP_FAILURE_REASONS = 10;
    private static final int DEFAULT_REASON_BATCH_LIMIT = 100;
    private static final int MAX_REASON_BATCH_LIMIT = 500;
    private static final int REASON_BATCH_SCAN_LIMIT = 2000;
    private static final int DEFAULT_PREVENTION_LIST_LIMIT = 50;
    private static final int MAX_PREVENTION_LIST_LIMIT = 500;
    private static final int MAX_PREVENTION_BATCH_LIMIT = 500;
    private static final int DEFAULT_FAILURE_LEDGER_LIST_LIMIT = 100;
    private static final int MAX_FAILURE_LEDGER_LIST_LIMIT = 500;
    private static final int MAX_FAILURE_LEDGER_RESET_BATCH_LIMIT = 500;
    private static final int DEFAULT_FAILURE_LEDGER_EXPORT_LIMIT = 500;
    private static final int MAX_FAILURE_LEDGER_EXPORT_LIMIT = 2000;
    private static final int DEFAULT_FAILURE_LEDGER_RESET_FILTER_LIMIT = 100;
    private static final int MAX_FAILURE_LEDGER_RESET_FILTER_LIMIT = 500;
    private static final int FAILURE_LEDGER_RESET_FILTER_SCAN_LIMIT = 2000;
    private static final int DEFAULT_DEAD_LETTER_LIST_LIMIT = 50;
    private static final int MAX_DEAD_LETTER_LIST_LIMIT = 500;
    private static final int DEFAULT_DEAD_LETTER_EXPORT_LIMIT = 500;
    private static final int MAX_DEAD_LETTER_EXPORT_LIMIT = 2000;
    private static final int MAX_DEAD_LETTER_BATCH_LIMIT = 500;
    private static final int DEFAULT_WINDOW_DAYS = 7;
    private static final int MAX_WINDOW_DAYS = 365;
    private static final List<String> RENDITION_SUMMARY_STATUSES = List.of(
        "CREATED",
        "NOT_CREATED",
        "STALE",
        "FAILED",
        "UNSUPPORTED",
        "PROCESSING"
    );
    private final Map<String, QueueDeclinedExportAsyncTask> queueDeclinedExportAsyncTasks = new ConcurrentHashMap<>();
    private final Deque<String> queueDeclinedExportAsyncTaskOrder = new ArrayDeque<>();
    private final Object queueDeclinedExportAsyncTaskLock = new Object();
    private final Map<String, QueueDeclinedRequeueDryRunExportAsyncTask> queueDeclinedRequeueDryRunExportAsyncTasks = new ConcurrentHashMap<>();
    private final Deque<String> queueDeclinedRequeueDryRunExportAsyncTaskOrder = new ArrayDeque<>();
    private final Object queueDeclinedRequeueDryRunExportAsyncTaskLock = new Object();
    private final Map<String, RenditionResourcesExportAsyncTask> renditionResourcesExportAsyncTasks = new ConcurrentHashMap<>();
    private final Map<String, PreviewRenditionResourcesExportAsyncRequestDto> renditionResourcesExportAsyncTaskRequests =
        new ConcurrentHashMap<>();
    private final Deque<String> renditionResourcesExportAsyncTaskOrder = new ArrayDeque<>();
    private final Object renditionResourcesExportAsyncTaskLock = new Object();

    @GetMapping("/failures")
    @Operation(summary = "Recent preview failures", description = "List recent preview failures (FAILED/UNSUPPORTED) with derived categories.")
    public ResponseEntity<List<PreviewFailureSampleDto>> getRecentFailures(
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(defaultValue = "7") int days
    ) {
        int safeLimit = clamp(limit, 1, MAX_FAILURE_LIST_LIMIT);
        int safeDays = normalizeWindowDays(days);
        Pageable pageable = PageRequest.of(0, safeLimit);
        LocalDateTime updatedSince = resolveUpdatedSince(safeDays);

        var statuses = failureStatuses();
        var page = documentRepository.findRecentPreviewFailuresByWindow(statuses, updatedSince, pageable);

        List<PreviewFailureSampleDto> payload = page.getContent().stream()
            .map(this::toPreviewFailureSample)
            .toList();

        return ResponseEntity.ok(payload);
    }

    @PostMapping("/failures/queue-batch")
    @Operation(
        summary = "Queue preview failures in batch",
        description = "Queue preview retry/rebuild for a batch of document ids from preview diagnostics."
    )
    public ResponseEntity<PreviewQueueBatchResponseDto> queueFailuresBatch(
        @RequestBody PreviewQueueBatchRequestDto request
    ) {
        List<UUID> rawIds = request != null && request.documentIds() != null
            ? request.documentIds()
            : List.of();
        boolean force = request != null && Boolean.TRUE.equals(request.force());
        PreviewQueueBatchResponseDto payload = queueFailuresInternal(rawIds, force);
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/failures/queue-by-reason")
    @Operation(
        summary = "Queue preview failures by reason group",
        description = "Queue preview retry/rebuild for matched failures in a reason/category scope across the selected diagnostics window."
    )
    public ResponseEntity<PreviewReasonBatchQueueResponseDto> queueFailuresByReason(
        @RequestBody PreviewReasonBatchQueueRequestDto request
    ) {
        String normalizedReason = normalizeReason(request != null ? request.reason() : null);
        String normalizedCategory = normalizedUpperOrDefault(request != null ? request.category() : null, "ANY");
        Boolean retryable = request != null ? request.retryable() : null;
        boolean force = request != null && Boolean.TRUE.equals(request.force());
        int requestedMaxDocuments = request != null && request.maxDocuments() != null
            ? request.maxDocuments()
            : DEFAULT_REASON_BATCH_LIMIT;
        int safeMaxDocuments = clamp(requestedMaxDocuments, 1, MAX_REASON_BATCH_LIMIT);
        int requestedDays = request != null && request.days() != null ? request.days() : DEFAULT_WINDOW_DAYS;
        int safeDays = normalizeWindowDays(requestedDays);
        LocalDateTime updatedSince = resolveUpdatedSince(safeDays);
        var statuses = failureStatuses();

        long totalByReason = documentRepository.countPreviewFailuresByReasonAndWindow(
            statuses,
            updatedSince,
            normalizedReason
        );

        List<Document> matchedDocuments = new ArrayList<>();
        int scanned = 0;
        int pageSize = Math.min(200, Math.max(1, safeMaxDocuments));
        int pageNumber = 0;
        boolean truncated = false;

        while (matchedDocuments.size() < safeMaxDocuments && scanned < REASON_BATCH_SCAN_LIMIT) {
            Pageable pageable = PageRequest.of(pageNumber, pageSize);
            Page<Document> page = documentRepository.findPreviewFailuresByReasonAndWindow(
                statuses,
                updatedSince,
                normalizedReason,
                pageable
            );
            if (page.isEmpty()) {
                break;
            }
            for (Document document : page.getContent()) {
                scanned += 1;
                PreviewFailureSampleDto sample = toPreviewFailureSample(document);
                String sampleCategory = normalizedUpperOrDefault(sample.previewFailureCategory(), "UNKNOWN");
                boolean sampleRetryable = PreviewFailureClassifier.CATEGORY_TEMPORARY.equals(sampleCategory);

                boolean categoryMatch = "ANY".equals(normalizedCategory) || sampleCategory.equals(normalizedCategory);
                boolean retryableMatch = retryable == null || sampleRetryable == retryable;

                if (categoryMatch && retryableMatch) {
                    matchedDocuments.add(document);
                    if (matchedDocuments.size() >= safeMaxDocuments) {
                        break;
                    }
                }
                if (scanned >= REASON_BATCH_SCAN_LIMIT) {
                    truncated = true;
                    break;
                }
            }
            if (!page.hasNext()) {
                break;
            }
            pageNumber += 1;
        }

        if (matchedDocuments.size() >= safeMaxDocuments) {
            truncated = true;
        }

        List<UUID> ids = matchedDocuments.stream().map(Document::getId).toList();
        PreviewQueueBatchResponseDto batch = queueFailuresInternal(ids, force);

        PreviewReasonBatchQueueResponseDto payload = new PreviewReasonBatchQueueResponseDto(
            normalizedReason,
            normalizedCategory,
            retryable,
            safeDays,
            safeMaxDocuments,
            totalByReason,
            scanned,
            matchedDocuments.size(),
            truncated,
            batch.queued(),
            batch.skipped(),
            batch.failed(),
            batch.results()
        );
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/queue/summary")
    @Operation(
        summary = "Preview queue diagnostics summary",
        description = "Summarize current preview queue governance state and sampled queued tasks."
    )
    public ResponseEntity<PreviewQueueDiagnosticsSummaryDto> getQueueDiagnosticsSummary(
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(required = false) String state,
        @RequestParam(required = false) String query
    ) {
        int safeLimit = clamp(
            limit <= 0 ? DEFAULT_QUEUE_DIAGNOSTICS_LIMIT : limit,
            1,
            MAX_QUEUE_DIAGNOSTICS_LIMIT
        );
        String safeStateFilter = normalizeQueueDiagnosticsStateFilter(state);
        String safeQueryFilter = normalizeQueueDiagnosticsQueryFilter(query);
        PreviewQueueService.PreviewQueueDiagnosticsSnapshot snapshot = loadQueueDiagnosticsSnapshot(safeLimit);
        List<PreviewQueueDiagnosticsItemDto> items = mapQueueDiagnosticsItems(snapshot, safeStateFilter, safeQueryFilter);
        int totalSampledItems = snapshot.items() != null ? snapshot.items().size() : 0;
        PreviewQueueDiagnosticsSummaryDto payload = new PreviewQueueDiagnosticsSummaryDto(
            snapshot.backend(),
            snapshot.queueEnabled(),
            snapshot.scheduledCount(),
            snapshot.governanceCount(),
            snapshot.runningCount(),
            snapshot.runningCountAccurate(),
            snapshot.cancellationRequestedCount(),
            snapshot.sampleLimit(),
            snapshot.sampleTruncated(),
            safeStateFilter,
            safeQueryFilter,
            totalSampledItems,
            items.size(),
            items
        );
        return ResponseEntity.ok(payload);
    }

    @GetMapping(value = "/queue/summary/export", produces = "text/csv")
    @Operation(
        summary = "Export preview queue diagnostics summary",
        description = "Export sampled preview queue diagnostics summary entries as CSV."
    )
    public ResponseEntity<String> exportQueueDiagnosticsSummaryCsv(
        @RequestParam(defaultValue = "200") int limit,
        @RequestParam(required = false) String state,
        @RequestParam(required = false) String query
    ) {
        int safeLimit = clamp(
            limit <= 0 ? DEFAULT_QUEUE_DIAGNOSTICS_EXPORT_LIMIT : limit,
            1,
            MAX_QUEUE_DIAGNOSTICS_EXPORT_LIMIT
        );
        String safeStateFilter = normalizeQueueDiagnosticsStateFilter(state);
        String safeQueryFilter = normalizeQueueDiagnosticsQueryFilter(query);
        PreviewQueueService.PreviewQueueDiagnosticsSnapshot snapshot = loadQueueDiagnosticsSnapshot(safeLimit);
        List<PreviewQueueDiagnosticsItemDto> items = mapQueueDiagnosticsItems(snapshot, safeStateFilter, safeQueryFilter);
        String csv = buildQueueDiagnosticsCsv(snapshot, safeStateFilter, safeQueryFilter, items);

        String filename = "preview_queue_diagnostics_" + Instant.now().toEpochMilli() + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        int itemCount = items.size();
        headers.add("X-Preview-Queue-Item-Count", String.valueOf(itemCount));
        auditQueueDiagnosticsExport(safeLimit, itemCount, snapshot.backend(), safeStateFilter, safeQueryFilter);
        return ResponseEntity.ok()
            .headers(headers)
            .body(csv);
    }

    @PostMapping("/queue/cancel-active")
    @Operation(
        summary = "Cancel active preview queue tasks",
        description = "Cancel queued/running preview queue tasks matched by queue diagnostics filters."
    )
    public ResponseEntity<PreviewQueueCancelActiveResponseDto> cancelActiveQueueTasks(
        @RequestParam(defaultValue = "200") int limit,
        @RequestParam(required = false) String state,
        @RequestParam(required = false) String query
    ) {
        int safeLimit = clamp(
            limit <= 0 ? DEFAULT_QUEUE_CANCEL_LIMIT : limit,
            1,
            MAX_QUEUE_CANCEL_LIMIT
        );
        String safeStateFilter = normalizeQueueDiagnosticsStateFilter(state);
        String safeQueryFilter = normalizeQueueDiagnosticsQueryFilter(query);

        PreviewQueueService.PreviewQueueDiagnosticsSnapshot snapshot = loadQueueDiagnosticsSnapshot(safeLimit);
        List<PreviewQueueDiagnosticsItemDto> candidates = mapQueueDiagnosticsItems(snapshot, safeStateFilter, safeQueryFilter)
            .stream()
            .limit(safeLimit)
            .toList();

        int cancelled = 0;
        int skipped = 0;
        int failed = 0;
        List<PreviewQueueCancelActiveItemDto> results = new ArrayList<>();
        for (PreviewQueueDiagnosticsItemDto candidate : candidates) {
            UUID documentId = candidate.documentId();
            if (documentId == null) {
                failed += 1;
                results.add(new PreviewQueueCancelActiveItemDto(
                    null,
                    candidate.previewStatus(),
                    candidate.previewFailureReason(),
                    candidate.previewFailureCategory(),
                    candidate.previewLastUpdated(),
                    candidate.queueState(),
                    "FAILED",
                    "Missing document id in queue diagnostics item"
                ));
                continue;
            }
            try {
                PreviewQueueService.PreviewQueueCancellationStatus status = previewQueueService.cancel(documentId);
                boolean wasCancelled = status != null && status.cancelled();
                if (wasCancelled) {
                    cancelled += 1;
                } else {
                    skipped += 1;
                }
                results.add(new PreviewQueueCancelActiveItemDto(
                    documentId,
                    candidate.previewStatus(),
                    candidate.previewFailureReason(),
                    candidate.previewFailureCategory(),
                    candidate.previewLastUpdated(),
                    status != null ? status.queueState() : candidate.queueState(),
                    wasCancelled ? "CANCELLED" : "SKIPPED",
                    status != null ? status.message() : "No cancellation status returned"
                ));
            } catch (Exception ex) {
                failed += 1;
                results.add(new PreviewQueueCancelActiveItemDto(
                    documentId,
                    candidate.previewStatus(),
                    candidate.previewFailureReason(),
                    candidate.previewFailureCategory(),
                    candidate.previewLastUpdated(),
                    candidate.queueState(),
                    "FAILED",
                    ex.getMessage()
                ));
            }
        }

        PreviewQueueCancelActiveResponseDto payload = new PreviewQueueCancelActiveResponseDto(
            safeStateFilter,
            safeQueryFilter,
            safeLimit,
            candidates.size(),
            cancelled,
            skipped,
            failed,
            results
        );
        auditQueueCancelActive(payload);
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/queue/declined")
    @Operation(
        summary = "Preview queue declined diagnostics",
        description = "List declined queueing decisions (quiet period, unsupported, disabled, prevention, etc.) with filters."
    )
    public ResponseEntity<PreviewQueueDeclinedSummaryDto> getQueueDeclinedSummary(
        @RequestParam(defaultValue = "50") int limit,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String forceRequired,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) Integer windowHours
    ) {
        int safeLimit = clamp(
            limit <= 0 ? DEFAULT_QUEUE_DECLINED_LIMIT : limit,
            1,
            MAX_QUEUE_DECLINED_LIMIT
        );
        String safeCategoryFilter = normalizeQueueDeclinedCategoryFilter(category);
        String safeForceRequiredFilter = normalizeQueueDeclinedForceRequiredFilter(forceRequired);
        String safeQueryFilter = normalizeQueueDiagnosticsQueryFilter(query);
        Integer safeWindowHoursFilter = normalizeQueueDeclinedWindowHours(windowHours);
        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = previewQueueService.declinedSnapshot(safeLimit);
        List<PreviewQueueDeclinedItemDto> items = mapQueueDeclinedItems(
            snapshot,
            safeCategoryFilter,
            safeForceRequiredFilter,
            safeQueryFilter,
            safeWindowHoursFilter
        );
        List<PreviewQueueDeclinedCategoryCountDto> categoryCounts = mapQueueDeclinedCategoryCounts(items);
        long forceRequiredCount = items.stream().filter(PreviewQueueDeclinedItemDto::forceRequired).count();
        int totalSampledItems = snapshot.items() != null ? snapshot.items().size() : 0;
        PreviewQueueDeclinedSummaryDto payload = new PreviewQueueDeclinedSummaryDto(
            snapshot.queueEnabled(),
            snapshot.totalDeclined(),
            snapshot.sampleLimit(),
            snapshot.sampleTruncated(),
            safeCategoryFilter,
            safeForceRequiredFilter,
            safeQueryFilter,
            safeWindowHoursFilter,
            totalSampledItems,
            items.size(),
            forceRequiredCount,
            categoryCounts,
            items
        );
        return ResponseEntity.ok(payload);
    }

    @GetMapping(value = "/queue/declined/export", produces = "text/csv")
    @Operation(
        summary = "Export preview queue declined diagnostics",
        description = "Export declined queueing decisions as CSV with filters."
    )
    public ResponseEntity<String> exportQueueDeclinedCsv(
        @RequestParam(defaultValue = "500") int limit,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String forceRequired,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) Integer windowHours
    ) {
        int safeLimit = clamp(
            limit <= 0 ? DEFAULT_QUEUE_DECLINED_EXPORT_LIMIT : limit,
            1,
            MAX_QUEUE_DECLINED_EXPORT_LIMIT
        );
        String safeCategoryFilter = normalizeQueueDeclinedCategoryFilter(category);
        String safeForceRequiredFilter = normalizeQueueDeclinedForceRequiredFilter(forceRequired);
        String safeQueryFilter = normalizeQueueDiagnosticsQueryFilter(query);
        Integer safeWindowHoursFilter = normalizeQueueDeclinedWindowHours(windowHours);
        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = previewQueueService.declinedSnapshot(safeLimit);
        List<PreviewQueueDeclinedItemDto> items = mapQueueDeclinedItems(
            snapshot,
            safeCategoryFilter,
            safeForceRequiredFilter,
            safeQueryFilter,
            safeWindowHoursFilter
        );
        String csv = buildQueueDeclinedCsv(
            snapshot,
            safeCategoryFilter,
            safeForceRequiredFilter,
            safeQueryFilter,
            safeWindowHoursFilter,
            items
        );

        String filename = "preview_queue_declined_" + Instant.now().toEpochMilli() + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        int itemCount = items.size();
        headers.add("X-Preview-Queue-Declined-Count", String.valueOf(itemCount));
        auditQueueDeclinedExport(
            safeLimit,
            itemCount,
            safeCategoryFilter,
            safeForceRequiredFilter,
            safeQueryFilter,
            safeWindowHoursFilter
        );
        return ResponseEntity.ok()
            .headers(headers)
            .body(csv);
    }

    @PostMapping("/queue/declined/export-async")
    @Operation(
        summary = "Start async preview queue declined diagnostics export",
        description = "Start an asynchronous CSV export task for declined preview queue diagnostics."
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
    public ResponseEntity<PreviewQueueDeclinedExportAsyncCreateResponseDto> startQueueDeclinedCsvAsyncExport(
        @RequestBody(required = false) PreviewQueueDeclinedExportAsyncRequestDto request
    ) {
        PreviewQueueDeclinedExportAsyncRequestDto requestSnapshot = copyQueueDeclinedExportAsyncRequest(request);
        PreviewQueueDeclinedExportAsyncRequestDto normalizedRequest = normalizeQueueDeclinedExportAsyncRequest(requestSnapshot);
        QueueDeclinedExportAsyncTask activeTask = findActiveQueueDeclinedExportAsyncTask(normalizedRequest);
        if (activeTask != null) {
            auditQueueDeclinedAsyncExportStartDedupHit(activeTask);
            return acceptedQueueDeclinedExportAsyncCreateResponse(new PreviewQueueDeclinedExportAsyncCreateResponseDto(
                activeTask.taskId(),
                activeTask.status().name(),
                activeTask.createdAt(),
                activeTask.timeoutAt(),
                activeTask.expiresAt(),
                activeTask.createdBy(),
                activeTask.updatedBy(),
                true,
                activeTask.taskId(),
                "Reused active queue declined async export task with same filters"
            ), activeTask.taskId());
        }
        QueueDeclinedExportAsyncTask task = createQueueDeclinedExportAsyncTask(normalizedRequest);
        auditQueueDeclinedAsyncExportStarted(task);

        CompletableFuture.runAsync(() -> runQueueDeclinedExportAsyncTask(task, normalizedRequest));

        return acceptedQueueDeclinedExportAsyncCreateResponse(new PreviewQueueDeclinedExportAsyncCreateResponseDto(
            task.taskId(),
            task.status().name(),
            task.createdAt(),
            task.timeoutAt(),
            task.expiresAt(),
            task.createdBy(),
            task.updatedBy(),
            false,
            null,
            "Queue declined async export task started"
        ), task.taskId());
    }

    @GetMapping("/queue/declined/export-async")
    @Operation(
        summary = "List async preview queue declined diagnostics export tasks",
        description = "List recent asynchronous declined preview queue CSV export tasks."
    )
    public ResponseEntity<PreviewQueueDeclinedExportAsyncListResponseDto> listQueueDeclinedCsvAsyncExportTasks(
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Integer maxItems,
        @RequestParam(defaultValue = "0") Integer skipCount,
        @RequestParam(required = false) String status
    ) {
        int boundedMaxItems = resolveListMaxItems(maxItems, limit, MAX_QUEUE_DECLINED_EXPORT_ASYNC_LIST_LIMIT);
        int normalizedSkipCount = Math.max(skipCount != null ? skipCount : 0, 0);
        QueueDeclinedExportAsyncStatus statusFilter = parseQueueDeclinedExportAsyncStatus(status);
        QueueDeclinedExportAsyncListPage page = listQueueDeclinedExportAsyncTasks(
            normalizedSkipCount,
            boundedMaxItems,
            statusFilter
        );
        boolean hasMoreItems = normalizedSkipCount + page.items().size() < page.totalItems();
        return ResponseEntity.ok(new PreviewQueueDeclinedExportAsyncListResponseDto(
            page.items().size(),
            new PreviewTaskCenterPagingDto(
                normalizedSkipCount,
                boundedMaxItems,
                page.totalItems(),
                hasMoreItems
            ),
            page.items()
        ));
    }

    @GetMapping("/queue/declined/export-async/summary")
    @Operation(
        summary = "Preview queue declined async export task summary",
        description = "Return aggregate declined queue async export task counts by status and lifecycle class."
    )
    public ResponseEntity<PreviewQueueDeclinedExportAsyncSummaryResponseDto> summarizeQueueDeclinedCsvAsyncExportTasks(
        @RequestParam(required = false) String status
    ) {
        QueueDeclinedExportAsyncStatus statusFilter = parseQueueDeclinedExportAsyncStatus(status);
        return ResponseEntity.ok(buildQueueDeclinedExportAsyncSummary(statusFilter));
    }

    @PostMapping("/queue/declined/export-async/cleanup")
    @Operation(
        summary = "Cleanup preview queue declined async export tasks",
        description = "Delete terminal async export tasks by default, or delete a specific terminal status."
    )
    public ResponseEntity<PreviewQueueDeclinedExportAsyncCleanupResponseDto> cleanupQueueDeclinedCsvAsyncExportTasks(
        @RequestParam(required = false) String status
    ) {
        QueueDeclinedExportAsyncStatus statusFilter = parseQueueDeclinedExportAsyncStatus(status);
        if (statusFilter == QueueDeclinedExportAsyncStatus.QUEUED
            || statusFilter == QueueDeclinedExportAsyncStatus.RUNNING) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "status filter only supports terminal states: COMPLETED, CANCELLED, FAILED, TIMED_OUT, EXPIRED"
            );
        }

        long deletedCount;
        int remainingCount;
        List<QueueDeclinedExportAsyncTask> deletedTasks = new ArrayList<>();
        synchronized (queueDeclinedExportAsyncTaskLock) {
            refreshQueueDeclinedExportAsyncTasksLifecycleLocked();
            deletedCount = cleanupQueueDeclinedExportAsyncTasksLocked(statusFilter, deletedTasks);
            remainingCount = queueDeclinedExportAsyncTasks.size();
        }

        String statusFilterName = statusFilter != null ? statusFilter.name() : null;
        String message = deletedCount > 0
            ? statusFilterName == null
                ? String.format("Deleted %d terminal async preview queue declined export tasks", deletedCount)
                : String.format("Deleted %d async preview queue declined export tasks with status %s", deletedCount, statusFilterName)
            : statusFilterName == null
                ? "No terminal async preview queue declined export tasks to delete"
                : String.format("No async preview queue declined export tasks with status %s to delete", statusFilterName);

        auditQueueDeclinedAsyncExportCleanup(statusFilterName, deletedCount, remainingCount, deletedTasks);

        return ResponseEntity.ok(new PreviewQueueDeclinedExportAsyncCleanupResponseDto(
            deletedCount,
            remainingCount,
            statusFilterName,
            message
        ));
    }

    @PostMapping("/queue/declined/export-async/cancel-active")
    @Operation(
        summary = "Cancel active preview queue declined async export tasks",
        description = "Cancel queued/running async export tasks, optionally narrowed by active status."
    )
    public ResponseEntity<PreviewQueueDeclinedExportAsyncCancelActiveResponseDto> cancelActiveQueueDeclinedCsvAsyncExportTasks(
        @RequestParam(required = false) String status
    ) {
        QueueDeclinedExportAsyncStatus statusFilter = parseQueueDeclinedExportAsyncStatus(status);
        if (statusFilter != null && !isActiveQueueDeclinedExportAsyncStatus(statusFilter)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "status filter only supports active states: QUEUED, RUNNING"
            );
        }

        long[] cancelledCount = {0L};
        List<QueueDeclinedExportAsyncTask> cancelledTasks = new ArrayList<>();
        int remainingActiveCount;
        synchronized (queueDeclinedExportAsyncTaskLock) {
            refreshQueueDeclinedExportAsyncTasksLifecycleLocked();
            List<String> taskIds = new ArrayList<>(queueDeclinedExportAsyncTasks.keySet());
            for (String taskId : taskIds) {
                queueDeclinedExportAsyncTasks.computeIfPresent(taskId, (key, current) -> {
                    if (current == null || !isActiveQueueDeclinedExportAsyncStatus(current.status())) {
                        return current;
                    }
                    if (statusFilter != null && current.status() != statusFilter) {
                        return current;
                    }
                    cancelledCount[0] += 1;
                    QueueDeclinedExportAsyncTask cancelledTask =
                        current.cancel("Cancelled by active-task bulk request", resolveAuditUsername());
                    cancelledTasks.add(cancelledTask);
                    return cancelledTask;
                });
            }
            queueDeclinedExportAsyncTaskOrder.removeIf(taskId -> !queueDeclinedExportAsyncTasks.containsKey(taskId));
            remainingActiveCount = countActiveQueueDeclinedExportAsyncTasksLocked();
        }

        String statusFilterName = statusFilter != null ? statusFilter.name() : null;
        String message = cancelledCount[0] > 0
            ? statusFilterName == null
                ? String.format("Cancelled %d active async preview queue declined export tasks", cancelledCount[0])
                : String.format("Cancelled %d async preview queue declined export tasks with status %s", cancelledCount[0], statusFilterName)
            : statusFilterName == null
                ? "No active async preview queue declined export tasks to cancel"
                : String.format("No async preview queue declined export tasks with status %s to cancel", statusFilterName);

        auditQueueDeclinedAsyncExportCancelActive(
            statusFilterName,
            cancelledCount[0],
            remainingActiveCount,
            cancelledTasks
        );

        return ResponseEntity.ok(new PreviewQueueDeclinedExportAsyncCancelActiveResponseDto(
            cancelledCount[0],
            remainingActiveCount,
            statusFilterName,
            message
        ));
    }

    @GetMapping("/queue/declined/export-async/{taskId}")
    @Operation(
        summary = "Get async preview queue declined diagnostics export task status",
        description = "Get status/details for a declined preview queue asynchronous CSV export task."
    )
    public ResponseEntity<PreviewQueueDeclinedExportAsyncStatusResponseDto> getQueueDeclinedCsvAsyncExportTaskStatus(
        @PathVariable String taskId
    ) {
        QueueDeclinedExportAsyncTask task;
        synchronized (queueDeclinedExportAsyncTaskLock) {
            refreshQueueDeclinedExportAsyncTasksLifecycleLocked();
            task = queueDeclinedExportAsyncTasks.get(taskId);
        }
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toQueueDeclinedExportAsyncStatusResponse(task));
    }

    @PostMapping("/queue/declined/export-async/{taskId}/retry")
    @Operation(
        summary = "Retry async preview queue declined diagnostics export task",
        description = "Retry a terminal declined preview queue asynchronous CSV export task using the original filter snapshot."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202",
            description = "Retry accepted (new or deduplicated active task).",
            headers = {
                @Header(
                    name = HttpHeaders.LOCATION,
                    description = "Task status endpoint.",
                    schema = @Schema(type = "string")
                )
            }
        ),
        @ApiResponse(responseCode = "404", description = "Source task not found."),
        @ApiResponse(responseCode = "409", description = "Source task is active and cannot be retried.")
    })
    public ResponseEntity<PreviewQueueDeclinedExportAsyncCreateResponseDto> retryQueueDeclinedCsvAsyncExportTask(
        @PathVariable String taskId
    ) {
        QueueDeclinedExportAsyncTask sourceTask;
        synchronized (queueDeclinedExportAsyncTaskLock) {
            refreshQueueDeclinedExportAsyncTasksLifecycleLocked();
            sourceTask = queueDeclinedExportAsyncTasks.get(taskId);
        }
        if (sourceTask == null) {
            auditQueueDeclinedAsyncExportRetry(taskId, null, null, "NOT_FOUND");
            return ResponseEntity.notFound().build();
        }
        if (isActiveQueueDeclinedExportAsyncStatus(sourceTask.status())) {
            auditQueueDeclinedAsyncExportRetry(taskId, sourceTask, null, sourceTask.status().name());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        PreviewQueueDeclinedExportAsyncRequestDto retryRequest = toQueueDeclinedExportAsyncRequest(sourceTask);
        QueueDeclinedExportAsyncTask activeTask = findActiveQueueDeclinedExportAsyncTask(retryRequest);
        if (activeTask != null) {
            auditQueueDeclinedAsyncExportRetry(taskId, sourceTask, activeTask, activeTask.status().name());
            return acceptedQueueDeclinedExportAsyncCreateResponse(new PreviewQueueDeclinedExportAsyncCreateResponseDto(
                activeTask.taskId(),
                activeTask.status().name(),
                activeTask.createdAt(),
                activeTask.timeoutAt(),
                activeTask.expiresAt(),
                activeTask.createdBy(),
                activeTask.updatedBy(),
                true,
                activeTask.taskId(),
                "Reused active queue declined async export task with same filters"
            ), activeTask.taskId());
        }

        QueueDeclinedExportAsyncTask retryTask = createQueueDeclinedExportAsyncTask(retryRequest);
        auditQueueDeclinedAsyncExportRetry(taskId, sourceTask, retryTask, retryTask.status().name());

        CompletableFuture.runAsync(() -> runQueueDeclinedExportAsyncTask(retryTask, retryRequest));

        return acceptedQueueDeclinedExportAsyncCreateResponse(new PreviewQueueDeclinedExportAsyncCreateResponseDto(
            retryTask.taskId(),
            retryTask.status().name(),
            retryTask.createdAt(),
            retryTask.timeoutAt(),
            retryTask.expiresAt(),
            retryTask.createdBy(),
            retryTask.updatedBy(),
            false,
            null,
            "Retried queue declined async export task"
        ), retryTask.taskId());
    }

    @PostMapping("/queue/declined/export-async/retry-terminal")
    @Operation(
        summary = "Retry terminal async preview queue declined diagnostics export tasks",
        description = "Retry terminal declined preview queue asynchronous CSV export tasks in bulk."
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
        @ApiResponse(responseCode = "400", description = "Invalid status filter.")
    })
    public ResponseEntity<PreviewQueueDeclinedExportAsyncRetryTerminalResponseDto> retryTerminalQueueDeclinedCsvAsyncExportTasks(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "20") int limit
    ) {
        QueueDeclinedExportAsyncStatus statusFilter = parseQueueDeclinedExportAsyncStatus(status);
        if (statusFilter != null && isActiveQueueDeclinedExportAsyncStatus(statusFilter)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "status filter only supports terminal states: COMPLETED, CANCELLED, FAILED, TIMED_OUT, EXPIRED"
            );
        }

        int boundedLimit = clamp(
            limit <= 0 ? DEFAULT_QUEUE_DECLINED_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT : limit,
            1,
            MAX_QUEUE_DECLINED_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT
        );
        List<QueueDeclinedExportAsyncTask> candidates = listQueueDeclinedExportAsyncRetryTerminalCandidates(
            boundedLimit,
            statusFilter
        );

        int requested = candidates.size();
        int retried = 0;
        int reused = 0;
        int skipped = 0;
        int failed = 0;
        List<PreviewQueueDeclinedExportAsyncRetryTerminalItemDto> results = new ArrayList<>();
        List<QueueDeclinedExportAsyncTask> retriedSourceTasks = new ArrayList<>();
        List<QueueDeclinedExportAsyncTask> retriedTasks = new ArrayList<>();

        for (QueueDeclinedExportAsyncTask sourceTask : candidates) {
            if (sourceTask == null || !sourceTask.isTerminal()) {
                skipped += 1;
                results.add(new PreviewQueueDeclinedExportAsyncRetryTerminalItemDto(
                    sourceTask != null ? sourceTask.taskId() : null,
                    null,
                    sourceTask != null && sourceTask.status() != null ? sourceTask.status().name() : "UNKNOWN",
                    "SKIPPED",
                    "Source task is missing or not terminal"
                ));
                continue;
            }

            try {
                PreviewQueueDeclinedExportAsyncRequestDto retryRequest = toQueueDeclinedExportAsyncRequest(sourceTask);
                QueueDeclinedExportAsyncTask activeTask = findActiveQueueDeclinedExportAsyncTask(retryRequest);
                boolean deduplicated = activeTask != null;
                QueueDeclinedExportAsyncTask retryTask = deduplicated
                    ? activeTask
                    : createQueueDeclinedExportAsyncTask(retryRequest);
                retried += 1;
                if (deduplicated) {
                    reused += 1;
                }
                retriedSourceTasks.add(sourceTask);
                retriedTasks.add(retryTask);
                results.add(new PreviewQueueDeclinedExportAsyncRetryTerminalItemDto(
                    sourceTask.taskId(),
                    retryTask.taskId(),
                    sourceTask.status().name(),
                    deduplicated ? "REUSED" : "RETRIED",
                    deduplicated ? "Reused active async export task with same filters" : "Retried terminal async export task"
                ));
                if (!deduplicated) {
                    CompletableFuture.runAsync(() -> runQueueDeclinedExportAsyncTask(retryTask, retryRequest));
                }
            } catch (Exception ex) {
                failed += 1;
                results.add(new PreviewQueueDeclinedExportAsyncRetryTerminalItemDto(
                    sourceTask.taskId(),
                    null,
                    sourceTask.status().name(),
                    "FAILED",
                    resolveErrorMessage(ex)
                ));
            }
        }

        String statusFilterName = statusFilter != null ? statusFilter.name() : "FAILED|CANCELLED|TIMED_OUT|EXPIRED";
        String message = requested == 0
            ? "No terminal async preview queue declined export tasks matched retry filters"
            : String.format(
                "Retried %d/%d terminal async preview queue declined export tasks (reused=%d, skipped=%d, failed=%d)",
                retried,
                requested,
                reused,
                skipped,
                failed
            );

        auditQueueDeclinedAsyncExportRetryBulk(
            statusFilterName,
            boundedLimit,
            requested,
            retried,
            reused,
            skipped,
            failed,
            retriedSourceTasks,
            retriedTasks
        );

        return acceptedQueueDeclinedExportAsyncRetryTerminalResponse(new PreviewQueueDeclinedExportAsyncRetryTerminalResponseDto(
            requested,
            retried,
            reused,
            skipped,
            failed,
            boundedLimit,
            statusFilterName,
            message,
            results
        ));
    }

    @PostMapping("/queue/declined/export-async/retry-terminal/dry-run")
    @Operation(
        summary = "Dry-run terminal async preview queue declined diagnostics export task retries",
        description = "Estimate terminal declined preview queue asynchronous CSV export tasks that can be retried in bulk."
    )
    public ResponseEntity<PreviewQueueDeclinedExportAsyncRetryTerminalDryRunResponseDto> dryRunRetryTerminalQueueDeclinedCsvAsyncExportTasks(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "20") int limit
    ) {
        QueueDeclinedExportAsyncRetryTerminalDryRunComputation dryRun = computeQueueDeclinedExportAsyncRetryTerminalDryRun(
            status,
            limit
        );

        auditQueueDeclinedAsyncExportRetryBulkDryRun(
            dryRun.statusFilterName(),
            dryRun.limit(),
            dryRun.requested(),
            dryRun.retryable(),
            dryRun.skipped(),
            dryRun.sourceTasks(),
            dryRun.reasonBreakdown()
        );

        return ResponseEntity.ok(new PreviewQueueDeclinedExportAsyncRetryTerminalDryRunResponseDto(
            dryRun.requested(),
            dryRun.retryable(),
            dryRun.skipped(),
            dryRun.limit(),
            dryRun.statusFilterName(),
            dryRun.message(),
            dryRun.results(),
            dryRun.reasonBreakdown()
        ));
    }

    @GetMapping(value = "/queue/declined/export-async/retry-terminal/dry-run/export", produces = "text/csv")
    @Operation(
        summary = "Export retry-terminal dry-run diagnostics CSV for declined async preview queue export tasks",
        description = "Export dry-run retry-terminal diagnostics rows and reason breakdown as CSV."
    )
    public ResponseEntity<byte[]> exportDryRunRetryTerminalQueueDeclinedCsvAsyncExportTasks(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "20") int limit
    ) {
        QueueDeclinedExportAsyncRetryTerminalDryRunComputation dryRun = computeQueueDeclinedExportAsyncRetryTerminalDryRun(
            status,
            limit
        );
        String csv = buildQueueDeclinedExportAsyncRetryTerminalDryRunCsv(dryRun);
        String filename = String.format(
            "preview_queue_declined_async_retry_dry_run_%s.csv",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.add("X-Preview-Queue-Declined-Retry-Dry-Run-Count", String.valueOf(dryRun.results().size()));
        auditQueueDeclinedAsyncExportRetryBulkDryRunExport(
            dryRun.statusFilterName(),
            dryRun.limit(),
            dryRun.requested(),
            dryRun.retryable(),
            dryRun.skipped(),
            dryRun.reasonBreakdown()
        );
        return ResponseEntity.ok()
            .headers(headers)
            .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/queue/declined/export-async/retry-terminal/by-task-ids")
    @Operation(
        summary = "Retry selected terminal async preview queue declined diagnostics export tasks",
        description = "Retry terminal declined preview queue asynchronous CSV export tasks by source task ids."
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
    public ResponseEntity<PreviewQueueDeclinedExportAsyncRetryTerminalResponseDto> retrySelectedTerminalQueueDeclinedCsvAsyncExportTasks(
        @RequestBody(required = false) PreviewQueueDeclinedExportAsyncRetryTerminalByTaskIdsRequestDto request
    ) {
        List<String> sourceTaskIds = normalizeQueueDeclinedRetrySourceTaskIds(
            request != null ? request.sourceTaskIds() : null
        );
        synchronized (queueDeclinedExportAsyncTaskLock) {
            refreshQueueDeclinedExportAsyncTasksLifecycleLocked();
        }

        int requested = sourceTaskIds.size();
        int retried = 0;
        int reused = 0;
        int skipped = 0;
        int failed = 0;
        List<PreviewQueueDeclinedExportAsyncRetryTerminalItemDto> results = new ArrayList<>();
        List<QueueDeclinedExportAsyncTask> retriedSourceTasks = new ArrayList<>();
        List<QueueDeclinedExportAsyncTask> retriedTasks = new ArrayList<>();

        for (String sourceTaskId : sourceTaskIds) {
            QueueDeclinedExportAsyncTask sourceTask = queueDeclinedExportAsyncTasks.get(sourceTaskId);
            if (sourceTask == null) {
                skipped += 1;
                results.add(new PreviewQueueDeclinedExportAsyncRetryTerminalItemDto(
                    sourceTaskId,
                    null,
                    "NOT_FOUND",
                    "SKIPPED",
                    "Source task not found"
                ));
                continue;
            }
            if (!sourceTask.isTerminal()) {
                skipped += 1;
                results.add(new PreviewQueueDeclinedExportAsyncRetryTerminalItemDto(
                    sourceTask.taskId(),
                    null,
                    sourceTask.status().name(),
                    "SKIPPED",
                    "Source task is not terminal"
                ));
                continue;
            }
            try {
                PreviewQueueDeclinedExportAsyncRequestDto retryRequest = toQueueDeclinedExportAsyncRequest(sourceTask);
                QueueDeclinedExportAsyncTask activeTask = findActiveQueueDeclinedExportAsyncTask(retryRequest);
                boolean deduplicated = activeTask != null;
                QueueDeclinedExportAsyncTask retryTask = deduplicated
                    ? activeTask
                    : createQueueDeclinedExportAsyncTask(retryRequest);
                retried += 1;
                if (deduplicated) {
                    reused += 1;
                }
                retriedSourceTasks.add(sourceTask);
                retriedTasks.add(retryTask);
                results.add(new PreviewQueueDeclinedExportAsyncRetryTerminalItemDto(
                    sourceTask.taskId(),
                    retryTask.taskId(),
                    sourceTask.status().name(),
                    deduplicated ? "REUSED" : "RETRIED",
                    deduplicated ? "Reused active async export task with same filters" : "Retried selected terminal async export task"
                ));
                if (!deduplicated) {
                    CompletableFuture.runAsync(() -> runQueueDeclinedExportAsyncTask(retryTask, retryRequest));
                }
            } catch (Exception ex) {
                failed += 1;
                results.add(new PreviewQueueDeclinedExportAsyncRetryTerminalItemDto(
                    sourceTask.taskId(),
                    null,
                    sourceTask.status().name(),
                    "FAILED",
                    resolveErrorMessage(ex)
                ));
            }
        }

        String message = requested == 0
            ? "No source task ids provided for terminal async preview queue declined export retry"
            : String.format(
                "Retried %d/%d selected terminal async preview queue declined export tasks (reused=%d, skipped=%d, failed=%d)",
                retried,
                requested,
                reused,
                skipped,
                failed
            );

        auditQueueDeclinedAsyncExportRetryBulkSelected(
            requested,
            retried,
            reused,
            skipped,
            failed,
            retriedSourceTasks,
            retriedTasks
        );

        return acceptedQueueDeclinedExportAsyncRetryTerminalResponse(new PreviewQueueDeclinedExportAsyncRetryTerminalResponseDto(
            requested,
            retried,
            reused,
            skipped,
            failed,
            requested,
            "BY_TASK_IDS",
            message,
            results
        ));
    }

    @PostMapping("/queue/declined/export-async/{taskId}/cancel")
    @Operation(
        summary = "Cancel async preview queue declined diagnostics export task",
        description = "Cancel a queued/running declined preview queue asynchronous CSV export task."
    )
    public ResponseEntity<PreviewQueueDeclinedExportAsyncStatusResponseDto> cancelQueueDeclinedCsvAsyncExportTask(
        @PathVariable String taskId
    ) {
        QueueDeclinedExportAsyncTask existing;
        synchronized (queueDeclinedExportAsyncTaskLock) {
            refreshQueueDeclinedExportAsyncTasksLifecycleLocked();
            existing = queueDeclinedExportAsyncTasks.get(taskId);
        }
        if (existing == null) {
            auditQueueDeclinedAsyncExportCancelSingle(taskId, null);
            return ResponseEntity.notFound().build();
        }
        if (existing.isTerminal()) {
            auditQueueDeclinedAsyncExportCancelSingle(taskId, existing);
            return ResponseEntity.status(409).body(toQueueDeclinedExportAsyncStatusResponse(existing));
        }
        QueueDeclinedExportAsyncTask updated = queueDeclinedExportAsyncTasks.computeIfPresent(taskId, (key, current) -> {
            if (current.isTerminal()) {
                return current;
            }
            return current.cancel("Cancelled by user", resolveAuditUsername());
        });
        if (updated == null) {
            auditQueueDeclinedAsyncExportCancelSingle(taskId, null);
            return ResponseEntity.notFound().build();
        }
        auditQueueDeclinedAsyncExportCancelSingle(taskId, updated);
        return ResponseEntity.ok(toQueueDeclinedExportAsyncStatusResponse(updated));
    }

    @GetMapping(value = "/queue/declined/export-async/{taskId}/download", produces = "text/csv")
    @Operation(
        summary = "Download async preview queue declined diagnostics export result",
        description = "Download CSV for a completed declined preview queue asynchronous export task."
    )
    public ResponseEntity<byte[]> downloadQueueDeclinedCsvAsyncExportResult(
        @PathVariable String taskId
    ) {
        QueueDeclinedExportAsyncTask task;
        synchronized (queueDeclinedExportAsyncTaskLock) {
            refreshQueueDeclinedExportAsyncTasksLifecycleLocked();
            task = queueDeclinedExportAsyncTasks.get(taskId);
        }
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        if (task.status() != QueueDeclinedExportAsyncStatus.COMPLETED
            || task.csvContent() == null
            || task.filename() == null) {
            return ResponseEntity.status(409).build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(task.filename(), StandardCharsets.UTF_8)
            .build());
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        auditQueueDeclinedAsyncExportDownloaded(task);
        return ResponseEntity.ok()
            .headers(headers)
            .body(task.csvContent().clone());
    }

    @PostMapping("/queue/declined/requeue")
    @Operation(
        summary = "Requeue declined preview queue decisions",
        description = "Requeue declined queueing decisions matched by declined diagnostics filters."
    )
    public ResponseEntity<PreviewQueueDeclinedRequeueResponseDto> requeueDeclinedQueueTasks(
        @RequestParam(defaultValue = "200") int limit,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String forceRequired,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) Integer windowHours,
        @RequestParam(defaultValue = "true") boolean force
    ) {
        int safeLimit = clamp(
            limit <= 0 ? DEFAULT_QUEUE_DECLINED_ACTION_LIMIT : limit,
            1,
            MAX_QUEUE_DECLINED_ACTION_LIMIT
        );
        String safeCategoryFilter = normalizeQueueDeclinedCategoryFilter(category);
        String safeForceRequiredFilter = normalizeQueueDeclinedForceRequiredFilter(forceRequired);
        String safeQueryFilter = normalizeQueueDiagnosticsQueryFilter(query);
        Integer safeWindowHoursFilter = normalizeQueueDeclinedWindowHours(windowHours);
        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = previewQueueService.declinedSnapshot(safeLimit);
        List<PreviewQueueDeclinedItemDto> candidates = mapQueueDeclinedItems(
            snapshot,
            safeCategoryFilter,
            safeForceRequiredFilter,
            safeQueryFilter,
            safeWindowHoursFilter
        )
            .stream()
            .limit(safeLimit)
            .toList();
        Map<UUID, Document> candidateDocumentsById = documentRepository.findAllById(
            candidates.stream()
                .map(PreviewQueueDeclinedItemDto::documentId)
                .filter(Objects::nonNull)
                .toList()
        ).stream().collect(Collectors.toMap(Document::getId, document -> document));

        int queued = 0;
        int skipped = 0;
        int failed = 0;
        List<PreviewQueueDeclinedRequeueItemDto> results = new ArrayList<>();
        for (PreviewQueueDeclinedItemDto candidate : candidates) {
            UUID documentId = candidate.documentId();
            if (documentId == null) {
                failed += 1;
                results.add(buildFailedPreviewQueueDeclinedRequeueItem(
                    candidate,
                    "Missing document id in declined queue item"
                ));
                continue;
            }
            try {
                PreviewQueueService.PreviewQueueStatus status = previewQueueService.enqueue(documentId, force);
                boolean wasQueued = status != null && status.queued();
                if (wasQueued) {
                    queued += 1;
                } else {
                    skipped += 1;
                }
                results.add(buildPreviewQueueDeclinedRequeueItem(
                    candidate,
                    candidateDocumentsById.get(documentId),
                    wasQueued ? "QUEUED" : "SKIPPED",
                    status != null ? status.message() : "No queue status returned",
                    status
                ));
            } catch (Exception ex) {
                failed += 1;
                results.add(buildPreviewQueueDeclinedRequeueItem(
                    candidate,
                    candidateDocumentsById.get(documentId),
                    "FAILED",
                    ex.getMessage(),
                    null
                ));
            }
        }

        PreviewQueueDeclinedRequeueResponseDto payload = new PreviewQueueDeclinedRequeueResponseDto(
            safeCategoryFilter,
            safeForceRequiredFilter,
            safeQueryFilter,
            safeWindowHoursFilter,
            safeLimit,
            force,
            candidates.size(),
            queued,
            skipped,
            failed,
            results
        );
        auditQueueDeclinedRequeue(payload);
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/queue/declined/requeue/dry-run")
    @Operation(
        summary = "Dry-run declined preview queue requeue",
        description = "Evaluate declined queueing decisions matched by filters and return estimated queue/skip/failure outcomes without mutating queue state."
    )
    public ResponseEntity<PreviewQueueDeclinedRequeueDryRunResponseDto> dryRunRequeueDeclinedQueueTasks(
        @RequestParam(defaultValue = "200") int limit,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String forceRequired,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) Integer windowHours,
        @RequestParam(defaultValue = "true") boolean force
    ) {
        PreviewQueueDeclinedRequeueDryRunResponseDto payload = computeQueueDeclinedRequeueDryRun(
            limit,
            category,
            forceRequired,
            query,
            windowHours,
            force
        );
        auditQueueDeclinedRequeueDryRun(payload);
        return ResponseEntity.ok(payload);
    }

    @GetMapping(value = "/queue/declined/requeue/dry-run/export", produces = "text/csv")
    @Operation(
        summary = "Export declined preview queue requeue dry-run as CSV",
        description = "Export dry-run estimated queue/skip/failure outcomes and reason breakdown without mutating queue state."
    )
    public ResponseEntity<byte[]> exportDryRunRequeueDeclinedQueueTasks(
        @RequestParam(defaultValue = "200") int limit,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String forceRequired,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) Integer windowHours,
        @RequestParam(defaultValue = "true") boolean force
    ) {
        PreviewQueueDeclinedRequeueDryRunResponseDto payload = computeQueueDeclinedRequeueDryRun(
            limit,
            category,
            forceRequired,
            query,
            windowHours,
            force
        );
        String csv = buildQueueDeclinedRequeueDryRunCsv(payload);
        String filename = String.format(
            "preview_queue_declined_requeue_dry_run_%s.csv",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.add("X-Preview-Queue-Declined-Requeue-Dry-Run-Count", String.valueOf(payload.results().size()));
        auditQueueDeclinedRequeueDryRunExport(payload);
        return ResponseEntity.ok()
            .headers(headers)
            .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/queue/declined/requeue/dry-run/export-async")
    @Operation(
        summary = "Start async export for declined preview queue requeue dry-run",
        description = "Start an asynchronous CSV export task for declined requeue dry-run diagnostics."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202",
            description = "Async dry-run export task accepted (new or deduplicated active task).",
            headers = {
                @Header(
                    name = HttpHeaders.LOCATION,
                    description = "Task status endpoint.",
                    schema = @Schema(type = "string")
                )
            }
        )
    })
    public ResponseEntity<PreviewQueueDeclinedRequeueDryRunExportAsyncCreateResponseDto> startQueueDeclinedRequeueDryRunCsvAsyncExport(
        @RequestBody(required = false) PreviewQueueDeclinedRequeueDryRunExportAsyncRequestDto request
    ) {
        PreviewQueueDeclinedRequeueDryRunExportAsyncRequestDto requestSnapshot = copyQueueDeclinedRequeueDryRunExportAsyncRequest(request);
        PreviewQueueDeclinedRequeueDryRunExportAsyncRequestDto normalizedRequest = normalizeQueueDeclinedRequeueDryRunExportAsyncRequest(requestSnapshot);
        QueueDeclinedRequeueDryRunExportAsyncTask activeTask = findActiveQueueDeclinedRequeueDryRunExportAsyncTask(normalizedRequest);
        if (activeTask != null) {
            auditQueueDeclinedRequeueDryRunAsyncExportStartDedupHit(activeTask);
            return acceptedQueueDeclinedRequeueDryRunExportAsyncCreateResponse(new PreviewQueueDeclinedRequeueDryRunExportAsyncCreateResponseDto(
                activeTask.taskId(),
                activeTask.status().name(),
                activeTask.createdAt(),
                activeTask.timeoutAt(),
                activeTask.expiresAt(),
                activeTask.createdBy(),
                activeTask.updatedBy(),
                true,
                activeTask.taskId(),
                "Reused active queue declined requeue dry-run async export task with same filters"
            ), activeTask.taskId());
        }

        QueueDeclinedRequeueDryRunExportAsyncTask task = createQueueDeclinedRequeueDryRunExportAsyncTask(normalizedRequest);
        auditQueueDeclinedRequeueDryRunAsyncExportStarted(task);
        CompletableFuture.runAsync(() -> runQueueDeclinedRequeueDryRunExportAsyncTask(task, normalizedRequest));

        return acceptedQueueDeclinedRequeueDryRunExportAsyncCreateResponse(new PreviewQueueDeclinedRequeueDryRunExportAsyncCreateResponseDto(
            task.taskId(),
            task.status().name(),
            task.createdAt(),
            task.timeoutAt(),
            task.expiresAt(),
            task.createdBy(),
            task.updatedBy(),
            false,
            null,
            "Queue declined requeue dry-run async export task started"
        ), task.taskId());
    }

    @GetMapping("/queue/declined/requeue/dry-run/export-async")
    @Operation(
        summary = "List async declined preview queue requeue dry-run export tasks",
        description = "List recent asynchronous declined requeue dry-run CSV export tasks."
    )
    public ResponseEntity<PreviewQueueDeclinedRequeueDryRunExportAsyncListResponseDto> listQueueDeclinedRequeueDryRunCsvAsyncExportTasks(
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Integer maxItems,
        @RequestParam(defaultValue = "0") Integer skipCount,
        @RequestParam(required = false) String status
    ) {
        int boundedMaxItems = resolveListMaxItems(maxItems, limit, MAX_QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_ASYNC_LIST_LIMIT);
        int normalizedSkipCount = Math.max(skipCount != null ? skipCount : 0, 0);
        QueueDeclinedRequeueDryRunExportAsyncStatus statusFilter = parseQueueDeclinedRequeueDryRunExportAsyncStatus(status);
        QueueDeclinedRequeueDryRunExportAsyncListPage page = listQueueDeclinedRequeueDryRunExportAsyncTasks(
            normalizedSkipCount,
            boundedMaxItems,
            statusFilter
        );
        boolean hasMoreItems = normalizedSkipCount + page.items().size() < page.totalItems();
        return ResponseEntity.ok(new PreviewQueueDeclinedRequeueDryRunExportAsyncListResponseDto(
            page.items().size(),
            new PreviewTaskCenterPagingDto(
                normalizedSkipCount,
                boundedMaxItems,
                page.totalItems(),
                hasMoreItems
            ),
            page.items()
        ));
    }

    @GetMapping("/queue/declined/requeue/dry-run/export-async/summary")
    @Operation(
        summary = "Declined requeue dry-run async export task summary",
        description = "Return aggregate declined requeue dry-run async export task counts by status."
    )
    public ResponseEntity<PreviewQueueDeclinedRequeueDryRunExportAsyncSummaryResponseDto> summarizeQueueDeclinedRequeueDryRunCsvAsyncExportTasks(
        @RequestParam(required = false) String status
    ) {
        QueueDeclinedRequeueDryRunExportAsyncStatus statusFilter = parseQueueDeclinedRequeueDryRunExportAsyncStatus(status);
        return ResponseEntity.ok(buildQueueDeclinedRequeueDryRunExportAsyncSummary(statusFilter));
    }

    @PostMapping("/queue/declined/requeue/dry-run/export-async/cleanup")
    @Operation(
        summary = "Cleanup declined requeue dry-run async export tasks",
        description = "Delete terminal declined requeue dry-run async export tasks."
    )
    public ResponseEntity<PreviewQueueDeclinedRequeueDryRunExportAsyncCleanupResponseDto> cleanupQueueDeclinedRequeueDryRunCsvAsyncExportTasks(
        @RequestParam(required = false) String status
    ) {
        QueueDeclinedRequeueDryRunExportAsyncStatus statusFilter = parseQueueDeclinedRequeueDryRunExportAsyncStatus(status);
        if (statusFilter == QueueDeclinedRequeueDryRunExportAsyncStatus.QUEUED
            || statusFilter == QueueDeclinedRequeueDryRunExportAsyncStatus.RUNNING) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "status filter only supports terminal states: COMPLETED, CANCELLED, FAILED, TIMED_OUT, EXPIRED"
            );
        }

        long deletedCount;
        int remainingCount;
        List<QueueDeclinedRequeueDryRunExportAsyncTask> deletedTasks = new ArrayList<>();
        synchronized (queueDeclinedRequeueDryRunExportAsyncTaskLock) {
            refreshQueueDeclinedRequeueDryRunExportAsyncTasksLifecycleLocked();
            deletedCount = cleanupQueueDeclinedRequeueDryRunExportAsyncTasksLocked(statusFilter, deletedTasks);
            remainingCount = queueDeclinedRequeueDryRunExportAsyncTasks.size();
        }

        String statusFilterName = statusFilter != null ? statusFilter.name() : null;
        String message = deletedCount > 0
            ? statusFilterName == null
                ? String.format("Deleted %d terminal declined requeue dry-run async export tasks", deletedCount)
                : String.format("Deleted %d declined requeue dry-run async export tasks with status %s", deletedCount, statusFilterName)
            : statusFilterName == null
                ? "No terminal declined requeue dry-run async export tasks to delete"
                : String.format("No declined requeue dry-run async export tasks with status %s to delete", statusFilterName);

        auditQueueDeclinedRequeueDryRunAsyncExportCleanup(statusFilterName, deletedCount, remainingCount, deletedTasks);

        return ResponseEntity.ok(new PreviewQueueDeclinedRequeueDryRunExportAsyncCleanupResponseDto(
            deletedCount,
            remainingCount,
            statusFilterName,
            message
        ));
    }

    @PostMapping("/queue/declined/requeue/dry-run/export-async/cancel-active")
    @Operation(
        summary = "Cancel active declined requeue dry-run async export tasks",
        description = "Cancel queued/running declined requeue dry-run async export tasks."
    )
    public ResponseEntity<PreviewQueueDeclinedRequeueDryRunExportAsyncCancelActiveResponseDto> cancelActiveQueueDeclinedRequeueDryRunCsvAsyncExportTasks(
        @RequestParam(required = false) String status
    ) {
        QueueDeclinedRequeueDryRunExportAsyncStatus statusFilter = parseQueueDeclinedRequeueDryRunExportAsyncStatus(status);
        if (statusFilter != null && !isActiveQueueDeclinedRequeueDryRunExportAsyncStatus(statusFilter)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "status filter only supports active states: QUEUED, RUNNING"
            );
        }

        long[] cancelledCount = {0L};
        List<QueueDeclinedRequeueDryRunExportAsyncTask> cancelledTasks = new ArrayList<>();
        int remainingActiveCount;
        synchronized (queueDeclinedRequeueDryRunExportAsyncTaskLock) {
            refreshQueueDeclinedRequeueDryRunExportAsyncTasksLifecycleLocked();
            List<String> taskIds = new ArrayList<>(queueDeclinedRequeueDryRunExportAsyncTasks.keySet());
            for (String taskId : taskIds) {
                queueDeclinedRequeueDryRunExportAsyncTasks.computeIfPresent(taskId, (key, current) -> {
                    if (current == null || !isActiveQueueDeclinedRequeueDryRunExportAsyncStatus(current.status())) {
                        return current;
                    }
                    if (statusFilter != null && current.status() != statusFilter) {
                        return current;
                    }
                    cancelledCount[0] += 1;
                    QueueDeclinedRequeueDryRunExportAsyncTask cancelledTask =
                        current.cancel("Cancelled by active-task bulk request", resolveAuditUsername());
                    cancelledTasks.add(cancelledTask);
                    return cancelledTask;
                });
            }
            queueDeclinedRequeueDryRunExportAsyncTaskOrder.removeIf(taskId ->
                !queueDeclinedRequeueDryRunExportAsyncTasks.containsKey(taskId)
            );
            remainingActiveCount = countActiveQueueDeclinedRequeueDryRunExportAsyncTasksLocked();
        }

        String statusFilterName = statusFilter != null ? statusFilter.name() : null;
        String message = cancelledCount[0] > 0
            ? statusFilterName == null
                ? String.format("Cancelled %d active declined requeue dry-run async export tasks", cancelledCount[0])
                : String.format("Cancelled %d declined requeue dry-run async export tasks with status %s", cancelledCount[0], statusFilterName)
            : statusFilterName == null
                ? "No active declined requeue dry-run async export tasks to cancel"
                : String.format("No declined requeue dry-run async export tasks with status %s to cancel", statusFilterName);

        auditQueueDeclinedRequeueDryRunAsyncExportCancelActive(
            statusFilterName,
            cancelledCount[0],
            remainingActiveCount,
            cancelledTasks
        );

        return ResponseEntity.ok(new PreviewQueueDeclinedRequeueDryRunExportAsyncCancelActiveResponseDto(
            cancelledCount[0],
            remainingActiveCount,
            statusFilterName,
            message
        ));
    }

    @GetMapping("/queue/declined/requeue/dry-run/export-async/{taskId}")
    @Operation(
        summary = "Get declined requeue dry-run async export task status",
        description = "Get status/details for a declined requeue dry-run asynchronous CSV export task."
    )
    public ResponseEntity<PreviewQueueDeclinedRequeueDryRunExportAsyncStatusResponseDto> getQueueDeclinedRequeueDryRunCsvAsyncExportTaskStatus(
        @PathVariable String taskId
    ) {
        QueueDeclinedRequeueDryRunExportAsyncTask task;
        synchronized (queueDeclinedRequeueDryRunExportAsyncTaskLock) {
            refreshQueueDeclinedRequeueDryRunExportAsyncTasksLifecycleLocked();
            task = queueDeclinedRequeueDryRunExportAsyncTasks.get(taskId);
        }
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toQueueDeclinedRequeueDryRunExportAsyncStatusResponse(task));
    }

    @PostMapping("/queue/declined/requeue/dry-run/export-async/{taskId}/retry")
    @Operation(
        summary = "Retry declined requeue dry-run async export task",
        description = "Retry a terminal declined requeue dry-run asynchronous CSV export task using the original filter snapshot."
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "202",
            description = "Retry accepted (new or deduplicated active task).",
            headers = {
                @Header(
                    name = HttpHeaders.LOCATION,
                    description = "Task status endpoint.",
                    schema = @Schema(type = "string")
                )
            }
        ),
        @ApiResponse(responseCode = "404", description = "Source task not found."),
        @ApiResponse(responseCode = "409", description = "Source task is active and cannot be retried.")
    })
    public ResponseEntity<PreviewQueueDeclinedRequeueDryRunExportAsyncCreateResponseDto> retryQueueDeclinedRequeueDryRunCsvAsyncExportTask(
        @PathVariable String taskId
    ) {
        QueueDeclinedRequeueDryRunExportAsyncTask sourceTask;
        synchronized (queueDeclinedRequeueDryRunExportAsyncTaskLock) {
            refreshQueueDeclinedRequeueDryRunExportAsyncTasksLifecycleLocked();
            sourceTask = queueDeclinedRequeueDryRunExportAsyncTasks.get(taskId);
        }
        if (sourceTask == null) {
            auditQueueDeclinedRequeueDryRunAsyncExportRetry(taskId, null, null, "NOT_FOUND");
            return ResponseEntity.notFound().build();
        }
        if (isActiveQueueDeclinedRequeueDryRunExportAsyncStatus(sourceTask.status())) {
            auditQueueDeclinedRequeueDryRunAsyncExportRetry(taskId, sourceTask, null, sourceTask.status().name());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }

        PreviewQueueDeclinedRequeueDryRunExportAsyncRequestDto retryRequest =
            toQueueDeclinedRequeueDryRunExportAsyncRequest(sourceTask);
        QueueDeclinedRequeueDryRunExportAsyncTask activeTask = findActiveQueueDeclinedRequeueDryRunExportAsyncTask(retryRequest);
        if (activeTask != null) {
            auditQueueDeclinedRequeueDryRunAsyncExportRetry(taskId, sourceTask, activeTask, activeTask.status().name());
            return acceptedQueueDeclinedRequeueDryRunExportAsyncCreateResponse(new PreviewQueueDeclinedRequeueDryRunExportAsyncCreateResponseDto(
                activeTask.taskId(),
                activeTask.status().name(),
                activeTask.createdAt(),
                activeTask.timeoutAt(),
                activeTask.expiresAt(),
                activeTask.createdBy(),
                activeTask.updatedBy(),
                true,
                activeTask.taskId(),
                "Reused active queue declined requeue dry-run async export task with same filters"
            ), activeTask.taskId());
        }

        QueueDeclinedRequeueDryRunExportAsyncTask retryTask = createQueueDeclinedRequeueDryRunExportAsyncTask(retryRequest);
        auditQueueDeclinedRequeueDryRunAsyncExportRetry(taskId, sourceTask, retryTask, retryTask.status().name());
        CompletableFuture.runAsync(() -> runQueueDeclinedRequeueDryRunExportAsyncTask(retryTask, retryRequest));

        return acceptedQueueDeclinedRequeueDryRunExportAsyncCreateResponse(new PreviewQueueDeclinedRequeueDryRunExportAsyncCreateResponseDto(
            retryTask.taskId(),
            retryTask.status().name(),
            retryTask.createdAt(),
            retryTask.timeoutAt(),
            retryTask.expiresAt(),
            retryTask.createdBy(),
            retryTask.updatedBy(),
            false,
            null,
            "Retried queue declined requeue dry-run async export task"
        ), retryTask.taskId());
    }

    @PostMapping("/queue/declined/requeue/dry-run/export-async/retry-terminal")
    @Operation(
        summary = "Retry terminal declined requeue dry-run async export tasks",
        description = "Retry terminal declined requeue dry-run asynchronous CSV export tasks in bulk."
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
        @ApiResponse(responseCode = "400", description = "Invalid status filter.")
    })
    public ResponseEntity<PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalResponseDto> retryTerminalQueueDeclinedRequeueDryRunCsvAsyncExportTasks(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "20") int limit
    ) {
        QueueDeclinedRequeueDryRunExportAsyncStatus statusFilter = parseQueueDeclinedRequeueDryRunExportAsyncStatus(status);
        if (statusFilter != null && isActiveQueueDeclinedRequeueDryRunExportAsyncStatus(statusFilter)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "status filter only supports terminal states: COMPLETED, CANCELLED, FAILED, TIMED_OUT, EXPIRED"
            );
        }

        int boundedLimit = clamp(
            limit <= 0 ? DEFAULT_QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT : limit,
            1,
            MAX_QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT
        );
        List<QueueDeclinedRequeueDryRunExportAsyncTask> candidates =
            listQueueDeclinedRequeueDryRunExportAsyncRetryTerminalCandidates(boundedLimit, statusFilter);

        int requested = candidates.size();
        int retried = 0;
        int reused = 0;
        int skipped = 0;
        int failed = 0;
        List<PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalItemDto> results = new ArrayList<>();
        List<QueueDeclinedRequeueDryRunExportAsyncTask> retriedSourceTasks = new ArrayList<>();
        List<QueueDeclinedRequeueDryRunExportAsyncTask> retriedTasks = new ArrayList<>();

        for (QueueDeclinedRequeueDryRunExportAsyncTask sourceTask : candidates) {
            if (sourceTask == null || !sourceTask.isTerminal()) {
                skipped += 1;
                results.add(new PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalItemDto(
                    sourceTask != null ? sourceTask.taskId() : null,
                    null,
                    sourceTask != null && sourceTask.status() != null ? sourceTask.status().name() : "UNKNOWN",
                    "SKIPPED",
                    "Source task is missing or not terminal"
                ));
                continue;
            }

            try {
                PreviewQueueDeclinedRequeueDryRunExportAsyncRequestDto retryRequest =
                    toQueueDeclinedRequeueDryRunExportAsyncRequest(sourceTask);
                QueueDeclinedRequeueDryRunExportAsyncTask activeTask =
                    findActiveQueueDeclinedRequeueDryRunExportAsyncTask(retryRequest);
                boolean deduplicated = activeTask != null;
                QueueDeclinedRequeueDryRunExportAsyncTask retryTask = deduplicated
                    ? activeTask
                    : createQueueDeclinedRequeueDryRunExportAsyncTask(retryRequest);
                retried += 1;
                if (deduplicated) {
                    reused += 1;
                }
                retriedSourceTasks.add(sourceTask);
                retriedTasks.add(retryTask);
                results.add(new PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalItemDto(
                    sourceTask.taskId(),
                    retryTask.taskId(),
                    sourceTask.status().name(),
                    deduplicated ? "REUSED" : "RETRIED",
                    deduplicated ? "Reused active async export task with same filters" : "Retried terminal async export task"
                ));
                if (!deduplicated) {
                    CompletableFuture.runAsync(() -> runQueueDeclinedRequeueDryRunExportAsyncTask(retryTask, retryRequest));
                }
            } catch (Exception ex) {
                failed += 1;
                results.add(new PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalItemDto(
                    sourceTask.taskId(),
                    null,
                    sourceTask.status().name(),
                    "FAILED",
                    resolveErrorMessage(ex)
                ));
            }
        }

        String statusFilterName = statusFilter != null ? statusFilter.name() : "FAILED|CANCELLED|TIMED_OUT|EXPIRED";
        String message = requested == 0
            ? "No terminal declined requeue dry-run async export tasks matched retry filters"
            : String.format(
                "Retried %d/%d terminal declined requeue dry-run async export tasks (reused=%d, skipped=%d, failed=%d)",
                retried,
                requested,
                reused,
                skipped,
                failed
            );

        auditQueueDeclinedRequeueDryRunAsyncExportRetryBulk(
            statusFilterName,
            boundedLimit,
            requested,
            retried,
            reused,
            skipped,
            failed,
            retriedSourceTasks,
            retriedTasks
        );

        return acceptedQueueDeclinedRequeueDryRunExportAsyncRetryTerminalResponse(new PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalResponseDto(
            requested,
            retried,
            reused,
            skipped,
            failed,
            boundedLimit,
            statusFilterName,
            message,
            results
        ));
    }

    @PostMapping("/queue/declined/requeue/dry-run/export-async/retry-terminal/dry-run")
    @Operation(
        summary = "Dry-run terminal declined requeue dry-run async export retries",
        description = "Estimate terminal declined requeue dry-run asynchronous CSV export tasks that can be retried in bulk."
    )
    public ResponseEntity<PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunResponseDto> dryRunRetryTerminalQueueDeclinedRequeueDryRunCsvAsyncExportTasks(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "20") int limit
    ) {
        QueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunComputation dryRun =
            computeQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRun(status, limit);

        auditQueueDeclinedRequeueDryRunAsyncExportRetryBulkDryRun(
            dryRun.statusFilterName(),
            dryRun.limit(),
            dryRun.requested(),
            dryRun.retryable(),
            dryRun.skipped(),
            dryRun.sourceTasks(),
            dryRun.reasonBreakdown()
        );

        return ResponseEntity.ok(new PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunResponseDto(
            dryRun.requested(),
            dryRun.retryable(),
            dryRun.skipped(),
            dryRun.limit(),
            dryRun.statusFilterName(),
            dryRun.message(),
            dryRun.results(),
            dryRun.reasonBreakdown()
        ));
    }

    @GetMapping(value = "/queue/declined/requeue/dry-run/export-async/retry-terminal/dry-run/export", produces = "text/csv")
    @Operation(
        summary = "Export retry-terminal dry-run diagnostics CSV for declined requeue dry-run async export tasks",
        description = "Export dry-run retry-terminal diagnostics rows and reason breakdown as CSV."
    )
    public ResponseEntity<byte[]> exportDryRunRetryTerminalQueueDeclinedRequeueDryRunCsvAsyncExportTasks(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "20") int limit
    ) {
        QueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunComputation dryRun =
            computeQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRun(status, limit);
        String csv = buildQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunCsv(dryRun);
        String filename = String.format(
            "preview_queue_declined_requeue_dry_run_async_retry_dry_run_%s.csv",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.add("X-Preview-Queue-Declined-Requeue-Dry-Run-Retry-Dry-Run-Count", String.valueOf(dryRun.results().size()));
        auditQueueDeclinedRequeueDryRunAsyncExportRetryBulkDryRunExport(
            dryRun.statusFilterName(),
            dryRun.limit(),
            dryRun.requested(),
            dryRun.retryable(),
            dryRun.skipped(),
            dryRun.reasonBreakdown()
        );
        return ResponseEntity.ok()
            .headers(headers)
            .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/queue/declined/requeue/dry-run/export-async/retry-terminal/by-task-ids")
    @Operation(
        summary = "Retry selected terminal declined requeue dry-run async export tasks",
        description = "Retry terminal declined requeue dry-run asynchronous CSV export tasks by source task ids."
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
    public ResponseEntity<PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalResponseDto> retrySelectedTerminalQueueDeclinedRequeueDryRunCsvAsyncExportTasks(
        @RequestBody(required = false) PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalByTaskIdsRequestDto request
    ) {
        List<String> sourceTaskIds = normalizeQueueDeclinedRequeueDryRunRetrySourceTaskIds(
            request != null ? request.sourceTaskIds() : null
        );
        synchronized (queueDeclinedRequeueDryRunExportAsyncTaskLock) {
            refreshQueueDeclinedRequeueDryRunExportAsyncTasksLifecycleLocked();
        }

        int requested = sourceTaskIds.size();
        int retried = 0;
        int reused = 0;
        int skipped = 0;
        int failed = 0;
        List<PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalItemDto> results = new ArrayList<>();
        List<QueueDeclinedRequeueDryRunExportAsyncTask> retriedSourceTasks = new ArrayList<>();
        List<QueueDeclinedRequeueDryRunExportAsyncTask> retriedTasks = new ArrayList<>();

        for (String sourceTaskId : sourceTaskIds) {
            QueueDeclinedRequeueDryRunExportAsyncTask sourceTask = queueDeclinedRequeueDryRunExportAsyncTasks.get(sourceTaskId);
            if (sourceTask == null) {
                skipped += 1;
                results.add(new PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalItemDto(
                    sourceTaskId,
                    null,
                    "NOT_FOUND",
                    "SKIPPED",
                    "Source task not found"
                ));
                continue;
            }
            if (!sourceTask.isTerminal()) {
                skipped += 1;
                results.add(new PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalItemDto(
                    sourceTask.taskId(),
                    null,
                    sourceTask.status().name(),
                    "SKIPPED",
                    "Source task is not terminal"
                ));
                continue;
            }
            try {
                PreviewQueueDeclinedRequeueDryRunExportAsyncRequestDto retryRequest =
                    toQueueDeclinedRequeueDryRunExportAsyncRequest(sourceTask);
                QueueDeclinedRequeueDryRunExportAsyncTask activeTask =
                    findActiveQueueDeclinedRequeueDryRunExportAsyncTask(retryRequest);
                boolean deduplicated = activeTask != null;
                QueueDeclinedRequeueDryRunExportAsyncTask retryTask = deduplicated
                    ? activeTask
                    : createQueueDeclinedRequeueDryRunExportAsyncTask(retryRequest);
                retried += 1;
                if (deduplicated) {
                    reused += 1;
                }
                retriedSourceTasks.add(sourceTask);
                retriedTasks.add(retryTask);
                results.add(new PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalItemDto(
                    sourceTask.taskId(),
                    retryTask.taskId(),
                    sourceTask.status().name(),
                    deduplicated ? "REUSED" : "RETRIED",
                    deduplicated ? "Reused active async export task with same filters" : "Retried selected terminal async export task"
                ));
                if (!deduplicated) {
                    CompletableFuture.runAsync(() -> runQueueDeclinedRequeueDryRunExportAsyncTask(retryTask, retryRequest));
                }
            } catch (Exception ex) {
                failed += 1;
                results.add(new PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalItemDto(
                    sourceTask.taskId(),
                    null,
                    sourceTask.status().name(),
                    "FAILED",
                    resolveErrorMessage(ex)
                ));
            }
        }

        String message = requested == 0
            ? "No source task ids provided for terminal declined requeue dry-run async export retry"
            : String.format(
                "Retried %d/%d selected terminal declined requeue dry-run async export tasks (reused=%d, skipped=%d, failed=%d)",
                retried,
                requested,
                reused,
                skipped,
                failed
            );

        auditQueueDeclinedRequeueDryRunAsyncExportRetryBulkSelected(
            requested,
            retried,
            reused,
            skipped,
            failed,
            retriedSourceTasks,
            retriedTasks
        );

        return acceptedQueueDeclinedRequeueDryRunExportAsyncRetryTerminalResponse(new PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalResponseDto(
            requested,
            retried,
            reused,
            skipped,
            failed,
            requested,
            "BY_TASK_IDS",
            message,
            results
        ));
    }

    @PostMapping("/queue/declined/requeue/dry-run/export-async/{taskId}/cancel")
    @Operation(
        summary = "Cancel declined requeue dry-run async export task",
        description = "Cancel a queued/running declined requeue dry-run asynchronous CSV export task."
    )
    public ResponseEntity<PreviewQueueDeclinedRequeueDryRunExportAsyncStatusResponseDto> cancelQueueDeclinedRequeueDryRunCsvAsyncExportTask(
        @PathVariable String taskId
    ) {
        QueueDeclinedRequeueDryRunExportAsyncTask existing;
        synchronized (queueDeclinedRequeueDryRunExportAsyncTaskLock) {
            refreshQueueDeclinedRequeueDryRunExportAsyncTasksLifecycleLocked();
            existing = queueDeclinedRequeueDryRunExportAsyncTasks.get(taskId);
        }
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (existing.isTerminal()) {
            return ResponseEntity.status(409).body(toQueueDeclinedRequeueDryRunExportAsyncStatusResponse(existing));
        }
        QueueDeclinedRequeueDryRunExportAsyncTask updated = queueDeclinedRequeueDryRunExportAsyncTasks.computeIfPresent(taskId, (key, current) -> {
            if (current == null || current.isTerminal()) {
                return current;
            }
            return current.cancel("Cancelled by user request", resolveAuditUsername());
        });
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        auditQueueDeclinedRequeueDryRunAsyncExportCancelSingle(taskId, updated);
        return ResponseEntity.ok(toQueueDeclinedRequeueDryRunExportAsyncStatusResponse(updated));
    }

    @GetMapping(value = "/queue/declined/requeue/dry-run/export-async/{taskId}/download", produces = "text/csv")
    @Operation(
        summary = "Download declined requeue dry-run async export task result",
        description = "Download generated CSV for a completed declined requeue dry-run asynchronous export task."
    )
    public ResponseEntity<byte[]> downloadQueueDeclinedRequeueDryRunCsvAsyncExportTask(
        @PathVariable String taskId
    ) {
        QueueDeclinedRequeueDryRunExportAsyncTask task;
        synchronized (queueDeclinedRequeueDryRunExportAsyncTaskLock) {
            refreshQueueDeclinedRequeueDryRunExportAsyncTasksLifecycleLocked();
            task = queueDeclinedRequeueDryRunExportAsyncTasks.get(taskId);
        }
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        if (task.status() != QueueDeclinedRequeueDryRunExportAsyncStatus.COMPLETED
            || task.csvContent() == null
            || task.filename() == null) {
            return ResponseEntity.status(409).build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(task.filename(), StandardCharsets.UTF_8)
            .build());
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        auditQueueDeclinedRequeueDryRunAsyncExportDownloaded(task);
        return ResponseEntity.ok()
            .headers(headers)
            .body(task.csvContent().clone());
    }

    @PostMapping("/queue/declined/clear")
    @Operation(
        summary = "Clear declined preview queue decisions",
        description = "Clear declined queueing decisions matched by declined diagnostics filters."
    )
    public ResponseEntity<PreviewQueueDeclinedClearResponseDto> clearDeclinedQueueTasks(
        @RequestParam(defaultValue = "200") int limit,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String forceRequired,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) Integer windowHours
    ) {
        int safeLimit = clamp(
            limit <= 0 ? DEFAULT_QUEUE_DECLINED_ACTION_LIMIT : limit,
            1,
            MAX_QUEUE_DECLINED_ACTION_LIMIT
        );
        String safeCategoryFilter = normalizeQueueDeclinedCategoryFilter(category);
        String safeForceRequiredFilter = normalizeQueueDeclinedForceRequiredFilter(forceRequired);
        String safeQueryFilter = normalizeQueueDiagnosticsQueryFilter(query);
        Integer safeWindowHoursFilter = normalizeQueueDeclinedWindowHours(windowHours);
        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = previewQueueService.declinedSnapshot(safeLimit);
        List<PreviewQueueDeclinedItemDto> candidates = mapQueueDeclinedItems(
            snapshot,
            safeCategoryFilter,
            safeForceRequiredFilter,
            safeQueryFilter,
            safeWindowHoursFilter
        )
            .stream()
            .limit(safeLimit)
            .toList();

        int cleared = 0;
        int skipped = 0;
        int failed = 0;
        List<PreviewQueueDeclinedClearItemDto> results = new ArrayList<>();
        for (PreviewQueueDeclinedItemDto candidate : candidates) {
            UUID documentId = candidate.documentId();
            if (documentId == null) {
                failed += 1;
                results.add(new PreviewQueueDeclinedClearItemDto(
                    null,
                    candidate.category(),
                    "FAILED",
                    "Missing document id in declined queue item"
                ));
                continue;
            }
            try {
                boolean removed = previewQueueService.clearDeclined(documentId);
                if (removed) {
                    cleared += 1;
                } else {
                    skipped += 1;
                }
                results.add(new PreviewQueueDeclinedClearItemDto(
                    documentId,
                    candidate.category(),
                    removed ? "CLEARED" : "SKIPPED",
                    removed ? "Declined queue item cleared" : "Declined queue item not found"
                ));
            } catch (Exception ex) {
                failed += 1;
                results.add(new PreviewQueueDeclinedClearItemDto(
                    documentId,
                    candidate.category(),
                    "FAILED",
                    ex.getMessage()
                ));
            }
        }

        PreviewQueueDeclinedClearResponseDto payload = new PreviewQueueDeclinedClearResponseDto(
            safeCategoryFilter,
            safeForceRequiredFilter,
            safeQueryFilter,
            safeWindowHoursFilter,
            safeLimit,
            candidates.size(),
            cleared,
            skipped,
            failed,
            results
        );
        auditQueueDeclinedClear(payload);
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/cad-failover")
    @Operation(
        summary = "CAD failover diagnostics",
        description = "Show CAD preview endpoint chain and recent per-endpoint success/failure counters."
    )
    public ResponseEntity<PreviewCadFailoverDiagnosticsDto> getCadFailoverDiagnostics() {
        List<String> endpoints = cadRenderEndpointRegistry.resolveEndpoints();
        List<PreviewCadFailoverEndpointStatsDto> stats = cadRenderFailoverTracker.snapshot(endpoints).stream()
            .map(entry -> new PreviewCadFailoverEndpointStatsDto(
                entry.endpoint(),
                entry.successCount(),
                entry.failureCount(),
                entry.lastSuccessAt(),
                entry.lastFailureAt(),
                entry.lastFailureReason(),
                entry.consecutiveFailureCount(),
                entry.circuitState(),
                entry.circuitOpenUntil(),
                entry.lastCircuitOpenedAt(),
                entry.halfOpenInFlight()
            ))
            .toList();

        PreviewCadFailoverDiagnosticsDto payload = new PreviewCadFailoverDiagnosticsDto(
            cadRenderEndpointRegistry.isCadPreviewEnabled(),
            !endpoints.isEmpty(),
            cadRenderFailoverTracker.isCircuitBreakerEnabled(),
            cadRenderFailoverTracker.getCircuitFailureThreshold(),
            cadRenderFailoverTracker.getCircuitOpenMs(),
            cadRenderFailoverTracker.getHalfOpenTrialTimeoutMs(),
            endpoints,
            stats
        );
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/failures/summary")
    @Operation(
        summary = "Preview failure summary",
        description = "Summarize recent preview failures with sample confidence and top failure reasons."
    )
    public ResponseEntity<PreviewFailureSummaryDto> getFailureSummary(
        @RequestParam(defaultValue = "500") int sampleLimit,
        @RequestParam(defaultValue = "7") int days
    ) {
        int requestedLimit = sampleLimit <= 0 ? DEFAULT_SUMMARY_SAMPLE_LIMIT : sampleLimit;
        int safeLimit = clamp(requestedLimit, 1, MAX_SUMMARY_SAMPLE_LIMIT);
        int safeDays = normalizeWindowDays(days);
        Pageable pageable = PageRequest.of(0, safeLimit);
        LocalDateTime updatedSince = resolveUpdatedSince(safeDays);
        var statuses = failureStatuses();

        long totalFailures = documentRepository.countPreviewFailuresByWindow(statuses, updatedSince);
        List<Document> samples = documentRepository.findRecentPreviewFailuresByWindow(statuses, updatedSince, pageable).getContent();

        List<PreviewFailureSampleDto> normalizedSamples = samples.stream()
            .map(this::toPreviewFailureSample)
            .toList();

        Map<String, Long> statusCounts = new HashMap<>();
        Map<String, Long> categoryCounts = new HashMap<>();
        Map<ReasonKey, Long> reasonCounts = new HashMap<>();

        for (PreviewFailureSampleDto sample : normalizedSamples) {
            String status = normalizedUpperOrDefault(sample.previewStatus(), "UNKNOWN");
            String category = normalizedUpperOrDefault(sample.previewFailureCategory(), "UNKNOWN");
            String reason = normalizeReason(sample.previewFailureReason());
            boolean retryable = PreviewFailureClassifier.CATEGORY_TEMPORARY.equals(category);

            statusCounts.merge(status, 1L, Long::sum);
            categoryCounts.merge(category, 1L, Long::sum);
            reasonCounts.merge(new ReasonKey(reason, category, retryable), 1L, Long::sum);
        }

        int sampledFailures = normalizedSamples.size();
        boolean sampleTruncated = totalFailures > sampledFailures;

        List<PreviewFailureStatusCountDto> statusPayload = statusCounts.entrySet().stream()
            .map(entry -> new PreviewFailureStatusCountDto(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingLong(PreviewFailureStatusCountDto::count).reversed()
                .thenComparing(PreviewFailureStatusCountDto::status))
            .toList();

        List<PreviewFailureCategoryCountDto> categoryPayload = categoryCounts.entrySet().stream()
            .map(entry -> new PreviewFailureCategoryCountDto(
                entry.getKey(),
                PreviewFailureClassifier.CATEGORY_TEMPORARY.equals(entry.getKey()),
                entry.getValue()))
            .sorted(Comparator.comparingLong(PreviewFailureCategoryCountDto::count).reversed()
                .thenComparing(PreviewFailureCategoryCountDto::category))
            .toList();

        List<PreviewFailureReasonCountDto> reasonPayload = reasonCounts.entrySet().stream()
            .sorted(
                Comparator.<Map.Entry<ReasonKey, Long>>comparingLong(Map.Entry::getValue)
                    .reversed()
                    .thenComparing(entry -> entry.getKey().reason())
            )
            .limit(10)
            .map(entry -> new PreviewFailureReasonCountDto(
                entry.getKey().reason(),
                entry.getKey().category(),
                entry.getKey().retryable(),
                entry.getValue()))
            .toList();

        PreviewFailureSummaryDto payload = new PreviewFailureSummaryDto(
            totalFailures,
            sampledFailures,
            safeLimit,
            safeDays,
            updatedSince,
            sampleTruncated,
            sampleTruncated ? "LOW" : "HIGH",
            sampleTruncated ? "sample_truncated" : "sample_complete",
            statusPayload,
            categoryPayload,
            reasonPayload
        );
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/failures/ledger")
    @Operation(
        summary = "Preview failure ledger",
        description = "List persisted preview failure ledger entries with failed timestamp, count, and last reason."
    )
    public ResponseEntity<PreviewFailureLedgerDiagnosticsDto> getFailureLedger(
        @RequestParam(defaultValue = "100") int limit,
        @RequestParam(defaultValue = "30") int days
    ) {
        int requestedLimit = limit <= 0 ? DEFAULT_FAILURE_LEDGER_LIST_LIMIT : limit;
        int safeLimit = clamp(requestedLimit, 1, MAX_FAILURE_LEDGER_LIST_LIMIT);
        int safeDays = normalizeWindowDays(days);
        LocalDateTime failedSince = resolveUpdatedSince(safeDays);
        Pageable pageable = PageRequest.of(
            0,
            safeLimit,
            Sort.by(
                Sort.Order.desc("previewFailedAt").nullsLast(),
                Sort.Order.desc("previewLastUpdated").nullsLast(),
                Sort.Order.desc("lastModifiedDate").nullsLast()
            )
        );

        long totalEntries = documentRepository.countPreviewFailureLedgerEntries(failedSince);
        List<Document> samples = documentRepository.findPreviewFailureLedgerEntries(failedSince, pageable).getContent();
        List<PreviewFailureLedgerItemDto> items = samples.stream()
            .map(PreviewFailureLedgerItemDto::from)
            .toList();

        PreviewFailureLedgerDiagnosticsDto payload = new PreviewFailureLedgerDiagnosticsDto(
            totalEntries,
            items.size(),
            safeLimit,
            safeDays,
            failedSince,
            totalEntries > items.size(),
            items
        );
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/failures/ledger/{documentId}/reset")
    @Operation(
        summary = "Reset preview failure ledger for one document",
        description = "Reset persisted preview failure ledger fields for one document without replaying preview."
    )
    public ResponseEntity<PreviewFailureLedgerResetItemDto> resetFailureLedger(
        @PathVariable UUID documentId
    ) {
        PreviewFailureLedgerResetItemDto item = resetFailureLedgerInternal(documentId);
        PreviewFailureLedgerResetBatchResponseDto auditPayload = new PreviewFailureLedgerResetBatchResponseDto(
            1,
            1,
            "RESET".equals(item.outcome()) ? 1 : 0,
            "FAILED".equals(item.outcome()) ? 1 : 0,
            List.of(item)
        );
        auditFailureLedgerReset(auditPayload);
        return ResponseEntity.ok(item);
    }

    @PostMapping("/failures/ledger/reset-batch")
    @Operation(
        summary = "Reset preview failure ledger in batch",
        description = "Reset persisted preview failure ledger fields for a batch of document ids."
    )
    public ResponseEntity<PreviewFailureLedgerResetBatchResponseDto> resetFailureLedgerBatch(
        @RequestBody PreviewFailureLedgerResetBatchRequestDto request
    ) {
        List<UUID> rawIds = request != null && request.documentIds() != null
            ? request.documentIds()
            : List.of();
        List<UUID> deduplicatedIds = rawIds.stream()
            .filter(id -> id != null)
            .distinct()
            .limit(MAX_FAILURE_LEDGER_RESET_BATCH_LIMIT)
            .toList();
        List<PreviewFailureLedgerResetItemDto> results = deduplicatedIds.stream()
            .map(this::resetFailureLedgerInternal)
            .toList();

        int reset = (int) results.stream().filter(item -> "RESET".equals(item.outcome())).count();
        int failed = (int) results.stream().filter(item -> "FAILED".equals(item.outcome())).count();
        PreviewFailureLedgerResetBatchResponseDto payload = new PreviewFailureLedgerResetBatchResponseDto(
            rawIds.size(),
            deduplicatedIds.size(),
            reset,
            failed,
            results
        );
        auditFailureLedgerReset(payload);
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/failures/ledger/reset-by-filter")
    @Operation(
        summary = "Reset preview failure ledger by filter",
        description = "Reset preview failure ledger entries matched by reason/category/retryable in selected diagnostics window."
    )
    public ResponseEntity<PreviewFailureLedgerResetByFilterResponseDto> resetFailureLedgerByFilter(
        @RequestBody PreviewFailureLedgerResetByFilterRequestDto request
    ) {
        String normalizedReason = normalizeReason(request != null ? request.reason() : null);
        String normalizedCategory = normalizedUpperOrDefault(request != null ? request.category() : null, "ANY");
        Boolean retryable = request != null ? request.retryable() : null;
        int requestedMaxDocuments = request != null && request.maxDocuments() != null
            ? request.maxDocuments()
            : DEFAULT_FAILURE_LEDGER_RESET_FILTER_LIMIT;
        int safeMaxDocuments = clamp(requestedMaxDocuments, 1, MAX_FAILURE_LEDGER_RESET_FILTER_LIMIT);
        int requestedDays = request != null && request.days() != null ? request.days() : DEFAULT_WINDOW_DAYS;
        int safeDays = normalizeWindowDays(requestedDays);
        LocalDateTime failedSince = resolveUpdatedSince(safeDays);

        long totalCandidates = documentRepository.countPreviewFailureLedgerEntries(failedSince);
        FailureLedgerFilterScanResult filterScanResult = collectFailureLedgerDocumentsByFilter(
            failedSince,
            normalizedReason,
            normalizedCategory,
            retryable,
            safeMaxDocuments
        );
        List<Document> matchedDocuments = filterScanResult.matchedDocuments();
        int scanned = filterScanResult.scanned();
        boolean truncated = filterScanResult.truncated();

        List<PreviewFailureLedgerResetItemDto> results = matchedDocuments.stream()
            .map(document -> resetFailureLedgerInternal(document.getId()))
            .toList();

        int matched = matchedDocuments.size();
        int reset = (int) results.stream().filter(item -> "RESET".equals(item.outcome())).count();
        int skipped = (int) results.stream().filter(item -> "SKIPPED".equals(item.outcome())).count();
        int failed = (int) results.stream().filter(item -> "FAILED".equals(item.outcome())).count();

        PreviewFailureLedgerResetByFilterResponseDto payload = new PreviewFailureLedgerResetByFilterResponseDto(
            normalizedReason,
            normalizedCategory,
            retryable,
            safeDays,
            safeMaxDocuments,
            totalCandidates,
            scanned,
            matched,
            truncated,
            reset,
            skipped,
            failed,
            results
        );
        auditFailureLedgerResetByFilter(payload);
        return ResponseEntity.ok(payload);
    }

    @GetMapping(value = "/failures/ledger/export", produces = "text/csv")
    @Operation(
        summary = "Export preview failure ledger",
        description = "Export preview failure ledger entries in selected diagnostics window."
    )
    public ResponseEntity<String> exportFailureLedgerCsv(
        @RequestParam(defaultValue = "500") int limit,
        @RequestParam(defaultValue = "30") int days
    ) {
        int safeLimit = clamp(
            limit <= 0 ? DEFAULT_FAILURE_LEDGER_EXPORT_LIMIT : limit,
            1,
            MAX_FAILURE_LEDGER_EXPORT_LIMIT
        );
        int safeDays = normalizeWindowDays(days);
        LocalDateTime failedSince = resolveUpdatedSince(safeDays);
        Pageable pageable = PageRequest.of(
            0,
            safeLimit,
            Sort.by(
                Sort.Order.desc("previewFailedAt").nullsLast(),
                Sort.Order.desc("previewLastUpdated").nullsLast()
            )
        );
        List<PreviewFailureLedgerItemDto> items = documentRepository.findPreviewFailureLedgerEntries(failedSince, pageable)
            .getContent()
            .stream()
            .map(PreviewFailureLedgerItemDto::from)
            .toList();
        String csv = buildFailureLedgerCsv(items);

        String filename = "preview_failure_ledger_" + Instant.now().toEpochMilli() + ".csv";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        headers.add("X-Preview-Failure-Ledger-Count", String.valueOf(items.size()));
        auditFailureLedgerExport(safeDays, safeLimit, items.size());
        return ResponseEntity.ok()
            .headers(headers)
            .body(csv);
    }

    @GetMapping("/renditions/summary")
    @Operation(
        summary = "Preview rendition summary",
        description = "Summarize rendition status distribution and top failure reasons within a diagnostics window."
    )
    public ResponseEntity<PreviewRenditionSummaryDto> getRenditionSummary(
        @RequestParam(defaultValue = "500") int sampleLimit,
        @RequestParam(defaultValue = "7") int days
    ) {
        int requestedLimit = sampleLimit <= 0 ? DEFAULT_SUMMARY_SAMPLE_LIMIT : sampleLimit;
        int safeLimit = clamp(requestedLimit, 1, MAX_SUMMARY_SAMPLE_LIMIT);
        int safeDays = normalizeWindowDays(days);
        LocalDateTime updatedSince = resolveUpdatedSince(safeDays);

        Specification<Document> windowSpec = buildRenditionSummarySpecification(updatedSince);
        Pageable pageable = buildRenditionSummaryPageable(safeLimit);

        long totalDocuments = documentRepository.count(windowSpec);
        List<Document> samples = documentRepository.findAll(windowSpec, pageable).getContent();

        Map<String, Long> statusCounts = new HashMap<>();
        for (String status : RENDITION_SUMMARY_STATUSES) {
            statusCounts.put(status, 0L);
        }
        Map<String, Long> reasonCounts = new HashMap<>();

        for (Document document : samples) {
            String renditionStatus = deriveRenditionStatus(document);
            statusCounts.merge(renditionStatus, 1L, Long::sum);

            if (shouldAggregateFailureReason(renditionStatus)) {
                reasonCounts.merge(normalizeReason(resolveEffectivePreviewFailureReason(document)), 1L, Long::sum);
            }
        }

        List<PreviewRenditionStatusCountDto> statusPayload = RENDITION_SUMMARY_STATUSES.stream()
            .map(status -> new PreviewRenditionStatusCountDto(status, statusCounts.getOrDefault(status, 0L)))
            .toList();

        List<PreviewRenditionFailureReasonCountDto> reasonPayload = reasonCounts.entrySet().stream()
            .sorted(
                Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                    .reversed()
                    .thenComparing(Map.Entry::getKey)
            )
            .limit(MAX_TOP_FAILURE_REASONS)
            .map(entry -> new PreviewRenditionFailureReasonCountDto(entry.getKey(), entry.getValue()))
            .toList();

        int sampledDocuments = samples.size();
        PreviewRenditionSummaryDto payload = new PreviewRenditionSummaryDto(
            totalDocuments,
            sampledDocuments,
            safeLimit,
            safeDays,
            updatedSince,
            totalDocuments > sampledDocuments,
            statusPayload,
            reasonPayload
        );
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/renditions/resources")
    @Operation(
        summary = "Preview rendition resources diagnostics",
        description = "List resource-level rendition diagnostics in the selected diagnostics window."
    )
    public ResponseEntity<PreviewRenditionResourcesDiagnosticsDto> getRenditionResourcesDiagnostics(
        @RequestParam(defaultValue = "100") int limit,
        @RequestParam(defaultValue = "7") int days
    ) {
        int requestedLimit = limit <= 0 ? DEFAULT_RENDITION_RESOURCE_LIMIT : limit;
        int safeLimit = clamp(requestedLimit, 1, MAX_RENDITION_RESOURCE_LIMIT);
        int safeDays = normalizeWindowDays(days);
        LocalDateTime updatedSince = resolveUpdatedSince(safeDays);

        Specification<Document> windowSpec = buildRenditionSummarySpecification(updatedSince);
        Pageable pageable = buildRenditionSummaryPageable(safeLimit);

        long totalDocuments = documentRepository.count(windowSpec);
        List<Document> samples = documentRepository.findAll(windowSpec, pageable).getContent();

        List<PreviewRenditionResourceItemDto> items = samples.stream()
            .map(document -> {
                EffectivePreviewSemantics previewSemantics = resolveEffectivePreviewSemantics(document);
                String normalizedMimeType = normalizeMimeType(document.getMimeType());
                String renditionStatus = deriveRenditionStatus(document);
                boolean includeFailureDetails = shouldAggregateFailureReason(renditionStatus);
                return new PreviewRenditionResourceItemDto(
                    document.getId(),
                    document.getName(),
                    document.getPath(),
                    normalizedMimeType,
                    previewSemantics.status(),
                    renditionStatus,
                    includeFailureDetails ? previewSemantics.failureReason() : null,
                    includeFailureDetails ? previewSemantics.failureCategory() : null,
                    document.getPreviewLastUpdated()
                );
            })
            .toList();

        PreviewRenditionResourcesDiagnosticsDto payload = new PreviewRenditionResourcesDiagnosticsDto(
            totalDocuments,
            items.size(),
            safeLimit,
            safeDays,
            updatedSince,
            totalDocuments > items.size(),
            items
        );
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/renditions/resources/export")
    @Operation(
        summary = "Export preview rendition resources diagnostics",
        description = "Export resource-level rendition diagnostics entries as CSV."
    )
    public ResponseEntity<byte[]> exportRenditionResourcesCsv(
        @RequestParam(defaultValue = "500") int limit,
        @RequestParam(defaultValue = "7") int days
    ) {
        int safeDays = normalizeWindowDays(days);
        int safeLimit = normalizeRenditionResourcesExportLimit(limit);
        List<Document> samples = collectRenditionResourcesForExport(safeDays, safeLimit);

        String csv = buildRenditionResourcesCsv(samples);
        String filename = String.format(
            "preview_rendition_resources_%s.csv",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(filename, StandardCharsets.UTF_8)
            .build());
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.add("X-Preview-Rendition-Resource-Count", String.valueOf(samples.size()));

        auditRenditionResourceExport(safeDays, safeLimit, samples.size());
        return ResponseEntity.ok()
            .headers(headers)
            .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/renditions/resources/export-async")
    @Operation(
        summary = "Start async preview rendition resources diagnostics export",
        description = "Start an asynchronous CSV export task for rendition resources diagnostics."
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
    public ResponseEntity<PreviewRenditionResourcesExportAsyncCreateResponseDto> startRenditionResourcesCsvAsyncExport(
        @RequestBody(required = false) PreviewRenditionResourcesExportAsyncRequestDto request
    ) {
        PreviewRenditionResourcesExportAsyncRequestDto requestSnapshot = normalizeRenditionResourcesExportAsyncRequest(
            copyRenditionResourcesExportRequest(request)
        );
        RenditionResourcesExportAsyncTask task;
        boolean deduplicated;
        String deduplicatedFromTaskId;
        synchronized (renditionResourcesExportAsyncTaskLock) {
            refreshRenditionResourcesExportAsyncTasksLifecycleLocked();
            RenditionResourcesExportAsyncTask activeTask =
                findActiveRenditionResourcesExportAsyncTaskByRequestLocked(requestSnapshot);
            deduplicated = activeTask != null;
            task = deduplicated ? activeTask : createRenditionResourcesExportAsyncTask(requestSnapshot);
            deduplicatedFromTaskId = deduplicated && activeTask != null ? activeTask.taskId() : null;
        }

        if (!deduplicated) {
            CompletableFuture.runAsync(() -> runRenditionResourcesExportAsyncTask(task));
        }

        return acceptedRenditionResourcesExportAsyncCreateResponse(toRenditionResourcesExportAsyncCreateResponse(
            task,
            deduplicated,
            deduplicatedFromTaskId,
            deduplicated
                ? "Reused active async export task with same filters"
                : "Started async export task"
        ), task.taskId());
    }

    @PostMapping("/renditions/resources/export-async/{taskId}/retry")
    @Operation(
        summary = "Retry async preview rendition resources diagnostics export task",
        description = "Retry a terminal rendition resources async export task with its original request snapshot."
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
    public ResponseEntity<PreviewRenditionResourcesExportAsyncCreateResponseDto> retryRenditionResourcesCsvAsyncExportTask(
        @PathVariable String taskId
    ) {
        PreviewRenditionResourcesExportAsyncRequestDto retryRequest;
        RenditionResourcesExportAsyncTask retryTask;
        boolean deduplicated;
        String deduplicatedFromTaskId;
        synchronized (renditionResourcesExportAsyncTaskLock) {
            refreshRenditionResourcesExportAsyncTasksLifecycleLocked();
            RenditionResourcesExportAsyncTask sourceTask = renditionResourcesExportAsyncTasks.get(taskId);
            if (sourceTask == null) {
                return ResponseEntity.notFound().build();
            }
            if (!sourceTask.isTerminal()) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "task is not terminal and cannot be retried"
                );
            }
            PreviewRenditionResourcesExportAsyncRequestDto sourceRequest = renditionResourcesExportAsyncTaskRequests.get(taskId);
            retryRequest = normalizeRenditionResourcesExportAsyncRequest(sourceRequest);
            RenditionResourcesExportAsyncTask activeTask =
                findActiveRenditionResourcesExportAsyncTaskByRequestLocked(retryRequest);
            deduplicated = activeTask != null;
            retryTask = deduplicated ? activeTask : createRenditionResourcesExportAsyncTask(retryRequest);
            deduplicatedFromTaskId = deduplicated && activeTask != null ? activeTask.taskId() : null;
        }

        if (!deduplicated) {
            CompletableFuture.runAsync(() -> runRenditionResourcesExportAsyncTask(retryTask));
        }
        return acceptedRenditionResourcesExportAsyncCreateResponse(toRenditionResourcesExportAsyncCreateResponse(
            retryTask,
            deduplicated,
            deduplicatedFromTaskId,
            deduplicated
                ? "Reused active async export task with same filters"
                : "Retried terminal async export task"
        ), retryTask.taskId());
    }

    @PostMapping("/renditions/resources/export-async/retry-terminal")
    @Operation(
        summary = "Retry terminal async preview rendition resources diagnostics export tasks",
        description = "Retry terminal rendition resources asynchronous CSV export tasks in bulk."
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
        @ApiResponse(responseCode = "400", description = "Invalid status filter.")
    })
    public ResponseEntity<PreviewRenditionResourcesExportAsyncRetryTerminalResponseDto> retryTerminalRenditionResourcesCsvAsyncExportTasks(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "20") int limit
    ) {
        RenditionResourcesExportAsyncStatus statusFilter = parseRenditionResourcesExportAsyncStatus(status);
        if (statusFilter != null && isActiveRenditionResourcesExportAsyncStatus(statusFilter)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "status filter only supports terminal states: COMPLETED, CANCELLED, FAILED, TIMED_OUT, EXPIRED"
            );
        }

        int boundedLimit = clamp(
            limit <= 0 ? DEFAULT_RENDITION_RESOURCE_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT : limit,
            1,
            MAX_RENDITION_RESOURCE_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT
        );
        List<RenditionResourcesExportAsyncTask> candidates = listRenditionResourcesExportAsyncRetryTerminalCandidates(
            boundedLimit,
            statusFilter
        );

        int requested = candidates.size();
        int retried = 0;
        int reused = 0;
        int skipped = 0;
        int failed = 0;
        List<PreviewRenditionResourcesExportAsyncRetryTerminalItemDto> results = new ArrayList<>();

        for (RenditionResourcesExportAsyncTask candidate : candidates) {
            if (candidate == null || !candidate.isTerminal()) {
                skipped += 1;
                results.add(new PreviewRenditionResourcesExportAsyncRetryTerminalItemDto(
                    candidate != null ? candidate.taskId() : null,
                    null,
                    candidate != null && candidate.status() != null ? candidate.status().name() : "UNKNOWN",
                    "SKIPPED",
                    "Source task is missing or not terminal"
                ));
                continue;
            }

            try {
                RenditionResourcesExportAsyncTask sourceTask;
                PreviewRenditionResourcesExportAsyncRequestDto retryRequest;
                RenditionResourcesExportAsyncTask retryTask;
                boolean deduplicated;
                synchronized (renditionResourcesExportAsyncTaskLock) {
                    refreshRenditionResourcesExportAsyncTasksLifecycleLocked();
                    sourceTask = renditionResourcesExportAsyncTasks.get(candidate.taskId());
                    if (sourceTask == null || !sourceTask.isTerminal()) {
                        sourceTask = null;
                    }
                    if (sourceTask == null) {
                        throw new IllegalStateException("Source task is missing or not terminal");
                    }
                    PreviewRenditionResourcesExportAsyncRequestDto sourceRequest =
                        renditionResourcesExportAsyncTaskRequests.get(sourceTask.taskId());
                    retryRequest = normalizeRenditionResourcesExportAsyncRequest(sourceRequest);
                    RenditionResourcesExportAsyncTask activeTask =
                        findActiveRenditionResourcesExportAsyncTaskByRequestLocked(retryRequest);
                    deduplicated = activeTask != null;
                    retryTask = deduplicated ? activeTask : createRenditionResourcesExportAsyncTask(retryRequest);
                }

                retried += 1;
                if (deduplicated) {
                    reused += 1;
                }
                results.add(new PreviewRenditionResourcesExportAsyncRetryTerminalItemDto(
                    sourceTask.taskId(),
                    retryTask.taskId(),
                    sourceTask.status().name(),
                    deduplicated ? "REUSED" : "RETRIED",
                    deduplicated ? "Reused active async export task with same filters" : "Retried terminal async export task"
                ));
                if (!deduplicated) {
                    CompletableFuture.runAsync(() -> runRenditionResourcesExportAsyncTask(retryTask));
                }
            } catch (Exception ex) {
                failed += 1;
                results.add(new PreviewRenditionResourcesExportAsyncRetryTerminalItemDto(
                    candidate.taskId(),
                    null,
                    candidate.status().name(),
                    "FAILED",
                    resolveErrorMessage(ex)
                ));
            }
        }

        String statusFilterName = statusFilter != null ? statusFilter.name() : "FAILED|CANCELLED|COMPLETED|TIMED_OUT|EXPIRED";
        String message = requested == 0
            ? "No terminal async preview rendition resources export tasks matched retry filters"
            : String.format(
                "Retried %d/%d terminal async preview rendition resources export tasks (reused=%d, skipped=%d, failed=%d)",
                retried,
                requested,
                reused,
                skipped,
                failed
            );

        return acceptedRenditionResourcesExportAsyncRetryTerminalResponse(new PreviewRenditionResourcesExportAsyncRetryTerminalResponseDto(
            requested,
            retried,
            reused,
            skipped,
            failed,
            boundedLimit,
            statusFilterName,
            message,
            results
        ));
    }

    @PostMapping("/renditions/resources/export-async/retry-terminal/dry-run")
    @Operation(
        summary = "Dry-run retry terminal rendition resources async export tasks",
        description = "Evaluate terminal rendition resources asynchronous CSV export tasks for retry eligibility without executing retries."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Dry-run computed."),
        @ApiResponse(responseCode = "400", description = "Invalid status filter.")
    })
    public ResponseEntity<PreviewRenditionResourcesExportAsyncRetryTerminalDryRunResponseDto> dryRunRetryTerminalRenditionResourcesCsvAsyncExportTasks(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "20") int limit
    ) {
        RenditionResourcesExportAsyncStatus statusFilter = parseRenditionResourcesExportAsyncStatus(status);
        if (statusFilter != null && isActiveRenditionResourcesExportAsyncStatus(statusFilter)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "status filter only supports terminal states: COMPLETED, CANCELLED, FAILED, TIMED_OUT, EXPIRED"
            );
        }
        int boundedLimit = clamp(
            limit <= 0 ? DEFAULT_RENDITION_RESOURCE_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT : limit,
            1,
            MAX_RENDITION_RESOURCE_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT
        );
        RenditionResourcesExportAsyncRetryTerminalDryRunComputation dryRun =
            computeRenditionResourcesExportAsyncRetryTerminalDryRun(statusFilter, boundedLimit);
        return ResponseEntity.ok(new PreviewRenditionResourcesExportAsyncRetryTerminalDryRunResponseDto(
            dryRun.requested(),
            dryRun.retryable(),
            dryRun.skipped(),
            dryRun.limit(),
            dryRun.statusFilterName(),
            dryRun.message(),
            dryRun.results(),
            dryRun.reasonBreakdown()
        ));
    }

    @GetMapping(value = "/renditions/resources/export-async/retry-terminal/dry-run/export", produces = "text/csv")
    @Operation(
        summary = "Export dry-run retry terminal rendition resources async export diagnostics CSV",
        description = "Export dry-run retry-terminal diagnostics rows and reason breakdown as CSV."
    )
    public ResponseEntity<byte[]> exportDryRunRetryTerminalRenditionResourcesCsvAsyncExportTasks(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "20") int limit
    ) {
        RenditionResourcesExportAsyncStatus statusFilter = parseRenditionResourcesExportAsyncStatus(status);
        if (statusFilter != null && isActiveRenditionResourcesExportAsyncStatus(statusFilter)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "status filter only supports terminal states: COMPLETED, CANCELLED, FAILED, TIMED_OUT, EXPIRED"
            );
        }
        int boundedLimit = clamp(
            limit <= 0 ? DEFAULT_RENDITION_RESOURCE_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT : limit,
            1,
            MAX_RENDITION_RESOURCE_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT
        );
        RenditionResourcesExportAsyncRetryTerminalDryRunComputation dryRun =
            computeRenditionResourcesExportAsyncRetryTerminalDryRun(statusFilter, boundedLimit);
        String csv = buildRenditionResourcesExportAsyncRetryTerminalDryRunCsv(dryRun);
        String filename = String.format(
            Locale.ROOT,
            "preview_rendition_resources_async_retry_dry_run_%s.csv",
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
        );
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(
            ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
        );
        headers.add("X-Preview-Rendition-Resources-Retry-Dry-Run-Count", String.valueOf(dryRun.results().size()));
        return ResponseEntity.ok()
            .headers(headers)
            .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/renditions/resources/export-async/retry-terminal/by-task-ids")
    @Operation(
        summary = "Retry selected terminal async preview rendition resources diagnostics export tasks",
        description = "Retry terminal rendition resources asynchronous CSV export tasks by source task ids."
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
    public ResponseEntity<PreviewRenditionResourcesExportAsyncRetryTerminalResponseDto> retrySelectedTerminalRenditionResourcesCsvAsyncExportTasks(
        @RequestBody(required = false) PreviewRenditionResourcesExportAsyncRetryTerminalByTaskIdsRequestDto request
    ) {
        List<String> sourceTaskIds = normalizeRenditionResourcesRetrySourceTaskIds(
            request != null ? request.sourceTaskIds() : null
        );
        int requested = sourceTaskIds.size();
        int retried = 0;
        int reused = 0;
        int skipped = 0;
        int failed = 0;
        List<PreviewRenditionResourcesExportAsyncRetryTerminalItemDto> results = new ArrayList<>();

        for (String sourceTaskId : sourceTaskIds) {
            try {
                RenditionResourcesExportAsyncTask sourceTask;
                PreviewRenditionResourcesExportAsyncRequestDto retryRequest;
                RenditionResourcesExportAsyncTask retryTask;
                boolean deduplicated;
                String sourceStatusName;
                synchronized (renditionResourcesExportAsyncTaskLock) {
                    refreshRenditionResourcesExportAsyncTasksLifecycleLocked();
                    sourceTask = renditionResourcesExportAsyncTasks.get(sourceTaskId);
                    if (sourceTask == null) {
                        skipped += 1;
                        results.add(new PreviewRenditionResourcesExportAsyncRetryTerminalItemDto(
                            sourceTaskId,
                            null,
                            "NOT_FOUND",
                            "SKIPPED",
                            "Source task not found"
                        ));
                        continue;
                    }
                    if (!sourceTask.isTerminal()) {
                        skipped += 1;
                        results.add(new PreviewRenditionResourcesExportAsyncRetryTerminalItemDto(
                            sourceTask.taskId(),
                            null,
                            sourceTask.status().name(),
                            "SKIPPED",
                            "Source task is not terminal"
                        ));
                        continue;
                    }
                    sourceStatusName = sourceTask.status().name();
                    PreviewRenditionResourcesExportAsyncRequestDto sourceRequest =
                        renditionResourcesExportAsyncTaskRequests.get(sourceTask.taskId());
                    retryRequest = normalizeRenditionResourcesExportAsyncRequest(sourceRequest);
                    RenditionResourcesExportAsyncTask activeTask =
                        findActiveRenditionResourcesExportAsyncTaskByRequestLocked(retryRequest);
                    deduplicated = activeTask != null;
                    retryTask = deduplicated ? activeTask : createRenditionResourcesExportAsyncTask(retryRequest);
                }

                retried += 1;
                if (deduplicated) {
                    reused += 1;
                }
                results.add(new PreviewRenditionResourcesExportAsyncRetryTerminalItemDto(
                    sourceTaskId,
                    retryTask.taskId(),
                    sourceStatusName,
                    deduplicated ? "REUSED" : "RETRIED",
                    deduplicated ? "Reused active async export task with same filters" : "Retried selected terminal async export task"
                ));
                if (!deduplicated) {
                    CompletableFuture.runAsync(() -> runRenditionResourcesExportAsyncTask(retryTask));
                }
            } catch (Exception ex) {
                failed += 1;
                results.add(new PreviewRenditionResourcesExportAsyncRetryTerminalItemDto(
                    sourceTaskId,
                    null,
                    "UNKNOWN",
                    "FAILED",
                    resolveErrorMessage(ex)
                ));
            }
        }

        String message = requested == 0
            ? "No selected terminal async preview rendition resources export tasks to retry"
            : String.format(
                "Retried %d/%d selected terminal async preview rendition resources export tasks (reused=%d, skipped=%d, failed=%d)",
                retried,
                requested,
                reused,
                skipped,
                failed
            );

        return acceptedRenditionResourcesExportAsyncRetryTerminalResponse(new PreviewRenditionResourcesExportAsyncRetryTerminalResponseDto(
            requested,
            retried,
            reused,
            skipped,
            failed,
            requested,
            "BY_TASK_IDS",
            message,
            results
        ));
    }

    @GetMapping("/renditions/resources/export-async")
    @Operation(
        summary = "List async preview rendition resources diagnostics export tasks",
        description = "List recent asynchronous rendition resources CSV export tasks."
    )
    public ResponseEntity<PreviewRenditionResourcesExportAsyncListResponseDto> listRenditionResourcesCsvAsyncExportTasks(
        @RequestParam(required = false) Integer limit,
        @RequestParam(required = false) Integer maxItems,
        @RequestParam(defaultValue = "0") Integer skipCount,
        @RequestParam(required = false) String status
    ) {
        int boundedMaxItems = resolveListMaxItems(maxItems, limit, MAX_RENDITION_RESOURCE_EXPORT_ASYNC_LIST_LIMIT);
        int normalizedSkipCount = Math.max(skipCount != null ? skipCount : 0, 0);
        RenditionResourcesExportAsyncStatus statusFilter = parseRenditionResourcesExportAsyncStatus(status);
        RenditionResourcesExportAsyncListPage page = listRenditionResourcesExportAsyncTasks(
            normalizedSkipCount,
            boundedMaxItems,
            statusFilter
        );
        boolean hasMoreItems = normalizedSkipCount + page.items().size() < page.totalItems();
        return ResponseEntity.ok(new PreviewRenditionResourcesExportAsyncListResponseDto(
            page.items().size(),
            new PreviewTaskCenterPagingDto(
                normalizedSkipCount,
                boundedMaxItems,
                page.totalItems(),
                hasMoreItems
            ),
            page.items()
        ));
    }

    @GetMapping("/renditions/resources/export-async/summary")
    @Operation(
        summary = "Preview rendition resources async export task summary",
        description = "Return aggregate async export task counts by status and lifecycle class."
    )
    public ResponseEntity<PreviewRenditionResourcesExportAsyncSummaryResponseDto> summarizeRenditionResourcesCsvAsyncExportTasks(
        @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(
            AsyncTaskSummaryAdapters.toPreviewRenditions(
                summarizeRenditionResourcesCsvAsyncExportTaskSnapshot(status)
            )
        );
    }

    public AsyncTaskSummarySnapshot summarizeRenditionResourcesCsvAsyncExportTaskSnapshot(String status) {
        RenditionResourcesExportAsyncStatus statusFilter = parseRenditionResourcesExportAsyncStatus(status);
        return buildRenditionResourcesExportAsyncSummarySnapshot(statusFilter);
    }

    @PostMapping("/renditions/resources/export-async/cleanup")
    @Operation(
        summary = "Cleanup preview rendition resources async export tasks",
        description = "Delete terminal async export tasks by default, or delete a specific terminal status."
    )
    public ResponseEntity<PreviewRenditionResourcesExportAsyncCleanupResponseDto> cleanupRenditionResourcesCsvAsyncExportTasks(
        @RequestParam(required = false) String status
    ) {
        RenditionResourcesExportAsyncStatus statusFilter = parseRenditionResourcesExportAsyncStatus(status);
        if (statusFilter == RenditionResourcesExportAsyncStatus.QUEUED
            || statusFilter == RenditionResourcesExportAsyncStatus.RUNNING) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "status filter only supports terminal states: COMPLETED, CANCELLED, FAILED, TIMED_OUT, EXPIRED"
            );
        }

        long deletedCount;
        int remainingCount;
        synchronized (renditionResourcesExportAsyncTaskLock) {
            refreshRenditionResourcesExportAsyncTasksLifecycleLocked();
            deletedCount = cleanupRenditionResourcesExportAsyncTasksLocked(statusFilter);
            remainingCount = renditionResourcesExportAsyncTasks.size();
        }

        String statusFilterName = statusFilter != null ? statusFilter.name() : null;
        String message = deletedCount > 0
            ? statusFilterName == null
                ? String.format("Deleted %d terminal async preview rendition resources export tasks", deletedCount)
                : String.format("Deleted %d async preview rendition resources export tasks with status %s", deletedCount, statusFilterName)
            : statusFilterName == null
                ? "No terminal async preview rendition resources export tasks to delete"
                : String.format("No async preview rendition resources export tasks with status %s to delete", statusFilterName);

        return ResponseEntity.ok(new PreviewRenditionResourcesExportAsyncCleanupResponseDto(
            deletedCount,
            remainingCount,
            statusFilterName,
            message
        ));
    }

    @PostMapping("/renditions/resources/export-async/cancel-active")
    @Operation(
        summary = "Cancel active preview rendition resources async export tasks",
        description = "Cancel queued/running async export tasks, optionally narrowed by active status."
    )
    public ResponseEntity<PreviewRenditionResourcesExportAsyncCancelActiveResponseDto> cancelActiveRenditionResourcesCsvAsyncExportTasks(
        @RequestParam(required = false) String status
    ) {
        RenditionResourcesExportAsyncStatus statusFilter = parseRenditionResourcesExportAsyncStatus(status);
        if (statusFilter != null && !isActiveRenditionResourcesExportAsyncStatus(statusFilter)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "status filter only supports active states: QUEUED, RUNNING"
            );
        }

        long[] cancelledCount = {0L};
        int remainingActiveCount;
        synchronized (renditionResourcesExportAsyncTaskLock) {
            refreshRenditionResourcesExportAsyncTasksLifecycleLocked();
            List<String> taskIds = new ArrayList<>(renditionResourcesExportAsyncTasks.keySet());
            String actor = resolveAuditUsername();
            for (String taskId : taskIds) {
                renditionResourcesExportAsyncTasks.computeIfPresent(taskId, (key, current) -> {
                    if (current == null || !isActiveRenditionResourcesExportAsyncStatus(current.status())) {
                        return current;
                    }
                    if (statusFilter != null && current.status() != statusFilter) {
                        return current;
                    }
                    cancelledCount[0] += 1;
                    return current.cancel("Cancelled by active-task bulk request", actor);
                });
            }
            renditionResourcesExportAsyncTaskOrder.removeIf(taskId -> !renditionResourcesExportAsyncTasks.containsKey(taskId));
            remainingActiveCount = countActiveRenditionResourcesExportAsyncTasksLocked();
        }

        String statusFilterName = statusFilter != null ? statusFilter.name() : null;
        String message = cancelledCount[0] > 0
            ? statusFilterName == null
                ? String.format("Cancelled %d active async preview rendition resources export tasks", cancelledCount[0])
                : String.format("Cancelled %d async preview rendition resources export tasks with status %s", cancelledCount[0], statusFilterName)
            : statusFilterName == null
                ? "No active async preview rendition resources export tasks to cancel"
                : String.format("No async preview rendition resources export tasks with status %s to cancel", statusFilterName);

        return ResponseEntity.ok(new PreviewRenditionResourcesExportAsyncCancelActiveResponseDto(
            cancelledCount[0],
            remainingActiveCount,
            statusFilterName,
            message
        ));
    }

    @GetMapping("/renditions/resources/export-async/{taskId}")
    @Operation(
        summary = "Get async preview rendition resources diagnostics export task status",
        description = "Get status/details for a rendition resources asynchronous CSV export task."
    )
    public ResponseEntity<PreviewRenditionResourcesExportAsyncStatusResponseDto> getRenditionResourcesCsvAsyncExportTaskStatus(
        @PathVariable String taskId
    ) {
        RenditionResourcesExportAsyncTask task;
        synchronized (renditionResourcesExportAsyncTaskLock) {
            refreshRenditionResourcesExportAsyncTasksLifecycleLocked();
            task = renditionResourcesExportAsyncTasks.get(taskId);
        }
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toRenditionResourcesExportAsyncStatusResponse(task));
    }

    @PostMapping("/renditions/resources/export-async/{taskId}/cancel")
    @Operation(
        summary = "Cancel async preview rendition resources diagnostics export task",
        description = "Cancel a queued/running rendition resources asynchronous CSV export task."
    )
    public ResponseEntity<PreviewRenditionResourcesExportAsyncStatusResponseDto> cancelRenditionResourcesCsvAsyncExportTask(
        @PathVariable String taskId
    ) {
        RenditionResourcesExportAsyncTask updated;
        synchronized (renditionResourcesExportAsyncTaskLock) {
            refreshRenditionResourcesExportAsyncTasksLifecycleLocked();
            RenditionResourcesExportAsyncTask existing = renditionResourcesExportAsyncTasks.get(taskId);
            if (existing == null) {
                return ResponseEntity.notFound().build();
            }
            if (existing.isTerminal()) {
                return ResponseEntity.status(409).body(toRenditionResourcesExportAsyncStatusResponse(existing));
            }
            String actor = resolveAuditUsername();
            updated = renditionResourcesExportAsyncTasks.computeIfPresent(taskId, (key, current) -> {
                if (current.isTerminal()) {
                    return current;
                }
                return current.cancel("Cancelled by user", actor);
            });
        }
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toRenditionResourcesExportAsyncStatusResponse(updated));
    }

    @GetMapping(value = "/renditions/resources/export-async/{taskId}/download", produces = "text/csv")
    @Operation(
        summary = "Download async preview rendition resources diagnostics export result",
        description = "Download CSV for a completed rendition resources asynchronous export task."
    )
    public ResponseEntity<byte[]> downloadRenditionResourcesCsvAsyncExportResult(
        @PathVariable String taskId
    ) {
        RenditionResourcesExportAsyncTask task;
        synchronized (renditionResourcesExportAsyncTaskLock) {
            refreshRenditionResourcesExportAsyncTasksLifecycleLocked();
            task = renditionResourcesExportAsyncTasks.get(taskId);
        }
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        if (task.status() != RenditionResourcesExportAsyncStatus.COMPLETED
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

    @GetMapping("/traces")
    @Operation(
        summary = "Recent preview transform traces",
        description = "Request-id grouped preview trace snapshots for recent transform attempts."
    )
    public ResponseEntity<List<PreviewTransformTraceDto>> getTransformTraces(
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(required = false) String requestId
    ) {
        int safeLimit = clamp(limit <= 0 ? DEFAULT_TRACE_LIST_LIMIT : limit, 1, MAX_TRACE_LIST_LIMIT);
        List<PreviewTransformTraceDto> payload = previewTransformTraceBuffer.snapshot(safeLimit, requestId).stream()
            .map(trace -> new PreviewTransformTraceDto(
                trace.requestId(),
                trace.documentId(),
                trace.mimeType(),
                trace.source(),
                trace.startedAt(),
                trace.finishedAt(),
                trace.status(),
                trace.retryNeeded(),
                trace.failureReason(),
                trace.latestMessage(),
                trace.events().stream()
                    .map(event -> new PreviewTransformTraceEventDto(event.at(), event.stage(), event.message()))
                    .toList()
            ))
            .toList();
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/policies")
    @Operation(
        summary = "Preview queue failure policy profiles",
        description = "List active failure policy profiles used by preview queue retry scheduling."
    )
    public ResponseEntity<List<PreviewFailurePolicyDto>> getFailurePolicies() {
        List<PreviewFailurePolicyDto> payload = previewFailurePolicyRegistry.listPolicies().stream()
            .map(policy -> new PreviewFailurePolicyDto(
                policy.key(),
                policy.label(),
                policy.maxAttempts(),
                policy.retryDelayMs(),
                policy.backoffMultiplier(),
                policy.quietPeriodMs(),
                policy.builtIn()
            ))
            .toList();
        return ResponseEntity.ok(payload);
    }

    @PutMapping("/policies/{profileKey}")
    @Operation(
        summary = "Update preview queue failure policy profile",
        description = "Update retry attempts/delay/backoff/quiet-period for a policy profile key."
    )
    public ResponseEntity<PreviewFailurePolicyDto> updateFailurePolicy(
        @PathVariable String profileKey,
        @RequestBody PreviewFailurePolicyUpdateRequestDto request
    ) {
        PreviewFailurePolicyRegistry.PreviewFailurePolicy updated = previewFailurePolicyRegistry.upsert(
            profileKey,
            new PreviewFailurePolicyRegistry.PreviewFailurePolicyUpdate(
                request != null ? request.maxAttempts() : null,
                request != null ? request.retryDelayMs() : null,
                request != null ? request.backoffMultiplier() : null,
                request != null ? request.quietPeriodMs() : null
            )
        );
        PreviewFailurePolicyDto payload = new PreviewFailurePolicyDto(
            updated.key(),
            updated.label(),
            updated.maxAttempts(),
            updated.retryDelayMs(),
            updated.backoffMultiplier(),
            updated.quietPeriodMs(),
            updated.builtIn()
        );
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/prevention/blocked")
    @Operation(
        summary = "Preview rendition prevention diagnostics",
        description = "List blocked rendition entries and lightweight document metadata for operator recovery actions."
    )
    public ResponseEntity<PreviewRenditionPreventionDiagnosticsDto> getBlockedRenditionPreventions(
        @RequestParam(defaultValue = "50") int limit
    ) {
        int requestedLimit = limit <= 0 ? DEFAULT_PREVENTION_LIST_LIMIT : limit;
        int safeLimit = clamp(requestedLimit, 1, MAX_PREVENTION_LIST_LIMIT);
        List<PreviewRenditionPreventionRegistry.BlockedEntry> blockedEntries = previewRenditionPreventionRegistry.list(safeLimit);

        Map<UUID, Document> documentsById = documentRepository.findAllById(
            blockedEntries.stream().map(PreviewRenditionPreventionRegistry.BlockedEntry::documentId).toList()
        ).stream().collect(Collectors.toMap(Document::getId, document -> document));

        List<PreviewRenditionBlockedItemDto> items = blockedEntries.stream()
            .map(entry -> {
                Document document = documentsById.get(entry.documentId());
                return new PreviewRenditionBlockedItemDto(
                    entry.documentId(),
                    document != null ? document.getName() : null,
                    document != null ? document.getPath() : null,
                    document != null ? normalizeMimeType(document.getMimeType()) : null,
                    resolveEffectivePreviewStatus(document),
                    entry.category(),
                    entry.reason(),
                    entry.blockedAt(),
                    entry.lastHitAt(),
                    entry.hitCount()
                );
            })
            .toList();

        PreviewRenditionPreventionDiagnosticsDto payload = new PreviewRenditionPreventionDiagnosticsDto(
            previewRenditionPreventionRegistry.isEnabled(),
            previewRenditionPreventionRegistry.getBlockedCount(),
            previewRenditionPreventionRegistry.getMaxBlocked(),
            previewRenditionPreventionRegistry.listAutoBlockCategories(),
            safeLimit,
            items
        );
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/prevention/{documentId}/unblock")
    @Operation(
        summary = "Unblock rendition prevention marker",
        description = "Remove a document from prevention registry so operators can retry later."
    )
    public ResponseEntity<PreviewRenditionPreventionActionDto> unblockRenditionPrevention(
        @PathVariable UUID documentId
    ) {
        PreviewRenditionPreventionActionDto payload = unblockPreventionInternal(documentId);
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/prevention/{documentId}/unblock-requeue")
    @Operation(
        summary = "Unblock and requeue preview",
        description = "One-click unblock + queue for preview reprocessing."
    )
    public ResponseEntity<PreviewRenditionPreventionActionDto> unblockAndRequeueRendition(
        @PathVariable UUID documentId,
        @RequestParam(defaultValue = "true") boolean force
    ) {
        try {
            PreviewRenditionPreventionActionDto payload = unblockAndRequeueInternal(documentId, force);
            return ResponseEntity.ok(payload);
        } catch (IllegalArgumentException ex) {
            PreviewRenditionPreventionActionDto payload = new PreviewRenditionPreventionActionDto(
                documentId,
                false,
                false,
                ex.getMessage(),
                null,
                0,
                null
            );
            return ResponseEntity.badRequest().body(payload);
        }
    }

    @PostMapping("/prevention/unblock-batch")
    @Operation(
        summary = "Unblock prevention markers in batch",
        description = "Bulk-remove documents from prevention registry."
    )
    public ResponseEntity<PreviewRenditionPreventionBatchResponseDto> unblockPreventionBatch(
        @RequestBody PreviewRenditionPreventionBatchRequestDto request
    ) {
        List<UUID> rawIds = request != null && request.documentIds() != null ? request.documentIds() : List.of();
        PreviewRenditionPreventionBatchResponseDto payload = processPreventionBatch(rawIds, false, true);
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/prevention/unblock-requeue-batch")
    @Operation(
        summary = "Unblock and requeue prevention markers in batch",
        description = "Bulk-remove prevention markers and queue preview generation."
    )
    public ResponseEntity<PreviewRenditionPreventionBatchResponseDto> unblockAndRequeuePreventionBatch(
        @RequestBody PreviewRenditionPreventionBatchRequestDto request
    ) {
        List<UUID> rawIds = request != null && request.documentIds() != null ? request.documentIds() : List.of();
        boolean force = request == null || request.force() == null || Boolean.TRUE.equals(request.force());
        PreviewRenditionPreventionBatchResponseDto payload = processPreventionBatch(rawIds, true, force);
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/dead-letter")
    @Operation(
        summary = "Preview dead-letter diagnostics",
        description = "List terminal preview failures captured by the queue dead-letter registry."
    )
    public ResponseEntity<PreviewDeadLetterDiagnosticsDto> getDeadLetterDiagnostics(
        @RequestParam(defaultValue = "50") int limit
    ) {
        int requestedLimit = limit <= 0 ? DEFAULT_DEAD_LETTER_LIST_LIMIT : limit;
        int safeLimit = clamp(requestedLimit, 1, MAX_DEAD_LETTER_LIST_LIMIT);
        List<PreviewDeadLetterRegistry.DeadLetterEntry> deadLetters = previewDeadLetterRegistry.list(safeLimit);

        Map<UUID, Document> documentsById = documentRepository.findAllById(
            deadLetters.stream().map(PreviewDeadLetterRegistry.DeadLetterEntry::documentId).toList()
        ).stream().collect(Collectors.toMap(Document::getId, document -> document));

        List<PreviewDeadLetterItemDto> items = deadLetters.stream()
            .map(entry -> {
                Document document = documentsById.get(entry.documentId());
                return new PreviewDeadLetterItemDto(
                    entry.entryKey(),
                    entry.documentId(),
                    entry.renditionKey(),
                    document != null ? document.getName() : null,
                    document != null ? document.getPath() : null,
                    document != null ? normalizeMimeType(document.getMimeType()) : null,
                    resolveEffectivePreviewStatus(document),
                    entry.reason(),
                    entry.category(),
                    entry.policyKey(),
                    entry.sourceStage(),
                    entry.failedAt(),
                    entry.attempts(),
                    entry.occurrences(),
                    entry.lastReplayAt(),
                    entry.replayCount()
                );
            })
            .toList();

        PreviewDeadLetterDiagnosticsDto payload = new PreviewDeadLetterDiagnosticsDto(
            previewDeadLetterRegistry.isEnabled(),
            previewDeadLetterRegistry.isRedisEnabled(),
            previewDeadLetterRegistry.getTtlMs(),
            previewDeadLetterRegistry.isRedisActive() ? "REDIS" : "MEMORY",
            previewDeadLetterRegistry.getItemCount(),
            previewDeadLetterRegistry.getMaxEntries(),
            safeLimit,
            items
        );
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/dead-letter/export")
    @Operation(
        summary = "Export preview dead-letter diagnostics",
        description = "Export dead-letter diagnostics entries as CSV."
    )
    public ResponseEntity<byte[]> exportDeadLetterCsv(
        @RequestParam(defaultValue = "500") int limit
    ) {
        int requestedLimit = limit <= 0 ? DEFAULT_DEAD_LETTER_EXPORT_LIMIT : limit;
        int safeLimit = clamp(requestedLimit, 1, MAX_DEAD_LETTER_EXPORT_LIMIT);
        List<PreviewDeadLetterRegistry.DeadLetterEntry> deadLetters = previewDeadLetterRegistry.list(safeLimit);

        Map<UUID, Document> documentsById = documentRepository.findAllById(
            deadLetters.stream().map(PreviewDeadLetterRegistry.DeadLetterEntry::documentId).toList()
        ).stream().collect(Collectors.toMap(Document::getId, document -> document));

        String csv = buildDeadLetterCsv(deadLetters, documentsById);
        String filename = String.format(
            "preview_dead_letter_%s.csv",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(filename, StandardCharsets.UTF_8)
            .build());
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.add("X-Preview-Dead-Letter-Count", String.valueOf(deadLetters.size()));

        auditDeadLetterExport(safeLimit, deadLetters.size());
        return ResponseEntity.ok()
            .headers(headers)
            .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/dead-letter/replay-batch")
    @Operation(
        summary = "Replay preview dead-letter entries",
        description = "Queue replay for selected dead-letter entries and remove entries once accepted to queue."
    )
    public ResponseEntity<PreviewDeadLetterReplayBatchResponseDto> replayDeadLetterBatch(
        @RequestBody PreviewDeadLetterReplayBatchRequestDto request
    ) {
        List<UUID> rawIds = request != null && request.documentIds() != null ? request.documentIds() : List.of();
        List<String> rawEntryKeys = request != null && request.entryKeys() != null ? request.entryKeys() : List.of();
        boolean force = request == null || request.force() == null || Boolean.TRUE.equals(request.force());
        PreviewDeadLetterReplayBatchResponseDto payload = processDeadLetterReplayBatch(rawIds, rawEntryKeys, force);
        auditDeadLetterReplay(payload, force);
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/dead-letter/clear-batch")
    @Operation(
        summary = "Clear preview dead-letter entries",
        description = "Remove selected dead-letter entries without re-queueing preview tasks."
    )
    public ResponseEntity<PreviewDeadLetterClearBatchResponseDto> clearDeadLetterBatch(
        @RequestBody PreviewDeadLetterClearBatchRequestDto request
    ) {
        List<UUID> rawIds = request != null && request.documentIds() != null ? request.documentIds() : List.of();
        List<String> rawEntryKeys = request != null && request.entryKeys() != null ? request.entryKeys() : List.of();
        PreviewDeadLetterClearBatchResponseDto payload = processDeadLetterClearBatch(rawIds, rawEntryKeys);
        auditDeadLetterClear(payload);
        return ResponseEntity.ok(payload);
    }

    private PreviewQueueBatchResponseDto queueFailuresInternal(List<UUID> inputIds, boolean force) {
        List<UUID> rawIds = inputIds != null ? inputIds : List.of();
        int requested = rawIds.size();
        LinkedHashSet<UUID> deduplicatedIds = new LinkedHashSet<>();
        rawIds.stream().filter(id -> id != null).forEach(deduplicatedIds::add);
        Map<UUID, Document> documentsById = documentRepository.findAllById(deduplicatedIds).stream()
            .collect(Collectors.toMap(Document::getId, document -> document));

        List<PreviewQueueBatchItemDto> results = new ArrayList<>();
        int queued = 0;
        int skipped = 0;
        int failed = 0;

        for (UUID documentId : deduplicatedIds) {
            Document document = documentsById.get(documentId);
            try {
                PreviewQueueService.PreviewQueueStatus status = previewQueueService.enqueue(documentId, force);
                boolean wasQueued = status.queued();
                String outcome = wasQueued ? "QUEUED" : "SKIPPED";
                if (wasQueued) {
                    queued += 1;
                } else {
                    skipped += 1;
                }
                results.add(buildPreviewQueueBatchItem(
                    documentId,
                    outcome,
                    status.message(),
                    document,
                    status
                ));
            } catch (Exception ex) {
                failed += 1;
                results.add(buildPreviewQueueBatchItem(
                    documentId,
                    "FAILED",
                    ex.getMessage(),
                    document,
                    null
                ));
            }
        }

        return new PreviewQueueBatchResponseDto(
            requested,
            deduplicatedIds.size(),
            queued,
            skipped,
            failed,
            results
        );
    }

    private PreviewRenditionPreventionBatchResponseDto processPreventionBatch(
        List<UUID> inputIds,
        boolean requeue,
        boolean force
    ) {
        List<UUID> rawIds = inputIds != null ? inputIds : List.of();
        int requested = rawIds.size();
        LinkedHashSet<UUID> deduplicatedIds = new LinkedHashSet<>();
        rawIds.stream()
            .filter(id -> id != null)
            .limit(MAX_PREVENTION_BATCH_LIMIT)
            .forEach(deduplicatedIds::add);

        List<PreviewRenditionPreventionActionDto> results = new ArrayList<>();
        int unblocked = 0;
        int queued = 0;
        int failed = 0;

        for (UUID documentId : deduplicatedIds) {
            try {
                PreviewRenditionPreventionActionDto action = requeue
                    ? unblockAndRequeueInternal(documentId, force)
                    : unblockPreventionInternal(documentId);
                if (action.unblocked()) {
                    unblocked += 1;
                }
                if (action.queued()) {
                    queued += 1;
                }
                results.add(action);
            } catch (Exception ex) {
                failed += 1;
                results.add(new PreviewRenditionPreventionActionDto(
                    documentId,
                    false,
                    false,
                    ex.getMessage(),
                    null,
                    0,
                    null
                ));
            }
        }

        return new PreviewRenditionPreventionBatchResponseDto(
            requested,
            deduplicatedIds.size(),
            unblocked,
            queued,
            failed,
            results
        );
    }

    private PreviewDeadLetterReplayBatchResponseDto processDeadLetterReplayBatch(
        List<UUID> inputIds,
        List<String> inputEntryKeys,
        boolean force
    ) {
        List<UUID> rawIds = inputIds != null ? inputIds : List.of();
        List<String> rawEntryKeys = inputEntryKeys != null ? inputEntryKeys : List.of();
        int requested = rawIds.size() + rawEntryKeys.size();
        LinkedHashSet<String> deduplicatedEntryKeys = new LinkedHashSet<>();

        rawEntryKeys.stream()
            .filter(value -> value != null && !value.isBlank())
            .limit(MAX_DEAD_LETTER_BATCH_LIMIT)
            .forEach(value -> deduplicatedEntryKeys.add(value.trim()));

        rawIds.stream()
            .filter(id -> id != null)
            .limit(MAX_DEAD_LETTER_BATCH_LIMIT)
            .map(id -> PreviewDeadLetterRegistry.buildEntryKey(id, PreviewDeadLetterRegistry.defaultRenditionKey()))
            .forEach(deduplicatedEntryKeys::add);
        LinkedHashSet<UUID> deduplicatedDocumentIds = new LinkedHashSet<>();
        for (String entryKey : deduplicatedEntryKeys) {
            String[] parts = entryKey.split("\\|", 2);
            if (parts.length == 0) {
                continue;
            }
            try {
                deduplicatedDocumentIds.add(UUID.fromString(parts[0]));
            } catch (IllegalArgumentException ignored) {
                // Invalid entry keys are handled later and surfaced in the batch results.
            }
        }
        Map<UUID, Document> documentsById = documentRepository.findAllById(deduplicatedDocumentIds).stream()
            .collect(Collectors.toMap(Document::getId, document -> document));

        List<PreviewQueueBatchItemDto> results = new ArrayList<>();
        int queued = 0;
        int skipped = 0;
        int failed = 0;

        for (String entryKey : deduplicatedEntryKeys) {
            PreviewDeadLetterRegistry.DeadLetterEntry deadLetterEntry = previewDeadLetterRegistry.findByEntryKey(entryKey);
            UUID documentId = deadLetterEntry != null ? deadLetterEntry.documentId() : null;
            String renditionKey = deadLetterEntry != null ? deadLetterEntry.renditionKey() : PreviewDeadLetterRegistry.defaultRenditionKey();
            if (documentId == null) {
                String[] parts = entryKey.split("\\|", 2);
                if (parts.length > 0) {
                    try {
                        documentId = UUID.fromString(parts[0]);
                    } catch (IllegalArgumentException ignored) {
                        documentId = null;
                    }
                }
            }
            if (documentId == null) {
                failed += 1;
                results.add(buildFailedPreviewQueueBatchItem(
                    "Invalid dead-letter entry key: " + entryKey
                ));
                continue;
            }
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
                results.add(buildPreviewQueueBatchItem(
                    documentId,
                    wasQueued ? "QUEUED" : "SKIPPED",
                    status.message(),
                    documentsById.get(documentId),
                    status
                ));
            } catch (Exception ex) {
                failed += 1;
                results.add(buildPreviewQueueBatchItem(
                    documentId,
                    "FAILED",
                    ex.getMessage(),
                    documentsById.get(documentId),
                    null
                ));
            }
        }

        return new PreviewDeadLetterReplayBatchResponseDto(
            requested,
            deduplicatedEntryKeys.size(),
            queued,
            skipped,
            failed,
            results
        );
    }

    private PreviewQueueBatchItemDto buildPreviewQueueBatchItem(
        UUID documentId,
        String outcome,
        String message,
        Document document,
        PreviewQueueService.PreviewQueueStatus status
    ) {
        EffectivePreviewSnapshot previewSnapshot = resolveEffectivePreviewSnapshot(document, null, null, null, null);
        boolean hasExplicitPreviewStatus = status != null && status.previewStatus() != null;
        String previewStatus = hasExplicitPreviewStatus
            ? status.previewStatus().name()
            : previewSnapshot.status();
        String previewFailureReason = hasExplicitPreviewStatus
            ? status.previewFailureReason()
            : previewSnapshot.failureReason();
        String previewFailureCategory = hasExplicitPreviewStatus
            ? status.previewFailureCategory()
            : previewSnapshot.failureCategory();
        LocalDateTime previewLastUpdated = status != null && status.previewLastUpdated() != null
            ? status.previewLastUpdated()
            : previewSnapshot.previewLastUpdated();
        return new PreviewQueueBatchItemDto(
            documentId,
            outcome,
            message,
            previewStatus,
            previewFailureReason,
            previewFailureCategory,
            previewLastUpdated,
            status != null ? status.attempts() : 0,
            status != null ? status.nextAttemptAt() : null
        );
    }

    private PreviewQueueBatchItemDto buildFailedPreviewQueueBatchItem(String message) {
        return new PreviewQueueBatchItemDto(
            null,
            "FAILED",
            message,
            null,
            null,
            null,
            null,
            0,
            null
        );
    }

    private PreviewDeadLetterClearBatchResponseDto processDeadLetterClearBatch(
        List<UUID> inputIds,
        List<String> inputEntryKeys
    ) {
        List<UUID> rawIds = inputIds != null ? inputIds : List.of();
        List<String> rawEntryKeys = inputEntryKeys != null ? inputEntryKeys : List.of();
        int requested = rawIds.size() + rawEntryKeys.size();
        LinkedHashSet<String> deduplicatedEntryKeys = new LinkedHashSet<>();

        rawEntryKeys.stream()
            .filter(value -> value != null && !value.isBlank())
            .limit(MAX_DEAD_LETTER_BATCH_LIMIT)
            .forEach(value -> deduplicatedEntryKeys.add(value.trim()));

        rawIds.stream()
            .filter(id -> id != null)
            .limit(MAX_DEAD_LETTER_BATCH_LIMIT)
            .map(id -> PreviewDeadLetterRegistry.buildEntryKey(id, PreviewDeadLetterRegistry.defaultRenditionKey()))
            .forEach(deduplicatedEntryKeys::add);

        List<PreviewDeadLetterClearItemDto> results = new ArrayList<>();
        int cleared = 0;
        int failed = 0;

        for (String entryKey : deduplicatedEntryKeys) {
            PreviewDeadLetterRegistry.DeadLetterEntry deadLetterEntry = previewDeadLetterRegistry.findByEntryKey(entryKey);
            UUID documentId = deadLetterEntry != null ? deadLetterEntry.documentId() : null;
            String renditionKey = deadLetterEntry != null ? deadLetterEntry.renditionKey() : PreviewDeadLetterRegistry.defaultRenditionKey();
            if (documentId == null) {
                String[] parts = entryKey.split("\\|", 2);
                if (parts.length > 0) {
                    try {
                        documentId = UUID.fromString(parts[0]);
                    } catch (IllegalArgumentException ignored) {
                        documentId = null;
                    }
                }
            }

            if (documentId == null) {
                failed += 1;
                results.add(new PreviewDeadLetterClearItemDto(
                    null,
                    entryKey,
                    null,
                    "FAILED",
                    "Invalid dead-letter entry key: " + entryKey
                ));
                continue;
            }

            try {
                previewDeadLetterRegistry.remove(documentId, renditionKey);
                boolean existed = deadLetterEntry != null;
                if (existed) {
                    cleared += 1;
                }
                results.add(new PreviewDeadLetterClearItemDto(
                    documentId,
                    entryKey,
                    renditionKey,
                    existed ? "CLEARED" : "SKIPPED",
                    existed ? "Dead-letter entry cleared" : "Dead-letter entry not found"
                ));
            } catch (Exception ex) {
                failed += 1;
                results.add(new PreviewDeadLetterClearItemDto(
                    documentId,
                    entryKey,
                    renditionKey,
                    "FAILED",
                    ex.getMessage()
                ));
            }
        }

        return new PreviewDeadLetterClearBatchResponseDto(
            requested,
            deduplicatedEntryKeys.size(),
            cleared,
            failed,
            results
        );
    }

    private PreviewRenditionPreventionActionDto unblockPreventionInternal(UUID documentId) {
        previewRenditionPreventionRegistry.unblock(documentId);
        return new PreviewRenditionPreventionActionDto(
            documentId,
            true,
            false,
            "Rendition prevention marker removed",
            null,
            0,
            null
        );
    }

    private PreviewRenditionPreventionActionDto unblockAndRequeueInternal(UUID documentId, boolean force) {
        previewRenditionPreventionRegistry.unblock(documentId);
        PreviewQueueService.PreviewQueueStatus status = previewQueueService.enqueue(documentId, force);
        return new PreviewRenditionPreventionActionDto(
            documentId,
            true,
            status.queued(),
            status.message(),
            status.previewStatus() != null ? status.previewStatus().name() : null,
            status.attempts(),
            status.nextAttemptAt()
        );
    }

    private PreviewFailureLedgerResetItemDto resetFailureLedgerInternal(UUID documentId) {
        if (documentId == null) {
            return new PreviewFailureLedgerResetItemDto(
                null,
                null,
                0,
                null,
                null,
                "FAILED",
                "documentId is required"
            );
        }

        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            return new PreviewFailureLedgerResetItemDto(
                documentId,
                null,
                0,
                null,
                null,
                "SKIPPED",
                "Document not found"
            );
        }

        int previousFailureCount = document.getPreviewFailureCount() != null
            ? document.getPreviewFailureCount()
            : 0;
        LocalDateTime previousFailedAt = document.getPreviewFailedAt();
        String previousReason = normalizeReasonOrNull(document.getPreviewLastFailureReason());
        boolean hasLedger = previousFailureCount > 0
            || previousFailedAt != null
            || previousReason != null
            || normalizeHashOrNull(document.getPreviewFailureContentHash()) != null;
        if (!hasLedger) {
            return new PreviewFailureLedgerResetItemDto(
                documentId,
                document.getName(),
                previousFailureCount,
                previousFailedAt,
                previousReason,
                "SKIPPED",
                "Failure ledger already empty"
            );
        }

        try {
            document.setPreviewFailureCount(0);
            document.setPreviewFailedAt(null);
            document.setPreviewLastFailureReason(null);
            document.setPreviewFailureContentHash(null);
            documentRepository.save(document);
            return new PreviewFailureLedgerResetItemDto(
                documentId,
                document.getName(),
                previousFailureCount,
                previousFailedAt,
                previousReason,
                "RESET",
                "Failure ledger reset"
            );
        } catch (Exception e) {
            return new PreviewFailureLedgerResetItemDto(
                documentId,
                document.getName(),
                previousFailureCount,
                previousFailedAt,
                previousReason,
                "FAILED",
                resolveErrorMessage(e)
            );
        }
    }

    private FailureLedgerFilterScanResult collectFailureLedgerDocumentsByFilter(
        LocalDateTime failedSince,
        String reason,
        String category,
        Boolean retryable,
        int maxDocuments
    ) {
        List<Document> matched = new ArrayList<>();
        int pageSize = Math.min(200, Math.max(1, maxDocuments));
        int pageNumber = 0;
        int scanned = 0;

        boolean truncated = false;
        while (matched.size() < maxDocuments && scanned < FAILURE_LEDGER_RESET_FILTER_SCAN_LIMIT) {
            Pageable pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(
                    Sort.Order.desc("previewFailedAt").nullsLast(),
                    Sort.Order.desc("previewLastUpdated").nullsLast()
                )
            );
            Page<Document> page = documentRepository.findPreviewFailureLedgerEntries(failedSince, pageable);
            if (page.isEmpty()) {
                break;
            }
            for (Document document : page.getContent()) {
                scanned += 1;
                PreviewFailureLedgerItemDto item = PreviewFailureLedgerItemDto.from(document);
                String itemReason = normalizeReason(item.lastReason());
                String itemCategory = normalizedUpperOrDefault(item.category(), "UNKNOWN");
                boolean reasonMatch = itemReason.equals(reason);
                boolean categoryMatch = "ANY".equals(category) || itemCategory.equals(category);
                boolean retryableMatch = retryable == null || item.retryable() == retryable;
                if (reasonMatch && categoryMatch && retryableMatch) {
                    matched.add(document);
                    if (matched.size() >= maxDocuments) {
                        truncated = true;
                        break;
                    }
                }
                if (scanned >= FAILURE_LEDGER_RESET_FILTER_SCAN_LIMIT) {
                    truncated = true;
                    break;
                }
            }
            if (!page.hasNext()) {
                break;
            }
            pageNumber += 1;
        }
        return new FailureLedgerFilterScanResult(matched, scanned, truncated);
    }

    private String buildFailureLedgerCsv(List<PreviewFailureLedgerItemDto> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("documentId,name,path,mimeType,previewStatus,failureCount,failedAt,lastReason,category,retryable,previewLastUpdated,failureContentHash,currentContentHash,staleByContentChange\n");
        for (PreviewFailureLedgerItemDto item : items) {
            sb.append(csv(item.documentId()))
                .append(',')
                .append(csv(item.name()))
                .append(',')
                .append(csv(item.path()))
                .append(',')
                .append(csv(item.mimeType()))
                .append(',')
                .append(csv(item.previewStatus()))
                .append(',')
                .append(item.failureCount())
                .append(',')
                .append(csv(item.failedAt()))
                .append(',')
                .append(csv(item.lastReason()))
                .append(',')
                .append(csv(item.category()))
                .append(',')
                .append(item.retryable())
                .append(',')
                .append(csv(item.previewLastUpdated()))
                .append(',')
                .append(csv(item.failureContentHash()))
                .append(',')
                .append(csv(item.currentContentHash()))
                .append(',')
                .append(item.staleByContentChange())
                .append('\n');
        }
        return sb.toString();
    }

    private List<PreviewQueueDiagnosticsItemDto> mapQueueDiagnosticsItems(
        PreviewQueueService.PreviewQueueDiagnosticsSnapshot snapshot,
        String stateFilter,
        String queryFilter
    ) {
        if (snapshot == null || snapshot.items() == null || snapshot.items().isEmpty()) {
            return List.of();
        }
        Map<UUID, Document> documentsById = documentRepository.findAllById(
            snapshot.items().stream().map(PreviewQueueService.PreviewQueueDiagnosticsItem::documentId).toList()
        ).stream().collect(Collectors.toMap(Document::getId, document -> document));

        String normalizedQuery = queryFilter != null ? queryFilter.toLowerCase(Locale.ROOT) : null;
        List<PreviewQueueDiagnosticsItemDto> mapped = new ArrayList<>();
        for (PreviewQueueService.PreviewQueueDiagnosticsItem item : snapshot.items()) {
            Document document = documentsById.get(item.documentId());
            String queueState = resolveQueueDiagnosticsItemState(item.running(), item.cancelRequested());
            EffectivePreviewSnapshot previewSnapshot = resolveEffectivePreviewSnapshot(
                document,
                null,
                null,
                null,
                document != null ? document.getPreviewLastUpdated() : null
            );
            PreviewQueueDiagnosticsItemDto dto = new PreviewQueueDiagnosticsItemDto(
                item.documentId(),
                document != null ? document.getName() : null,
                document != null ? document.getPath() : null,
                document != null ? normalizeMimeType(document.getMimeType()) : null,
                previewSnapshot.status(),
                previewSnapshot.failureReason(),
                previewSnapshot.failureCategory(),
                previewSnapshot.previewLastUpdated(),
                queueState,
                item.governanceKey(),
                item.attempts(),
                item.nextAttemptAt(),
                item.running(),
                item.cancelRequested()
            );
            if (!"ALL".equals(stateFilter) && !stateFilter.equals(queueState)) {
                continue;
            }
            if (!matchesQueueDiagnosticsQuery(dto, normalizedQuery)) {
                continue;
            }
            mapped.add(dto);
        }
        return mapped;
    }

    private PreviewQueueService.PreviewQueueDiagnosticsSnapshot loadQueueDiagnosticsSnapshot(int safeLimit) {
        PreviewQueueService.PreviewQueueDiagnosticsSnapshot snapshot = previewQueueService.diagnosticsSnapshot(safeLimit);
        if (snapshot != null) {
            return snapshot;
        }
        return new PreviewQueueService.PreviewQueueDiagnosticsSnapshot(
            "UNKNOWN",
            false,
            0,
            0,
            0,
            true,
            0,
            safeLimit,
            false,
            List.of()
        );
    }

    private String buildQueueDiagnosticsCsv(
        PreviewQueueService.PreviewQueueDiagnosticsSnapshot snapshot,
        String stateFilter,
        String queryFilter,
        List<PreviewQueueDiagnosticsItemDto> items
    ) {
        String backend = snapshot != null ? snapshot.backend() : "UNKNOWN";
        boolean queueEnabled = snapshot != null && snapshot.queueEnabled();
        long scheduledCount = snapshot != null ? snapshot.scheduledCount() : 0L;
        long governanceCount = snapshot != null ? snapshot.governanceCount() : 0L;
        long runningCount = snapshot != null ? snapshot.runningCount() : 0L;
        boolean runningCountAccurate = snapshot != null && snapshot.runningCountAccurate();
        long cancellationRequestedCount = snapshot != null ? snapshot.cancellationRequestedCount() : 0L;
        int sampleLimit = snapshot != null ? snapshot.sampleLimit() : 0;
        boolean sampleTruncated = snapshot != null && snapshot.sampleTruncated();
        int totalSampledItems = snapshot != null && snapshot.items() != null ? snapshot.items().size() : 0;
        int filteredSampledItems = items != null ? items.size() : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("backend,queueEnabled,scheduledCount,governanceCount,runningCount,runningCountAccurate,cancellationRequestedCount,sampleLimit,sampleTruncated,stateFilter,queryFilter,totalSampledItems,filteredSampledItems,queueState,documentId,name,path,mimeType,previewStatus,previewFailureReason,previewFailureCategory,previewLastUpdated,attempts,nextAttemptAt,running,cancelRequested,governanceKey\n");
        if (items == null || items.isEmpty()) {
            sb.append(csv(backend))
                .append(',')
                .append(queueEnabled)
                .append(',')
                .append(scheduledCount)
                .append(',')
                .append(governanceCount)
                .append(',')
                .append(runningCount)
                .append(',')
                .append(runningCountAccurate)
                .append(',')
                .append(cancellationRequestedCount)
                .append(',')
                .append(sampleLimit)
                .append(',')
                .append(sampleTruncated)
                .append(',')
                .append(csv(stateFilter))
                .append(',')
                .append(csv(queryFilter))
                .append(',')
                .append(totalSampledItems)
                .append(',')
                .append(filteredSampledItems)
                .append('\n');
            return sb.toString();
        }
        for (PreviewQueueDiagnosticsItemDto item : items) {
            sb.append(csv(backend))
                .append(',')
                .append(queueEnabled)
                .append(',')
                .append(scheduledCount)
                .append(',')
                .append(governanceCount)
                .append(',')
                .append(runningCount)
                .append(',')
                .append(runningCountAccurate)
                .append(',')
                .append(cancellationRequestedCount)
                .append(',')
                .append(sampleLimit)
                .append(',')
                .append(sampleTruncated)
                .append(',')
                .append(csv(stateFilter))
                .append(',')
                .append(csv(queryFilter))
                .append(',')
                .append(totalSampledItems)
                .append(',')
                .append(filteredSampledItems)
                .append(',')
                .append(csv(item.queueState()))
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
                .append(csv(item.previewFailureReason()))
                .append(',')
                .append(csv(item.previewFailureCategory()))
                .append(',')
                .append(csv(item.previewLastUpdated()))
                .append(',')
                .append(item.attempts())
                .append(',')
                .append(csv(item.nextAttemptAt()))
                .append(',')
                .append(item.running())
                .append(',')
                .append(item.cancelRequested())
                .append(',')
                .append(csv(item.governanceKey()))
                .append('\n');
        }
        return sb.toString();
    }

    private List<PreviewQueueDeclinedItemDto> mapQueueDeclinedItems(
        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot,
        String categoryFilter,
        String forceRequiredFilter,
        String queryFilter,
        Integer windowHoursFilter
    ) {
        if (snapshot == null || snapshot.items() == null || snapshot.items().isEmpty()) {
            return List.of();
        }
        List<UUID> documentIds = snapshot.items().stream()
            .map(PreviewQueueService.PreviewQueueDeclinedItem::documentId)
            .filter(id -> id != null)
            .toList();
        Map<UUID, Document> documentsById = documentRepository.findAllById(documentIds).stream()
            .collect(Collectors.toMap(Document::getId, document -> document));

        String normalizedQuery = queryFilter != null ? queryFilter.toLowerCase(Locale.ROOT) : null;
        Instant declinedSince = resolveQueueDeclinedSince(windowHoursFilter);
        List<PreviewQueueDeclinedItemDto> mapped = new ArrayList<>();
        for (PreviewQueueService.PreviewQueueDeclinedItem item : snapshot.items()) {
            UUID documentId = item.documentId();
            Document document = documentId != null ? documentsById.get(documentId) : null;
            String normalizedCategory = normalizedUpperOrDefault(item.category(), "DECLINED");
            EffectivePreviewSnapshot previewSnapshot = resolveEffectivePreviewSnapshot(
                document,
                item.previewStatus() != null ? item.previewStatus().name() : null,
                null,
                null,
                document != null ? document.getPreviewLastUpdated() : null
            );
            PreviewQueueDeclinedItemDto dto = new PreviewQueueDeclinedItemDto(
                documentId,
                document != null ? document.getName() : null,
                document != null ? document.getPath() : null,
                document != null ? normalizeMimeType(document.getMimeType()) : null,
                previewSnapshot.status(),
                previewSnapshot.failureReason(),
                previewSnapshot.failureCategory(),
                previewSnapshot.previewLastUpdated(),
                normalizeReasonOrNull(item.reason()),
                normalizedCategory,
                item.governanceKey(),
                item.declinedAt(),
                item.nextEligibleAt(),
                item.forceRequired()
            );
            if (!matchesQueueDeclinedWindow(dto, declinedSince)) {
                continue;
            }
            if (!"ANY".equals(categoryFilter) && !categoryFilter.equals(normalizedCategory)) {
                continue;
            }
            if ("YES".equals(forceRequiredFilter) && !dto.forceRequired()) {
                continue;
            }
            if ("NO".equals(forceRequiredFilter) && dto.forceRequired()) {
                continue;
            }
            if (!matchesQueueDeclinedQuery(dto, normalizedQuery)) {
                continue;
            }
            mapped.add(dto);
        }
        return mapped;
    }

    private List<PreviewQueueDeclinedCategoryCountDto> mapQueueDeclinedCategoryCounts(
        List<PreviewQueueDeclinedItemDto> items
    ) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<String, List<PreviewQueueDeclinedItemDto>> grouped = items.stream()
            .collect(Collectors.groupingBy(
                item -> normalizedUpperOrDefault(item.category(), "DECLINED"),
                java.util.LinkedHashMap::new,
                Collectors.toList()
            ));
        return grouped.entrySet().stream()
            .map(entry -> new PreviewQueueDeclinedCategoryCountDto(
                entry.getKey(),
                entry.getValue().size(),
                entry.getValue().stream().filter(PreviewQueueDeclinedItemDto::forceRequired).count()
            ))
            .sorted(Comparator.comparingLong(PreviewQueueDeclinedCategoryCountDto::count).reversed()
                .thenComparing(PreviewQueueDeclinedCategoryCountDto::category))
            .toList();
    }

    private PreviewQueueDeclinedRequeueDryRunResponseDto computeQueueDeclinedRequeueDryRun(
        int limit,
        String category,
        String forceRequired,
        String query,
        Integer windowHours,
        boolean force
    ) {
        int safeLimit = clamp(
            limit <= 0 ? DEFAULT_QUEUE_DECLINED_ACTION_LIMIT : limit,
            1,
            MAX_QUEUE_DECLINED_ACTION_LIMIT
        );
        String safeCategoryFilter = normalizeQueueDeclinedCategoryFilter(category);
        String safeForceRequiredFilter = normalizeQueueDeclinedForceRequiredFilter(forceRequired);
        String safeQueryFilter = normalizeQueueDiagnosticsQueryFilter(query);
        Integer safeWindowHoursFilter = normalizeQueueDeclinedWindowHours(windowHours);

        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = previewQueueService.declinedSnapshot(safeLimit);
        List<PreviewQueueDeclinedItemDto> candidates = mapQueueDeclinedItems(
            snapshot,
            safeCategoryFilter,
            safeForceRequiredFilter,
            safeQueryFilter,
            safeWindowHoursFilter
        )
            .stream()
            .limit(safeLimit)
            .toList();
        Map<UUID, Document> candidateDocumentsById = documentRepository.findAllById(
            candidates.stream()
                .map(PreviewQueueDeclinedItemDto::documentId)
                .filter(Objects::nonNull)
                .toList()
        ).stream().collect(Collectors.toMap(Document::getId, document -> document));

        int estimatedQueued = 0;
        int estimatedSkipped = 0;
        int estimatedFailed = 0;
        Map<String, Long> reasonCounter = new HashMap<>();
        List<PreviewQueueDeclinedRequeueDryRunItemDto> results = new ArrayList<>();

        for (PreviewQueueDeclinedItemDto candidate : candidates) {
            UUID documentId = candidate.documentId();
            PreviewPreflightResolver.PreflightDecision preflightDecision = evaluateQueueDeclinedPreflightDecision(
                candidate,
                candidateDocumentsById.get(documentId)
            );
            String preflightStatus = preflightDecision != null ? preflightDecision.preflightStatus() : null;
            String preflightSkipReason = preflightDecision != null ? preflightDecision.skipReason() : null;
            String preflightRoute = preflightDecision != null ? preflightDecision.route() : null;
            String preflightPolicyProfile = preflightDecision != null ? preflightDecision.policyProfileKey() : null;
            String preflightPipeline = preflightDecision != null ? preflightDecision.pipelineChainSummary() : null;

            if (documentId == null) {
                estimatedFailed += 1;
                String reasonCode = "MISSING_DOCUMENT_ID";
                results.add(buildFailedPreviewQueueDeclinedRequeueDryRunItem(
                    candidate,
                    reasonCode,
                    "Missing document id in declined queue item",
                    preflightStatus,
                    preflightSkipReason,
                    preflightRoute,
                    preflightPolicyProfile,
                    preflightPipeline
                ));
                reasonCounter.merge(reasonCode + "|FAILED", 1L, Long::sum);
                continue;
            }

            if (preflightDecision != null && !preflightDecision.accepted()) {
                estimatedSkipped += 1;
                String reasonCode = "PREFLIGHT_" + toPreflightSkipReasonCode(preflightDecision.skipReason());
                String message = preflightDecision.message() == null || preflightDecision.message().isBlank()
                    ? "Preflight declined preview queueing"
                    : "Preflight declined (" + toPreflightSkipReasonCode(preflightDecision.skipReason())
                        + "): " + preflightDecision.message();
                results.add(buildPreviewQueueDeclinedRequeueDryRunItem(
                    candidate,
                    candidateDocumentsById.get(documentId),
                    "SKIPPED",
                    reasonCode,
                    message,
                    null,
                    candidate.nextEligibleAt(),
                    preflightStatus,
                    preflightSkipReason,
                    preflightRoute,
                    preflightPolicyProfile,
                    preflightPipeline
                ));
                reasonCounter.merge(reasonCode + "|SKIPPED", 1L, Long::sum);
                continue;
            }

            try {
                PreviewQueueService.PreviewQueueStatus status = previewQueueService.evaluateEnqueue(documentId, force);
                if (status == null) {
                    estimatedFailed += 1;
                    String reasonCode = "MISSING_QUEUE_STATUS";
                    results.add(buildPreviewQueueDeclinedRequeueDryRunItem(
                        candidate,
                        candidateDocumentsById.get(documentId),
                        "FAILED",
                        reasonCode,
                        "No queue status returned",
                        null,
                        candidate.nextEligibleAt(),
                        preflightStatus,
                        preflightSkipReason,
                        preflightRoute,
                        preflightPolicyProfile,
                        preflightPipeline
                    ));
                    reasonCounter.merge(reasonCode + "|FAILED", 1L, Long::sum);
                    continue;
                }

                EffectivePreviewSnapshot previewSnapshot = resolvePreviewQueueMutationSnapshot(
                    candidate,
                    candidateDocumentsById.get(documentId),
                    status
                );
                boolean queued = status.queued();
                String outcome = queued ? "QUEUED" : "SKIPPED";
                String reasonCode = queued
                    ? "WOULD_QUEUE"
                    : deriveQueueDeclinedRequeueDryRunReasonCode(status.message(), previewSnapshot.status(), force);
                String message = status.message() == null || status.message().isBlank()
                    ? (queued ? "Preview queued" : "Preview queueing skipped by policy")
                    : status.message();
                Instant nextAttemptAt = status.nextAttemptAt() != null
                    ? status.nextAttemptAt()
                    : candidate.nextEligibleAt();

                if (queued) {
                    estimatedQueued += 1;
                } else {
                    estimatedSkipped += 1;
                }

                results.add(buildPreviewQueueDeclinedRequeueDryRunItem(
                    candidate,
                    outcome,
                    reasonCode,
                    message,
                    previewSnapshot,
                    nextAttemptAt,
                    preflightStatus,
                    preflightSkipReason,
                    preflightRoute,
                    preflightPolicyProfile,
                    preflightPipeline
                ));
                reasonCounter.merge(reasonCode + "|" + outcome, 1L, Long::sum);
            } catch (Exception ex) {
                estimatedFailed += 1;
                String reasonCode = "EVALUATE_EXCEPTION";
                results.add(buildPreviewQueueDeclinedRequeueDryRunItem(
                    candidate,
                    candidateDocumentsById.get(documentId),
                    "FAILED",
                    reasonCode,
                    resolveErrorMessage(ex),
                    null,
                    candidate.nextEligibleAt(),
                    preflightStatus,
                    preflightSkipReason,
                    preflightRoute,
                    preflightPolicyProfile,
                    preflightPipeline
                ));
                reasonCounter.merge(reasonCode + "|FAILED", 1L, Long::sum);
            }
        }

        List<PreviewQueueDeclinedRequeueDryRunReasonCountDto> reasonBreakdown = reasonCounter.entrySet()
            .stream()
            .map(entry -> {
                String[] parts = entry.getKey().split("\\|", 2);
                String reasonCode = parts.length > 0 ? parts[0] : "UNKNOWN";
                String outcome = parts.length > 1 ? parts[1] : "UNKNOWN";
                return new PreviewQueueDeclinedRequeueDryRunReasonCountDto(reasonCode, outcome, entry.getValue());
            })
            .sorted(Comparator.comparingLong(PreviewQueueDeclinedRequeueDryRunReasonCountDto::count)
                .reversed()
                .thenComparing(PreviewQueueDeclinedRequeueDryRunReasonCountDto::reasonCode))
            .toList();

        return new PreviewQueueDeclinedRequeueDryRunResponseDto(
            safeCategoryFilter,
            safeForceRequiredFilter,
            safeQueryFilter,
            safeWindowHoursFilter,
            safeLimit,
            force,
            candidates.size(),
            estimatedQueued,
            estimatedSkipped,
            estimatedFailed,
            results,
            reasonBreakdown
        );
    }

    private PreviewQueueDeclinedRequeueItemDto buildPreviewQueueDeclinedRequeueItem(
        PreviewQueueDeclinedItemDto candidate,
        Document document,
        String outcome,
        String message,
        PreviewQueueService.PreviewQueueStatus status
    ) {
        EffectivePreviewSnapshot previewSnapshot = resolvePreviewQueueMutationSnapshot(candidate, document, status);
        return new PreviewQueueDeclinedRequeueItemDto(
            candidate.documentId(),
            candidate.category(),
            outcome,
            message,
            previewSnapshot.status(),
            previewSnapshot.failureReason(),
            previewSnapshot.failureCategory(),
            previewSnapshot.previewLastUpdated()
        );
    }

    private PreviewQueueDeclinedRequeueItemDto buildFailedPreviewQueueDeclinedRequeueItem(
        PreviewQueueDeclinedItemDto candidate,
        String message
    ) {
        return new PreviewQueueDeclinedRequeueItemDto(
            null,
            candidate.category(),
            "FAILED",
            message,
            null,
            null,
            null,
            null
        );
    }

    private PreviewQueueDeclinedRequeueDryRunItemDto buildPreviewQueueDeclinedRequeueDryRunItem(
        PreviewQueueDeclinedItemDto candidate,
        Document document,
        String outcome,
        String reasonCode,
        String message,
        PreviewQueueService.PreviewQueueStatus status,
        Instant nextAttemptAt,
        String preflightStatus,
        String preflightSkipReason,
        String preflightRoute,
        String preflightPolicyProfile,
        String preflightPipeline
    ) {
        EffectivePreviewSnapshot previewSnapshot = resolvePreviewQueueMutationSnapshot(candidate, document, status);
        return new PreviewQueueDeclinedRequeueDryRunItemDto(
            candidate.documentId(),
            candidate.category(),
            outcome,
            reasonCode,
            message,
            previewSnapshot.status(),
            previewSnapshot.failureReason(),
            previewSnapshot.failureCategory(),
            previewSnapshot.previewLastUpdated(),
            nextAttemptAt,
            preflightStatus,
            preflightSkipReason,
            preflightRoute,
            preflightPolicyProfile,
            preflightPipeline
        );
    }

    private PreviewQueueDeclinedRequeueDryRunItemDto buildPreviewQueueDeclinedRequeueDryRunItem(
        PreviewQueueDeclinedItemDto candidate,
        String outcome,
        String reasonCode,
        String message,
        EffectivePreviewSnapshot previewSnapshot,
        Instant nextAttemptAt,
        String preflightStatus,
        String preflightSkipReason,
        String preflightRoute,
        String preflightPolicyProfile,
        String preflightPipeline
    ) {
        return new PreviewQueueDeclinedRequeueDryRunItemDto(
            candidate.documentId(),
            candidate.category(),
            outcome,
            reasonCode,
            message,
            previewSnapshot.status(),
            previewSnapshot.failureReason(),
            previewSnapshot.failureCategory(),
            previewSnapshot.previewLastUpdated(),
            nextAttemptAt,
            preflightStatus,
            preflightSkipReason,
            preflightRoute,
            preflightPolicyProfile,
            preflightPipeline
        );
    }

    private PreviewQueueDeclinedRequeueDryRunItemDto buildFailedPreviewQueueDeclinedRequeueDryRunItem(
        PreviewQueueDeclinedItemDto candidate,
        String reasonCode,
        String message,
        String preflightStatus,
        String preflightSkipReason,
        String preflightRoute,
        String preflightPolicyProfile,
        String preflightPipeline
    ) {
        return new PreviewQueueDeclinedRequeueDryRunItemDto(
            null,
            candidate.category(),
            "FAILED",
            reasonCode,
            message,
            candidate.previewStatus(),
            null,
            null,
            null,
            candidate.nextEligibleAt(),
            preflightStatus,
            preflightSkipReason,
            preflightRoute,
            preflightPolicyProfile,
            preflightPipeline
        );
    }

    private EffectivePreviewSnapshot resolvePreviewQueueMutationSnapshot(
        PreviewQueueDeclinedItemDto candidate,
        Document document,
        PreviewQueueService.PreviewQueueStatus status
    ) {
        EffectivePreviewSnapshot sharedSnapshot = resolveEffectivePreviewSnapshot(document, null, null, null, null);
        boolean hasExplicitPreviewStatus = status != null && status.previewStatus() != null;
        String previewStatus = hasExplicitPreviewStatus
            ? status.previewStatus().name()
            : firstNonBlank(
                sharedSnapshot.status(),
                candidate != null ? candidate.previewStatus() : null
            );
        String previewFailureReason = hasExplicitPreviewStatus
            ? status.previewFailureReason()
            : firstNonBlank(
                sharedSnapshot.failureReason(),
                candidate != null ? candidate.previewFailureReason() : null
            );
        String previewFailureCategory = hasExplicitPreviewStatus
            ? status.previewFailureCategory()
            : firstNonBlank(
                sharedSnapshot.failureCategory(),
                candidate != null ? candidate.previewFailureCategory() : null
            );
        LocalDateTime previewLastUpdated = status != null && status.previewLastUpdated() != null
            ? status.previewLastUpdated()
            : sharedSnapshot.previewLastUpdated() != null
                ? sharedSnapshot.previewLastUpdated()
                : (candidate != null ? candidate.previewLastUpdated() : null);
        return new EffectivePreviewSnapshot(
            previewStatus,
            previewFailureReason,
            previewFailureCategory,
            previewLastUpdated
        );
    }

    private PreviewPreflightResolver.PreflightDecision evaluateQueueDeclinedPreflightDecision(
        PreviewQueueDeclinedItemDto candidate,
        Document document
    ) {
        if (candidate == null) {
            return null;
        }
        try {
            if (document != null) {
                return previewPreflightResolver.evaluateDocument(document);
            }
            return previewPreflightResolver.evaluateCandidate(
                candidate.documentId(),
                candidate.name(),
                candidate.mimeType(),
                null
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private static String toPreflightSkipReasonCode(String skipReason) {
        if (skipReason == null || skipReason.isBlank()) {
            return "DECLINED";
        }
        return skipReason.trim().replaceAll("[^A-Za-z0-9]+", "_").toUpperCase(Locale.ROOT);
    }

    private static String deriveQueueDeclinedRequeueDryRunReasonCode(
        String message,
        String previewStatus,
        boolean force
    ) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (normalized.contains("already queued")) {
            return "ALREADY_QUEUED";
        }
        if (normalized.contains("already up to date")) {
            return "ALREADY_UP_TO_DATE";
        }
        if (normalized.contains("unsupported")) {
            return "UNSUPPORTED";
        }
        if (normalized.contains("failed permanently")) {
            return "PERMANENT_FAILURE";
        }
        if (normalized.contains("quiet period")) {
            return "QUIET_PERIOD_ACTIVE";
        }
        if (normalized.contains("queue disabled")) {
            return "QUEUE_DISABLED";
        }
        if (normalized.contains("rendition prevented")) {
            return "RENDITION_PREVENTED";
        }
        if (!force && "READY".equalsIgnoreCase(previewStatus)) {
            return "READY_NOT_FORCED";
        }
        return "POLICY_SKIPPED";
    }

    private String buildQueueDeclinedRequeueDryRunCsv(
        PreviewQueueDeclinedRequeueDryRunResponseDto payload
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("categoryFilter,forceRequiredFilter,queryFilter,windowHoursFilter,limit,force,requested,estimatedQueued,estimatedSkipped,estimatedFailed,documentId,category,outcome,reasonCode,message,previewStatus,previewFailureReason,previewFailureCategory,previewLastUpdated,nextAttemptAt,preflightStatus,preflightSkipReason,preflightRoute,preflightPolicyProfile,preflightPipeline\n");
        List<PreviewQueueDeclinedRequeueDryRunItemDto> results = payload != null ? payload.results() : List.of();
        if (results == null || results.isEmpty()) {
            sb.append(csv(payload != null ? payload.categoryFilter() : "ANY"))
                .append(',')
                .append(csv(payload != null ? payload.forceRequiredFilter() : "ANY"))
                .append(',')
                .append(csv(payload != null ? payload.queryFilter() : null))
                .append(',')
                .append(csv(payload != null ? payload.windowHoursFilter() : null))
                .append(',')
                .append(payload != null ? payload.limit() : 0)
                .append(',')
                .append(payload != null && payload.force())
                .append(',')
                .append(payload != null ? payload.requested() : 0)
                .append(',')
                .append(payload != null ? payload.estimatedQueued() : 0)
                .append(',')
                .append(payload != null ? payload.estimatedSkipped() : 0)
                .append(',')
                .append(payload != null ? payload.estimatedFailed() : 0)
                .append(",,,,,,,,,,,,")
                .append('\n');
        } else {
            for (PreviewQueueDeclinedRequeueDryRunItemDto item : results) {
                sb.append(csv(payload.categoryFilter()))
                    .append(',')
                    .append(csv(payload.forceRequiredFilter()))
                    .append(',')
                    .append(csv(payload.queryFilter()))
                    .append(',')
                    .append(csv(payload.windowHoursFilter()))
                    .append(',')
                    .append(payload.limit())
                    .append(',')
                    .append(payload.force())
                    .append(',')
                    .append(payload.requested())
                    .append(',')
                    .append(payload.estimatedQueued())
                    .append(',')
                    .append(payload.estimatedSkipped())
                    .append(',')
                    .append(payload.estimatedFailed())
                    .append(',')
                    .append(csv(item.documentId()))
                    .append(',')
                    .append(csv(item.category()))
                    .append(',')
                    .append(csv(item.outcome()))
                    .append(',')
                    .append(csv(item.reasonCode()))
                    .append(',')
                    .append(csv(item.message()))
                    .append(',')
                    .append(csv(item.previewStatus()))
                    .append(',')
                    .append(csv(item.previewFailureReason()))
                    .append(',')
                    .append(csv(item.previewFailureCategory()))
                    .append(',')
                    .append(csv(item.previewLastUpdated()))
                    .append(',')
                    .append(csv(item.nextAttemptAt()))
                    .append(',')
                    .append(csv(item.preflightStatus()))
                    .append(',')
                    .append(csv(item.preflightSkipReason()))
                    .append(',')
                    .append(csv(item.preflightRoute()))
                    .append(',')
                    .append(csv(item.preflightPolicyProfile()))
                    .append(',')
                    .append(csv(item.preflightPipeline()))
                    .append('\n');
            }
        }

        sb.append('\n');
        sb.append("reasonCode,outcome,count\n");
        List<PreviewQueueDeclinedRequeueDryRunReasonCountDto> reasonBreakdown = payload != null
            ? payload.reasonBreakdown()
            : List.of();
        if (reasonBreakdown == null || reasonBreakdown.isEmpty()) {
            sb.append("NONE,UNKNOWN,0\n");
            return sb.toString();
        }
        for (PreviewQueueDeclinedRequeueDryRunReasonCountDto item : reasonBreakdown) {
            sb.append(csv(item.reasonCode()))
                .append(',')
                .append(csv(item.outcome()))
                .append(',')
                .append(item.count())
                .append('\n');
        }
        return sb.toString();
    }

    private String buildQueueDeclinedCsv(
        PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot,
        String categoryFilter,
        String forceRequiredFilter,
        String queryFilter,
        Integer windowHoursFilter,
        List<PreviewQueueDeclinedItemDto> items
    ) {
        boolean queueEnabled = snapshot != null && snapshot.queueEnabled();
        long totalDeclined = snapshot != null ? snapshot.totalDeclined() : 0L;
        int sampleLimit = snapshot != null ? snapshot.sampleLimit() : 0;
        boolean sampleTruncated = snapshot != null && snapshot.sampleTruncated();
        int totalSampledItems = snapshot != null && snapshot.items() != null ? snapshot.items().size() : 0;
        int filteredSampledItems = items != null ? items.size() : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("queueEnabled,totalDeclined,sampleLimit,sampleTruncated,categoryFilter,forceRequiredFilter,queryFilter,windowHoursFilter,totalSampledItems,filteredSampledItems,documentId,name,path,mimeType,previewStatus,previewFailureReason,previewFailureCategory,previewLastUpdated,reason,category,governanceKey,declinedAt,nextEligibleAt,forceRequired\n");
        if (items == null || items.isEmpty()) {
            sb.append(queueEnabled)
                .append(',')
                .append(totalDeclined)
                .append(',')
                .append(sampleLimit)
                .append(',')
                .append(sampleTruncated)
                .append(',')
                .append(csv(categoryFilter))
                .append(',')
                .append(csv(forceRequiredFilter))
                .append(',')
                .append(csv(queryFilter))
                .append(',')
                .append(csv(windowHoursFilter))
                .append(',')
                .append(totalSampledItems)
                .append(',')
                .append(filteredSampledItems)
                .append('\n');
            return sb.toString();
        }
        for (PreviewQueueDeclinedItemDto item : items) {
            sb.append(queueEnabled)
                .append(',')
                .append(totalDeclined)
                .append(',')
                .append(sampleLimit)
                .append(',')
                .append(sampleTruncated)
                .append(',')
                .append(csv(categoryFilter))
                .append(',')
                .append(csv(forceRequiredFilter))
                .append(',')
                .append(csv(queryFilter))
                .append(',')
                .append(csv(windowHoursFilter))
                .append(',')
                .append(totalSampledItems)
                .append(',')
                .append(filteredSampledItems)
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
                .append(csv(item.previewFailureReason()))
                .append(',')
                .append(csv(item.previewFailureCategory()))
                .append(',')
                .append(csv(item.previewLastUpdated()))
                .append(',')
                .append(csv(item.reason()))
                .append(',')
                .append(csv(item.category()))
                .append(',')
                .append(csv(item.governanceKey()))
                .append(',')
                .append(csv(item.declinedAt()))
                .append(',')
                .append(csv(item.nextEligibleAt()))
                .append(',')
                .append(item.forceRequired())
                .append('\n');
        }
        return sb.toString();
    }

    private PreviewQueueDeclinedExportAsyncRequestDto copyQueueDeclinedExportAsyncRequest(
        PreviewQueueDeclinedExportAsyncRequestDto request
    ) {
        if (request == null) {
            return new PreviewQueueDeclinedExportAsyncRequestDto(
                DEFAULT_QUEUE_DECLINED_EXPORT_LIMIT,
                null,
                null,
                null,
                null
            );
        }
        return new PreviewQueueDeclinedExportAsyncRequestDto(
            request.limit(),
            request.category(),
            request.forceRequired(),
            request.query(),
            request.windowHours()
        );
    }

    private PreviewQueueDeclinedExportAsyncRequestDto normalizeQueueDeclinedExportAsyncRequest(
        PreviewQueueDeclinedExportAsyncRequestDto request
    ) {
        return new PreviewQueueDeclinedExportAsyncRequestDto(
            normalizeQueueDeclinedExportLimit(request != null ? request.limit() : null),
            normalizeQueueDeclinedCategoryFilter(request != null ? request.category() : null),
            normalizeQueueDeclinedForceRequiredFilter(request != null ? request.forceRequired() : null),
            normalizeQueueDiagnosticsQueryFilter(request != null ? request.query() : null),
            normalizeQueueDeclinedWindowHours(request != null ? request.windowHours() : null)
        );
    }

    private QueueDeclinedExportAsyncTask findActiveQueueDeclinedExportAsyncTask(
        PreviewQueueDeclinedExportAsyncRequestDto request
    ) {
        synchronized (queueDeclinedExportAsyncTaskLock) {
            refreshQueueDeclinedExportAsyncTasksLifecycleLocked();
            Iterator<String> iterator = queueDeclinedExportAsyncTaskOrder.descendingIterator();
            while (iterator.hasNext()) {
                String taskId = iterator.next();
                QueueDeclinedExportAsyncTask task = queueDeclinedExportAsyncTasks.get(taskId);
                if (task == null || !isActiveQueueDeclinedExportAsyncStatus(task.status())) {
                    continue;
                }
                if (matchesQueueDeclinedExportAsyncTaskRequest(task, request)) {
                    return task;
                }
            }
        }
        return null;
    }

    private boolean matchesQueueDeclinedExportAsyncTaskRequest(
        QueueDeclinedExportAsyncTask task,
        PreviewQueueDeclinedExportAsyncRequestDto request
    ) {
        if (task == null || request == null) {
            return false;
        }
        return task.limit() == (request.limit() != null ? request.limit() : normalizeQueueDeclinedExportLimit(null))
            && Objects.equals(
                normalizeQueueDeclinedCategoryFilter(task.categoryFilter()),
                normalizeQueueDeclinedCategoryFilter(request.category())
            )
            && Objects.equals(
                normalizeQueueDeclinedForceRequiredFilter(task.forceRequiredFilter()),
                normalizeQueueDeclinedForceRequiredFilter(request.forceRequired())
            )
            && Objects.equals(
                normalizeQueueDiagnosticsQueryFilter(task.queryFilter()),
                normalizeQueueDiagnosticsQueryFilter(request.query())
            )
            && Objects.equals(
                normalizeQueueDeclinedWindowHours(task.windowHoursFilter()),
                normalizeQueueDeclinedWindowHours(request.windowHours())
            );
    }

    private QueueDeclinedExportAsyncTask createQueueDeclinedExportAsyncTask(
        PreviewQueueDeclinedExportAsyncRequestDto request
    ) {
        String taskId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String actor = resolveAuditUsername();
        QueueDeclinedExportAsyncTask task = new QueueDeclinedExportAsyncTask(
            taskId,
            now,
            null,
            now,
            resolveAsyncTaskTimeoutAt(now),
            resolveAsyncTaskExpiresAt(now),
            resolveTaskActor(actor),
            resolveTaskActor(actor),
            QueueDeclinedExportAsyncStatus.QUEUED,
            null,
            null,
            null,
            null,
            request != null ? request.limit() : normalizeQueueDeclinedExportLimit(null),
            request != null ? request.category() : normalizeQueueDeclinedCategoryFilter(null),
            request != null ? request.forceRequired() : normalizeQueueDeclinedForceRequiredFilter(null),
            request != null ? request.query() : null,
            request != null ? request.windowHours() : null
        );
        synchronized (queueDeclinedExportAsyncTaskLock) {
            queueDeclinedExportAsyncTasks.put(taskId, task);
            queueDeclinedExportAsyncTaskOrder.addLast(taskId);
            trimQueueDeclinedExportAsyncTasksLocked();
        }
        return task;
    }

    private void runQueueDeclinedExportAsyncTask(
        QueueDeclinedExportAsyncTask initialTask,
        PreviewQueueDeclinedExportAsyncRequestDto request
    ) {
        queueDeclinedExportAsyncTasks.computeIfPresent(initialTask.taskId(), (key, current) ->
            current.status() == QueueDeclinedExportAsyncStatus.QUEUED
                ? current.withStatus(QueueDeclinedExportAsyncStatus.RUNNING, resolveAuditUsername())
                : current
        );
        QueueDeclinedExportAsyncTask current = queueDeclinedExportAsyncTasks.get(initialTask.taskId());
        if (current == null) {
            return;
        }
        if (current.isTerminal()) {
            if (current.status() == QueueDeclinedExportAsyncStatus.CANCELLED) {
                auditQueueDeclinedAsyncExportRunCancelled(current);
            }
            return;
        }
        try {
            int safeLimit = request != null && request.limit() != null
                ? request.limit()
                : initialTask.limit();
            String safeCategoryFilter = request != null ? request.category() : initialTask.categoryFilter();
            String safeForceRequiredFilter = request != null ? request.forceRequired() : initialTask.forceRequiredFilter();
            String safeQueryFilter = request != null ? request.query() : initialTask.queryFilter();
            Integer safeWindowHoursFilter = request != null ? request.windowHours() : initialTask.windowHoursFilter();

            PreviewQueueService.PreviewQueueDeclinedSnapshot snapshot = previewQueueService.declinedSnapshot(safeLimit);
            List<PreviewQueueDeclinedItemDto> items = mapQueueDeclinedItems(
                snapshot,
                safeCategoryFilter,
                safeForceRequiredFilter,
                safeQueryFilter,
                safeWindowHoursFilter
            );
            String csv = buildQueueDeclinedCsv(
                snapshot,
                safeCategoryFilter,
                safeForceRequiredFilter,
                safeQueryFilter,
                safeWindowHoursFilter,
                items
            );
            String filename = "preview_queue_declined_" + Instant.now().toEpochMilli() + ".csv";
            byte[] payload = csv.getBytes(StandardCharsets.UTF_8);
            QueueDeclinedExportAsyncTask terminalTask = queueDeclinedExportAsyncTasks.computeIfPresent(initialTask.taskId(), (key, runningTask) ->
                runningTask.status() == QueueDeclinedExportAsyncStatus.CANCELLED
                    ? runningTask
                    : runningTask.complete(filename, payload, resolveAuditUsername())
            );
            if (terminalTask != null) {
                if (terminalTask.status() == QueueDeclinedExportAsyncStatus.COMPLETED) {
                    auditQueueDeclinedAsyncExportRunCompleted(terminalTask, items.size());
                } else if (terminalTask.status() == QueueDeclinedExportAsyncStatus.CANCELLED) {
                    auditQueueDeclinedAsyncExportRunCancelled(terminalTask);
                }
            }
        } catch (Exception e) {
            QueueDeclinedExportAsyncTask terminalTask = queueDeclinedExportAsyncTasks.computeIfPresent(initialTask.taskId(), (key, runningTask) ->
                runningTask.status() == QueueDeclinedExportAsyncStatus.CANCELLED
                    ? runningTask
                    : runningTask.fail(resolveErrorMessage(e), resolveAuditUsername())
            );
            if (terminalTask != null) {
                if (terminalTask.status() == QueueDeclinedExportAsyncStatus.FAILED) {
                    auditQueueDeclinedAsyncExportRunFailed(terminalTask);
                } else if (terminalTask.status() == QueueDeclinedExportAsyncStatus.CANCELLED) {
                    auditQueueDeclinedAsyncExportRunCancelled(terminalTask);
                }
            }
        } finally {
            synchronized (queueDeclinedExportAsyncTaskLock) {
                trimQueueDeclinedExportAsyncTasksLocked();
            }
        }
    }

    private PreviewQueueDeclinedExportAsyncRequestDto toQueueDeclinedExportAsyncRequest(
        QueueDeclinedExportAsyncTask task
    ) {
        if (task == null) {
            return normalizeQueueDeclinedExportAsyncRequest(null);
        }
        return normalizeQueueDeclinedExportAsyncRequest(new PreviewQueueDeclinedExportAsyncRequestDto(
            task.limit(),
            task.categoryFilter(),
            task.forceRequiredFilter(),
            task.queryFilter(),
            task.windowHoursFilter()
        ));
    }

    private List<QueueDeclinedExportAsyncTask> listQueueDeclinedExportAsyncRetryTerminalCandidates(
        int limit,
        QueueDeclinedExportAsyncStatus statusFilter
    ) {
        List<QueueDeclinedExportAsyncTask> candidates = new ArrayList<>();
        synchronized (queueDeclinedExportAsyncTaskLock) {
            refreshQueueDeclinedExportAsyncTasksLifecycleLocked();
            Iterator<String> iterator = queueDeclinedExportAsyncTaskOrder.descendingIterator();
            while (iterator.hasNext() && candidates.size() < limit) {
                String taskId = iterator.next();
                QueueDeclinedExportAsyncTask task = queueDeclinedExportAsyncTasks.get(taskId);
                if (task == null || !task.isTerminal()) {
                    continue;
                }

                if (statusFilter != null) {
                    if (task.status() != statusFilter) {
                        continue;
                    }
                } else if (task.status() != QueueDeclinedExportAsyncStatus.FAILED
                    && task.status() != QueueDeclinedExportAsyncStatus.CANCELLED
                    && task.status() != QueueDeclinedExportAsyncStatus.TIMED_OUT
                    && task.status() != QueueDeclinedExportAsyncStatus.EXPIRED) {
                    continue;
                }

                candidates.add(task);
            }
        }
        return candidates;
    }

    private QueueDeclinedExportAsyncRetryTerminalDryRunComputation computeQueueDeclinedExportAsyncRetryTerminalDryRun(
        String status,
        int limit
    ) {
        QueueDeclinedExportAsyncStatus statusFilter = parseQueueDeclinedExportAsyncStatus(status);
        if (statusFilter != null && isActiveQueueDeclinedExportAsyncStatus(statusFilter)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "status filter only supports terminal states: COMPLETED, CANCELLED, FAILED, TIMED_OUT, EXPIRED"
            );
        }

        int boundedLimit = clamp(
            limit <= 0 ? DEFAULT_QUEUE_DECLINED_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT : limit,
            1,
            MAX_QUEUE_DECLINED_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT
        );
        List<QueueDeclinedExportAsyncTask> candidates = listQueueDeclinedExportAsyncRetryTerminalCandidates(
            boundedLimit,
            statusFilter
        );

        int requested = candidates.size();
        int retryable = 0;
        int skipped = 0;
        List<PreviewQueueDeclinedExportAsyncRetryTerminalDryRunItemDto> results = new ArrayList<>();
        Map<String, Long> reasonCounter = new HashMap<>();

        for (QueueDeclinedExportAsyncTask sourceTask : candidates) {
            if (sourceTask == null || !sourceTask.isTerminal()) {
                skipped += 1;
                String sourceStatus = sourceTask != null && sourceTask.status() != null
                    ? sourceTask.status().name()
                    : "UNKNOWN";
                PreviewQueueDeclinedExportAsyncRetryTerminalDryRunItemDto item =
                    new PreviewQueueDeclinedExportAsyncRetryTerminalDryRunItemDto(
                        sourceTask != null ? sourceTask.taskId() : null,
                        sourceStatus,
                        "SKIPPED",
                        "SOURCE_TASK_NOT_TERMINAL",
                        "Source task is missing or not terminal"
                    );
                results.add(item);
                String reasonKey = "SOURCE_TASK_NOT_TERMINAL|SKIPPED";
                reasonCounter.merge(reasonKey, 1L, Long::sum);
                continue;
            }

            retryable += 1;
            PreviewQueueDeclinedExportAsyncRetryTerminalDryRunItemDto item =
                new PreviewQueueDeclinedExportAsyncRetryTerminalDryRunItemDto(
                    sourceTask.taskId(),
                    sourceTask.status().name(),
                    "RETRYABLE",
                    "TERMINAL_TASK_RETRYABLE",
                    "Terminal async export task can be retried"
                );
            results.add(item);
            String reasonKey = "TERMINAL_TASK_RETRYABLE|RETRYABLE";
            reasonCounter.merge(reasonKey, 1L, Long::sum);
        }

        List<PreviewQueueDeclinedExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown = reasonCounter.entrySet()
            .stream()
            .map(entry -> {
                String[] keyParts = entry.getKey().split("\\|", 2);
                String reasonCode = keyParts.length > 0 ? keyParts[0] : "UNKNOWN";
                String outcome = keyParts.length > 1 ? keyParts[1] : "UNKNOWN";
                return new PreviewQueueDeclinedExportAsyncRetryTerminalDryRunReasonCountDto(
                    reasonCode,
                    outcome,
                    entry.getValue()
                );
            })
            .sorted(Comparator.comparingLong(PreviewQueueDeclinedExportAsyncRetryTerminalDryRunReasonCountDto::count)
                .reversed()
                .thenComparing(PreviewQueueDeclinedExportAsyncRetryTerminalDryRunReasonCountDto::reasonCode))
            .toList();

        String statusFilterName = statusFilter != null ? statusFilter.name() : "FAILED|CANCELLED|TIMED_OUT|EXPIRED";
        String message = requested == 0
            ? "No terminal async preview queue declined export tasks matched retry dry-run filters"
            : String.format(
                "Dry-run identified %d/%d retryable terminal async preview queue declined export tasks (skipped=%d)",
                retryable,
                requested,
                skipped
            );

        return new QueueDeclinedExportAsyncRetryTerminalDryRunComputation(
            requested,
            retryable,
            skipped,
            boundedLimit,
            statusFilterName,
            message,
            results,
            reasonBreakdown,
            candidates
        );
    }

    private String buildQueueDeclinedExportAsyncRetryTerminalDryRunCsv(
        QueueDeclinedExportAsyncRetryTerminalDryRunComputation dryRun
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("statusFilter,limit,requested,retryable,skipped,sourceTaskId,sourceStatus,outcome,reasonCode,message\n");
        List<PreviewQueueDeclinedExportAsyncRetryTerminalDryRunItemDto> results = dryRun != null
            ? dryRun.results()
            : List.of();
        if (results.isEmpty()) {
            sb.append(csv(dryRun != null ? dryRun.statusFilterName() : "FAILED|CANCELLED|TIMED_OUT|EXPIRED"))
                .append(',')
                .append(dryRun != null ? dryRun.limit() : 0)
                .append(',')
                .append(dryRun != null ? dryRun.requested() : 0)
                .append(',')
                .append(dryRun != null ? dryRun.retryable() : 0)
                .append(',')
                .append(dryRun != null ? dryRun.skipped() : 0)
                .append(",,,,,")
                .append('\n');
        } else {
            for (PreviewQueueDeclinedExportAsyncRetryTerminalDryRunItemDto item : results) {
                sb.append(csv(dryRun.statusFilterName()))
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
        List<PreviewQueueDeclinedExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown = dryRun != null
            ? dryRun.reasonBreakdown()
            : List.of();
        if (reasonBreakdown.isEmpty()) {
            sb.append("NONE,UNKNOWN,0\n");
            return sb.toString();
        }
        for (PreviewQueueDeclinedExportAsyncRetryTerminalDryRunReasonCountDto item : reasonBreakdown) {
            sb.append(csv(item.reasonCode()))
                .append(',')
                .append(csv(item.outcome()))
                .append(',')
                .append(item.count())
                .append('\n');
        }
        return sb.toString();
    }

    private List<String> normalizeQueueDeclinedRetrySourceTaskIds(List<String> sourceTaskIds) {
        if (sourceTaskIds == null || sourceTaskIds.isEmpty()) {
            return List.of();
        }
        List<String> normalized = sourceTaskIds.stream()
            .filter(taskId -> taskId != null && !taskId.isBlank())
            .map(String::trim)
            .distinct()
            .toList();
        if (normalized.size() > MAX_QUEUE_DECLINED_EXPORT_ASYNC_RETRY_SELECTED_IDS) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "sourceTaskIds exceeds max size: " + MAX_QUEUE_DECLINED_EXPORT_ASYNC_RETRY_SELECTED_IDS
            );
        }
        return normalized;
    }

    private PreviewQueueDeclinedRequeueDryRunExportAsyncRequestDto toQueueDeclinedRequeueDryRunExportAsyncRequest(
        QueueDeclinedRequeueDryRunExportAsyncTask task
    ) {
        if (task == null) {
            return new PreviewQueueDeclinedRequeueDryRunExportAsyncRequestDto(
                DEFAULT_QUEUE_DECLINED_ACTION_LIMIT,
                null,
                null,
                null,
                null,
                Boolean.TRUE
            );
        }
        return new PreviewQueueDeclinedRequeueDryRunExportAsyncRequestDto(
            task.limit(),
            task.categoryFilter(),
            task.forceRequiredFilter(),
            task.queryFilter(),
            task.windowHoursFilter(),
            task.force()
        );
    }

    private List<QueueDeclinedRequeueDryRunExportAsyncTask> listQueueDeclinedRequeueDryRunExportAsyncRetryTerminalCandidates(
        int limit,
        QueueDeclinedRequeueDryRunExportAsyncStatus statusFilter
    ) {
        List<QueueDeclinedRequeueDryRunExportAsyncTask> candidates = new ArrayList<>();
        synchronized (queueDeclinedRequeueDryRunExportAsyncTaskLock) {
            refreshQueueDeclinedRequeueDryRunExportAsyncTasksLifecycleLocked();
            Iterator<String> iterator = queueDeclinedRequeueDryRunExportAsyncTaskOrder.descendingIterator();
            while (iterator.hasNext() && candidates.size() < limit) {
                String taskId = iterator.next();
                QueueDeclinedRequeueDryRunExportAsyncTask task = queueDeclinedRequeueDryRunExportAsyncTasks.get(taskId);
                if (task == null || !task.isTerminal()) {
                    continue;
                }

                if (statusFilter != null) {
                    if (task.status() != statusFilter) {
                        continue;
                    }
                } else if (task.status() != QueueDeclinedRequeueDryRunExportAsyncStatus.FAILED
                    && task.status() != QueueDeclinedRequeueDryRunExportAsyncStatus.CANCELLED
                    && task.status() != QueueDeclinedRequeueDryRunExportAsyncStatus.TIMED_OUT
                    && task.status() != QueueDeclinedRequeueDryRunExportAsyncStatus.EXPIRED) {
                    continue;
                }
                candidates.add(task);
            }
        }
        return candidates;
    }

    private QueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunComputation computeQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRun(
        String status,
        int limit
    ) {
        QueueDeclinedRequeueDryRunExportAsyncStatus statusFilter = parseQueueDeclinedRequeueDryRunExportAsyncStatus(status);
        if (statusFilter != null && isActiveQueueDeclinedRequeueDryRunExportAsyncStatus(statusFilter)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "status filter only supports terminal states: COMPLETED, CANCELLED, FAILED, TIMED_OUT, EXPIRED"
            );
        }

        int boundedLimit = clamp(
            limit <= 0 ? DEFAULT_QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT : limit,
            1,
            MAX_QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_ASYNC_RETRY_TERMINAL_LIMIT
        );
        List<QueueDeclinedRequeueDryRunExportAsyncTask> candidates =
            listQueueDeclinedRequeueDryRunExportAsyncRetryTerminalCandidates(boundedLimit, statusFilter);

        int requested = candidates.size();
        int retryable = 0;
        int skipped = 0;
        List<PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunItemDto> results = new ArrayList<>();
        Map<String, Long> reasonCounter = new HashMap<>();

        for (QueueDeclinedRequeueDryRunExportAsyncTask sourceTask : candidates) {
            if (sourceTask == null || !sourceTask.isTerminal()) {
                skipped += 1;
                String sourceStatus = sourceTask != null && sourceTask.status() != null
                    ? sourceTask.status().name()
                    : "UNKNOWN";
                PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunItemDto item =
                    new PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunItemDto(
                        sourceTask != null ? sourceTask.taskId() : null,
                        sourceStatus,
                        "SKIPPED",
                        "SOURCE_TASK_NOT_TERMINAL",
                        "Source task is missing or not terminal"
                    );
                results.add(item);
                reasonCounter.merge("SOURCE_TASK_NOT_TERMINAL|SKIPPED", 1L, Long::sum);
                continue;
            }

            retryable += 1;
            PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunItemDto item =
                new PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunItemDto(
                    sourceTask.taskId(),
                    sourceTask.status().name(),
                    "RETRYABLE",
                    "TERMINAL_TASK_RETRYABLE",
                    "Terminal async export task can be retried"
                );
            results.add(item);
            reasonCounter.merge("TERMINAL_TASK_RETRYABLE|RETRYABLE", 1L, Long::sum);
        }

        List<PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown = reasonCounter.entrySet()
            .stream()
            .map(entry -> {
                String[] keyParts = entry.getKey().split("\\|", 2);
                String reasonCode = keyParts.length > 0 ? keyParts[0] : "UNKNOWN";
                String outcome = keyParts.length > 1 ? keyParts[1] : "UNKNOWN";
                return new PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunReasonCountDto(
                    reasonCode,
                    outcome,
                    entry.getValue()
                );
            })
            .sorted(Comparator.comparingLong(PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunReasonCountDto::count)
                .reversed()
                .thenComparing(PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunReasonCountDto::reasonCode))
            .toList();

        String statusFilterName = statusFilter != null ? statusFilter.name() : "FAILED|CANCELLED|TIMED_OUT|EXPIRED";
        String message = requested == 0
            ? "No terminal declined requeue dry-run async export tasks matched retry dry-run filters"
            : String.format(
                "Dry-run identified %d/%d retryable terminal declined requeue dry-run async export tasks (skipped=%d)",
                retryable,
                requested,
                skipped
            );

        return new QueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunComputation(
            requested,
            retryable,
            skipped,
            boundedLimit,
            statusFilterName,
            message,
            results,
            reasonBreakdown,
            candidates
        );
    }

    private String buildQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunCsv(
        QueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunComputation dryRun
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("statusFilter,limit,requested,retryable,skipped,sourceTaskId,sourceStatus,outcome,reasonCode,message\n");
        List<PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunItemDto> results = dryRun != null
            ? dryRun.results()
            : List.of();
        if (results.isEmpty()) {
            sb.append(csv(dryRun != null ? dryRun.statusFilterName() : "FAILED|CANCELLED|TIMED_OUT|EXPIRED"))
                .append(',')
                .append(dryRun != null ? dryRun.limit() : 0)
                .append(',')
                .append(dryRun != null ? dryRun.requested() : 0)
                .append(',')
                .append(dryRun != null ? dryRun.retryable() : 0)
                .append(',')
                .append(dryRun != null ? dryRun.skipped() : 0)
                .append(",,,,,")
                .append('\n');
        } else {
            for (PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunItemDto item : results) {
                sb.append(csv(dryRun.statusFilterName()))
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
        List<PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown = dryRun != null
            ? dryRun.reasonBreakdown()
            : List.of();
        if (reasonBreakdown.isEmpty()) {
            sb.append("NONE,UNKNOWN,0\n");
            return sb.toString();
        }
        for (PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunReasonCountDto item : reasonBreakdown) {
            sb.append(csv(item.reasonCode()))
                .append(',')
                .append(csv(item.outcome()))
                .append(',')
                .append(item.count())
                .append('\n');
        }
        return sb.toString();
    }

    private List<String> normalizeQueueDeclinedRequeueDryRunRetrySourceTaskIds(List<String> sourceTaskIds) {
        if (sourceTaskIds == null || sourceTaskIds.isEmpty()) {
            return List.of();
        }
        List<String> normalized = sourceTaskIds.stream()
            .filter(taskId -> taskId != null && !taskId.isBlank())
            .map(String::trim)
            .distinct()
            .toList();
        if (normalized.size() > MAX_QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_ASYNC_RETRY_SELECTED_IDS) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "sourceTaskIds exceeds max size: " + MAX_QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_ASYNC_RETRY_SELECTED_IDS
            );
        }
        return normalized;
    }

    private int normalizeQueueDeclinedExportLimit(Integer limit) {
        int requestedLimit = limit != null ? limit : DEFAULT_QUEUE_DECLINED_EXPORT_LIMIT;
        if (requestedLimit <= 0) {
            requestedLimit = DEFAULT_QUEUE_DECLINED_EXPORT_LIMIT;
        }
        return clamp(requestedLimit, 1, MAX_QUEUE_DECLINED_EXPORT_LIMIT);
    }

    private void trimQueueDeclinedExportAsyncTasksLocked() {
        if (queueDeclinedExportAsyncTasks.size() <= MAX_QUEUE_DECLINED_EXPORT_ASYNC_TASKS) {
            return;
        }
        Iterator<String> iterator = queueDeclinedExportAsyncTaskOrder.iterator();
        while (queueDeclinedExportAsyncTasks.size() > MAX_QUEUE_DECLINED_EXPORT_ASYNC_TASKS && iterator.hasNext()) {
            String candidateTaskId = iterator.next();
            QueueDeclinedExportAsyncTask candidate = queueDeclinedExportAsyncTasks.get(candidateTaskId);
            if (candidate == null) {
                iterator.remove();
                continue;
            }
            if (!candidate.isTerminal()) {
                continue;
            }
            if (queueDeclinedExportAsyncTasks.remove(candidateTaskId, candidate)) {
                iterator.remove();
            }
        }
        Iterator<String> fallbackIterator = queueDeclinedExportAsyncTaskOrder.iterator();
        while (queueDeclinedExportAsyncTasks.size() > MAX_QUEUE_DECLINED_EXPORT_ASYNC_TASKS && fallbackIterator.hasNext()) {
            String candidateTaskId = fallbackIterator.next();
            if (queueDeclinedExportAsyncTasks.remove(candidateTaskId) != null) {
                fallbackIterator.remove();
            }
        }
    }

    private void refreshQueueDeclinedExportAsyncTasksLifecycleLocked() {
        Instant now = Instant.now();
        List<String> taskIds = new ArrayList<>(queueDeclinedExportAsyncTaskOrder);
        for (String taskId : taskIds) {
            queueDeclinedExportAsyncTasks.computeIfPresent(taskId, (key, task) ->
                applyQueueDeclinedExportAsyncTaskLifecycle(task, now)
            );
        }
        queueDeclinedExportAsyncTaskOrder.removeIf(taskId -> !queueDeclinedExportAsyncTasks.containsKey(taskId));
    }

    private QueueDeclinedExportAsyncTask applyQueueDeclinedExportAsyncTaskLifecycle(
        QueueDeclinedExportAsyncTask task,
        Instant now
    ) {
        if (task == null) {
            return null;
        }
        QueueDeclinedExportAsyncTask current = task;
        if (isActiveQueueDeclinedExportAsyncStatus(current.status())
            && current.timeoutAt() != null
            && now != null
            && now.isAfter(current.timeoutAt())) {
            current = current.timeout(now, "system");
        }
        if (current.status() != QueueDeclinedExportAsyncStatus.EXPIRED
            && current.expiresAt() != null
            && now != null
            && now.isAfter(current.expiresAt())
            && current.isTerminal()) {
            current = current.expire(now, "system");
        }
        return current;
    }

    private QueueDeclinedExportAsyncListPage listQueueDeclinedExportAsyncTasks(
        int skipCount,
        int maxItems,
        QueueDeclinedExportAsyncStatus statusFilter
    ) {
        List<PreviewQueueDeclinedExportAsyncStatusResponseDto> items = new ArrayList<>();
        int matchedCount = 0;
        synchronized (queueDeclinedExportAsyncTaskLock) {
            refreshQueueDeclinedExportAsyncTasksLifecycleLocked();
            Iterator<String> iterator = queueDeclinedExportAsyncTaskOrder.descendingIterator();
            while (iterator.hasNext()) {
                String taskId = iterator.next();
                QueueDeclinedExportAsyncTask task = queueDeclinedExportAsyncTasks.get(taskId);
                if (task == null) {
                    continue;
                }
                if (statusFilter != null && task.status() != statusFilter) {
                    continue;
                }
                if (matchedCount >= skipCount && items.size() < maxItems) {
                    items.add(toQueueDeclinedExportAsyncStatusResponse(task));
                }
                matchedCount += 1;
            }
        }
        return new QueueDeclinedExportAsyncListPage(matchedCount, items);
    }

    private PreviewQueueDeclinedExportAsyncSummaryResponseDto buildQueueDeclinedExportAsyncSummary(
        QueueDeclinedExportAsyncStatus statusFilter
    ) {
        long queuedCount = 0L;
        long runningCount = 0L;
        long completedCount = 0L;
        long cancelledCount = 0L;
        long failedCount = 0L;
        long timedOutCount = 0L;
        long expiredCount = 0L;

        synchronized (queueDeclinedExportAsyncTaskLock) {
            refreshQueueDeclinedExportAsyncTasksLifecycleLocked();
            for (QueueDeclinedExportAsyncTask task : queueDeclinedExportAsyncTasks.values()) {
                if (task == null) {
                    continue;
                }
                if (statusFilter != null && task.status() != statusFilter) {
                    continue;
                }
                switch (task.status()) {
                    case QUEUED -> queuedCount += 1;
                    case RUNNING -> runningCount += 1;
                    case COMPLETED -> completedCount += 1;
                    case CANCELLED -> cancelledCount += 1;
                    case FAILED -> failedCount += 1;
                    case TIMED_OUT -> timedOutCount += 1;
                    case EXPIRED -> expiredCount += 1;
                    default -> {
                        // no-op
                    }
                }
            }
        }

        long totalCount = queuedCount + runningCount + completedCount + cancelledCount + failedCount + timedOutCount + expiredCount;
        long activeCount = queuedCount + runningCount;
        long terminalCount = completedCount + cancelledCount + failedCount + timedOutCount + expiredCount;
        return new PreviewQueueDeclinedExportAsyncSummaryResponseDto(
            totalCount,
            queuedCount,
            runningCount,
            completedCount,
            cancelledCount,
            failedCount,
            timedOutCount,
            expiredCount,
            activeCount,
            terminalCount
        );
    }

    private long cleanupQueueDeclinedExportAsyncTasksLocked(
        QueueDeclinedExportAsyncStatus statusFilter,
        List<QueueDeclinedExportAsyncTask> deletedTasks
    ) {
        Set<String> taskIdsToDelete = new HashSet<>();
        for (Map.Entry<String, QueueDeclinedExportAsyncTask> entry : queueDeclinedExportAsyncTasks.entrySet()) {
            QueueDeclinedExportAsyncTask task = entry.getValue();
            if (task == null) {
                taskIdsToDelete.add(entry.getKey());
                continue;
            }
            if (statusFilter == null) {
                if (task.isTerminal()) {
                    taskIdsToDelete.add(entry.getKey());
                    if (deletedTasks != null) {
                        deletedTasks.add(task);
                    }
                }
            } else if (task.status() == statusFilter) {
                taskIdsToDelete.add(entry.getKey());
                if (deletedTasks != null) {
                    deletedTasks.add(task);
                }
            }
        }

        if (taskIdsToDelete.isEmpty()) {
            queueDeclinedExportAsyncTaskOrder.removeIf(taskId -> !queueDeclinedExportAsyncTasks.containsKey(taskId));
            return 0L;
        }

        taskIdsToDelete.forEach(queueDeclinedExportAsyncTasks::remove);
        queueDeclinedExportAsyncTaskOrder.removeIf(taskId ->
            taskIdsToDelete.contains(taskId) || !queueDeclinedExportAsyncTasks.containsKey(taskId)
        );
        return taskIdsToDelete.size();
    }

    private QueueDeclinedExportAsyncStatus parseQueueDeclinedExportAsyncStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return QueueDeclinedExportAsyncStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                String.format(QUEUE_DECLINED_EXPORT_ASYNC_STATUS_ERROR_TEMPLATE, status)
            );
        }
    }

    private boolean isActiveQueueDeclinedExportAsyncStatus(QueueDeclinedExportAsyncStatus status) {
        return status == QueueDeclinedExportAsyncStatus.QUEUED
            || status == QueueDeclinedExportAsyncStatus.RUNNING;
    }

    private int countActiveQueueDeclinedExportAsyncTasksLocked() {
        int activeCount = 0;
        for (QueueDeclinedExportAsyncTask task : queueDeclinedExportAsyncTasks.values()) {
            if (task != null && isActiveQueueDeclinedExportAsyncStatus(task.status())) {
                activeCount += 1;
            }
        }
        return activeCount;
    }

    private PreviewQueueDeclinedExportAsyncStatusResponseDto toQueueDeclinedExportAsyncStatusResponse(
        QueueDeclinedExportAsyncTask task
    ) {
        return new PreviewQueueDeclinedExportAsyncStatusResponseDto(
            task.taskId(),
            task.status().name(),
            task.error(),
            task.createdAt(),
            task.startedAt(),
            task.updatedAt(),
            task.timeoutAt(),
            task.expiresAt(),
            task.finishedAt(),
            task.status() == QueueDeclinedExportAsyncStatus.COMPLETED ? task.filename() : null,
            task.createdBy(),
            task.updatedBy()
        );
    }

    private PreviewQueueDeclinedRequeueDryRunExportAsyncRequestDto copyQueueDeclinedRequeueDryRunExportAsyncRequest(
        PreviewQueueDeclinedRequeueDryRunExportAsyncRequestDto request
    ) {
        if (request == null) {
            return new PreviewQueueDeclinedRequeueDryRunExportAsyncRequestDto(
                DEFAULT_QUEUE_DECLINED_ACTION_LIMIT,
                null,
                null,
                null,
                null,
                null
            );
        }
        return new PreviewQueueDeclinedRequeueDryRunExportAsyncRequestDto(
            request.limit(),
            request.category(),
            request.forceRequired(),
            request.query(),
            request.windowHours(),
            request.force()
        );
    }

    private PreviewQueueDeclinedRequeueDryRunExportAsyncRequestDto normalizeQueueDeclinedRequeueDryRunExportAsyncRequest(
        PreviewQueueDeclinedRequeueDryRunExportAsyncRequestDto request
    ) {
        return new PreviewQueueDeclinedRequeueDryRunExportAsyncRequestDto(
            normalizeQueueDeclinedRequeueDryRunExportLimit(request != null ? request.limit() : null),
            normalizeQueueDeclinedCategoryFilter(request != null ? request.category() : null),
            normalizeQueueDeclinedForceRequiredFilter(request != null ? request.forceRequired() : null),
            normalizeQueueDiagnosticsQueryFilter(request != null ? request.query() : null),
            normalizeQueueDeclinedWindowHours(request != null ? request.windowHours() : null),
            request != null && request.force() != null ? request.force() : Boolean.TRUE
        );
    }

    private int normalizeQueueDeclinedRequeueDryRunExportLimit(Integer limit) {
        int requestedLimit = limit != null ? limit : DEFAULT_QUEUE_DECLINED_ACTION_LIMIT;
        if (requestedLimit <= 0) {
            requestedLimit = DEFAULT_QUEUE_DECLINED_ACTION_LIMIT;
        }
        return clamp(requestedLimit, 1, MAX_QUEUE_DECLINED_ACTION_LIMIT);
    }

    private QueueDeclinedRequeueDryRunExportAsyncTask findActiveQueueDeclinedRequeueDryRunExportAsyncTask(
        PreviewQueueDeclinedRequeueDryRunExportAsyncRequestDto request
    ) {
        synchronized (queueDeclinedRequeueDryRunExportAsyncTaskLock) {
            refreshQueueDeclinedRequeueDryRunExportAsyncTasksLifecycleLocked();
            Iterator<String> iterator = queueDeclinedRequeueDryRunExportAsyncTaskOrder.descendingIterator();
            while (iterator.hasNext()) {
                String taskId = iterator.next();
                QueueDeclinedRequeueDryRunExportAsyncTask task = queueDeclinedRequeueDryRunExportAsyncTasks.get(taskId);
                if (task == null || !isActiveQueueDeclinedRequeueDryRunExportAsyncStatus(task.status())) {
                    continue;
                }
                if (matchesQueueDeclinedRequeueDryRunExportAsyncTaskRequest(task, request)) {
                    return task;
                }
            }
        }
        return null;
    }

    private boolean matchesQueueDeclinedRequeueDryRunExportAsyncTaskRequest(
        QueueDeclinedRequeueDryRunExportAsyncTask task,
        PreviewQueueDeclinedRequeueDryRunExportAsyncRequestDto request
    ) {
        if (task == null || request == null) {
            return false;
        }
        return task.limit() == normalizeQueueDeclinedRequeueDryRunExportLimit(request.limit())
            && Objects.equals(
                normalizeQueueDeclinedCategoryFilter(task.categoryFilter()),
                normalizeQueueDeclinedCategoryFilter(request.category())
            )
            && Objects.equals(
                normalizeQueueDeclinedForceRequiredFilter(task.forceRequiredFilter()),
                normalizeQueueDeclinedForceRequiredFilter(request.forceRequired())
            )
            && Objects.equals(
                normalizeQueueDiagnosticsQueryFilter(task.queryFilter()),
                normalizeQueueDiagnosticsQueryFilter(request.query())
            )
            && Objects.equals(
                normalizeQueueDeclinedWindowHours(task.windowHoursFilter()),
                normalizeQueueDeclinedWindowHours(request.windowHours())
            )
            && task.force() == (request.force() != null ? request.force() : Boolean.TRUE);
    }

    private QueueDeclinedRequeueDryRunExportAsyncTask createQueueDeclinedRequeueDryRunExportAsyncTask(
        PreviewQueueDeclinedRequeueDryRunExportAsyncRequestDto request
    ) {
        String taskId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String actor = resolveAuditUsername();
        QueueDeclinedRequeueDryRunExportAsyncTask task = new QueueDeclinedRequeueDryRunExportAsyncTask(
            taskId,
            now,
            null,
            now,
            resolveAsyncTaskTimeoutAt(now),
            resolveAsyncTaskExpiresAt(now),
            resolveTaskActor(actor),
            resolveTaskActor(actor),
            QueueDeclinedRequeueDryRunExportAsyncStatus.QUEUED,
            null,
            null,
            null,
            null,
            request != null ? normalizeQueueDeclinedRequeueDryRunExportLimit(request.limit()) : normalizeQueueDeclinedRequeueDryRunExportLimit(null),
            request != null ? normalizeQueueDeclinedCategoryFilter(request.category()) : normalizeQueueDeclinedCategoryFilter(null),
            request != null ? normalizeQueueDeclinedForceRequiredFilter(request.forceRequired()) : normalizeQueueDeclinedForceRequiredFilter(null),
            request != null ? normalizeQueueDiagnosticsQueryFilter(request.query()) : null,
            request != null ? normalizeQueueDeclinedWindowHours(request.windowHours()) : null,
            request == null || request.force() == null || request.force()
        );
        synchronized (queueDeclinedRequeueDryRunExportAsyncTaskLock) {
            queueDeclinedRequeueDryRunExportAsyncTasks.put(taskId, task);
            queueDeclinedRequeueDryRunExportAsyncTaskOrder.addLast(taskId);
            trimQueueDeclinedRequeueDryRunExportAsyncTasksLocked();
        }
        return task;
    }

    private void runQueueDeclinedRequeueDryRunExportAsyncTask(
        QueueDeclinedRequeueDryRunExportAsyncTask initialTask,
        PreviewQueueDeclinedRequeueDryRunExportAsyncRequestDto request
    ) {
        queueDeclinedRequeueDryRunExportAsyncTasks.computeIfPresent(initialTask.taskId(), (key, current) ->
            current.status() == QueueDeclinedRequeueDryRunExportAsyncStatus.QUEUED
                ? current.withStatus(QueueDeclinedRequeueDryRunExportAsyncStatus.RUNNING, resolveAuditUsername())
                : current
        );
        QueueDeclinedRequeueDryRunExportAsyncTask current = queueDeclinedRequeueDryRunExportAsyncTasks.get(initialTask.taskId());
        if (current == null) {
            return;
        }
        if (current.isTerminal()) {
            if (current.status() == QueueDeclinedRequeueDryRunExportAsyncStatus.CANCELLED) {
                auditQueueDeclinedRequeueDryRunAsyncExportRunCancelled(current);
            }
            return;
        }

        try {
            int safeLimit = request != null && request.limit() != null
                ? normalizeQueueDeclinedRequeueDryRunExportLimit(request.limit())
                : initialTask.limit();
            String safeCategoryFilter = request != null ? normalizeQueueDeclinedCategoryFilter(request.category()) : initialTask.categoryFilter();
            String safeForceRequiredFilter = request != null
                ? normalizeQueueDeclinedForceRequiredFilter(request.forceRequired())
                : initialTask.forceRequiredFilter();
            String safeQueryFilter = request != null ? normalizeQueueDiagnosticsQueryFilter(request.query()) : initialTask.queryFilter();
            Integer safeWindowHoursFilter = request != null ? normalizeQueueDeclinedWindowHours(request.windowHours()) : initialTask.windowHoursFilter();
            boolean safeForce = request == null || request.force() == null || request.force();

            PreviewQueueDeclinedRequeueDryRunResponseDto payload = computeQueueDeclinedRequeueDryRun(
                safeLimit,
                safeCategoryFilter,
                safeForceRequiredFilter,
                safeQueryFilter,
                safeWindowHoursFilter,
                safeForce
            );
            String csv = buildQueueDeclinedRequeueDryRunCsv(payload);
            String filename = "preview_queue_declined_requeue_dry_run_" + Instant.now().toEpochMilli() + ".csv";
            byte[] csvPayload = csv.getBytes(StandardCharsets.UTF_8);
            QueueDeclinedRequeueDryRunExportAsyncTask terminalTask = queueDeclinedRequeueDryRunExportAsyncTasks.computeIfPresent(
                initialTask.taskId(),
                (key, runningTask) -> runningTask.status() == QueueDeclinedRequeueDryRunExportAsyncStatus.CANCELLED
                    ? runningTask
                    : runningTask.complete(filename, csvPayload, resolveAuditUsername())
            );
            if (terminalTask != null) {
                if (terminalTask.status() == QueueDeclinedRequeueDryRunExportAsyncStatus.COMPLETED) {
                    auditQueueDeclinedRequeueDryRunAsyncExportRunCompleted(terminalTask, payload.results().size());
                } else if (terminalTask.status() == QueueDeclinedRequeueDryRunExportAsyncStatus.CANCELLED) {
                    auditQueueDeclinedRequeueDryRunAsyncExportRunCancelled(terminalTask);
                }
            }
        } catch (Exception ex) {
            QueueDeclinedRequeueDryRunExportAsyncTask terminalTask = queueDeclinedRequeueDryRunExportAsyncTasks.computeIfPresent(
                initialTask.taskId(),
                (key, runningTask) -> runningTask.status() == QueueDeclinedRequeueDryRunExportAsyncStatus.CANCELLED
                    ? runningTask
                    : runningTask.fail(resolveErrorMessage(ex), resolveAuditUsername())
            );
            if (terminalTask != null) {
                if (terminalTask.status() == QueueDeclinedRequeueDryRunExportAsyncStatus.FAILED) {
                    auditQueueDeclinedRequeueDryRunAsyncExportRunFailed(terminalTask);
                } else if (terminalTask.status() == QueueDeclinedRequeueDryRunExportAsyncStatus.CANCELLED) {
                    auditQueueDeclinedRequeueDryRunAsyncExportRunCancelled(terminalTask);
                }
            }
        } finally {
            synchronized (queueDeclinedRequeueDryRunExportAsyncTaskLock) {
                trimQueueDeclinedRequeueDryRunExportAsyncTasksLocked();
            }
        }
    }

    private void trimQueueDeclinedRequeueDryRunExportAsyncTasksLocked() {
        if (queueDeclinedRequeueDryRunExportAsyncTasks.size() <= MAX_QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_ASYNC_TASKS) {
            return;
        }
        Iterator<String> iterator = queueDeclinedRequeueDryRunExportAsyncTaskOrder.iterator();
        while (queueDeclinedRequeueDryRunExportAsyncTasks.size() > MAX_QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_ASYNC_TASKS
            && iterator.hasNext()) {
            String candidateTaskId = iterator.next();
            QueueDeclinedRequeueDryRunExportAsyncTask candidate = queueDeclinedRequeueDryRunExportAsyncTasks.get(candidateTaskId);
            if (candidate == null) {
                iterator.remove();
                continue;
            }
            if (!candidate.isTerminal()) {
                continue;
            }
            if (queueDeclinedRequeueDryRunExportAsyncTasks.remove(candidateTaskId, candidate)) {
                iterator.remove();
            }
        }
        Iterator<String> fallbackIterator = queueDeclinedRequeueDryRunExportAsyncTaskOrder.iterator();
        while (queueDeclinedRequeueDryRunExportAsyncTasks.size() > MAX_QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_ASYNC_TASKS
            && fallbackIterator.hasNext()) {
            String candidateTaskId = fallbackIterator.next();
            if (queueDeclinedRequeueDryRunExportAsyncTasks.remove(candidateTaskId) != null) {
                fallbackIterator.remove();
            }
        }
    }

    private void refreshQueueDeclinedRequeueDryRunExportAsyncTasksLifecycleLocked() {
        Instant now = Instant.now();
        List<String> taskIds = new ArrayList<>(queueDeclinedRequeueDryRunExportAsyncTaskOrder);
        for (String taskId : taskIds) {
            queueDeclinedRequeueDryRunExportAsyncTasks.computeIfPresent(taskId, (key, task) ->
                applyQueueDeclinedRequeueDryRunExportAsyncTaskLifecycle(task, now)
            );
        }
        queueDeclinedRequeueDryRunExportAsyncTaskOrder.removeIf(taskId ->
            !queueDeclinedRequeueDryRunExportAsyncTasks.containsKey(taskId)
        );
    }

    private QueueDeclinedRequeueDryRunExportAsyncTask applyQueueDeclinedRequeueDryRunExportAsyncTaskLifecycle(
        QueueDeclinedRequeueDryRunExportAsyncTask task,
        Instant now
    ) {
        if (task == null) {
            return null;
        }
        QueueDeclinedRequeueDryRunExportAsyncTask current = task;
        if (isActiveQueueDeclinedRequeueDryRunExportAsyncStatus(current.status())
            && current.timeoutAt() != null
            && now != null
            && now.isAfter(current.timeoutAt())) {
            current = current.timeout(now, "system");
        }
        if (current.status() != QueueDeclinedRequeueDryRunExportAsyncStatus.EXPIRED
            && current.expiresAt() != null
            && now != null
            && now.isAfter(current.expiresAt())
            && current.isTerminal()) {
            current = current.expire(now, "system");
        }
        return current;
    }

    private QueueDeclinedRequeueDryRunExportAsyncListPage listQueueDeclinedRequeueDryRunExportAsyncTasks(
        int skipCount,
        int maxItems,
        QueueDeclinedRequeueDryRunExportAsyncStatus statusFilter
    ) {
        List<PreviewQueueDeclinedRequeueDryRunExportAsyncStatusResponseDto> items = new ArrayList<>();
        int matchedCount = 0;
        synchronized (queueDeclinedRequeueDryRunExportAsyncTaskLock) {
            refreshQueueDeclinedRequeueDryRunExportAsyncTasksLifecycleLocked();
            Iterator<String> iterator = queueDeclinedRequeueDryRunExportAsyncTaskOrder.descendingIterator();
            while (iterator.hasNext()) {
                String taskId = iterator.next();
                QueueDeclinedRequeueDryRunExportAsyncTask task = queueDeclinedRequeueDryRunExportAsyncTasks.get(taskId);
                if (task == null) {
                    continue;
                }
                if (statusFilter != null && task.status() != statusFilter) {
                    continue;
                }
                if (matchedCount >= skipCount && items.size() < maxItems) {
                    items.add(toQueueDeclinedRequeueDryRunExportAsyncStatusResponse(task));
                }
                matchedCount += 1;
            }
        }
        return new QueueDeclinedRequeueDryRunExportAsyncListPage(matchedCount, items);
    }

    private PreviewQueueDeclinedRequeueDryRunExportAsyncSummaryResponseDto buildQueueDeclinedRequeueDryRunExportAsyncSummary(
        QueueDeclinedRequeueDryRunExportAsyncStatus statusFilter
    ) {
        long queuedCount = 0L;
        long runningCount = 0L;
        long completedCount = 0L;
        long cancelledCount = 0L;
        long failedCount = 0L;
        long timedOutCount = 0L;
        long expiredCount = 0L;
        synchronized (queueDeclinedRequeueDryRunExportAsyncTaskLock) {
            refreshQueueDeclinedRequeueDryRunExportAsyncTasksLifecycleLocked();
            for (QueueDeclinedRequeueDryRunExportAsyncTask task : queueDeclinedRequeueDryRunExportAsyncTasks.values()) {
                if (task == null) {
                    continue;
                }
                if (statusFilter != null && task.status() != statusFilter) {
                    continue;
                }
                switch (task.status()) {
                    case QUEUED -> queuedCount += 1;
                    case RUNNING -> runningCount += 1;
                    case COMPLETED -> completedCount += 1;
                    case CANCELLED -> cancelledCount += 1;
                    case FAILED -> failedCount += 1;
                    case TIMED_OUT -> timedOutCount += 1;
                    case EXPIRED -> expiredCount += 1;
                    default -> {
                        // no-op
                    }
                }
            }
        }
        long totalCount = queuedCount + runningCount + completedCount + cancelledCount + failedCount + timedOutCount + expiredCount;
        long activeCount = queuedCount + runningCount;
        long terminalCount = completedCount + cancelledCount + failedCount + timedOutCount + expiredCount;
        return new PreviewQueueDeclinedRequeueDryRunExportAsyncSummaryResponseDto(
            totalCount,
            queuedCount,
            runningCount,
            completedCount,
            cancelledCount,
            failedCount,
            timedOutCount,
            expiredCount,
            activeCount,
            terminalCount
        );
    }

    private long cleanupQueueDeclinedRequeueDryRunExportAsyncTasksLocked(
        QueueDeclinedRequeueDryRunExportAsyncStatus statusFilter,
        List<QueueDeclinedRequeueDryRunExportAsyncTask> deletedTasks
    ) {
        Set<String> taskIdsToDelete = new HashSet<>();
        for (Map.Entry<String, QueueDeclinedRequeueDryRunExportAsyncTask> entry : queueDeclinedRequeueDryRunExportAsyncTasks.entrySet()) {
            QueueDeclinedRequeueDryRunExportAsyncTask task = entry.getValue();
            if (task == null) {
                taskIdsToDelete.add(entry.getKey());
                continue;
            }
            if (statusFilter == null) {
                if (task.isTerminal()) {
                    taskIdsToDelete.add(entry.getKey());
                    if (deletedTasks != null) {
                        deletedTasks.add(task);
                    }
                }
            } else if (task.status() == statusFilter) {
                taskIdsToDelete.add(entry.getKey());
                if (deletedTasks != null) {
                    deletedTasks.add(task);
                }
            }
        }

        if (taskIdsToDelete.isEmpty()) {
            queueDeclinedRequeueDryRunExportAsyncTaskOrder.removeIf(taskId ->
                !queueDeclinedRequeueDryRunExportAsyncTasks.containsKey(taskId)
            );
            return 0L;
        }

        taskIdsToDelete.forEach(queueDeclinedRequeueDryRunExportAsyncTasks::remove);
        queueDeclinedRequeueDryRunExportAsyncTaskOrder.removeIf(taskId ->
            taskIdsToDelete.contains(taskId) || !queueDeclinedRequeueDryRunExportAsyncTasks.containsKey(taskId)
        );
        return taskIdsToDelete.size();
    }

    private QueueDeclinedRequeueDryRunExportAsyncStatus parseQueueDeclinedRequeueDryRunExportAsyncStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return QueueDeclinedRequeueDryRunExportAsyncStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                String.format(QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_ASYNC_STATUS_ERROR_TEMPLATE, status)
            );
        }
    }

    private boolean isActiveQueueDeclinedRequeueDryRunExportAsyncStatus(
        QueueDeclinedRequeueDryRunExportAsyncStatus status
    ) {
        return status == QueueDeclinedRequeueDryRunExportAsyncStatus.QUEUED
            || status == QueueDeclinedRequeueDryRunExportAsyncStatus.RUNNING;
    }

    private int countActiveQueueDeclinedRequeueDryRunExportAsyncTasksLocked() {
        int activeCount = 0;
        for (QueueDeclinedRequeueDryRunExportAsyncTask task : queueDeclinedRequeueDryRunExportAsyncTasks.values()) {
            if (task != null && isActiveQueueDeclinedRequeueDryRunExportAsyncStatus(task.status())) {
                activeCount += 1;
            }
        }
        return activeCount;
    }

    private PreviewQueueDeclinedRequeueDryRunExportAsyncStatusResponseDto toQueueDeclinedRequeueDryRunExportAsyncStatusResponse(
        QueueDeclinedRequeueDryRunExportAsyncTask task
    ) {
        return new PreviewQueueDeclinedRequeueDryRunExportAsyncStatusResponseDto(
            task.taskId(),
            task.status().name(),
            task.error(),
            task.createdAt(),
            task.startedAt(),
            task.updatedAt(),
            task.timeoutAt(),
            task.expiresAt(),
            task.finishedAt(),
            task.status() == QueueDeclinedRequeueDryRunExportAsyncStatus.COMPLETED ? task.filename() : null,
            task.createdBy(),
            task.updatedBy(),
            task.limit(),
            task.categoryFilter(),
            task.forceRequiredFilter(),
            task.queryFilter(),
            task.windowHoursFilter(),
            task.force()
        );
    }

    private static String normalizeQueueDiagnosticsStateFilter(String stateFilter) {
        String normalized = normalizedUpperOrDefault(stateFilter, "ALL");
        return switch (normalized) {
            case "ALL", "QUEUED", "RUNNING", "CANCEL_REQUESTED" -> normalized;
            default -> "ALL";
        };
    }

    private static String normalizeQueueDeclinedCategoryFilter(String categoryFilter) {
        String normalized = normalizedUpperOrDefault(categoryFilter, "ANY");
        return normalized.isBlank() ? "ANY" : normalized;
    }

    private static String normalizeQueueDeclinedForceRequiredFilter(String forceRequiredFilter) {
        String normalized = normalizedUpperOrDefault(forceRequiredFilter, "ANY");
        return switch (normalized) {
            case "ANY", "YES", "NO" -> normalized;
            default -> "ANY";
        };
    }

    private static Integer normalizeQueueDeclinedWindowHours(Integer windowHours) {
        if (windowHours == null || windowHours <= 0) {
            return null;
        }
        return clamp(windowHours, 1, MAX_QUEUE_DECLINED_WINDOW_HOURS);
    }

    private static String normalizeQueueDiagnosticsQueryFilter(String queryFilter) {
        if (queryFilter == null || queryFilter.isBlank()) {
            return null;
        }
        return queryFilter.trim();
    }

    private static String resolveQueueDiagnosticsItemState(boolean running, boolean cancelRequested) {
        if (cancelRequested) {
            return "CANCEL_REQUESTED";
        }
        return running ? "RUNNING" : "QUEUED";
    }

    private static boolean matchesQueueDiagnosticsQuery(PreviewQueueDiagnosticsItemDto item, String queryFilterLower) {
        if (queryFilterLower == null || queryFilterLower.isBlank()) {
            return true;
        }
        return containsQueueDiagnosticsText(item.documentId(), queryFilterLower)
            || containsQueueDiagnosticsText(item.name(), queryFilterLower)
            || containsQueueDiagnosticsText(item.path(), queryFilterLower)
            || containsQueueDiagnosticsText(item.mimeType(), queryFilterLower)
            || containsQueueDiagnosticsText(item.previewStatus(), queryFilterLower)
            || containsQueueDiagnosticsText(item.previewFailureReason(), queryFilterLower)
            || containsQueueDiagnosticsText(item.previewFailureCategory(), queryFilterLower)
            || containsQueueDiagnosticsText(item.queueState(), queryFilterLower)
            || containsQueueDiagnosticsText(item.governanceKey(), queryFilterLower);
    }

    private static boolean containsQueueDiagnosticsText(Object value, String queryFilterLower) {
        if (value == null || queryFilterLower == null || queryFilterLower.isBlank()) {
            return false;
        }
        return String.valueOf(value).toLowerCase(Locale.ROOT).contains(queryFilterLower);
    }

    private static boolean matchesQueueDeclinedQuery(PreviewQueueDeclinedItemDto item, String queryFilterLower) {
        if (queryFilterLower == null || queryFilterLower.isBlank()) {
            return true;
        }
        return containsQueueDiagnosticsText(item.documentId(), queryFilterLower)
            || containsQueueDiagnosticsText(item.name(), queryFilterLower)
            || containsQueueDiagnosticsText(item.path(), queryFilterLower)
            || containsQueueDiagnosticsText(item.mimeType(), queryFilterLower)
            || containsQueueDiagnosticsText(item.previewStatus(), queryFilterLower)
            || containsQueueDiagnosticsText(item.previewFailureReason(), queryFilterLower)
            || containsQueueDiagnosticsText(item.previewFailureCategory(), queryFilterLower)
            || containsQueueDiagnosticsText(item.reason(), queryFilterLower)
            || containsQueueDiagnosticsText(item.category(), queryFilterLower)
            || containsQueueDiagnosticsText(item.governanceKey(), queryFilterLower);
    }

    private static Instant resolveQueueDeclinedSince(Integer windowHoursFilter) {
        if (windowHoursFilter == null || windowHoursFilter <= 0) {
            return null;
        }
        return Instant.now().minusSeconds(windowHoursFilter.longValue() * 3600L);
    }

    private static boolean matchesQueueDeclinedWindow(PreviewQueueDeclinedItemDto item, Instant declinedSince) {
        if (declinedSince == null) {
            return true;
        }
        if (item == null || item.declinedAt() == null) {
            return false;
        }
        return !item.declinedAt().isBefore(declinedSince);
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private static int resolveListMaxItems(Integer maxItems, Integer limit, int upperBound) {
        int resolved = maxItems != null ? maxItems : (limit != null ? limit : 20);
        return clamp(resolved, 1, upperBound);
    }

    private ResponseEntity<PreviewQueueDeclinedExportAsyncCreateResponseDto> acceptedQueueDeclinedExportAsyncCreateResponse(
        PreviewQueueDeclinedExportAsyncCreateResponseDto body,
        String taskId
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .header(HttpHeaders.LOCATION, queueDeclinedExportAsyncTaskStatusLocation(taskId))
            .body(body);
    }

    private ResponseEntity<PreviewQueueDeclinedExportAsyncRetryTerminalResponseDto> acceptedQueueDeclinedExportAsyncRetryTerminalResponse(
        PreviewQueueDeclinedExportAsyncRetryTerminalResponseDto body
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .header(HttpHeaders.LOCATION, queueDeclinedExportAsyncTaskListLocation())
            .body(body);
    }

    private ResponseEntity<PreviewQueueDeclinedRequeueDryRunExportAsyncCreateResponseDto> acceptedQueueDeclinedRequeueDryRunExportAsyncCreateResponse(
        PreviewQueueDeclinedRequeueDryRunExportAsyncCreateResponseDto body,
        String taskId
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .header(HttpHeaders.LOCATION, queueDeclinedRequeueDryRunExportAsyncTaskStatusLocation(taskId))
            .body(body);
    }

    private ResponseEntity<PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalResponseDto> acceptedQueueDeclinedRequeueDryRunExportAsyncRetryTerminalResponse(
        PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalResponseDto body
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .header(HttpHeaders.LOCATION, queueDeclinedRequeueDryRunExportAsyncTaskListLocation())
            .body(body);
    }

    private static String queueDeclinedExportAsyncTaskStatusLocation(String taskId) {
        return UriComponentsBuilder
            .fromPath("/api/v1/preview/diagnostics/queue/declined/export-async/{taskId}")
            .buildAndExpand(taskId)
            .toUriString();
    }

    private static String queueDeclinedExportAsyncTaskListLocation() {
        return UriComponentsBuilder
            .fromPath("/api/v1/preview/diagnostics/queue/declined/export-async")
            .build()
            .toUriString();
    }

    private static String queueDeclinedRequeueDryRunExportAsyncTaskStatusLocation(String taskId) {
        return UriComponentsBuilder
            .fromPath("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/{taskId}")
            .buildAndExpand(taskId)
            .toUriString();
    }

    private static String queueDeclinedRequeueDryRunExportAsyncTaskListLocation() {
        return UriComponentsBuilder
            .fromPath("/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async")
            .build()
            .toUriString();
    }

    private static String renditionResourcesExportAsyncTaskListLocation() {
        return UriComponentsBuilder
            .fromPath("/api/v1/preview/diagnostics/renditions/resources/export-async")
            .build()
            .toUriString();
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

    private static List<PreviewStatus> failureStatuses() {
        List<PreviewStatus> statuses = new ArrayList<>();
        statuses.add(PreviewStatus.FAILED);
        statuses.add(PreviewStatus.UNSUPPORTED);
        return statuses;
    }

    private static Specification<Document> buildRenditionSummarySpecification(LocalDateTime updatedSince) {
        return (root, query, cb) -> {
            if (updatedSince == null) {
                return cb.equal(root.get("deleted"), false);
            }
            return cb.and(
                cb.equal(root.get("deleted"), false),
                cb.greaterThanOrEqualTo(root.get("previewLastUpdated").as(LocalDateTime.class), updatedSince)
            );
        };
    }

    private static Pageable buildRenditionSummaryPageable(int limit) {
        return PageRequest.of(
            0,
            limit,
            Sort.by(
                Sort.Order.desc("previewLastUpdated").nullsLast(),
                Sort.Order.desc("lastModifiedDate").nullsLast(),
                Sort.Order.desc("createdDate").nullsLast()
            )
        );
    }

    private static String deriveRenditionStatus(Document document) {
        EffectivePreviewSemantics previewSemantics = resolveEffectivePreviewSemantics(document);
        if (previewSemantics.status() == null) {
            return "NOT_CREATED";
        }

        return switch (previewSemantics.status()) {
            case "READY" -> "CREATED";
            case "PROCESSING" -> "PROCESSING";
            case "UNSUPPORTED" -> "UNSUPPORTED";
            case "FAILED" -> {
                if (PreviewFailureClassifier.CATEGORY_TEMPORARY.equalsIgnoreCase(previewSemantics.failureCategory())) {
                    yield "STALE";
                }
                if (PreviewFailureClassifier.CATEGORY_UNSUPPORTED.equalsIgnoreCase(previewSemantics.failureCategory())) {
                    yield "UNSUPPORTED";
                }
                yield "FAILED";
            }
            default -> "NOT_CREATED";
        };
    }

    private static boolean shouldAggregateFailureReason(String renditionStatus) {
        return "STALE".equals(renditionStatus)
            || "FAILED".equals(renditionStatus)
            || "UNSUPPORTED".equals(renditionStatus);
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "UNSPECIFIED";
        }
        return reason.replaceAll("\\s+", " ").trim();
    }

    private static EffectivePreviewSemantics resolveEffectivePreviewSemantics(Document document) {
        if (document == null) {
            return new EffectivePreviewSemantics(null, null, null);
        }
        String status = PreviewStatusSemantics.resolveEffectiveStatus(document);
        String failureReason = resolveEffectivePreviewFailureReason(document);
        String failureCategory = PreviewFailureClassifier.classify(
            status,
            normalizeMimeType(document.getMimeType()),
            failureReason
        );
        return new EffectivePreviewSemantics(status, failureReason, failureCategory);
    }

    private static String resolveEffectivePreviewFailureReason(Document document) {
        return normalizeReasonOrNull(PreviewStatusSemantics.resolveEffectiveFailureReason(document));
    }

    private static String normalizeUpperOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
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

    private EffectivePreviewSnapshot resolveEffectivePreviewSnapshot(
        Document document,
        String fallbackPreviewStatus,
        String fallbackFailureReason,
        String fallbackFailureCategory,
        LocalDateTime fallbackPreviewLastUpdated
    ) {
        RenditionResourceService.EffectivePreviewSnapshot sharedSnapshot =
            renditionResourceService.resolveEffectivePreviewSnapshot(
                document,
                fallbackPreviewStatus,
                fallbackFailureReason,
                fallbackFailureCategory,
                fallbackPreviewLastUpdated
            );
        if (sharedSnapshot != null) {
            return new EffectivePreviewSnapshot(
                sharedSnapshot.previewStatus(),
                normalizeReasonOrNull(sharedSnapshot.previewFailureReason()),
                normalizeUpperOrNull(sharedSnapshot.previewFailureCategory()),
                sharedSnapshot.previewLastUpdated()
            );
        }
        RenditionResourceService.RenditionSummary renditionSummary = document != null
            ? renditionResourceService.summarizeDocument(document)
            : RenditionResourceService.RenditionSummary.empty(null);
        if (renditionSummary != null && renditionSummary.document()) {
            return new EffectivePreviewSnapshot(
                renditionSummary.previewStatus(),
                normalizeReasonOrNull(renditionSummary.previewFailureReason()),
                normalizeUpperOrNull(renditionSummary.previewFailureCategory()),
                renditionSummary.previewLastUpdated()
            );
        }
        if (document != null) {
            EffectivePreviewSemantics semantics = resolveEffectivePreviewSemantics(document);
            return new EffectivePreviewSnapshot(
                semantics.status(),
                semantics.failureReason(),
                normalizeUpperOrNull(semantics.failureCategory()),
                document.getPreviewLastUpdated()
            );
        }
        return new EffectivePreviewSnapshot(
            normalizeUpperOrNull(fallbackPreviewStatus),
            normalizeReasonOrNull(fallbackFailureReason),
            normalizeUpperOrNull(fallbackFailureCategory),
            fallbackPreviewLastUpdated
        );
    }

    private static String normalizeReasonOrNull(String reason) {
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

    private static String normalizeMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return "";
        }
        return mimeType.split(";")[0].trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeHashOrNull(String hash) {
        if (hash == null || hash.isBlank()) {
            return null;
        }
        return hash.trim().toLowerCase(Locale.ROOT);
    }

    private record EffectivePreviewSemantics(
        String status,
        String failureReason,
        String failureCategory
    ) {}

    private record EffectivePreviewSnapshot(
        String status,
        String failureReason,
        String failureCategory,
        LocalDateTime previewLastUpdated
    ) {}

    private static String resolveErrorMessage(Exception e) {
        if (e == null) {
            return "Unknown export error";
        }
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private String buildDeadLetterCsv(
        List<PreviewDeadLetterRegistry.DeadLetterEntry> entries,
        Map<UUID, Document> documentsById
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("entryKey,documentId,renditionKey,name,path,mimeType,previewStatus,category,policyKey,sourceStage,attempts,occurrences,replayCount,failedAt,lastReplayAt,reason\n");
        for (PreviewDeadLetterRegistry.DeadLetterEntry entry : entries) {
            Document document = documentsById.get(entry.documentId());
            String name = document != null ? document.getName() : "";
            String path = document != null ? document.getPath() : "";
            String mimeType = document != null ? normalizeMimeType(document.getMimeType()) : "";
            String previewStatus = Objects.requireNonNullElse(resolveEffectivePreviewStatus(document), "");
            sb.append(csv(entry.entryKey()))
                .append(',')
                .append(csv(entry.documentId()))
                .append(',')
                .append(csv(entry.renditionKey()))
                .append(',')
                .append(csv(name))
                .append(',')
                .append(csv(path))
                .append(',')
                .append(csv(mimeType))
                .append(',')
                .append(csv(previewStatus))
                .append(',')
                .append(csv(entry.category()))
                .append(',')
                .append(csv(entry.policyKey()))
                .append(',')
                .append(csv(entry.sourceStage()))
                .append(',')
                .append(entry.attempts())
                .append(',')
                .append(entry.occurrences())
                .append(',')
                .append(entry.replayCount())
                .append(',')
                .append(csv(entry.failedAt()))
                .append(',')
                .append(csv(entry.lastReplayAt()))
                .append(',')
                .append(csv(entry.reason()))
                .append('\n');
        }
        return sb.toString();
    }

    private String buildRenditionResourcesCsv(List<Document> documents) {
        StringBuilder sb = new StringBuilder();
        sb.append("documentId,name,path,mimeType,previewStatus,renditionStatus,previewFailureCategory,previewFailureReason,previewLastUpdated\n");
        for (Document document : documents) {
            EffectivePreviewSemantics previewSemantics = resolveEffectivePreviewSemantics(document);
            String normalizedMimeType = normalizeMimeType(document.getMimeType());
            String renditionStatus = deriveRenditionStatus(document);
            boolean includeFailureDetails = shouldAggregateFailureReason(renditionStatus);
            sb.append(csv(document.getId()))
                .append(',')
                .append(csv(document.getName()))
                .append(',')
                .append(csv(document.getPath()))
                .append(',')
                .append(csv(normalizedMimeType))
                .append(',')
                .append(csv(previewSemantics.status()))
                .append(',')
                .append(csv(renditionStatus))
                .append(',')
                .append(csv(includeFailureDetails ? previewSemantics.failureCategory() : ""))
                .append(',')
                .append(csv(includeFailureDetails ? previewSemantics.failureReason() : ""))
                .append(',')
                .append(csv(document.getPreviewLastUpdated()))
                .append('\n');
        }
        return sb.toString();
    }

    private int normalizeRenditionResourcesExportLimit(int limit) {
        int requestedLimit = limit <= 0 ? DEFAULT_RENDITION_RESOURCE_EXPORT_LIMIT : limit;
        return clamp(requestedLimit, 1, MAX_RENDITION_RESOURCE_EXPORT_LIMIT);
    }

    private List<Document> collectRenditionResourcesForExport(int days, int limit) {
        LocalDateTime updatedSince = resolveUpdatedSince(days);
        Specification<Document> windowSpec = buildRenditionSummarySpecification(updatedSince);
        Pageable pageable = buildRenditionSummaryPageable(limit);
        return documentRepository.findAll(windowSpec, pageable).getContent();
    }

    private PreviewRenditionResourcesExportAsyncRequestDto copyRenditionResourcesExportRequest(
        PreviewRenditionResourcesExportAsyncRequestDto request
    ) {
        if (request == null) {
            return new PreviewRenditionResourcesExportAsyncRequestDto(DEFAULT_WINDOW_DAYS, DEFAULT_RENDITION_RESOURCE_EXPORT_LIMIT);
        }
        return new PreviewRenditionResourcesExportAsyncRequestDto(request.days(), request.limit());
    }

    private PreviewRenditionResourcesExportAsyncRequestDto normalizeRenditionResourcesExportAsyncRequest(
        PreviewRenditionResourcesExportAsyncRequestDto request
    ) {
        int safeDays = normalizeWindowDays(request != null && request.days() != null ? request.days() : DEFAULT_WINDOW_DAYS);
        int safeLimit = normalizeRenditionResourcesExportLimit(request != null && request.limit() != null
            ? request.limit()
            : DEFAULT_RENDITION_RESOURCE_EXPORT_LIMIT);
        return new PreviewRenditionResourcesExportAsyncRequestDto(safeDays, safeLimit);
    }

    private RenditionResourcesExportAsyncTask createRenditionResourcesExportAsyncTask(
        PreviewRenditionResourcesExportAsyncRequestDto request
    ) {
        String taskId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String actor = resolveAuditUsername();
        PreviewRenditionResourcesExportAsyncRequestDto normalizedRequest = normalizeRenditionResourcesExportAsyncRequest(request);
        RenditionResourcesExportAsyncTask task = new RenditionResourcesExportAsyncTask(
            taskId,
            now,
            null,
            now,
            resolveAsyncTaskTimeoutAt(now),
            resolveAsyncTaskExpiresAt(now),
            resolveTaskActor(actor),
            resolveTaskActor(actor),
            RenditionResourcesExportAsyncStatus.QUEUED,
            null,
            null,
            null,
            null
        );
        synchronized (renditionResourcesExportAsyncTaskLock) {
            renditionResourcesExportAsyncTasks.put(taskId, task);
            renditionResourcesExportAsyncTaskRequests.put(taskId, normalizedRequest);
            renditionResourcesExportAsyncTaskOrder.addLast(taskId);
            trimRenditionResourcesExportAsyncTasksLocked();
        }
        return task;
    }

    private RenditionResourcesExportAsyncTask findActiveRenditionResourcesExportAsyncTaskByRequestLocked(
        PreviewRenditionResourcesExportAsyncRequestDto request
    ) {
        PreviewRenditionResourcesExportAsyncRequestDto normalizedRequest = normalizeRenditionResourcesExportAsyncRequest(request);
        Iterator<String> iterator = renditionResourcesExportAsyncTaskOrder.descendingIterator();
        while (iterator.hasNext()) {
            String candidateTaskId = iterator.next();
            RenditionResourcesExportAsyncTask candidateTask = renditionResourcesExportAsyncTasks.get(candidateTaskId);
            if (candidateTask == null || candidateTask.isTerminal()) {
                continue;
            }
            PreviewRenditionResourcesExportAsyncRequestDto candidateRequest = normalizeRenditionResourcesExportAsyncRequest(
                renditionResourcesExportAsyncTaskRequests.get(candidateTaskId)
            );
            if (Objects.equals(candidateRequest, normalizedRequest)) {
                return candidateTask;
            }
        }
        return null;
    }

    private PreviewRenditionResourcesExportAsyncCreateResponseDto toRenditionResourcesExportAsyncCreateResponse(
        RenditionResourcesExportAsyncTask task,
        boolean deduplicated,
        String deduplicatedFromTaskId,
        String message
    ) {
        if (task == null) {
            return null;
        }
        return new PreviewRenditionResourcesExportAsyncCreateResponseDto(
            task.taskId(),
            task.status().name(),
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

    private ResponseEntity<PreviewRenditionResourcesExportAsyncCreateResponseDto> acceptedRenditionResourcesExportAsyncCreateResponse(
        PreviewRenditionResourcesExportAsyncCreateResponseDto payload,
        String taskId
    ) {
        String normalizedTaskId = taskId != null ? taskId.trim() : "";
        String location = normalizedTaskId.isEmpty()
            ? "/api/v1/preview/diagnostics/renditions/resources/export-async"
            : "/api/v1/preview/diagnostics/renditions/resources/export-async/" + normalizedTaskId;
        return ResponseEntity.accepted()
            .header(HttpHeaders.LOCATION, location)
            .body(payload);
    }

    private ResponseEntity<PreviewRenditionResourcesExportAsyncRetryTerminalResponseDto> acceptedRenditionResourcesExportAsyncRetryTerminalResponse(
        PreviewRenditionResourcesExportAsyncRetryTerminalResponseDto body
    ) {
        return ResponseEntity.accepted()
            .header(HttpHeaders.LOCATION, renditionResourcesExportAsyncTaskListLocation())
            .body(body);
    }

    private void runRenditionResourcesExportAsyncTask(RenditionResourcesExportAsyncTask initialTask) {
        renditionResourcesExportAsyncTasks.computeIfPresent(initialTask.taskId(), (key, current) ->
            current.status() == RenditionResourcesExportAsyncStatus.QUEUED
                ? current.withStatus(RenditionResourcesExportAsyncStatus.RUNNING, resolveAuditUsername())
                : current
        );
        RenditionResourcesExportAsyncTask current = renditionResourcesExportAsyncTasks.get(initialTask.taskId());
        if (current == null) {
            return;
        }
        if (current.isTerminal()) {
            return;
        }
        try {
            PreviewRenditionResourcesExportAsyncRequestDto request = normalizeRenditionResourcesExportAsyncRequest(
                renditionResourcesExportAsyncTaskRequests.get(initialTask.taskId())
            );
            int safeDays = request.days() != null ? request.days() : DEFAULT_WINDOW_DAYS;
            int safeLimit = request.limit() != null ? request.limit() : DEFAULT_RENDITION_RESOURCE_EXPORT_LIMIT;
            List<Document> samples = collectRenditionResourcesForExport(safeDays, safeLimit);
            String csvContent = buildRenditionResourcesCsv(samples);
            String filename = "preview_rendition_resources_" + Instant.now().toEpochMilli() + ".csv";
            byte[] payload = csvContent.getBytes(StandardCharsets.UTF_8);
            renditionResourcesExportAsyncTasks.computeIfPresent(initialTask.taskId(), (key, runningTask) ->
                runningTask.isTerminal()
                    ? runningTask
                    : runningTask.complete(filename, payload, resolveAuditUsername())
            );
        } catch (Exception e) {
            renditionResourcesExportAsyncTasks.computeIfPresent(initialTask.taskId(), (key, runningTask) ->
                runningTask.isTerminal()
                    ? runningTask
                    : runningTask.fail(resolveErrorMessage(e), resolveAuditUsername())
            );
        } finally {
            synchronized (renditionResourcesExportAsyncTaskLock) {
                trimRenditionResourcesExportAsyncTasksLocked();
            }
        }
    }

    private void trimRenditionResourcesExportAsyncTasksLocked() {
        if (renditionResourcesExportAsyncTasks.size() <= MAX_RENDITION_RESOURCE_EXPORT_ASYNC_TASKS) {
            return;
        }
        Iterator<String> iterator = renditionResourcesExportAsyncTaskOrder.iterator();
        while (renditionResourcesExportAsyncTasks.size() > MAX_RENDITION_RESOURCE_EXPORT_ASYNC_TASKS && iterator.hasNext()) {
            String candidateTaskId = iterator.next();
            RenditionResourcesExportAsyncTask candidate = renditionResourcesExportAsyncTasks.get(candidateTaskId);
            if (candidate == null) {
                iterator.remove();
                continue;
            }
            if (!candidate.isTerminal()) {
                continue;
            }
            if (renditionResourcesExportAsyncTasks.remove(candidateTaskId, candidate)) {
                renditionResourcesExportAsyncTaskRequests.remove(candidateTaskId);
                iterator.remove();
            }
        }
        Iterator<String> fallbackIterator = renditionResourcesExportAsyncTaskOrder.iterator();
        while (renditionResourcesExportAsyncTasks.size() > MAX_RENDITION_RESOURCE_EXPORT_ASYNC_TASKS && fallbackIterator.hasNext()) {
            String candidateTaskId = fallbackIterator.next();
            if (renditionResourcesExportAsyncTasks.remove(candidateTaskId) != null) {
                renditionResourcesExportAsyncTaskRequests.remove(candidateTaskId);
                fallbackIterator.remove();
            }
        }
    }

    private RenditionResourcesExportAsyncListPage listRenditionResourcesExportAsyncTasks(
        int skipCount,
        int maxItems,
        RenditionResourcesExportAsyncStatus statusFilter
    ) {
        List<PreviewRenditionResourcesExportAsyncStatusResponseDto> items = new ArrayList<>();
        int matchedCount = 0;
        synchronized (renditionResourcesExportAsyncTaskLock) {
            refreshRenditionResourcesExportAsyncTasksLifecycleLocked();
            Iterator<String> iterator = renditionResourcesExportAsyncTaskOrder.descendingIterator();
            while (iterator.hasNext()) {
                String taskId = iterator.next();
                RenditionResourcesExportAsyncTask task = renditionResourcesExportAsyncTasks.get(taskId);
                if (task == null) {
                    continue;
                }
                if (statusFilter != null && task.status() != statusFilter) {
                    continue;
                }
                if (matchedCount >= skipCount && items.size() < maxItems) {
                    items.add(toRenditionResourcesExportAsyncStatusResponse(task));
                }
                matchedCount += 1;
            }
        }
        return new RenditionResourcesExportAsyncListPage(matchedCount, items);
    }

    private List<RenditionResourcesExportAsyncTask> listRenditionResourcesExportAsyncRetryTerminalCandidates(
        int limit,
        RenditionResourcesExportAsyncStatus statusFilter
    ) {
        List<RenditionResourcesExportAsyncTask> candidates = new ArrayList<>();
        synchronized (renditionResourcesExportAsyncTaskLock) {
            refreshRenditionResourcesExportAsyncTasksLifecycleLocked();
            Iterator<String> iterator = renditionResourcesExportAsyncTaskOrder.descendingIterator();
            while (iterator.hasNext() && candidates.size() < limit) {
                String taskId = iterator.next();
                RenditionResourcesExportAsyncTask task = renditionResourcesExportAsyncTasks.get(taskId);
                if (task == null || !task.isTerminal()) {
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

    private RenditionResourcesExportAsyncRetryTerminalDryRunComputation computeRenditionResourcesExportAsyncRetryTerminalDryRun(
        RenditionResourcesExportAsyncStatus statusFilter,
        int limit
    ) {
        List<RenditionResourcesExportAsyncTask> candidates = listRenditionResourcesExportAsyncRetryTerminalCandidates(
            limit,
            statusFilter
        );
        int requested = candidates.size();
        int retryable = 0;
        int skipped = 0;
        List<PreviewRenditionResourcesExportAsyncRetryTerminalDryRunItemDto> results = new ArrayList<>();
        Map<String, Long> reasonCounter = new HashMap<>();

        for (RenditionResourcesExportAsyncTask candidate : candidates) {
            if (candidate == null) {
                skipped += 1;
                String reasonCode = "SOURCE_TASK_MISSING";
                String outcome = "SKIPPED";
                reasonCounter.merge(reasonCode + "|" + outcome, 1L, Long::sum);
                results.add(new PreviewRenditionResourcesExportAsyncRetryTerminalDryRunItemDto(
                    null,
                    "UNKNOWN",
                    outcome,
                    reasonCode,
                    "Source task not found"
                ));
                continue;
            }

            synchronized (renditionResourcesExportAsyncTaskLock) {
                refreshRenditionResourcesExportAsyncTasksLifecycleLocked();
                RenditionResourcesExportAsyncTask sourceTask = renditionResourcesExportAsyncTasks.get(candidate.taskId());
                if (sourceTask == null) {
                    skipped += 1;
                    String reasonCode = "SOURCE_TASK_MISSING";
                    String outcome = "SKIPPED";
                    reasonCounter.merge(reasonCode + "|" + outcome, 1L, Long::sum);
                    results.add(new PreviewRenditionResourcesExportAsyncRetryTerminalDryRunItemDto(
                        candidate.taskId(),
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
                    results.add(new PreviewRenditionResourcesExportAsyncRetryTerminalDryRunItemDto(
                        sourceTask.taskId(),
                        sourceTask.status().name(),
                        outcome,
                        reasonCode,
                        "Source task is not terminal"
                    ));
                    continue;
                }

                PreviewRenditionResourcesExportAsyncRequestDto sourceRequest =
                    renditionResourcesExportAsyncTaskRequests.get(sourceTask.taskId());
                PreviewRenditionResourcesExportAsyncRequestDto normalizedRequest =
                    normalizeRenditionResourcesExportAsyncRequest(sourceRequest);
                RenditionResourcesExportAsyncTask activeTask =
                    findActiveRenditionResourcesExportAsyncTaskByRequestLocked(normalizedRequest);

                retryable += 1;
                String reasonCode = activeTask != null
                    ? "ACTIVE_TASK_WILL_BE_REUSED"
                    : "TERMINAL_TASK_RETRYABLE";
                String message = activeTask != null
                    ? "Retry would reuse active async export task with same filters"
                    : "Terminal async export task can be retried";
                String outcome = "RETRYABLE";
                reasonCounter.merge(reasonCode + "|" + outcome, 1L, Long::sum);
                results.add(new PreviewRenditionResourcesExportAsyncRetryTerminalDryRunItemDto(
                    sourceTask.taskId(),
                    sourceTask.status().name(),
                    outcome,
                    reasonCode,
                    message
                ));
            }
        }

        List<PreviewRenditionResourcesExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown = reasonCounter.entrySet()
            .stream()
            .map((entry) -> {
                String[] parts = entry.getKey().split("\\|", 2);
                String reasonCode = parts.length > 0 ? parts[0] : "UNKNOWN";
                String outcome = parts.length > 1 ? parts[1] : "UNKNOWN";
                return new PreviewRenditionResourcesExportAsyncRetryTerminalDryRunReasonCountDto(
                    reasonCode,
                    outcome,
                    entry.getValue() != null ? entry.getValue() : 0L
                );
            })
            .sorted(Comparator.comparingLong(PreviewRenditionResourcesExportAsyncRetryTerminalDryRunReasonCountDto::count)
                .reversed()
                .thenComparing(PreviewRenditionResourcesExportAsyncRetryTerminalDryRunReasonCountDto::reasonCode))
            .toList();

        String statusFilterName = statusFilter != null
            ? statusFilter.name()
            : "FAILED|CANCELLED|COMPLETED|TIMED_OUT|EXPIRED";
        String message = requested == 0
            ? "No terminal async preview rendition resources export tasks matched dry-run filters"
            : String.format(
                Locale.ROOT,
                "Dry-run identified %d/%d retryable terminal async preview rendition resources export tasks (skipped=%d)",
                retryable,
                requested,
                skipped
            );
        return new RenditionResourcesExportAsyncRetryTerminalDryRunComputation(
            requested,
            retryable,
            skipped,
            limit,
            statusFilterName,
            message,
            results,
            reasonBreakdown
        );
    }

    private String buildRenditionResourcesExportAsyncRetryTerminalDryRunCsv(
        RenditionResourcesExportAsyncRetryTerminalDryRunComputation dryRun
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("statusFilter,limit,requested,retryable,skipped,sourceTaskId,sourceStatus,outcome,reasonCode,message\n");
        List<PreviewRenditionResourcesExportAsyncRetryTerminalDryRunItemDto> results = dryRun != null
            ? dryRun.results()
            : List.of();
        if (results.isEmpty()) {
            sb.append(csv(dryRun != null ? dryRun.statusFilterName() : "FAILED|CANCELLED|COMPLETED|TIMED_OUT|EXPIRED"))
                .append(',')
                .append(dryRun != null ? dryRun.limit() : 0)
                .append(',')
                .append(dryRun != null ? dryRun.requested() : 0)
                .append(',')
                .append(dryRun != null ? dryRun.retryable() : 0)
                .append(',')
                .append(dryRun != null ? dryRun.skipped() : 0)
                .append(",,,,,")
                .append('\n');
        } else {
            for (PreviewRenditionResourcesExportAsyncRetryTerminalDryRunItemDto item : results) {
                sb.append(csv(dryRun.statusFilterName()))
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
        List<PreviewRenditionResourcesExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown = dryRun != null
            ? dryRun.reasonBreakdown()
            : List.of();
        if (reasonBreakdown.isEmpty()) {
            sb.append("NONE,UNKNOWN,0\n");
            return sb.toString();
        }
        for (PreviewRenditionResourcesExportAsyncRetryTerminalDryRunReasonCountDto item : reasonBreakdown) {
            sb.append(csv(item.reasonCode()))
                .append(',')
                .append(csv(item.outcome()))
                .append(',')
                .append(item.count())
                .append('\n');
        }
        return sb.toString();
    }

    private List<String> normalizeRenditionResourcesRetrySourceTaskIds(List<String> sourceTaskIds) {
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
            if (deduplicated.size() >= MAX_RENDITION_RESOURCE_EXPORT_ASYNC_RETRY_SELECTED_IDS) {
                break;
            }
        }
        return new ArrayList<>(deduplicated);
    }

    private AsyncTaskSummarySnapshot buildRenditionResourcesExportAsyncSummarySnapshot(
        RenditionResourcesExportAsyncStatus statusFilter
    ) {
        long queuedCount = 0L;
        long runningCount = 0L;
        long completedCount = 0L;
        long cancelledCount = 0L;
        long failedCount = 0L;
        long timedOutCount = 0L;
        long expiredCount = 0L;

        synchronized (renditionResourcesExportAsyncTaskLock) {
            refreshRenditionResourcesExportAsyncTasksLifecycleLocked();
            for (RenditionResourcesExportAsyncTask task : renditionResourcesExportAsyncTasks.values()) {
                if (task == null) {
                    continue;
                }
                if (statusFilter != null && task.status() != statusFilter) {
                    continue;
                }
                switch (task.status()) {
                    case QUEUED -> queuedCount += 1;
                    case RUNNING -> runningCount += 1;
                    case COMPLETED -> completedCount += 1;
                    case CANCELLED -> cancelledCount += 1;
                    case FAILED -> failedCount += 1;
                    case TIMED_OUT -> timedOutCount += 1;
                    case EXPIRED -> expiredCount += 1;
                    default -> {
                        // no-op
                    }
                }
            }
        }

        long totalCount = queuedCount + runningCount + completedCount + cancelledCount + failedCount + timedOutCount + expiredCount;
        long activeCount = queuedCount + runningCount;
        long terminalCount = completedCount + cancelledCount + failedCount + timedOutCount + expiredCount;
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

    private void refreshRenditionResourcesExportAsyncTasksLifecycleLocked() {
        Instant now = Instant.now();
        List<String> taskIds = new ArrayList<>(renditionResourcesExportAsyncTaskOrder);
        for (String taskId : taskIds) {
            renditionResourcesExportAsyncTasks.computeIfPresent(taskId, (key, task) ->
                applyRenditionResourcesExportAsyncTaskLifecycle(task, now)
            );
        }
        renditionResourcesExportAsyncTaskOrder.removeIf(taskId -> !renditionResourcesExportAsyncTasks.containsKey(taskId));
    }

    private RenditionResourcesExportAsyncTask applyRenditionResourcesExportAsyncTaskLifecycle(
        RenditionResourcesExportAsyncTask task,
        Instant now
    ) {
        if (task == null) {
            return null;
        }
        RenditionResourcesExportAsyncTask current = task;
        if (isActiveRenditionResourcesExportAsyncStatus(current.status())
            && current.timeoutAt() != null
            && now != null
            && now.isAfter(current.timeoutAt())) {
            current = current.timeout(now, "system");
        }
        if (current.status() != RenditionResourcesExportAsyncStatus.EXPIRED
            && current.expiresAt() != null
            && now != null
            && now.isAfter(current.expiresAt())
            && current.isTerminal()) {
            current = current.expire(now, "system");
        }
        return current;
    }

    private long cleanupRenditionResourcesExportAsyncTasksLocked(RenditionResourcesExportAsyncStatus statusFilter) {
        Set<String> taskIdsToDelete = new HashSet<>();
        for (Map.Entry<String, RenditionResourcesExportAsyncTask> entry : renditionResourcesExportAsyncTasks.entrySet()) {
            RenditionResourcesExportAsyncTask task = entry.getValue();
            if (task == null) {
                taskIdsToDelete.add(entry.getKey());
                continue;
            }
            if (statusFilter == null) {
                if (task.isTerminal()) {
                    taskIdsToDelete.add(entry.getKey());
                }
            } else if (task.status() == statusFilter) {
                taskIdsToDelete.add(entry.getKey());
            }
        }

        if (taskIdsToDelete.isEmpty()) {
            renditionResourcesExportAsyncTaskOrder.removeIf(taskId -> !renditionResourcesExportAsyncTasks.containsKey(taskId));
            return 0L;
        }

        taskIdsToDelete.forEach(taskId -> {
            renditionResourcesExportAsyncTasks.remove(taskId);
            renditionResourcesExportAsyncTaskRequests.remove(taskId);
        });
        renditionResourcesExportAsyncTaskOrder.removeIf(taskId ->
            taskIdsToDelete.contains(taskId) || !renditionResourcesExportAsyncTasks.containsKey(taskId)
        );
        return taskIdsToDelete.size();
    }

    private RenditionResourcesExportAsyncStatus parseRenditionResourcesExportAsyncStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return RenditionResourcesExportAsyncStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                String.format(RENDITION_RESOURCES_EXPORT_ASYNC_STATUS_ERROR_TEMPLATE, status)
            );
        }
    }

    private boolean isActiveRenditionResourcesExportAsyncStatus(RenditionResourcesExportAsyncStatus status) {
        return status == RenditionResourcesExportAsyncStatus.QUEUED
            || status == RenditionResourcesExportAsyncStatus.RUNNING;
    }

    private int countActiveRenditionResourcesExportAsyncTasksLocked() {
        int activeCount = 0;
        for (RenditionResourcesExportAsyncTask task : renditionResourcesExportAsyncTasks.values()) {
            if (task != null && isActiveRenditionResourcesExportAsyncStatus(task.status())) {
                activeCount += 1;
            }
        }
        return activeCount;
    }

    private PreviewRenditionResourcesExportAsyncStatusResponseDto toRenditionResourcesExportAsyncStatusResponse(
        RenditionResourcesExportAsyncTask task
    ) {
        return new PreviewRenditionResourcesExportAsyncStatusResponseDto(
            task.taskId(),
            task.status().name(),
            task.error(),
            task.createdAt(),
            task.startedAt(),
            task.updatedAt(),
            task.timeoutAt(),
            task.expiresAt(),
            task.finishedAt(),
            task.status() == RenditionResourcesExportAsyncStatus.COMPLETED ? task.filename() : null,
            task.createdBy(),
            task.updatedBy()
        );
    }

    private static String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).replace("\"", "\"\"");
        return "\"" + text + "\"";
    }

    private void auditDeadLetterExport(int limit, int exported) {
        auditService.logEvent(
            "PREVIEW_DEAD_LETTER_EXPORTED",
            null,
            "PREVIEW_DIAGNOSTICS",
            resolveAuditUsername(),
            String.format(
                "Exported preview dead-letter CSV (limit=%d, exported=%d, backend=%s)",
                limit,
                exported,
                previewDeadLetterRegistry.isRedisActive() ? "REDIS" : "MEMORY"
            )
        );
    }

    private void auditRenditionResourceExport(int days, int limit, int exported) {
        auditService.logEvent(
            "PREVIEW_RENDITION_RESOURCES_EXPORTED",
            null,
            "PREVIEW_DIAGNOSTICS",
            resolveAuditUsername(),
            String.format(
                "Exported preview rendition resources CSV (days=%d, limit=%d, exported=%d)",
                days,
                limit,
                exported
            )
        );
    }

    private void auditDeadLetterReplay(PreviewDeadLetterReplayBatchResponseDto payload, boolean force) {
        if (payload == null) {
            return;
        }
        auditService.logEvent(
            "PREVIEW_DEAD_LETTER_REPLAY",
            null,
            "PREVIEW_DIAGNOSTICS",
            resolveAuditUsername(),
            String.format(
                "Replayed dead-letter batch (force=%s, requested=%d, deduplicated=%d, queued=%d, skipped=%d, failed=%d)",
                force,
                payload.requested(),
                payload.deduplicated(),
                payload.queued(),
                payload.skipped(),
                payload.failed()
            )
        );
    }

    private void auditDeadLetterClear(PreviewDeadLetterClearBatchResponseDto payload) {
        if (payload == null) {
            return;
        }
        auditService.logEvent(
            "PREVIEW_DEAD_LETTER_CLEARED",
            null,
            "PREVIEW_DIAGNOSTICS",
            resolveAuditUsername(),
            String.format(
                "Cleared dead-letter batch (requested=%d, deduplicated=%d, cleared=%d, failed=%d)",
                payload.requested(),
                payload.deduplicated(),
                payload.cleared(),
                payload.failed()
            )
        );
    }

    private void auditFailureLedgerReset(PreviewFailureLedgerResetBatchResponseDto payload) {
        if (payload == null) {
            return;
        }
        auditService.logEvent(
            "PREVIEW_FAILURE_LEDGER_RESET",
            null,
            "PREVIEW_DIAGNOSTICS",
            resolveAuditUsername(),
            String.format(
                "Reset preview failure ledger (requested=%d, deduplicated=%d, reset=%d, failed=%d)",
                payload.requested(),
                payload.deduplicated(),
                payload.reset(),
                payload.failed()
            )
        );
    }

    private void auditFailureLedgerResetByFilter(PreviewFailureLedgerResetByFilterResponseDto payload) {
        if (payload == null) {
            return;
        }
        auditService.logEvent(
            "PREVIEW_FAILURE_LEDGER_RESET_BY_FILTER",
            null,
            "PREVIEW_DIAGNOSTICS",
            resolveAuditUsername(),
            String.format(
                "Reset preview failure ledger by filter (reason=%s, category=%s, retryable=%s, days=%d, scanned=%d, matched=%d, reset=%d, skipped=%d, failed=%d, truncated=%s)",
                payload.reason(),
                payload.category(),
                payload.retryable(),
                payload.windowDays(),
                payload.scanned(),
                payload.matched(),
                payload.reset(),
                payload.skipped(),
                payload.failed(),
                payload.truncated()
            )
        );
    }

    private void auditFailureLedgerExport(int days, int limit, int exported) {
        auditService.logEvent(
            "PREVIEW_FAILURE_LEDGER_EXPORTED",
            null,
            "PREVIEW_DIAGNOSTICS",
            resolveAuditUsername(),
            String.format(
                "Exported preview failure ledger CSV (days=%d, limit=%d, exported=%d)",
                days,
                limit,
                exported
            )
        );
    }

    private void auditQueueDiagnosticsExport(int limit, int exported, String backend, String stateFilter, String queryFilter) {
        auditService.logEvent(
            "PREVIEW_QUEUE_DIAGNOSTICS_EXPORTED",
            null,
            "PREVIEW_DIAGNOSTICS",
            resolveAuditUsername(),
            String.format(
                "Exported preview queue diagnostics CSV (limit=%d, exported=%d, backend=%s, state=%s, query=%s)",
                limit,
                exported,
                backend == null || backend.isBlank() ? "UNKNOWN" : backend,
                stateFilter == null || stateFilter.isBlank() ? "ALL" : stateFilter,
                queryFilter == null || queryFilter.isBlank() ? "-" : queryFilter
            )
        );
    }

    private void auditQueueCancelActive(PreviewQueueCancelActiveResponseDto payload) {
        if (payload == null) {
            return;
        }
        auditService.logEvent(
            "PREVIEW_QUEUE_CANCEL_ACTIVE",
            null,
            "PREVIEW_DIAGNOSTICS",
            resolveAuditUsername(),
            String.format(
                "Cancelled active preview queue tasks (state=%s, query=%s, limit=%d, requested=%d, cancelled=%d, skipped=%d, failed=%d)",
                payload.stateFilter(),
                payload.queryFilter() == null || payload.queryFilter().isBlank() ? "-" : payload.queryFilter(),
                payload.limit(),
                payload.requested(),
                payload.cancelled(),
                payload.skipped(),
                payload.failed()
            )
        );
    }

    private void auditQueueDeclinedExport(
        int limit,
        int exported,
        String categoryFilter,
        String forceRequiredFilter,
        String queryFilter,
        Integer windowHoursFilter
    ) {
        auditService.logEvent(
            "PREVIEW_QUEUE_DECLINED_EXPORTED",
            null,
            "PREVIEW_DIAGNOSTICS",
            resolveAuditUsername(),
            String.format(
                "Exported preview queue declined CSV (limit=%d, exported=%d, category=%s, forceRequired=%s, query=%s, windowHours=%s)",
                limit,
                exported,
                categoryFilter == null || categoryFilter.isBlank() ? "ANY" : categoryFilter,
                forceRequiredFilter == null || forceRequiredFilter.isBlank() ? "ANY" : forceRequiredFilter,
                queryFilter == null || queryFilter.isBlank() ? "-" : queryFilter,
                windowHoursFilter == null ? "ANY" : String.valueOf(windowHoursFilter)
            )
        );
    }

    private void auditQueueDeclinedAsyncExportStarted(QueueDeclinedExportAsyncTask task) {
        logQueueDeclinedAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_STARTED",
            task != null ? task.taskId() : null,
            task != null ? task.status().name() : "QUEUED",
            buildQueueDeclinedAsyncFilterContext(task),
            null
        );
    }

    private void auditQueueDeclinedAsyncExportStartDedupHit(QueueDeclinedExportAsyncTask task) {
        logQueueDeclinedAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_START_DEDUP_HIT",
            task != null ? task.taskId() : null,
            task != null ? task.status().name() : "UNKNOWN",
            buildQueueDeclinedAsyncFilterContext(task),
            String.format(
                "reusedTaskId=%s",
                task == null || task.taskId() == null || task.taskId().isBlank() ? "-" : task.taskId()
            )
        );
    }

    private void auditQueueDeclinedAsyncExportCancelSingle(String requestedTaskId, QueueDeclinedExportAsyncTask task) {
        String taskId = task != null ? task.taskId() : requestedTaskId;
        String status = task != null ? task.status().name() : "NOT_FOUND";
        logQueueDeclinedAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_CANCEL_SINGLE",
            taskId,
            status,
            buildQueueDeclinedAsyncFilterContext(task),
            null
        );
    }

    private void auditQueueDeclinedAsyncExportRetry(
        String sourceTaskId,
        QueueDeclinedExportAsyncTask sourceTask,
        QueueDeclinedExportAsyncTask retryTask,
        String status
    ) {
        String resolvedSourceTaskId = sourceTask != null
            && sourceTask.taskId() != null
            && !sourceTask.taskId().isBlank()
                ? sourceTask.taskId()
                : sourceTaskId;
        String resolvedStatus = status;
        if (resolvedStatus == null || resolvedStatus.isBlank()) {
            if (retryTask != null && retryTask.status() != null) {
                resolvedStatus = retryTask.status().name();
            } else if (sourceTask != null && sourceTask.status() != null) {
                resolvedStatus = sourceTask.status().name();
            } else {
                resolvedStatus = "UNKNOWN";
            }
        }

        String details = String.format(
            "sourceTaskId=%s, newTaskId=%s",
            resolvedSourceTaskId == null || resolvedSourceTaskId.isBlank() ? "-" : resolvedSourceTaskId,
            retryTask == null || retryTask.taskId() == null || retryTask.taskId().isBlank() ? "-" : retryTask.taskId()
        );
        logQueueDeclinedAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY",
            retryTask != null ? retryTask.taskId() : resolvedSourceTaskId,
            resolvedStatus,
            buildQueueDeclinedAsyncFilterContext(retryTask != null ? retryTask : sourceTask),
            details
        );
    }

    private void auditQueueDeclinedAsyncExportRetryBulk(
        String statusFilterName,
        int limit,
        int requested,
        int retried,
        int reused,
        int skipped,
        int failed,
        List<QueueDeclinedExportAsyncTask> sourceTasks,
        List<QueueDeclinedExportAsyncTask> newTasks
    ) {
        String normalizedStatusFilter = statusFilterName == null || statusFilterName.isBlank()
            ? "FAILED|CANCELLED|TIMED_OUT|EXPIRED"
            : statusFilterName;
        String details = String.format(
            "statusFilter=%s, limit=%d, requested=%d, retried=%d, reused=%d, skipped=%d, failed=%d, sourceTaskIds=%s, newTaskIds=%s",
            normalizedStatusFilter,
            limit,
            requested,
            retried,
            reused,
            skipped,
            failed,
            summarizeQueueDeclinedAsyncTaskIds(sourceTasks),
            summarizeQueueDeclinedAsyncTaskIds(newTasks)
        );
        logQueueDeclinedAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK",
            summarizeQueueDeclinedAsyncTaskIds(newTasks),
            normalizedStatusFilter,
            buildQueueDeclinedAsyncFilterContext(sourceTasks),
            details
        );
    }

    private void auditQueueDeclinedAsyncExportRetryBulkDryRun(
        String statusFilterName,
        int limit,
        int requested,
        int retryable,
        int skipped,
        List<QueueDeclinedExportAsyncTask> sourceTasks,
        List<PreviewQueueDeclinedExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown
    ) {
        String normalizedStatusFilter = statusFilterName == null || statusFilterName.isBlank()
            ? "FAILED|CANCELLED|TIMED_OUT|EXPIRED"
            : statusFilterName;
        String details = String.format(
            "statusFilter=%s, limit=%d, requested=%d, retryable=%d, skipped=%d, sourceTaskIds=%s, reasonBreakdown=%s",
            normalizedStatusFilter,
            limit,
            requested,
            retryable,
            skipped,
            summarizeQueueDeclinedAsyncTaskIds(sourceTasks),
            summarizeQueueDeclinedRetryDryRunReasonBreakdown(reasonBreakdown)
        );
        logQueueDeclinedAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK_DRY_RUN",
            summarizeQueueDeclinedAsyncTaskIds(sourceTasks),
            normalizedStatusFilter,
            buildQueueDeclinedAsyncFilterContext(sourceTasks),
            details
        );
    }

    private void auditQueueDeclinedAsyncExportRetryBulkDryRunExport(
        String statusFilterName,
        int limit,
        int requested,
        int retryable,
        int skipped,
        List<PreviewQueueDeclinedExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown
    ) {
        String normalizedStatusFilter = statusFilterName == null || statusFilterName.isBlank()
            ? "FAILED|CANCELLED|TIMED_OUT|EXPIRED"
            : statusFilterName;
        String details = String.format(
            "statusFilter=%s, limit=%d, requested=%d, retryable=%d, skipped=%d, reasonBreakdown=%s",
            normalizedStatusFilter,
            limit,
            requested,
            retryable,
            skipped,
            summarizeQueueDeclinedRetryDryRunReasonBreakdown(reasonBreakdown)
        );
        logQueueDeclinedAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK_DRY_RUN_EXPORTED",
            null,
            normalizedStatusFilter,
            String.format("limit=%d, category=ANY, forceRequired=ANY, query=-, windowHours=ANY", limit),
            details
        );
    }

    private void auditQueueDeclinedAsyncExportRetryBulkSelected(
        int requested,
        int retried,
        int reused,
        int skipped,
        int failed,
        List<QueueDeclinedExportAsyncTask> sourceTasks,
        List<QueueDeclinedExportAsyncTask> newTasks
    ) {
        String details = String.format(
            "statusFilter=BY_TASK_IDS, requested=%d, retried=%d, reused=%d, skipped=%d, failed=%d, sourceTaskIds=%s, newTaskIds=%s",
            requested,
            retried,
            reused,
            skipped,
            failed,
            summarizeQueueDeclinedAsyncTaskIds(sourceTasks),
            summarizeQueueDeclinedAsyncTaskIds(newTasks)
        );
        logQueueDeclinedAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK_SELECTED",
            summarizeQueueDeclinedAsyncTaskIds(newTasks),
            "BY_TASK_IDS",
            buildQueueDeclinedAsyncFilterContext(sourceTasks),
            details
        );
    }

    private void auditQueueDeclinedAsyncExportCancelActive(
        String statusFilterName,
        long cancelledCount,
        int remainingActiveCount,
        List<QueueDeclinedExportAsyncTask> cancelledTasks
    ) {
        logQueueDeclinedAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_CANCEL_ACTIVE",
            summarizeQueueDeclinedAsyncTaskIds(cancelledTasks),
            statusFilterName == null ? "ALL_ACTIVE" : statusFilterName,
            buildQueueDeclinedAsyncFilterContext(cancelledTasks),
            String.format("cancelled=%d, remainingActive=%d", cancelledCount, remainingActiveCount)
        );
    }

    private void auditQueueDeclinedAsyncExportCleanup(
        String statusFilterName,
        long deletedCount,
        int remainingCount,
        List<QueueDeclinedExportAsyncTask> deletedTasks
    ) {
        logQueueDeclinedAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_CLEANUP",
            summarizeQueueDeclinedAsyncTaskIds(deletedTasks),
            statusFilterName == null ? "TERMINAL" : statusFilterName,
            buildQueueDeclinedAsyncFilterContext(deletedTasks),
            String.format("deleted=%d, remaining=%d", deletedCount, remainingCount)
        );
    }

    private void auditQueueDeclinedAsyncExportDownloaded(QueueDeclinedExportAsyncTask task) {
        String details = task != null
            ? String.format(
                "filename=%s, bytes=%d",
                task.filename() == null || task.filename().isBlank() ? "-" : task.filename(),
                task.csvContent() != null ? task.csvContent().length : 0
            )
            : null;
        logQueueDeclinedAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_DOWNLOADED",
            task != null ? task.taskId() : null,
            task != null ? task.status().name() : "UNKNOWN",
            buildQueueDeclinedAsyncFilterContext(task),
            details
        );
    }

    private void auditQueueDeclinedAsyncExportRunCompleted(QueueDeclinedExportAsyncTask task, int exported) {
        logQueueDeclinedAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_COMPLETED",
            task != null ? task.taskId() : null,
            task != null ? task.status().name() : QueueDeclinedExportAsyncStatus.COMPLETED.name(),
            buildQueueDeclinedAsyncFilterContext(task),
            String.format("exported=%d", exported)
        );
    }

    private void auditQueueDeclinedAsyncExportRunFailed(QueueDeclinedExportAsyncTask task) {
        logQueueDeclinedAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_FAILED",
            task != null ? task.taskId() : null,
            task != null ? task.status().name() : QueueDeclinedExportAsyncStatus.FAILED.name(),
            buildQueueDeclinedAsyncFilterContext(task),
            String.format(
                "error=%s",
                task == null || task.error() == null || task.error().isBlank() ? "-" : task.error()
            )
        );
    }

    private void auditQueueDeclinedAsyncExportRunCancelled(QueueDeclinedExportAsyncTask task) {
        logQueueDeclinedAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_CANCELLED",
            task != null ? task.taskId() : null,
            task != null ? task.status().name() : QueueDeclinedExportAsyncStatus.CANCELLED.name(),
            buildQueueDeclinedAsyncFilterContext(task),
            String.format(
                "reason=%s",
                task == null || task.error() == null || task.error().isBlank() ? "-" : task.error()
            )
        );
    }

    private void logQueueDeclinedAsyncExportAuditEvent(
        String eventName,
        String taskId,
        String status,
        String filterContext,
        String details
    ) {
        StringBuilder message = new StringBuilder()
            .append("Async preview queue declined export task event")
            .append(" (taskId=")
            .append(taskId == null || taskId.isBlank() ? "-" : taskId)
            .append(", status=")
            .append(status == null || status.isBlank() ? "UNKNOWN" : status)
            .append(", ")
            .append(filterContext == null || filterContext.isBlank()
                ? "limit=ANY, category=ANY, forceRequired=ANY, query=-, windowHours=ANY"
                : filterContext);
        if (details != null && !details.isBlank()) {
            message.append(", ").append(details);
        }
        message.append(')');

        auditService.logEvent(
            eventName,
            null,
            "PREVIEW_DIAGNOSTICS",
            resolveAuditUsername(),
            message.toString()
        );
    }

    private String buildQueueDeclinedAsyncFilterContext(QueueDeclinedExportAsyncTask task) {
        if (task == null) {
            return "limit=ANY, category=ANY, forceRequired=ANY, query=-, windowHours=ANY";
        }
        return String.format(
            "limit=%d, category=%s, forceRequired=%s, query=%s, windowHours=%s",
            task.limit(),
            task.categoryFilter() == null || task.categoryFilter().isBlank() ? "ANY" : task.categoryFilter(),
            task.forceRequiredFilter() == null || task.forceRequiredFilter().isBlank() ? "ANY" : task.forceRequiredFilter(),
            task.queryFilter() == null || task.queryFilter().isBlank() ? "-" : task.queryFilter(),
            task.windowHoursFilter() == null ? "ANY" : task.windowHoursFilter()
        );
    }

    private String buildQueueDeclinedAsyncFilterContext(List<QueueDeclinedExportAsyncTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return "limit=ANY, category=ANY, forceRequired=ANY, query=-, windowHours=ANY";
        }
        Set<String> limits = new LinkedHashSet<>();
        Set<String> categories = new LinkedHashSet<>();
        Set<String> forceRequired = new LinkedHashSet<>();
        Set<String> queries = new LinkedHashSet<>();
        Set<String> windowHours = new LinkedHashSet<>();

        for (QueueDeclinedExportAsyncTask task : tasks) {
            if (task == null) {
                continue;
            }
            limits.add(String.valueOf(task.limit()));
            categories.add(task.categoryFilter() == null || task.categoryFilter().isBlank() ? "ANY" : task.categoryFilter());
            forceRequired.add(task.forceRequiredFilter() == null || task.forceRequiredFilter().isBlank() ? "ANY" : task.forceRequiredFilter());
            queries.add(task.queryFilter() == null || task.queryFilter().isBlank() ? "-" : task.queryFilter());
            windowHours.add(task.windowHoursFilter() == null ? "ANY" : String.valueOf(task.windowHoursFilter()));
        }

        return String.format(
            "limit=%s, category=%s, forceRequired=%s, query=%s, windowHours=%s",
            combineQueueDeclinedAsyncFilterValues(limits, "ANY"),
            combineQueueDeclinedAsyncFilterValues(categories, "ANY"),
            combineQueueDeclinedAsyncFilterValues(forceRequired, "ANY"),
            combineQueueDeclinedAsyncFilterValues(queries, "-"),
            combineQueueDeclinedAsyncFilterValues(windowHours, "ANY")
        );
    }

    private String combineQueueDeclinedAsyncFilterValues(Set<String> values, String fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        if (values.size() == 1) {
            return values.iterator().next();
        }
        return String.join("|", values);
    }

    private String summarizeQueueDeclinedAsyncTaskIds(List<QueueDeclinedExportAsyncTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return "-";
        }
        String taskIds = tasks.stream()
            .map(QueueDeclinedExportAsyncTask::taskId)
            .filter(taskId -> taskId != null && !taskId.isBlank())
            .collect(Collectors.joining("|"));
        return taskIds.isBlank() ? "-" : taskIds;
    }

    private String summarizeQueueDeclinedRetryDryRunReasonBreakdown(
        List<PreviewQueueDeclinedExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown
    ) {
        if (reasonBreakdown == null || reasonBreakdown.isEmpty()) {
            return "-";
        }
        return reasonBreakdown.stream()
            .map(item -> String.format(
                "%s:%s=%d",
                item.reasonCode() == null || item.reasonCode().isBlank() ? "UNKNOWN" : item.reasonCode(),
                item.outcome() == null || item.outcome().isBlank() ? "UNKNOWN" : item.outcome(),
                item.count()
            ))
            .collect(Collectors.joining("|"));
    }

    private void auditQueueDeclinedRequeueDryRunAsyncExportStarted(QueueDeclinedRequeueDryRunExportAsyncTask task) {
        logQueueDeclinedRequeueDryRunAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_STARTED",
            task != null ? task.taskId() : null,
            task != null ? task.status().name() : "QUEUED",
            buildQueueDeclinedRequeueDryRunAsyncFilterContext(task),
            null
        );
    }

    private void auditQueueDeclinedRequeueDryRunAsyncExportStartDedupHit(QueueDeclinedRequeueDryRunExportAsyncTask task) {
        logQueueDeclinedRequeueDryRunAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_START_DEDUP_HIT",
            task != null ? task.taskId() : null,
            task != null ? task.status().name() : "UNKNOWN",
            buildQueueDeclinedRequeueDryRunAsyncFilterContext(task),
            String.format(
                "reusedTaskId=%s",
                task == null || task.taskId() == null || task.taskId().isBlank() ? "-" : task.taskId()
            )
        );
    }

    private void auditQueueDeclinedRequeueDryRunAsyncExportCancelSingle(
        String requestedTaskId,
        QueueDeclinedRequeueDryRunExportAsyncTask task
    ) {
        String taskId = task != null ? task.taskId() : requestedTaskId;
        String status = task != null ? task.status().name() : "NOT_FOUND";
        logQueueDeclinedRequeueDryRunAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_CANCEL_SINGLE",
            taskId,
            status,
            buildQueueDeclinedRequeueDryRunAsyncFilterContext(task),
            null
        );
    }

    private void auditQueueDeclinedRequeueDryRunAsyncExportRetry(
        String sourceTaskId,
        QueueDeclinedRequeueDryRunExportAsyncTask sourceTask,
        QueueDeclinedRequeueDryRunExportAsyncTask retryTask,
        String status
    ) {
        String resolvedSourceTaskId = sourceTask != null
            && sourceTask.taskId() != null
            && !sourceTask.taskId().isBlank()
                ? sourceTask.taskId()
                : sourceTaskId;
        String resolvedStatus = status;
        if (resolvedStatus == null || resolvedStatus.isBlank()) {
            if (retryTask != null && retryTask.status() != null) {
                resolvedStatus = retryTask.status().name();
            } else if (sourceTask != null && sourceTask.status() != null) {
                resolvedStatus = sourceTask.status().name();
            } else {
                resolvedStatus = "UNKNOWN";
            }
        }

        String details = String.format(
            "sourceTaskId=%s, newTaskId=%s",
            resolvedSourceTaskId == null || resolvedSourceTaskId.isBlank() ? "-" : resolvedSourceTaskId,
            retryTask == null || retryTask.taskId() == null || retryTask.taskId().isBlank() ? "-" : retryTask.taskId()
        );
        logQueueDeclinedRequeueDryRunAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_RETRY",
            retryTask != null ? retryTask.taskId() : resolvedSourceTaskId,
            resolvedStatus,
            buildQueueDeclinedRequeueDryRunAsyncFilterContext(retryTask != null ? retryTask : sourceTask),
            details
        );
    }

    private void auditQueueDeclinedRequeueDryRunAsyncExportRetryBulk(
        String statusFilterName,
        int limit,
        int requested,
        int retried,
        int reused,
        int skipped,
        int failed,
        List<QueueDeclinedRequeueDryRunExportAsyncTask> sourceTasks,
        List<QueueDeclinedRequeueDryRunExportAsyncTask> newTasks
    ) {
        String normalizedStatusFilter = statusFilterName == null || statusFilterName.isBlank()
            ? "FAILED|CANCELLED|TIMED_OUT|EXPIRED"
            : statusFilterName;
        String details = String.format(
            "statusFilter=%s, limit=%d, requested=%d, retried=%d, reused=%d, skipped=%d, failed=%d, sourceTaskIds=%s, newTaskIds=%s",
            normalizedStatusFilter,
            limit,
            requested,
            retried,
            reused,
            skipped,
            failed,
            summarizeQueueDeclinedRequeueDryRunAsyncTaskIds(sourceTasks),
            summarizeQueueDeclinedRequeueDryRunAsyncTaskIds(newTasks)
        );
        logQueueDeclinedRequeueDryRunAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_RETRY_BULK",
            summarizeQueueDeclinedRequeueDryRunAsyncTaskIds(newTasks),
            normalizedStatusFilter,
            buildQueueDeclinedRequeueDryRunAsyncFilterContext(sourceTasks),
            details
        );
    }

    private void auditQueueDeclinedRequeueDryRunAsyncExportRetryBulkDryRun(
        String statusFilterName,
        int limit,
        int requested,
        int retryable,
        int skipped,
        List<QueueDeclinedRequeueDryRunExportAsyncTask> sourceTasks,
        List<PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown
    ) {
        String normalizedStatusFilter = statusFilterName == null || statusFilterName.isBlank()
            ? "FAILED|CANCELLED|TIMED_OUT|EXPIRED"
            : statusFilterName;
        String details = String.format(
            "statusFilter=%s, limit=%d, requested=%d, retryable=%d, skipped=%d, sourceTaskIds=%s, reasonBreakdown=%s",
            normalizedStatusFilter,
            limit,
            requested,
            retryable,
            skipped,
            summarizeQueueDeclinedRequeueDryRunAsyncTaskIds(sourceTasks),
            summarizeQueueDeclinedRequeueDryRunRetryDryRunReasonBreakdown(reasonBreakdown)
        );
        logQueueDeclinedRequeueDryRunAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_RETRY_BULK_DRY_RUN",
            summarizeQueueDeclinedRequeueDryRunAsyncTaskIds(sourceTasks),
            normalizedStatusFilter,
            buildQueueDeclinedRequeueDryRunAsyncFilterContext(sourceTasks),
            details
        );
    }

    private void auditQueueDeclinedRequeueDryRunAsyncExportRetryBulkDryRunExport(
        String statusFilterName,
        int limit,
        int requested,
        int retryable,
        int skipped,
        List<PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown
    ) {
        String normalizedStatusFilter = statusFilterName == null || statusFilterName.isBlank()
            ? "FAILED|CANCELLED|TIMED_OUT|EXPIRED"
            : statusFilterName;
        String details = String.format(
            "statusFilter=%s, limit=%d, requested=%d, retryable=%d, skipped=%d, reasonBreakdown=%s",
            normalizedStatusFilter,
            limit,
            requested,
            retryable,
            skipped,
            summarizeQueueDeclinedRequeueDryRunRetryDryRunReasonBreakdown(reasonBreakdown)
        );
        logQueueDeclinedRequeueDryRunAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_RETRY_BULK_DRY_RUN_EXPORTED",
            null,
            normalizedStatusFilter,
            String.format("limit=%d, category=ANY, forceRequired=ANY, query=-, windowHours=ANY, force=true", limit),
            details
        );
    }

    private void auditQueueDeclinedRequeueDryRunAsyncExportRetryBulkSelected(
        int requested,
        int retried,
        int reused,
        int skipped,
        int failed,
        List<QueueDeclinedRequeueDryRunExportAsyncTask> sourceTasks,
        List<QueueDeclinedRequeueDryRunExportAsyncTask> newTasks
    ) {
        String details = String.format(
            "statusFilter=BY_TASK_IDS, requested=%d, retried=%d, reused=%d, skipped=%d, failed=%d, sourceTaskIds=%s, newTaskIds=%s",
            requested,
            retried,
            reused,
            skipped,
            failed,
            summarizeQueueDeclinedRequeueDryRunAsyncTaskIds(sourceTasks),
            summarizeQueueDeclinedRequeueDryRunAsyncTaskIds(newTasks)
        );
        logQueueDeclinedRequeueDryRunAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_RETRY_BULK_SELECTED",
            summarizeQueueDeclinedRequeueDryRunAsyncTaskIds(newTasks),
            "BY_TASK_IDS",
            buildQueueDeclinedRequeueDryRunAsyncFilterContext(sourceTasks),
            details
        );
    }

    private void auditQueueDeclinedRequeueDryRunAsyncExportCancelActive(
        String statusFilterName,
        long cancelledCount,
        int remainingActiveCount,
        List<QueueDeclinedRequeueDryRunExportAsyncTask> cancelledTasks
    ) {
        logQueueDeclinedRequeueDryRunAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_CANCEL_ACTIVE",
            summarizeQueueDeclinedRequeueDryRunAsyncTaskIds(cancelledTasks),
            statusFilterName == null ? "ALL_ACTIVE" : statusFilterName,
            buildQueueDeclinedRequeueDryRunAsyncFilterContext(cancelledTasks),
            String.format("cancelled=%d, remainingActive=%d", cancelledCount, remainingActiveCount)
        );
    }

    private void auditQueueDeclinedRequeueDryRunAsyncExportCleanup(
        String statusFilterName,
        long deletedCount,
        int remainingCount,
        List<QueueDeclinedRequeueDryRunExportAsyncTask> deletedTasks
    ) {
        logQueueDeclinedRequeueDryRunAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_CLEANUP",
            summarizeQueueDeclinedRequeueDryRunAsyncTaskIds(deletedTasks),
            statusFilterName == null ? "TERMINAL" : statusFilterName,
            buildQueueDeclinedRequeueDryRunAsyncFilterContext(deletedTasks),
            String.format("deleted=%d, remaining=%d", deletedCount, remainingCount)
        );
    }

    private void auditQueueDeclinedRequeueDryRunAsyncExportDownloaded(QueueDeclinedRequeueDryRunExportAsyncTask task) {
        String details = task != null
            ? String.format(
                "filename=%s, bytes=%d",
                task.filename() == null || task.filename().isBlank() ? "-" : task.filename(),
                task.csvContent() != null ? task.csvContent().length : 0
            )
            : null;
        logQueueDeclinedRequeueDryRunAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_DOWNLOADED",
            task != null ? task.taskId() : null,
            task != null ? task.status().name() : "UNKNOWN",
            buildQueueDeclinedRequeueDryRunAsyncFilterContext(task),
            details
        );
    }

    private void auditQueueDeclinedRequeueDryRunAsyncExportRunCompleted(
        QueueDeclinedRequeueDryRunExportAsyncTask task,
        int exported
    ) {
        logQueueDeclinedRequeueDryRunAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_COMPLETED",
            task != null ? task.taskId() : null,
            task != null ? task.status().name() : QueueDeclinedRequeueDryRunExportAsyncStatus.COMPLETED.name(),
            buildQueueDeclinedRequeueDryRunAsyncFilterContext(task),
            String.format("exported=%d", exported)
        );
    }

    private void auditQueueDeclinedRequeueDryRunAsyncExportRunFailed(QueueDeclinedRequeueDryRunExportAsyncTask task) {
        logQueueDeclinedRequeueDryRunAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_FAILED",
            task != null ? task.taskId() : null,
            task != null ? task.status().name() : QueueDeclinedRequeueDryRunExportAsyncStatus.FAILED.name(),
            buildQueueDeclinedRequeueDryRunAsyncFilterContext(task),
            String.format("error=%s", task == null || task.error() == null || task.error().isBlank() ? "-" : task.error())
        );
    }

    private void auditQueueDeclinedRequeueDryRunAsyncExportRunCancelled(QueueDeclinedRequeueDryRunExportAsyncTask task) {
        logQueueDeclinedRequeueDryRunAsyncExportAuditEvent(
            "PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_ASYNC_EXPORT_CANCELLED",
            task != null ? task.taskId() : null,
            task != null ? task.status().name() : QueueDeclinedRequeueDryRunExportAsyncStatus.CANCELLED.name(),
            buildQueueDeclinedRequeueDryRunAsyncFilterContext(task),
            String.format("reason=%s", task == null || task.error() == null || task.error().isBlank() ? "-" : task.error())
        );
    }

    private void logQueueDeclinedRequeueDryRunAsyncExportAuditEvent(
        String eventName,
        String taskId,
        String status,
        String filterContext,
        String details
    ) {
        StringBuilder message = new StringBuilder()
            .append("Async declined requeue dry-run export task event")
            .append(" (taskId=")
            .append(taskId == null || taskId.isBlank() ? "-" : taskId)
            .append(", status=")
            .append(status == null || status.isBlank() ? "UNKNOWN" : status)
            .append(", ")
            .append(filterContext == null || filterContext.isBlank()
                ? "limit=ANY, category=ANY, forceRequired=ANY, query=-, windowHours=ANY, force=true"
                : filterContext);
        if (details != null && !details.isBlank()) {
            message.append(", ").append(details);
        }
        message.append(')');
        auditService.logEvent(
            eventName,
            null,
            "PREVIEW_DIAGNOSTICS",
            resolveAuditUsername(),
            message.toString()
        );
    }

    private String buildQueueDeclinedRequeueDryRunAsyncFilterContext(QueueDeclinedRequeueDryRunExportAsyncTask task) {
        if (task == null) {
            return "limit=ANY, category=ANY, forceRequired=ANY, query=-, windowHours=ANY, force=true";
        }
        return String.format(
            "limit=%d, category=%s, forceRequired=%s, query=%s, windowHours=%s, force=%s",
            task.limit(),
            task.categoryFilter() == null || task.categoryFilter().isBlank() ? "ANY" : task.categoryFilter(),
            task.forceRequiredFilter() == null || task.forceRequiredFilter().isBlank() ? "ANY" : task.forceRequiredFilter(),
            task.queryFilter() == null || task.queryFilter().isBlank() ? "-" : task.queryFilter(),
            task.windowHoursFilter() == null ? "ANY" : task.windowHoursFilter(),
            task.force()
        );
    }

    private String buildQueueDeclinedRequeueDryRunAsyncFilterContext(List<QueueDeclinedRequeueDryRunExportAsyncTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return "limit=ANY, category=ANY, forceRequired=ANY, query=-, windowHours=ANY, force=true";
        }
        Set<String> limits = new LinkedHashSet<>();
        Set<String> categories = new LinkedHashSet<>();
        Set<String> forceRequired = new LinkedHashSet<>();
        Set<String> queries = new LinkedHashSet<>();
        Set<String> windowHours = new LinkedHashSet<>();
        Set<String> forces = new LinkedHashSet<>();
        for (QueueDeclinedRequeueDryRunExportAsyncTask task : tasks) {
            if (task == null) {
                continue;
            }
            limits.add(String.valueOf(task.limit()));
            categories.add(task.categoryFilter() == null || task.categoryFilter().isBlank() ? "ANY" : task.categoryFilter());
            forceRequired.add(task.forceRequiredFilter() == null || task.forceRequiredFilter().isBlank() ? "ANY" : task.forceRequiredFilter());
            queries.add(task.queryFilter() == null || task.queryFilter().isBlank() ? "-" : task.queryFilter());
            windowHours.add(task.windowHoursFilter() == null ? "ANY" : String.valueOf(task.windowHoursFilter()));
            forces.add(String.valueOf(task.force()));
        }
        return String.format(
            "limit=%s, category=%s, forceRequired=%s, query=%s, windowHours=%s, force=%s",
            combineQueueDeclinedAsyncFilterValues(limits, "ANY"),
            combineQueueDeclinedAsyncFilterValues(categories, "ANY"),
            combineQueueDeclinedAsyncFilterValues(forceRequired, "ANY"),
            combineQueueDeclinedAsyncFilterValues(queries, "-"),
            combineQueueDeclinedAsyncFilterValues(windowHours, "ANY"),
            combineQueueDeclinedAsyncFilterValues(forces, "true")
        );
    }

    private String summarizeQueueDeclinedRequeueDryRunAsyncTaskIds(List<QueueDeclinedRequeueDryRunExportAsyncTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return "-";
        }
        String taskIds = tasks.stream()
            .map(QueueDeclinedRequeueDryRunExportAsyncTask::taskId)
            .filter(taskId -> taskId != null && !taskId.isBlank())
            .collect(Collectors.joining("|"));
        return taskIds.isBlank() ? "-" : taskIds;
    }

    private String summarizeQueueDeclinedRequeueDryRunRetryDryRunReasonBreakdown(
        List<PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown
    ) {
        if (reasonBreakdown == null || reasonBreakdown.isEmpty()) {
            return "-";
        }
        return reasonBreakdown.stream()
            .map(item -> String.format(
                "%s:%s=%d",
                item.reasonCode() == null || item.reasonCode().isBlank() ? "UNKNOWN" : item.reasonCode(),
                item.outcome() == null || item.outcome().isBlank() ? "UNKNOWN" : item.outcome(),
                item.count()
            ))
            .collect(Collectors.joining("|"));
    }

    private void auditQueueDeclinedRequeue(PreviewQueueDeclinedRequeueResponseDto payload) {
        if (payload == null) {
            return;
        }
        auditService.logEvent(
            "PREVIEW_QUEUE_DECLINED_REQUEUE",
            null,
            "PREVIEW_DIAGNOSTICS",
            resolveAuditUsername(),
            String.format(
                "Requeued declined preview queue decisions (category=%s, forceRequired=%s, query=%s, windowHours=%s, force=%s, limit=%d, requested=%d, queued=%d, skipped=%d, failed=%d)",
                payload.categoryFilter(),
                payload.forceRequiredFilter(),
                payload.queryFilter() == null || payload.queryFilter().isBlank() ? "-" : payload.queryFilter(),
                payload.windowHoursFilter() == null ? "ANY" : payload.windowHoursFilter(),
                payload.force(),
                payload.limit(),
                payload.requested(),
                payload.queued(),
                payload.skipped(),
                payload.failed()
            )
        );
    }

    private void auditQueueDeclinedRequeueDryRun(PreviewQueueDeclinedRequeueDryRunResponseDto payload) {
        if (payload == null) {
            return;
        }
        auditService.logEvent(
            "PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN",
            null,
            "PREVIEW_DIAGNOSTICS",
            resolveAuditUsername(),
            String.format(
                "Dry-run requeue declined preview queue decisions (category=%s, forceRequired=%s, query=%s, windowHours=%s, force=%s, limit=%d, requested=%d, estimatedQueued=%d, estimatedSkipped=%d, estimatedFailed=%d, reasonBreakdown=%s)",
                payload.categoryFilter(),
                payload.forceRequiredFilter(),
                payload.queryFilter() == null || payload.queryFilter().isBlank() ? "-" : payload.queryFilter(),
                payload.windowHoursFilter() == null ? "ANY" : payload.windowHoursFilter(),
                payload.force(),
                payload.limit(),
                payload.requested(),
                payload.estimatedQueued(),
                payload.estimatedSkipped(),
                payload.estimatedFailed(),
                summarizeQueueDeclinedRequeueDryRunReasonBreakdown(payload.reasonBreakdown())
            )
        );
    }

    private void auditQueueDeclinedRequeueDryRunExport(PreviewQueueDeclinedRequeueDryRunResponseDto payload) {
        if (payload == null) {
            return;
        }
        auditService.logEvent(
            "PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORTED",
            null,
            "PREVIEW_DIAGNOSTICS",
            resolveAuditUsername(),
            String.format(
                "Exported dry-run requeue declined preview queue decisions CSV (category=%s, forceRequired=%s, query=%s, windowHours=%s, force=%s, limit=%d, requested=%d, estimatedQueued=%d, estimatedSkipped=%d, estimatedFailed=%d, reasonBreakdown=%s)",
                payload.categoryFilter(),
                payload.forceRequiredFilter(),
                payload.queryFilter() == null || payload.queryFilter().isBlank() ? "-" : payload.queryFilter(),
                payload.windowHoursFilter() == null ? "ANY" : payload.windowHoursFilter(),
                payload.force(),
                payload.limit(),
                payload.requested(),
                payload.estimatedQueued(),
                payload.estimatedSkipped(),
                payload.estimatedFailed(),
                summarizeQueueDeclinedRequeueDryRunReasonBreakdown(payload.reasonBreakdown())
            )
        );
    }

    private void auditQueueDeclinedClear(PreviewQueueDeclinedClearResponseDto payload) {
        if (payload == null) {
            return;
        }
        auditService.logEvent(
            "PREVIEW_QUEUE_DECLINED_CLEAR",
            null,
            "PREVIEW_DIAGNOSTICS",
            resolveAuditUsername(),
            String.format(
                "Cleared declined preview queue decisions (category=%s, forceRequired=%s, query=%s, windowHours=%s, limit=%d, requested=%d, cleared=%d, skipped=%d, failed=%d)",
                payload.categoryFilter(),
                payload.forceRequiredFilter(),
                payload.queryFilter() == null || payload.queryFilter().isBlank() ? "-" : payload.queryFilter(),
                payload.windowHoursFilter() == null ? "ANY" : payload.windowHoursFilter(),
                payload.limit(),
                payload.requested(),
                payload.cleared(),
                payload.skipped(),
                payload.failed()
            )
        );
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

    private String summarizeQueueDeclinedRequeueDryRunReasonBreakdown(
        List<PreviewQueueDeclinedRequeueDryRunReasonCountDto> reasonBreakdown
    ) {
        if (reasonBreakdown == null || reasonBreakdown.isEmpty()) {
            return "[]";
        }
        return reasonBreakdown.stream()
            .map(item -> String.format(
                "%s:%s=%d",
                item.reasonCode() == null || item.reasonCode().isBlank() ? "UNKNOWN" : item.reasonCode(),
                item.outcome() == null || item.outcome().isBlank() ? "UNKNOWN" : item.outcome(),
                item.count()
            ))
            .collect(Collectors.joining(", ", "[", "]"));
    }

    private record ReasonKey(String reason, String category, boolean retryable) {}

    private record FailureLedgerFilterScanResult(
        List<Document> matchedDocuments,
        int scanned,
        boolean truncated
    ) {}

    public record PreviewFailureSummaryDto(
        long totalFailures,
        int sampledFailures,
        int sampleLimit,
        int windowDays,
        LocalDateTime windowStart,
        boolean sampleTruncated,
        String confidenceLevel,
        String confidenceReason,
        List<PreviewFailureStatusCountDto> statusCounts,
        List<PreviewFailureCategoryCountDto> categoryCounts,
        List<PreviewFailureReasonCountDto> topReasons
    ) {}

    public record PreviewFailureLedgerDiagnosticsDto(
        long totalEntries,
        int sampledEntries,
        int limit,
        int windowDays,
        LocalDateTime windowStart,
        boolean sampleTruncated,
        List<PreviewFailureLedgerItemDto> items
    ) {}

    public record PreviewFailureLedgerItemDto(
        UUID documentId,
        String name,
        String path,
        String mimeType,
        String previewStatus,
        int failureCount,
        LocalDateTime failedAt,
        String lastReason,
        String category,
        boolean retryable,
        LocalDateTime previewLastUpdated,
        String failureContentHash,
        String currentContentHash,
        boolean staleByContentChange
    ) {
        static PreviewFailureLedgerItemDto from(Document document) {
            if (document == null) {
                return null;
            }
            EffectivePreviewSemantics previewSemantics = resolveEffectivePreviewSemantics(document);
            String normalizedMimeType = normalizeMimeType(document.getMimeType());
            String lastReason = normalizeReasonOrNull(document.getPreviewLastFailureReason());
            String category = PreviewFailureClassifier.classify(
                previewSemantics.status(),
                normalizedMimeType,
                lastReason != null ? lastReason : previewSemantics.failureReason()
            );
            String failureHash = normalizeHashOrNull(document.getPreviewFailureContentHash());
            String contentHash = normalizeHashOrNull(document.getContentHash());
            return new PreviewFailureLedgerItemDto(
                document.getId(),
                document.getName(),
                document.getPath(),
                normalizedMimeType,
                previewSemantics.status(),
                document.getPreviewFailureCount() != null ? document.getPreviewFailureCount() : 0,
                document.getPreviewFailedAt(),
                lastReason,
                category,
                PreviewFailureClassifier.CATEGORY_TEMPORARY.equalsIgnoreCase(category),
                document.getPreviewLastUpdated(),
                failureHash,
                contentHash,
                failureHash != null && contentHash != null && !failureHash.equals(contentHash)
            );
        }
    }

    public record PreviewFailureLedgerResetBatchRequestDto(
        List<UUID> documentIds
    ) {}

    public record PreviewFailureLedgerResetBatchResponseDto(
        int requested,
        int deduplicated,
        int reset,
        int failed,
        List<PreviewFailureLedgerResetItemDto> results
    ) {}

    public record PreviewFailureLedgerResetItemDto(
        UUID documentId,
        String name,
        int previousFailureCount,
        LocalDateTime previousFailedAt,
        String previousReason,
        String outcome,
        String message
    ) {}

    public record PreviewFailureLedgerResetByFilterRequestDto(
        String reason,
        String category,
        Boolean retryable,
        Integer maxDocuments,
        Integer days
    ) {}

    public record PreviewFailureLedgerResetByFilterResponseDto(
        String reason,
        String category,
        Boolean retryable,
        int windowDays,
        int maxDocuments,
        long totalCandidates,
        int scanned,
        int matched,
        boolean truncated,
        int reset,
        int skipped,
        int failed,
        List<PreviewFailureLedgerResetItemDto> results
    ) {}

    public record PreviewRenditionSummaryDto(
        long totalResources,
        int sampledResources,
        int sampleLimit,
        int windowDays,
        LocalDateTime windowStart,
        boolean sampleTruncated,
        List<PreviewRenditionStatusCountDto> statusCounts,
        List<PreviewRenditionFailureReasonCountDto> topReasons
    ) {}

    public record PreviewRenditionResourcesDiagnosticsDto(
        long totalResources,
        int sampledResources,
        int limit,
        int windowDays,
        LocalDateTime windowStart,
        boolean sampleTruncated,
        List<PreviewRenditionResourceItemDto> items
    ) {}

    public record PreviewRenditionResourceItemDto(
        UUID documentId,
        String name,
        String path,
        String mimeType,
        String previewStatus,
        String renditionStatus,
        String previewFailureReason,
        String previewFailureCategory,
        LocalDateTime previewLastUpdated
    ) {}

    public record PreviewRenditionResourcesExportAsyncRequestDto(
        Integer days,
        Integer limit
    ) {}

    public record PreviewRenditionResourcesExportAsyncRetryTerminalByTaskIdsRequestDto(
        List<String> sourceTaskIds
    ) {}

    public record PreviewRenditionResourcesExportAsyncCreateResponseDto(
        String taskId,
        String status,
        Instant createdAt,
        Instant timeoutAt,
        Instant expiresAt,
        String createdBy,
        String updatedBy,
        boolean deduplicated,
        String deduplicatedFromTaskId,
        String message
    ) {}

    public record PreviewRenditionResourcesExportAsyncStatusResponseDto(
        String taskId,
        String status,
        String error,
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

    public record PreviewRenditionResourcesExportAsyncListResponseDto(
        int count,
        PreviewTaskCenterPagingDto paging,
        List<PreviewRenditionResourcesExportAsyncStatusResponseDto> items
    ) {}

    public record PreviewRenditionResourcesExportAsyncSummaryResponseDto(
        long totalCount,
        long queuedCount,
        long runningCount,
        long completedCount,
        long cancelledCount,
        long failedCount,
        long timedOutCount,
        long expiredCount,
        long activeCount,
        long terminalCount
    ) {}

    public record PreviewRenditionResourcesExportAsyncCleanupResponseDto(
        long deletedCount,
        long remainingCount,
        String statusFilter,
        String message
    ) {}

    public record PreviewRenditionResourcesExportAsyncCancelActiveResponseDto(
        long cancelledCount,
        int remainingActiveCount,
        String statusFilter,
        String message
    ) {}

    public record PreviewRenditionResourcesExportAsyncRetryTerminalResponseDto(
        int requested,
        int retried,
        int reused,
        int skipped,
        int failed,
        int limit,
        String statusFilter,
        String message,
        List<PreviewRenditionResourcesExportAsyncRetryTerminalItemDto> results
    ) {}

    public record PreviewRenditionResourcesExportAsyncRetryTerminalItemDto(
        String sourceTaskId,
        String newTaskId,
        String sourceStatus,
        String outcome,
        String message
    ) {}

    public record PreviewRenditionResourcesExportAsyncRetryTerminalDryRunResponseDto(
        int requested,
        int retryable,
        int skipped,
        int limit,
        String statusFilter,
        String message,
        List<PreviewRenditionResourcesExportAsyncRetryTerminalDryRunItemDto> results,
        List<PreviewRenditionResourcesExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown
    ) {}

    public record PreviewRenditionResourcesExportAsyncRetryTerminalDryRunItemDto(
        String sourceTaskId,
        String sourceStatus,
        String outcome,
        String reasonCode,
        String message
    ) {}

    public record PreviewRenditionResourcesExportAsyncRetryTerminalDryRunReasonCountDto(
        String reasonCode,
        String outcome,
        long count
    ) {}

    public record PreviewRenditionStatusCountDto(
        String status,
        long count
    ) {}

    public record PreviewRenditionFailureReasonCountDto(
        String reason,
        long count
    ) {}

    public record PreviewQueueBatchRequestDto(
        List<UUID> documentIds,
        Boolean force
    ) {}

    public record PreviewReasonBatchQueueRequestDto(
        String reason,
        String category,
        Boolean retryable,
        Integer maxDocuments,
        Integer days,
        Boolean force
    ) {}

    public record PreviewReasonBatchQueueResponseDto(
        String reason,
        String category,
        Boolean retryable,
        int windowDays,
        int maxDocuments,
        long totalByReason,
        int scanned,
        int matched,
        boolean truncated,
        int queued,
        int skipped,
        int failed,
        List<PreviewQueueBatchItemDto> results
    ) {}

    public record PreviewQueueDiagnosticsSummaryDto(
        String backend,
        boolean queueEnabled,
        long scheduledCount,
        long governanceCount,
        long runningCount,
        boolean runningCountAccurate,
        long cancellationRequestedCount,
        int sampleLimit,
        boolean sampleTruncated,
        String stateFilter,
        String queryFilter,
        int totalSampledItems,
        int filteredSampledItems,
        List<PreviewQueueDiagnosticsItemDto> items
    ) {}

    public record PreviewQueueDiagnosticsItemDto(
        UUID documentId,
        String name,
        String path,
        String mimeType,
        String previewStatus,
        String previewFailureReason,
        String previewFailureCategory,
        LocalDateTime previewLastUpdated,
        String queueState,
        String governanceKey,
        int attempts,
        Instant nextAttemptAt,
        boolean running,
        boolean cancelRequested
    ) {}

    public record PreviewQueueCancelActiveResponseDto(
        String stateFilter,
        String queryFilter,
        int limit,
        int requested,
        int cancelled,
        int skipped,
        int failed,
        List<PreviewQueueCancelActiveItemDto> results
    ) {}

    public record PreviewQueueCancelActiveItemDto(
        UUID documentId,
        String previewStatus,
        String previewFailureReason,
        String previewFailureCategory,
        LocalDateTime previewLastUpdated,
        String queueState,
        String outcome,
        String message
    ) {}

    public record PreviewQueueDeclinedSummaryDto(
        boolean queueEnabled,
        long totalDeclined,
        int sampleLimit,
        boolean sampleTruncated,
        String categoryFilter,
        String forceRequiredFilter,
        String queryFilter,
        Integer windowHoursFilter,
        int totalSampledItems,
        int filteredSampledItems,
        long forceRequiredCount,
        List<PreviewQueueDeclinedCategoryCountDto> categoryCounts,
        List<PreviewQueueDeclinedItemDto> items
    ) {}

    public record PreviewQueueDeclinedCategoryCountDto(
        String category,
        long count,
        long forceRequiredCount
    ) {}

    public record PreviewQueueDeclinedItemDto(
        UUID documentId,
        String name,
        String path,
        String mimeType,
        String previewStatus,
        String previewFailureReason,
        String previewFailureCategory,
        LocalDateTime previewLastUpdated,
        String reason,
        String category,
        String governanceKey,
        Instant declinedAt,
        Instant nextEligibleAt,
        boolean forceRequired
    ) {}

    public record PreviewQueueDeclinedExportAsyncRequestDto(
        Integer limit,
        String category,
        String forceRequired,
        String query,
        Integer windowHours
    ) {}

    public record PreviewQueueDeclinedExportAsyncRetryTerminalByTaskIdsRequestDto(
        List<String> sourceTaskIds
    ) {}

    public record PreviewQueueDeclinedExportAsyncCreateResponseDto(
        String taskId,
        String status,
        Instant createdAt,
        Instant timeoutAt,
        Instant expiresAt,
        String createdBy,
        String updatedBy,
        boolean deduplicated,
        String deduplicatedFromTaskId,
        String message
    ) {}

    public record PreviewQueueDeclinedExportAsyncStatusResponseDto(
        String taskId,
        String status,
        String error,
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

    public record PreviewQueueDeclinedExportAsyncListResponseDto(
        int count,
        PreviewTaskCenterPagingDto paging,
        List<PreviewQueueDeclinedExportAsyncStatusResponseDto> items
    ) {}

    public record PreviewQueueDeclinedExportAsyncSummaryResponseDto(
        long totalCount,
        long queuedCount,
        long runningCount,
        long completedCount,
        long cancelledCount,
        long failedCount,
        long timedOutCount,
        long expiredCount,
        long activeCount,
        long terminalCount
    ) {}

    public record PreviewQueueDeclinedExportAsyncCleanupResponseDto(
        long deletedCount,
        long remainingCount,
        String statusFilter,
        String message
    ) {}

    public record PreviewQueueDeclinedExportAsyncCancelActiveResponseDto(
        long cancelledCount,
        int remainingActiveCount,
        String statusFilter,
        String message
    ) {}

    public record PreviewQueueDeclinedExportAsyncRetryTerminalResponseDto(
        int requested,
        int retried,
        int reused,
        int skipped,
        int failed,
        int limit,
        String statusFilter,
        String message,
        List<PreviewQueueDeclinedExportAsyncRetryTerminalItemDto> results
    ) {}

    public record PreviewQueueDeclinedExportAsyncRetryTerminalItemDto(
        String sourceTaskId,
        String newTaskId,
        String sourceStatus,
        String outcome,
        String message
    ) {}

    public record PreviewQueueDeclinedExportAsyncRetryTerminalDryRunResponseDto(
        int requested,
        int retryable,
        int skipped,
        int limit,
        String statusFilter,
        String message,
        List<PreviewQueueDeclinedExportAsyncRetryTerminalDryRunItemDto> results,
        List<PreviewQueueDeclinedExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown
    ) {}

    public record PreviewQueueDeclinedExportAsyncRetryTerminalDryRunItemDto(
        String sourceTaskId,
        String sourceStatus,
        String outcome,
        String reasonCode,
        String message
    ) {}

    public record PreviewQueueDeclinedExportAsyncRetryTerminalDryRunReasonCountDto(
        String reasonCode,
        String outcome,
        long count
    ) {}

    public record PreviewQueueDeclinedRequeueResponseDto(
        String categoryFilter,
        String forceRequiredFilter,
        String queryFilter,
        Integer windowHoursFilter,
        int limit,
        boolean force,
        int requested,
        int queued,
        int skipped,
        int failed,
        List<PreviewQueueDeclinedRequeueItemDto> results
    ) {}

    public record PreviewQueueDeclinedRequeueItemDto(
        UUID documentId,
        String category,
        String outcome,
        String message,
        String previewStatus,
        String previewFailureReason,
        String previewFailureCategory,
        LocalDateTime previewLastUpdated
    ) {}

    public record PreviewQueueDeclinedRequeueDryRunResponseDto(
        String categoryFilter,
        String forceRequiredFilter,
        String queryFilter,
        Integer windowHoursFilter,
        int limit,
        boolean force,
        int requested,
        int estimatedQueued,
        int estimatedSkipped,
        int estimatedFailed,
        List<PreviewQueueDeclinedRequeueDryRunItemDto> results,
        List<PreviewQueueDeclinedRequeueDryRunReasonCountDto> reasonBreakdown
    ) {}

    public record PreviewQueueDeclinedRequeueDryRunItemDto(
        UUID documentId,
        String category,
        String outcome,
        String reasonCode,
        String message,
        String previewStatus,
        String previewFailureReason,
        String previewFailureCategory,
        LocalDateTime previewLastUpdated,
        Instant nextAttemptAt,
        String preflightStatus,
        String preflightSkipReason,
        String preflightRoute,
        String preflightPolicyProfile,
        String preflightPipeline
    ) {}

    public record PreviewQueueDeclinedRequeueDryRunReasonCountDto(
        String reasonCode,
        String outcome,
        long count
    ) {}

    public record PreviewQueueDeclinedRequeueDryRunExportAsyncRequestDto(
        Integer limit,
        String category,
        String forceRequired,
        String query,
        Integer windowHours,
        Boolean force
    ) {}

    public record PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalByTaskIdsRequestDto(
        List<String> sourceTaskIds
    ) {}

    public record PreviewQueueDeclinedRequeueDryRunExportAsyncCreateResponseDto(
        String taskId,
        String status,
        Instant createdAt,
        Instant timeoutAt,
        Instant expiresAt,
        String createdBy,
        String updatedBy,
        boolean deduplicated,
        String deduplicatedFromTaskId,
        String message
    ) {}

    public record PreviewQueueDeclinedRequeueDryRunExportAsyncStatusResponseDto(
        String taskId,
        String status,
        String error,
        Instant createdAt,
        Instant startedAt,
        Instant updatedAt,
        Instant timeoutAt,
        Instant expiresAt,
        Instant finishedAt,
        String filename,
        String createdBy,
        String updatedBy,
        int limit,
        String categoryFilter,
        String forceRequiredFilter,
        String queryFilter,
        Integer windowHoursFilter,
        boolean force
    ) {}

    public record PreviewQueueDeclinedRequeueDryRunExportAsyncListResponseDto(
        int count,
        PreviewTaskCenterPagingDto paging,
        List<PreviewQueueDeclinedRequeueDryRunExportAsyncStatusResponseDto> items
    ) {}

    public record PreviewTaskCenterPagingDto(
        int skipCount,
        int maxItems,
        int totalItems,
        boolean hasMoreItems
    ) {}

    public record PreviewQueueDeclinedRequeueDryRunExportAsyncSummaryResponseDto(
        long totalCount,
        long queuedCount,
        long runningCount,
        long completedCount,
        long cancelledCount,
        long failedCount,
        long timedOutCount,
        long expiredCount,
        long activeCount,
        long terminalCount
    ) {}

    public record PreviewQueueDeclinedRequeueDryRunExportAsyncCleanupResponseDto(
        long deletedCount,
        int remainingCount,
        String statusFilter,
        String message
    ) {}

    public record PreviewQueueDeclinedRequeueDryRunExportAsyncCancelActiveResponseDto(
        long cancelledCount,
        int remainingActiveCount,
        String statusFilter,
        String message
    ) {}

    public record PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalResponseDto(
        int requested,
        int retried,
        int reused,
        int skipped,
        int failed,
        int limit,
        String statusFilter,
        String message,
        List<PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalItemDto> results
    ) {}

    public record PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalItemDto(
        String sourceTaskId,
        String newTaskId,
        String sourceStatus,
        String outcome,
        String message
    ) {}

    public record PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunResponseDto(
        int requested,
        int retryable,
        int skipped,
        int limit,
        String statusFilter,
        String message,
        List<PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunItemDto> results,
        List<PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown
    ) {}

    public record PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunItemDto(
        String sourceTaskId,
        String sourceStatus,
        String outcome,
        String reasonCode,
        String message
    ) {}

    public record PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunReasonCountDto(
        String reasonCode,
        String outcome,
        long count
    ) {}

    public record PreviewQueueDeclinedClearResponseDto(
        String categoryFilter,
        String forceRequiredFilter,
        String queryFilter,
        Integer windowHoursFilter,
        int limit,
        int requested,
        int cleared,
        int skipped,
        int failed,
        List<PreviewQueueDeclinedClearItemDto> results
    ) {}

    public record PreviewQueueDeclinedClearItemDto(
        UUID documentId,
        String category,
        String outcome,
        String message
    ) {}

    public record PreviewCadFailoverDiagnosticsDto(
        boolean cadPreviewEnabled,
        boolean configured,
        boolean circuitBreakerEnabled,
        int circuitFailureThreshold,
        long circuitOpenMs,
        long halfOpenTrialTimeoutMs,
        List<String> endpoints,
        List<PreviewCadFailoverEndpointStatsDto> endpointStats
    ) {}

    public record PreviewCadFailoverEndpointStatsDto(
        String endpoint,
        long successCount,
        long failureCount,
        Instant lastSuccessAt,
        Instant lastFailureAt,
        String lastFailureReason,
        long consecutiveFailureCount,
        String circuitState,
        Instant circuitOpenUntil,
        Instant lastCircuitOpenedAt,
        boolean halfOpenInFlight
    ) {}

    public record PreviewQueueBatchResponseDto(
        int requested,
        int deduplicated,
        int queued,
        int skipped,
        int failed,
        List<PreviewQueueBatchItemDto> results
    ) {}

    public record PreviewQueueBatchItemDto(
        UUID documentId,
        String outcome,
        String message,
        String previewStatus,
        String previewFailureReason,
        String previewFailureCategory,
        LocalDateTime previewLastUpdated,
        int attempts,
        Instant nextAttemptAt
    ) {}

    public record PreviewFailureStatusCountDto(
        String status,
        long count
    ) {}

    public record PreviewFailureCategoryCountDto(
        String category,
        boolean retryable,
        long count
    ) {}

    public record PreviewFailureReasonCountDto(
        String reason,
        String category,
        boolean retryable,
        long count
    ) {}

    public record PreviewFailureSampleDto(
        UUID id,
        String name,
        String path,
        String mimeType,
        String previewStatus,
        String previewFailureReason,
        String previewFailureCategory,
        LocalDateTime previewLastUpdated
    ) {
        static PreviewFailureSampleDto from(
            Document document,
            RenditionResourceService.RenditionSummary renditionSummary
        ) {
            if (document == null) {
                return null;
            }
            String mimeType = normalizeMimeType(document.getMimeType());
            boolean useRenditionSummary = renditionSummary != null && renditionSummary.document();
            EffectivePreviewSemantics semantics = useRenditionSummary
                ? new EffectivePreviewSemantics(
                    renditionSummary.previewStatus(),
                    normalizeReasonOrNull(renditionSummary.previewFailureReason()),
                    normalizeUpperOrNull(renditionSummary.previewFailureCategory())
                )
                : resolveEffectivePreviewSemantics(document);
            LocalDateTime previewLastUpdated = useRenditionSummary
                ? renditionSummary.previewLastUpdated()
                : document.getPreviewLastUpdated();
            return new PreviewFailureSampleDto(
                document.getId(),
                document.getName(),
                document.getPath(),
                mimeType,
                semantics.status(),
                semantics.failureReason(),
                semantics.failureCategory(),
                previewLastUpdated
            );
        }

        private static String normalizeMimeType(String mimeType) {
            if (mimeType == null || mimeType.isBlank()) {
                return "";
            }
            String normalized = mimeType.split(";")[0].trim().toLowerCase(Locale.ROOT);
            return normalized;
        }
    }

    private PreviewFailureSampleDto toPreviewFailureSample(Document document) {
        return PreviewFailureSampleDto.from(
            document,
            document != null ? renditionResourceService.summarizeDocument(document) : RenditionResourceService.RenditionSummary.empty(null)
        );
    }

    private String resolveEffectivePreviewStatus(Document document) {
        return resolveEffectivePreviewStatus(document, null);
    }

    private String resolveEffectivePreviewStatus(Document document, String fallbackPreviewStatus) {
        EffectivePreviewSnapshot snapshot = resolveEffectivePreviewSnapshot(document, fallbackPreviewStatus, null, null, null);
        if (snapshot != null && snapshot.status() != null) {
            return snapshot.status();
        }
        if (document != null) {
            EffectivePreviewSemantics semantics = resolveEffectivePreviewSemantics(document);
            if (semantics != null && semantics.status() != null) {
                return semantics.status();
            }
        }
        return normalizeUpperOrNull(fallbackPreviewStatus);
    }

    public record PreviewTransformTraceDto(
        String requestId,
        UUID documentId,
        String mimeType,
        String source,
        Instant startedAt,
        Instant finishedAt,
        String status,
        boolean retryNeeded,
        String failureReason,
        String latestMessage,
        List<PreviewTransformTraceEventDto> events
    ) {}

    public record PreviewTransformTraceEventDto(
        Instant at,
        String stage,
        String message
    ) {}

    public record PreviewFailurePolicyDto(
        String key,
        String label,
        int maxAttempts,
        long retryDelayMs,
        double backoffMultiplier,
        long quietPeriodMs,
        boolean builtIn
    ) {}

    public record PreviewFailurePolicyUpdateRequestDto(
        Integer maxAttempts,
        Long retryDelayMs,
        Double backoffMultiplier,
        Long quietPeriodMs
    ) {}

    public record PreviewRenditionPreventionDiagnosticsDto(
        boolean enabled,
        int blockedCount,
        int maxBlocked,
        List<String> autoBlockCategories,
        int limit,
        List<PreviewRenditionBlockedItemDto> items
    ) {}

    public record PreviewRenditionBlockedItemDto(
        UUID documentId,
        String name,
        String path,
        String mimeType,
        String previewStatus,
        String category,
        String reason,
        Instant blockedAt,
        Instant lastHitAt,
        long hitCount
    ) {}

    public record PreviewRenditionPreventionActionDto(
        UUID documentId,
        boolean unblocked,
        boolean queued,
        String message,
        String previewStatus,
        int attempts,
        Instant nextAttemptAt
    ) {}

    public record PreviewRenditionPreventionBatchRequestDto(
        List<UUID> documentIds,
        Boolean force
    ) {}

    public record PreviewRenditionPreventionBatchResponseDto(
        int requested,
        int deduplicated,
        int unblocked,
        int queued,
        int failed,
        List<PreviewRenditionPreventionActionDto> results
    ) {}

    public record PreviewDeadLetterDiagnosticsDto(
        boolean enabled,
        boolean redisEnabled,
        long ttlMs,
        String backendMode,
        int itemCount,
        int maxEntries,
        int limit,
        List<PreviewDeadLetterItemDto> items
    ) {}

    public record PreviewDeadLetterItemDto(
        String entryKey,
        UUID documentId,
        String renditionKey,
        String name,
        String path,
        String mimeType,
        String previewStatus,
        String reason,
        String category,
        String policyKey,
        String sourceStage,
        Instant failedAt,
        int attempts,
        long occurrences,
        Instant lastReplayAt,
        long replayCount
    ) {}

    public record PreviewDeadLetterReplayBatchRequestDto(
        List<UUID> documentIds,
        List<String> entryKeys,
        Boolean force
    ) {}

    public record PreviewDeadLetterReplayBatchResponseDto(
        int requested,
        int deduplicated,
        int queued,
        int skipped,
        int failed,
        List<PreviewQueueBatchItemDto> results
    ) {}

    public record PreviewDeadLetterClearBatchRequestDto(
        List<UUID> documentIds,
        List<String> entryKeys
    ) {}

    public record PreviewDeadLetterClearBatchResponseDto(
        int requested,
        int deduplicated,
        int cleared,
        int failed,
        List<PreviewDeadLetterClearItemDto> results
    ) {}

    public record PreviewDeadLetterClearItemDto(
        UUID documentId,
        String entryKey,
        String renditionKey,
        String outcome,
        String message
    ) {}

    private record QueueDeclinedExportAsyncRetryTerminalDryRunComputation(
        int requested,
        int retryable,
        int skipped,
        int limit,
        String statusFilterName,
        String message,
        List<PreviewQueueDeclinedExportAsyncRetryTerminalDryRunItemDto> results,
        List<PreviewQueueDeclinedExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown,
        List<QueueDeclinedExportAsyncTask> sourceTasks
    ) {}

    private record QueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunComputation(
        int requested,
        int retryable,
        int skipped,
        int limit,
        String statusFilterName,
        String message,
        List<PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunItemDto> results,
        List<PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown,
        List<QueueDeclinedRequeueDryRunExportAsyncTask> sourceTasks
    ) {}

    private record QueueDeclinedExportAsyncListPage(
        int totalItems,
        List<PreviewQueueDeclinedExportAsyncStatusResponseDto> items
    ) {}

    private record QueueDeclinedRequeueDryRunExportAsyncListPage(
        int totalItems,
        List<PreviewQueueDeclinedRequeueDryRunExportAsyncStatusResponseDto> items
    ) {}

    private record RenditionResourcesExportAsyncListPage(
        int totalItems,
        List<PreviewRenditionResourcesExportAsyncStatusResponseDto> items
    ) {}

    private record RenditionResourcesExportAsyncRetryTerminalDryRunComputation(
        int requested,
        int retryable,
        int skipped,
        int limit,
        String statusFilterName,
        String message,
        List<PreviewRenditionResourcesExportAsyncRetryTerminalDryRunItemDto> results,
        List<PreviewRenditionResourcesExportAsyncRetryTerminalDryRunReasonCountDto> reasonBreakdown
    ) {}

    private enum QueueDeclinedExportAsyncStatus {
        QUEUED,
        RUNNING,
        COMPLETED,
        CANCELLED,
        FAILED,
        TIMED_OUT,
        EXPIRED
    }

    private record QueueDeclinedExportAsyncTask(
        String taskId,
        Instant createdAt,
        Instant startedAt,
        Instant updatedAt,
        Instant timeoutAt,
        Instant expiresAt,
        String createdBy,
        String updatedBy,
        QueueDeclinedExportAsyncStatus status,
        String error,
        Instant finishedAt,
        String filename,
        byte[] csvContent,
        int limit,
        String categoryFilter,
        String forceRequiredFilter,
        String queryFilter,
        Integer windowHoursFilter
    ) {
        private QueueDeclinedExportAsyncTask withStatus(QueueDeclinedExportAsyncStatus nextStatus, String actor) {
            Instant now = Instant.now();
            Instant nextStartedAt = startedAt;
            Instant nextTimeoutAt = timeoutAt;
            String nextError = error;
            Instant nextFinishedAt = finishedAt;
            String nextFilename = filename;
            byte[] nextCsvContent = csvContent != null ? csvContent.clone() : null;

            if (nextStatus == QueueDeclinedExportAsyncStatus.QUEUED) {
                nextStartedAt = null;
                nextTimeoutAt = resolveAsyncTaskTimeoutAt(now);
                nextError = null;
                nextFinishedAt = null;
                nextFilename = null;
                nextCsvContent = null;
            } else if (nextStatus == QueueDeclinedExportAsyncStatus.RUNNING) {
                if (nextStartedAt == null) {
                    nextStartedAt = now;
                }
                nextTimeoutAt = resolveAsyncTaskTimeoutAt(now);
                nextError = null;
                nextFinishedAt = null;
                nextFilename = null;
                nextCsvContent = null;
            }

            return new QueueDeclinedExportAsyncTask(
                taskId,
                createdAt,
                nextStartedAt,
                now,
                nextTimeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                nextStatus,
                nextError,
                nextFinishedAt,
                nextFilename,
                nextCsvContent,
                limit,
                categoryFilter,
                forceRequiredFilter,
                queryFilter,
                windowHoursFilter
            );
        }

        private QueueDeclinedExportAsyncTask complete(String completedFilename, byte[] payload, String actor) {
            Instant now = Instant.now();
            return new QueueDeclinedExportAsyncTask(
                taskId,
                createdAt,
                startedAt,
                now,
                timeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                QueueDeclinedExportAsyncStatus.COMPLETED,
                null,
                now,
                completedFilename,
                payload != null ? payload.clone() : null,
                limit,
                categoryFilter,
                forceRequiredFilter,
                queryFilter,
                windowHoursFilter
            );
        }

        private QueueDeclinedExportAsyncTask fail(String errorMessage, String actor) {
            Instant now = Instant.now();
            return new QueueDeclinedExportAsyncTask(
                taskId,
                createdAt,
                startedAt,
                now,
                timeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                QueueDeclinedExportAsyncStatus.FAILED,
                errorMessage,
                now,
                null,
                null,
                limit,
                categoryFilter,
                forceRequiredFilter,
                queryFilter,
                windowHoursFilter
            );
        }

        private QueueDeclinedExportAsyncTask cancel(String reason, String actor) {
            Instant now = Instant.now();
            return new QueueDeclinedExportAsyncTask(
                taskId,
                createdAt,
                startedAt,
                now,
                timeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                QueueDeclinedExportAsyncStatus.CANCELLED,
                reason,
                now,
                null,
                null,
                limit,
                categoryFilter,
                forceRequiredFilter,
                queryFilter,
                windowHoursFilter
            );
        }

        private QueueDeclinedExportAsyncTask timeout(Instant timedOutAt, String actor) {
            Instant now = timedOutAt != null ? timedOutAt : Instant.now();
            return new QueueDeclinedExportAsyncTask(
                taskId,
                createdAt,
                startedAt,
                now,
                timeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                QueueDeclinedExportAsyncStatus.TIMED_OUT,
                ASYNC_TASK_TIMEOUT_MESSAGE,
                now,
                null,
                null,
                limit,
                categoryFilter,
                forceRequiredFilter,
                queryFilter,
                windowHoursFilter
            );
        }

        private QueueDeclinedExportAsyncTask expire(Instant expiredAt, String actor) {
            Instant now = expiredAt != null ? expiredAt : Instant.now();
            String expireMessage = (error == null || error.isBlank()) ? ASYNC_TASK_EXPIRED_MESSAGE : error;
            return new QueueDeclinedExportAsyncTask(
                taskId,
                createdAt,
                startedAt,
                now,
                timeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                QueueDeclinedExportAsyncStatus.EXPIRED,
                expireMessage,
                finishedAt != null ? finishedAt : now,
                null,
                null,
                limit,
                categoryFilter,
                forceRequiredFilter,
                queryFilter,
                windowHoursFilter
            );
        }

        private boolean isTerminal() {
            return status == QueueDeclinedExportAsyncStatus.COMPLETED
                || status == QueueDeclinedExportAsyncStatus.CANCELLED
                || status == QueueDeclinedExportAsyncStatus.FAILED
                || status == QueueDeclinedExportAsyncStatus.TIMED_OUT
                || status == QueueDeclinedExportAsyncStatus.EXPIRED;
        }
    }

    private enum QueueDeclinedRequeueDryRunExportAsyncStatus {
        QUEUED,
        RUNNING,
        COMPLETED,
        CANCELLED,
        FAILED,
        TIMED_OUT,
        EXPIRED
    }

    private record QueueDeclinedRequeueDryRunExportAsyncTask(
        String taskId,
        Instant createdAt,
        Instant startedAt,
        Instant updatedAt,
        Instant timeoutAt,
        Instant expiresAt,
        String createdBy,
        String updatedBy,
        QueueDeclinedRequeueDryRunExportAsyncStatus status,
        String error,
        Instant finishedAt,
        String filename,
        byte[] csvContent,
        int limit,
        String categoryFilter,
        String forceRequiredFilter,
        String queryFilter,
        Integer windowHoursFilter,
        boolean force
    ) {
        private QueueDeclinedRequeueDryRunExportAsyncTask withStatus(
            QueueDeclinedRequeueDryRunExportAsyncStatus nextStatus,
            String actor
        ) {
            Instant now = Instant.now();
            Instant nextStartedAt = startedAt;
            Instant nextTimeoutAt = timeoutAt;
            String nextError = error;
            Instant nextFinishedAt = finishedAt;
            String nextFilename = filename;
            byte[] nextCsvContent = csvContent != null ? csvContent.clone() : null;

            if (nextStatus == QueueDeclinedRequeueDryRunExportAsyncStatus.QUEUED) {
                nextStartedAt = null;
                nextTimeoutAt = resolveAsyncTaskTimeoutAt(now);
                nextError = null;
                nextFinishedAt = null;
                nextFilename = null;
                nextCsvContent = null;
            } else if (nextStatus == QueueDeclinedRequeueDryRunExportAsyncStatus.RUNNING) {
                if (nextStartedAt == null) {
                    nextStartedAt = now;
                }
                nextTimeoutAt = resolveAsyncTaskTimeoutAt(now);
                nextError = null;
                nextFinishedAt = null;
                nextFilename = null;
                nextCsvContent = null;
            }

            return new QueueDeclinedRequeueDryRunExportAsyncTask(
                taskId,
                createdAt,
                nextStartedAt,
                now,
                nextTimeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                nextStatus,
                nextError,
                nextFinishedAt,
                nextFilename,
                nextCsvContent,
                limit,
                categoryFilter,
                forceRequiredFilter,
                queryFilter,
                windowHoursFilter,
                force
            );
        }

        private QueueDeclinedRequeueDryRunExportAsyncTask complete(String completedFilename, byte[] payload, String actor) {
            Instant now = Instant.now();
            return new QueueDeclinedRequeueDryRunExportAsyncTask(
                taskId,
                createdAt,
                startedAt,
                now,
                timeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                QueueDeclinedRequeueDryRunExportAsyncStatus.COMPLETED,
                null,
                now,
                completedFilename,
                payload != null ? payload.clone() : null,
                limit,
                categoryFilter,
                forceRequiredFilter,
                queryFilter,
                windowHoursFilter,
                force
            );
        }

        private QueueDeclinedRequeueDryRunExportAsyncTask fail(String errorMessage, String actor) {
            Instant now = Instant.now();
            return new QueueDeclinedRequeueDryRunExportAsyncTask(
                taskId,
                createdAt,
                startedAt,
                now,
                timeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                QueueDeclinedRequeueDryRunExportAsyncStatus.FAILED,
                errorMessage,
                now,
                null,
                null,
                limit,
                categoryFilter,
                forceRequiredFilter,
                queryFilter,
                windowHoursFilter,
                force
            );
        }

        private QueueDeclinedRequeueDryRunExportAsyncTask cancel(String reason, String actor) {
            Instant now = Instant.now();
            return new QueueDeclinedRequeueDryRunExportAsyncTask(
                taskId,
                createdAt,
                startedAt,
                now,
                timeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                QueueDeclinedRequeueDryRunExportAsyncStatus.CANCELLED,
                reason,
                now,
                null,
                null,
                limit,
                categoryFilter,
                forceRequiredFilter,
                queryFilter,
                windowHoursFilter,
                force
            );
        }

        private QueueDeclinedRequeueDryRunExportAsyncTask timeout(Instant timedOutAt, String actor) {
            Instant now = timedOutAt != null ? timedOutAt : Instant.now();
            return new QueueDeclinedRequeueDryRunExportAsyncTask(
                taskId,
                createdAt,
                startedAt,
                now,
                timeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                QueueDeclinedRequeueDryRunExportAsyncStatus.TIMED_OUT,
                ASYNC_TASK_TIMEOUT_MESSAGE,
                now,
                null,
                null,
                limit,
                categoryFilter,
                forceRequiredFilter,
                queryFilter,
                windowHoursFilter,
                force
            );
        }

        private QueueDeclinedRequeueDryRunExportAsyncTask expire(Instant expiredAt, String actor) {
            Instant now = expiredAt != null ? expiredAt : Instant.now();
            String expireMessage = (error == null || error.isBlank()) ? ASYNC_TASK_EXPIRED_MESSAGE : error;
            return new QueueDeclinedRequeueDryRunExportAsyncTask(
                taskId,
                createdAt,
                startedAt,
                now,
                timeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                QueueDeclinedRequeueDryRunExportAsyncStatus.EXPIRED,
                expireMessage,
                finishedAt != null ? finishedAt : now,
                null,
                null,
                limit,
                categoryFilter,
                forceRequiredFilter,
                queryFilter,
                windowHoursFilter,
                force
            );
        }

        private boolean isTerminal() {
            return status == QueueDeclinedRequeueDryRunExportAsyncStatus.COMPLETED
                || status == QueueDeclinedRequeueDryRunExportAsyncStatus.CANCELLED
                || status == QueueDeclinedRequeueDryRunExportAsyncStatus.FAILED
                || status == QueueDeclinedRequeueDryRunExportAsyncStatus.TIMED_OUT
                || status == QueueDeclinedRequeueDryRunExportAsyncStatus.EXPIRED;
        }
    }

    private enum RenditionResourcesExportAsyncStatus {
        QUEUED,
        RUNNING,
        COMPLETED,
        CANCELLED,
        FAILED,
        TIMED_OUT,
        EXPIRED
    }

    private record RenditionResourcesExportAsyncTask(
        String taskId,
        Instant createdAt,
        Instant startedAt,
        Instant updatedAt,
        Instant timeoutAt,
        Instant expiresAt,
        String createdBy,
        String updatedBy,
        RenditionResourcesExportAsyncStatus status,
        String error,
        Instant finishedAt,
        String filename,
        byte[] csvContent
    ) {
        private RenditionResourcesExportAsyncTask withStatus(
            RenditionResourcesExportAsyncStatus nextStatus,
            String actor
        ) {
            Instant now = Instant.now();
            String nextActor = resolveTaskActor(actor);
            Instant nextStartedAt = startedAt;
            Instant nextTimeoutAt = timeoutAt;
            if (nextStatus == RenditionResourcesExportAsyncStatus.RUNNING) {
                if (nextStartedAt == null) {
                    nextStartedAt = now;
                }
                nextTimeoutAt = resolveAsyncTaskTimeoutAt(now);
            }
            return new RenditionResourcesExportAsyncTask(
                taskId,
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

        private RenditionResourcesExportAsyncTask complete(
            String completedFilename,
            byte[] payload,
            String actor
        ) {
            Instant now = Instant.now();
            return new RenditionResourcesExportAsyncTask(
                taskId,
                createdAt,
                startedAt,
                now,
                timeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                RenditionResourcesExportAsyncStatus.COMPLETED,
                null,
                now,
                completedFilename,
                payload != null ? payload.clone() : null
            );
        }

        private RenditionResourcesExportAsyncTask fail(String errorMessage, String actor) {
            Instant now = Instant.now();
            return new RenditionResourcesExportAsyncTask(
                taskId,
                createdAt,
                startedAt,
                now,
                timeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                RenditionResourcesExportAsyncStatus.FAILED,
                errorMessage,
                now,
                null,
                null
            );
        }

        private RenditionResourcesExportAsyncTask cancel(String reason, String actor) {
            Instant now = Instant.now();
            return new RenditionResourcesExportAsyncTask(
                taskId,
                createdAt,
                startedAt,
                now,
                timeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                RenditionResourcesExportAsyncStatus.CANCELLED,
                reason,
                now,
                null,
                null
            );
        }

        private RenditionResourcesExportAsyncTask timeout(Instant at, String actor) {
            Instant now = at != null ? at : Instant.now();
            String timeoutMessage = (error == null || error.isBlank()) ? ASYNC_TASK_TIMEOUT_MESSAGE : error;
            return new RenditionResourcesExportAsyncTask(
                taskId,
                createdAt,
                startedAt,
                now,
                timeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                RenditionResourcesExportAsyncStatus.TIMED_OUT,
                timeoutMessage,
                now,
                null,
                null
            );
        }

        private RenditionResourcesExportAsyncTask expire(Instant at, String actor) {
            Instant now = at != null ? at : Instant.now();
            String expireMessage = (error == null || error.isBlank()) ? ASYNC_TASK_EXPIRED_MESSAGE : error;
            return new RenditionResourcesExportAsyncTask(
                taskId,
                createdAt,
                startedAt,
                now,
                timeoutAt,
                expiresAt,
                createdBy,
                resolveTaskActor(actor),
                RenditionResourcesExportAsyncStatus.EXPIRED,
                expireMessage,
                finishedAt != null ? finishedAt : now,
                null,
                null
            );
        }

        private boolean isTerminal() {
            return status == RenditionResourcesExportAsyncStatus.COMPLETED
                || status == RenditionResourcesExportAsyncStatus.CANCELLED
                || status == RenditionResourcesExportAsyncStatus.FAILED
                || status == RenditionResourcesExportAsyncStatus.TIMED_OUT
                || status == RenditionResourcesExportAsyncStatus.EXPIRED;
        }
    }
}
