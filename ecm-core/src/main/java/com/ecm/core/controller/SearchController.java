package com.ecm.core.controller;

import com.ecm.core.asynctask.AsyncTaskSummaryAdapters;
import com.ecm.core.asynctask.AsyncTaskSummarySnapshot;
import com.ecm.core.batch.BatchExecutor;
import com.ecm.core.preview.PreviewFailureClassifier;
import com.ecm.core.preview.PreviewPreflightResolver;
import com.ecm.core.preview.PreviewQueueService;
import com.ecm.core.search.*;
import com.ecm.core.search.FacetedSearchService.*;
import com.ecm.core.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
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

/**
 * Search API Controller
 *
 * Provides REST endpoints for:
 * - Full-text search with highlighting
 * - Advanced search with filters
 * - Faceted search with aggregations
 * - Index management (rebuild, stats)
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Full-text search APIs")
public class SearchController {

    private final FullTextSearchService fullTextSearchService;
    private final SearchIndexService searchIndexService;
    private final FacetedSearchService facetedSearchService;
    private final SecurityService securityService;
    private final PreviewQueueService previewQueueService;
    private final PreviewPreflightResolver previewPreflightResolver;
    private static final int DEFAULT_PREVIEW_QUEUE_MATCH_LIMIT = 100;
    private static final int MAX_PREVIEW_QUEUE_MATCH_LIMIT = 500;
    private static final int DEFAULT_PREVIEW_QUEUE_SCAN_PAGE_SIZE = 100;
    private static final int MAX_PREVIEW_QUEUE_SCAN_LIMIT = 5000;
    private static final int DEFAULT_PREVIEW_QUEUE_BATCH_WORKER_COUNT = 4;
    private static final int MAX_PREVIEW_QUEUE_BATCH_WORKER_COUNT = 16;
    private static final int MAX_DRY_RUN_EXPORT_ASYNC_TASKS = 100;
    private static final int MAX_ADVANCED_STATS_PIVOT_PREVIEW_STATUS = 4;
    private static final int MAX_ADVANCED_STATS_PIVOT_MIME_TYPE = 4;
    private final Map<String, DryRunExportAsyncTask> dryRunExportAsyncTasks = new ConcurrentHashMap<>();
    private final Deque<String> dryRunExportAsyncTaskOrder = new ArrayDeque<>();
    private final Object dryRunExportAsyncTaskLock = new Object();

    // ==================== Search Endpoints ====================

    @GetMapping
    @Operation(summary = "Full-text search",
               description = "Search documents with full-text matching and highlighting")
    public ResponseEntity<Page<SearchResult>> search(
            @Parameter(description = "Search query")
            @RequestParam String q,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field (relevance, name, modified, size)")
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction (asc, desc)")
            @RequestParam(required = false) String sortDirection,
            @Parameter(description = "Optional folder scope (UUID). When set, search is limited to this folder.")
            @RequestParam(required = false) String folderId,
            @Parameter(description = "When folderId is set, whether to include subfolders (default true).")
            @RequestParam(defaultValue = "true") boolean includeChildren,
            @Parameter(description = "Optional preview status filter (CSV). Example: READY,FAILED,UNSUPPORTED,PENDING")
            @RequestParam(required = false, name = "previewStatus") String previewStatus) {

        List<String> previewStatuses = parseCsvParam(previewStatus);
        Page<SearchResult> results = fullTextSearchService.search(
            q,
            page,
            size,
            sortBy,
            sortDirection,
            folderId,
            includeChildren,
            previewStatuses
        );
        return ResponseEntity.ok(results);
    }

    private static List<String> parseCsvParam(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
    }

    @GetMapping("/diagnostics")
    @Operation(summary = "Search diagnostics",
               description = "Return applied ACL filter context for the current user")
    public ResponseEntity<SearchDiagnosticsResponse> searchDiagnostics() {
        String username = securityService.getCurrentUser();
        boolean isAdmin = securityService.hasRole("ROLE_ADMIN");
        Set<String> authorities = securityService.getUserAuthorities(username);
        int authorityCount = authorities == null ? 0 : authorities.size();
        List<String> sampleAuthorities = authorities == null
            ? List.of()
            : authorities.stream().sorted().limit(8).toList();
        String note = isAdmin
            ? "Admin role bypasses read filter."
            : (authorityCount == 0
                ? "No authorities resolved; search results may be empty."
                : "Read filter applied to search results.");

        return ResponseEntity.ok(new SearchDiagnosticsResponse(
            username,
            isAdmin,
            !isAdmin,
            authorityCount,
            sampleAuthorities,
            note,
            java.time.Instant.now().toString()
        ));
    }

    @PostMapping("/advanced")
    @Operation(summary = "Advanced search",
               description = "Search with filters, facets, and pagination")
    public ResponseEntity<Page<SearchResult>> advancedSearch(
            @RequestBody SearchRequest request) {

        if (request.getPageable() == null) {
            request.setPageable(new SimplePageRequest());
        }

        Page<SearchResult> results = fullTextSearchService.advancedSearch(request);
        return ResponseEntity.ok(results);
    }

    @PostMapping("/query")
    @Operation(
        summary = "Unified search query envelope",
        description = "Execute advanced search through a unified envelope that can include results, request context, stats, and pivot data."
    )
    public ResponseEntity<SearchQueryEnvelopeResponse> query(
        @RequestBody(required = false) SearchQueryEnvelopeRequest request
    ) {
        SearchQueryEnvelopeRequest effectiveRequest = request != null ? request : new SearchQueryEnvelopeRequest(
            null, null, null, null, null, null, null, null, null
        );
        SearchRequest normalizedRequest = toSearchRequest(effectiveRequest);
        List<String> include = resolveEnvelopeInclude(effectiveRequest.include());

        FacetedSearchResponse facetedResponse = requiresFacetedEnvelope(include)
            ? facetedSearchService.search(buildEnvelopeFacetedRequest(normalizedRequest, include))
            : null;
        Page<SearchResult> results = include.contains("results")
            ? (facetedResponse != null ? facetedResponse.getResults() : fullTextSearchService.advancedSearch(normalizedRequest))
            : null;
        AdvancedSearchContextResponse context = include.contains("context")
            ? buildAdvancedSearchContextResponse(normalizedRequest)
            : null;
        AdvancedSearchStatsResponse stats = include.contains("stats")
            ? buildAdvancedSearchStatsResponse(normalizedRequest)
            : null;
        AdvancedSearchPivotStatsResponse pivot = include.contains("pivot")
            ? buildAdvancedSearchPivotStatsResponse(normalizedRequest)
            : null;

        SearchQueryEnvelopeRequestEcho requestEcho = Boolean.TRUE.equals(effectiveRequest.includeRequest())
            ? buildEnvelopeRequestEcho(normalizedRequest, include)
            : null;

        return ResponseEntity.ok(new SearchQueryEnvelopeResponse(
            requestEcho,
            results,
            facetedResponse != null ? facetedResponse.getFacets() : null,
            facetedResponse != null ? facetedResponse.getSuggestions() : null,
            context,
            stats,
            pivot,
            Instant.now().toString()
        ));
    }

    @PostMapping("/advanced/context")
    @Operation(
        summary = "Advanced search request context",
        description = "Return normalized advanced-search request context for diagnostics and operator troubleshooting."
    )
    public ResponseEntity<AdvancedSearchContextResponse> advancedSearchContext(
        @RequestBody(required = false) SearchRequest request
    ) {
        SearchRequest effectiveRequest = request != null ? request : new SearchRequest();
        return ResponseEntity.ok(buildAdvancedSearchContextResponse(effectiveRequest));
    }

    @PostMapping("/advanced/stats")
    @Operation(
        summary = "Advanced search stats",
        description = "Return aggregated facet/range stats for advanced-search query and filters."
    )
    public ResponseEntity<AdvancedSearchStatsResponse> advancedSearchStats(
        @RequestBody(required = false) SearchRequest request
    ) {
        SearchRequest effectiveRequest = request != null ? request : new SearchRequest();
        return ResponseEntity.ok(buildAdvancedSearchStatsResponse(effectiveRequest));
    }

    @PostMapping("/advanced/stats/pivot")
    @Operation(
        summary = "Advanced search pivot stats",
        description = "Return top previewStatus/mimeType buckets and a bounded status x mime pivot matrix."
    )
    public ResponseEntity<AdvancedSearchPivotStatsResponse> advancedSearchPivotStats(
        @RequestBody(required = false) SearchRequest request
    ) {
        SearchRequest effectiveRequest = request != null ? request : new SearchRequest();
        return ResponseEntity.ok(buildAdvancedSearchPivotStatsResponse(effectiveRequest));
    }

    @PostMapping("/preview/queue-failed")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Queue failed previews by search scope",
        description = "Queue retry/rebuild for retryable failed previews matched by search query + filters (admin only)."
    )
    public ResponseEntity<PreviewQueueBySearchResponse> queueFailedPreviewsBySearch(
        @RequestBody PreviewQueueBySearchRequest request
    ) {
        SearchScopeMatchPlan plan = collectMatchedRetryableFailures(request);
        List<PreviewQueueBySearchReasonCountDto> reasonBreakdown = buildReasonBreakdown(plan.matches());
        boolean force = request != null && Boolean.TRUE.equals(request.force());
        int workerCount = resolvePreviewQueueWorkerCount(request);
        BatchExecutor.RunResult<PreviewQueueSearchBatchItemDto> execution = BatchExecutor.runParallel(
            plan.matches(),
            workerCount,
            item -> queueSearchResultPreview(item, force, plan.preflightByDocumentId().get(item.getId())),
            (item, ex) -> new PreviewQueueSearchBatchItemDto(
                item != null ? item.getId() : null,
                "FAILED",
                resolveErrorMessage(ex),
                item != null ? item.getPreviewStatus() : null,
                item != null ? item.getPreviewFailureReason() : null,
                item != null ? item.getPreviewFailureCategory() : null,
                null,
                "FAILED",
                0,
                null
            )
        );

        PreviewQueueBySearchResponse payload = new PreviewQueueBySearchResponse(
            plan.query(),
            plan.reason(),
            plan.maxDocuments(),
            plan.totalCandidates(),
            plan.scanned(),
            plan.matched(),
            sumSkipCounts(plan.skipBreakdown()),
            plan.truncated(),
            reasonBreakdown,
            plan.skipBreakdown(),
            execution.requested(),
            execution.processed(),
            execution.succeeded(),
            execution.skipped(),
            execution.failed(),
            execution.results(),
            workerCount
        );
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/preview/queue-failed/capabilities")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Preview queue failed-by-search capabilities",
        description = "Return bounded limits and worker defaults for search-scope preview retry/rebuild operations (admin only)."
    )
    public ResponseEntity<PreviewQueueBySearchCapabilitiesResponse> getQueueFailedPreviewsBySearchCapabilities() {
        return ResponseEntity.ok(new PreviewQueueBySearchCapabilitiesResponse(
            DEFAULT_PREVIEW_QUEUE_MATCH_LIMIT,
            MAX_PREVIEW_QUEUE_MATCH_LIMIT,
            DEFAULT_PREVIEW_QUEUE_SCAN_PAGE_SIZE,
            MAX_PREVIEW_QUEUE_SCAN_LIMIT,
            DEFAULT_PREVIEW_QUEUE_BATCH_WORKER_COUNT,
            MAX_PREVIEW_QUEUE_BATCH_WORKER_COUNT
        ));
    }

    @PostMapping(value = "/preview/queue-failed/dry-run/export", produces = "text/csv")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Export dry-run failed previews by search scope (CSV)",
        description = "Export matched retryable failed previews and reason breakdown as CSV without queueing."
    )
    public ResponseEntity<byte[]> exportDryRunQueueFailedPreviewsBySearchCsv(
        @RequestBody PreviewQueueBySearchRequest request
    ) {
        SearchScopeMatchPlan plan = collectMatchedRetryableFailures(request);
        int workerCount = resolvePreviewQueueWorkerCount(request);
        List<PreviewQueueBySearchReasonCountDto> reasonBreakdown = buildReasonBreakdown(plan.matches());

        String csvContent = buildDryRunExportCsv(plan, reasonBreakdown, workerCount);
        String filename = "search-preview-dry-run-" + Instant.now().toEpochMilli() + ".csv";

        return buildCsvAttachmentResponse(csvContent.getBytes(StandardCharsets.UTF_8), filename);
    }

    @PostMapping("/preview/queue-failed/dry-run/export-async")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Start async dry-run failed previews CSV export by search scope",
        description = "Start an asynchronous CSV export task for retryable failed previews (admin only)."
    )
    public ResponseEntity<PreviewQueueBySearchDryRunExportAsyncCreateResponse> startDryRunQueueFailedPreviewsBySearchCsvAsync(
        @RequestBody PreviewQueueBySearchRequest request
    ) {
        PreviewQueueBySearchRequest requestSnapshot = copyPreviewQueueBySearchRequest(request);
        DryRunExportAsyncTask task = createDryRunExportAsyncTask();

        CompletableFuture.runAsync(() -> runDryRunExportAsyncTask(task, requestSnapshot));

        return ResponseEntity.ok(new PreviewQueueBySearchDryRunExportAsyncCreateResponse(
            task.taskId(),
            task.status().name(),
            task.createdAt()
        ));
    }

    @GetMapping("/preview/queue-failed/dry-run/export-async")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "List async dry-run failed previews CSV export tasks",
        description = "List recent asynchronous CSV export tasks (admin only)."
    )
    public ResponseEntity<PreviewQueueBySearchDryRunExportAsyncListResponse> listDryRunQueueFailedPreviewsBySearchCsvAsyncTasks(
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(required = false) String status
    ) {
        int boundedLimit = clamp(limit, 1, 100);
        DryRunExportAsyncStatus statusFilter = parseDryRunExportAsyncStatus(status);
        List<PreviewQueueBySearchDryRunExportAsyncStatusResponse> items = listDryRunExportAsyncTasks(
            boundedLimit,
            statusFilter
        );
        return ResponseEntity.ok(new PreviewQueueBySearchDryRunExportAsyncListResponse(
            items.size(),
            items
        ));
    }

    @GetMapping("/preview/queue-failed/dry-run/export-async/summary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Get async dry-run failed previews CSV export task summary",
        description = "Return aggregate async export task counters by status and lifecycle class (admin only)."
    )
    public ResponseEntity<PreviewQueueBySearchDryRunExportAsyncSummaryResponse> summarizeDryRunQueueFailedPreviewsBySearchCsvAsyncTasks(
        @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(
            AsyncTaskSummaryAdapters.toSearchDryRun(
                summarizeDryRunQueueFailedPreviewsBySearchCsvAsyncTaskSnapshot(status)
            )
        );
    }

    public AsyncTaskSummarySnapshot summarizeDryRunQueueFailedPreviewsBySearchCsvAsyncTaskSnapshot(String status) {
        DryRunExportAsyncStatus statusFilter = parseDryRunExportAsyncStatus(status);
        return buildDryRunExportAsyncSummarySnapshot(statusFilter);
    }

    @GetMapping("/preview/queue-failed/dry-run/export-async/{taskId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Get async dry-run failed previews CSV export task status",
        description = "Get status and details for an asynchronous CSV export task (admin only)."
    )
    public ResponseEntity<PreviewQueueBySearchDryRunExportAsyncStatusResponse> getDryRunQueueFailedPreviewsBySearchCsvAsyncStatus(
        @PathVariable String taskId
    ) {
        DryRunExportAsyncTask task = dryRunExportAsyncTasks.get(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new PreviewQueueBySearchDryRunExportAsyncStatusResponse(
            task.taskId(),
            task.status().name(),
            task.error(),
            task.createdAt(),
            task.finishedAt(),
            task.status() == DryRunExportAsyncStatus.COMPLETED ? task.filename() : null
        ));
    }

    @PostMapping("/preview/queue-failed/dry-run/export-async/{taskId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Cancel async dry-run failed previews CSV export task",
        description = "Cancel a queued/running asynchronous CSV export task (admin only)."
    )
    public ResponseEntity<PreviewQueueBySearchDryRunExportAsyncStatusResponse> cancelDryRunQueueFailedPreviewsBySearchCsvAsyncTask(
        @PathVariable String taskId
    ) {
        DryRunExportAsyncTask existing = dryRunExportAsyncTasks.get(taskId);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (existing.isTerminal()) {
            return ResponseEntity.status(409).body(toAsyncStatusResponse(existing));
        }
        DryRunExportAsyncTask updated = dryRunExportAsyncTasks.computeIfPresent(taskId, (key, current) -> {
            if (current.isTerminal()) {
                return current;
            }
            return current.cancel("Cancelled by user");
        });
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toAsyncStatusResponse(updated));
    }

    @PostMapping("/preview/queue-failed/dry-run/export-async/cancel-active")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Cancel active async dry-run failed previews CSV export tasks",
        description = "Cancel all active async export tasks (QUEUED + RUNNING) or only one active status (admin only)."
    )
    public ResponseEntity<PreviewQueueBySearchDryRunExportAsyncCancelActiveResponse> cancelActiveDryRunQueueFailedPreviewsBySearchCsvAsyncTasks(
        @RequestParam(required = false) String status
    ) {
        DryRunExportAsyncStatus statusFilter = parseDryRunExportAsyncStatus(status);
        if (statusFilter != null && !isDryRunExportAsyncActiveStatus(statusFilter)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "status filter only supports active states: QUEUED, RUNNING"
            );
        }

        int cancelledCount;
        int remainingActiveCount;
        synchronized (dryRunExportAsyncTaskLock) {
            cancelledCount = cancelActiveDryRunExportAsyncTasksLocked(statusFilter);
            remainingActiveCount = countActiveDryRunExportAsyncTasksLocked();
        }

        String normalizedStatusFilter = statusFilter != null ? statusFilter.name() : null;
        String message = cancelledCount > 0
            ? normalizedStatusFilter == null
                ? String.format("Cancelled %d active async dry-run export tasks", cancelledCount)
                : String.format("Cancelled %d active async dry-run export tasks with status %s", cancelledCount, normalizedStatusFilter)
            : normalizedStatusFilter == null
                ? "No active async dry-run export tasks to cancel"
                : String.format("No active async dry-run export tasks with status %s to cancel", normalizedStatusFilter);

        return ResponseEntity.ok(new PreviewQueueBySearchDryRunExportAsyncCancelActiveResponse(
            cancelledCount,
            remainingActiveCount,
            normalizedStatusFilter,
            message
        ));
    }

    @PostMapping("/preview/queue-failed/dry-run/export-async/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Cleanup async dry-run failed previews CSV export tasks",
        description = "Delete terminal async export tasks by default, or by specific terminal status (admin only)."
    )
    public ResponseEntity<PreviewQueueBySearchDryRunExportAsyncCleanupResponse> cleanupDryRunQueueFailedPreviewsBySearchCsvAsyncTasks(
        @RequestParam(required = false) String status
    ) {
        DryRunExportAsyncStatus statusFilter = parseDryRunExportAsyncStatus(status);
        if (isDryRunExportAsyncActiveStatus(statusFilter)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "status filter only supports terminal states: COMPLETED, CANCELLED, FAILED"
            );
        }

        int deletedCount;
        int remainingCount;
        synchronized (dryRunExportAsyncTaskLock) {
            deletedCount = cleanupDryRunExportAsyncTasksLocked(statusFilter);
            remainingCount = dryRunExportAsyncTasks.size();
        }

        String normalizedStatusFilter = statusFilter != null ? statusFilter.name() : null;
        String message = deletedCount > 0
            ? normalizedStatusFilter == null
                ? String.format("Deleted %d terminal async dry-run export tasks", deletedCount)
                : String.format("Deleted %d async dry-run export tasks with status %s", deletedCount, normalizedStatusFilter)
            : normalizedStatusFilter == null
                ? "No terminal async dry-run export tasks to delete"
                : String.format("No async dry-run export tasks with status %s to delete", normalizedStatusFilter);

        return ResponseEntity.ok(new PreviewQueueBySearchDryRunExportAsyncCleanupResponse(
            deletedCount,
            remainingCount,
            normalizedStatusFilter,
            message
        ));
    }

    @GetMapping(value = "/preview/queue-failed/dry-run/export-async/{taskId}/download", produces = "text/csv")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Download async dry-run failed previews CSV export task result",
        description = "Download CSV attachment for a completed asynchronous export task (admin only)."
    )
    public ResponseEntity<byte[]> downloadDryRunQueueFailedPreviewsBySearchCsvAsyncResult(
        @PathVariable String taskId
    ) {
        DryRunExportAsyncTask task = dryRunExportAsyncTasks.get(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }

        if (task.status() != DryRunExportAsyncStatus.COMPLETED || task.csvContent() == null || task.filename() == null) {
            return ResponseEntity.status(409).build();
        }

        return buildCsvAttachmentResponse(task.csvContent().clone(), task.filename());
    }

    @PostMapping("/preview/queue-failed/dry-run")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Dry-run failed previews by search scope",
        description = "Preview retryable failed previews matched by search query + filters without queueing."
    )
    public ResponseEntity<PreviewQueueBySearchDryRunResponse> dryRunQueueFailedPreviewsBySearch(
        @RequestBody PreviewQueueBySearchRequest request
    ) {
        SearchScopeMatchPlan plan = collectMatchedRetryableFailures(request);
        int workerCount = resolvePreviewQueueWorkerCount(request);
        List<PreviewQueueBySearchReasonCountDto> reasonBreakdown = buildReasonBreakdown(plan.matches());
        List<PreviewQueueBySearchDryRunItemDto> samples = plan.matches().stream()
            .limit(20)
            .map(item -> {
                PreviewPreflightResolver.PreflightDecision decision = plan.preflightByDocumentId().get(item.getId());
                return new PreviewQueueBySearchDryRunItemDto(
                    item.getId(),
                    item.getName(),
                    item.getPreviewStatus(),
                    item.getPreviewFailureReason(),
                    item.getPreviewFailureCategory(),
                    decision != null ? decision.preflightStatus() : "UNKNOWN",
                    decision != null ? decision.skipReason() : null,
                    decision != null ? decision.route() : null,
                    decision != null ? decision.policyProfileKey() : null,
                    decision != null ? decision.pipelineChainSummary() : null
                );
            })
            .toList();

        PreviewQueueBySearchDryRunResponse payload = new PreviewQueueBySearchDryRunResponse(
            plan.query(),
            plan.reason(),
            plan.maxDocuments(),
            plan.totalCandidates(),
            plan.scanned(),
            plan.matched(),
            sumSkipCounts(plan.skipBreakdown()),
            plan.truncated(),
            reasonBreakdown,
            plan.skipBreakdown(),
            workerCount,
            samples.size(),
            samples
        );
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/quick")
    @Operation(summary = "Quick search",
               description = "Simple keyword search for autocomplete/suggestions")
    public ResponseEntity<Page<SearchResult>> quickSearch(
            @Parameter(description = "Search query")
            @RequestParam String q,
            @Parameter(description = "Maximum results")
            @RequestParam(defaultValue = "10") int limit) {

        Page<SearchResult> results = fullTextSearchService.search(q, 0, limit);
        return ResponseEntity.ok(results);
    }

    public record SearchDiagnosticsResponse(
        String username,
        boolean admin,
        boolean readFilterApplied,
        int authorityCount,
        List<String> authoritySample,
        String note,
        String generatedAt
    ) {}

    public record SearchQueryEnvelopeRequest(
        String query,
        SearchFilters filters,
        String sortBy,
        String sortDirection,
        SimplePageRequest pageable,
        Boolean highlightEnabled,
        List<String> facets,
        Boolean includeRequest,
        List<String> include
    ) {}

    public record SearchQueryEnvelopeRequestEcho(
        String query,
        String normalizedQuery,
        SearchFilters filters,
        String sortBy,
        String sortDirection,
        int page,
        int size,
        boolean highlightEnabled,
        List<String> facets,
        List<String> include
    ) {}

    public record SearchQueryEnvelopeResponse(
        SearchQueryEnvelopeRequestEcho request,
        Page<SearchResult> results,
        Map<String, List<FacetValue>> facets,
        List<String> suggestions,
        AdvancedSearchContextResponse context,
        AdvancedSearchStatsResponse stats,
        AdvancedSearchPivotStatsResponse pivot,
        String generatedAt
    ) {}

    public record AdvancedSearchContextResponse(
        String query,
        String normalizedQuery,
        boolean hasFilters,
        List<String> activeFilterKeys,
        Map<String, Integer> filterCounts,
        String sortBy,
        String sortDirection,
        int page,
        int size,
        boolean highlightEnabled,
        int requestedFacetCount,
        String folderId,
        boolean includeChildren,
        String path,
        String generatedAt
    ) {}

    public record AdvancedSearchStatsResponse(
        String query,
        String normalizedQuery,
        boolean hasFilters,
        long totalHits,
        int facetFieldCount,
        List<AdvancedSearchFacetStat> previewStatusStats,
        List<AdvancedSearchFacetStat> mimeTypeStats,
        List<AdvancedSearchFacetStat> createdByStats,
        List<AdvancedSearchFacetStat> fileSizeRangeStats,
        List<AdvancedSearchFacetStat> createdDateRangeStats,
        String generatedAt
    ) {}

    public record AdvancedSearchFacetStat(
        String value,
        long count
    ) {}

    public record AdvancedSearchPivotStatsResponse(
        String query,
        String normalizedQuery,
        boolean hasFilters,
        long totalHits,
        int previewStatusCount,
        int mimeTypeCount,
        List<AdvancedSearchFacetStat> previewStatusStats,
        List<AdvancedSearchFacetStat> mimeTypeStats,
        List<AdvancedSearchPivotMatrixRow> matrix,
        String generatedAt
    ) {}

    public record AdvancedSearchPivotMatrixRow(
        String previewStatus,
        List<AdvancedSearchPivotMatrixCell> mimeTypeCounts
    ) {}

    public record AdvancedSearchPivotMatrixCell(
        String mimeType,
        long count
    ) {}

    public record PreviewQueueBySearchRequest(
        String query,
        SearchFilters filters,
        String sortBy,
        String sortDirection,
        String reason,
        Integer maxDocuments,
        Boolean force,
        Integer workerCount
    ) {}

    public record PreviewQueueBySearchCapabilitiesResponse(
        int defaultMaxDocuments,
        int maxMaxDocuments,
        int scanPageSize,
        int scanLimit,
        int defaultWorkerCount,
        int maxWorkerCount
    ) {}

    public record PreviewQueueBySearchResponse(
        String query,
        String reason,
        int maxDocuments,
        int totalCandidates,
        int scanned,
        int matched,
        int scanSkipped,
        boolean truncated,
        List<PreviewQueueBySearchReasonCountDto> reasonBreakdown,
        List<PreviewQueueBySearchSkipCountDto> skipBreakdown,
        int requested,
        int deduplicated,
        int queued,
        int skipped,
        int failed,
        List<PreviewQueueSearchBatchItemDto> results,
        int workerCount
    ) {}

    public record PreviewQueueSearchBatchItemDto(
        String documentId,
        String outcome,
        String message,
        String previewStatus,
        String previewFailureReason,
        String previewFailureCategory,
        LocalDateTime previewLastUpdated,
        String queueState,
        int attempts,
        Instant nextAttemptAt
    ) {}

    public record PreviewQueueBySearchDryRunResponse(
        String query,
        String reason,
        int maxDocuments,
        int totalCandidates,
        int scanned,
        int matched,
        int scanSkipped,
        boolean truncated,
        List<PreviewQueueBySearchReasonCountDto> reasonBreakdown,
        List<PreviewQueueBySearchSkipCountDto> skipBreakdown,
        int workerCount,
        int sampleCount,
        List<PreviewQueueBySearchDryRunItemDto> samples
    ) {}

    public record PreviewQueueBySearchReasonCountDto(
        String reason,
        int count
    ) {}

    public record PreviewQueueBySearchSkipCountDto(
        String reason,
        int count
    ) {}

    public record PreviewQueueBySearchDryRunItemDto(
        String documentId,
        String name,
        String previewStatus,
        String previewFailureReason,
        String previewFailureCategory,
        String preflightStatus,
        String preflightSkipReason,
        String preflightRoute,
        String preflightPolicyProfile,
        String preflightPipeline
    ) {}

    public record PreviewQueueBySearchDryRunExportAsyncCreateResponse(
        String taskId,
        String status,
        Instant createdAt
    ) {}

    public record PreviewQueueBySearchDryRunExportAsyncStatusResponse(
        String taskId,
        String status,
        String error,
        Instant createdAt,
        Instant finishedAt,
        String filename
    ) {}

    public record PreviewQueueBySearchDryRunExportAsyncListResponse(
        int count,
        List<PreviewQueueBySearchDryRunExportAsyncStatusResponse> items
    ) {}

    public record PreviewQueueBySearchDryRunExportAsyncSummaryResponse(
        int total,
        int queued,
        int running,
        int completed,
        int cancelled,
        int failed,
        int terminal,
        int active
    ) {}

    public record PreviewQueueBySearchDryRunExportAsyncCleanupResponse(
        int deletedCount,
        int remainingCount,
        String status,
        String message
    ) {}

    public record PreviewQueueBySearchDryRunExportAsyncCancelActiveResponse(
        int cancelledCount,
        int remainingActiveCount,
        String statusFilter,
        String message
    ) {}

    // ==================== Index Management Endpoints ====================

    @PostMapping("/index/rebuild")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rebuild search index",
               description = "Rebuild the entire search index from PostgreSQL (admin only)")
    public ResponseEntity<Map<String, Object>> rebuildIndex() {
        int result = fullTextSearchService.rebuildIndex();

        if (result == -1) {
            return ResponseEntity.accepted().body(Map.of(
                "status", "in_progress",
                "message", "Index rebuild already in progress"
            ));
        }

        return ResponseEntity.ok(Map.of(
            "status", "completed",
            "documentsIndexed", result,
            "message", "Index rebuild completed successfully"
        ));
    }

    @GetMapping("/index/rebuild/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get rebuild status",
               description = "Check the progress of index rebuild operation")
    public ResponseEntity<Map<String, Object>> getRebuildStatus() {
        return ResponseEntity.ok(fullTextSearchService.getRebuildStatus());
    }

    @GetMapping("/index/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get index statistics",
               description = "Get search index statistics")
    public ResponseEntity<Map<String, Object>> getIndexStats() {
        return ResponseEntity.ok(fullTextSearchService.getIndexStats());
    }

    // ==================== Single Document Indexing ====================

    @PostMapping("/index/{documentId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('EDITOR')")
    @Operation(summary = "Index single document",
               description = "Add or update a single document in the search index")
    public ResponseEntity<Map<String, Object>> indexDocument(
            @PathVariable String documentId,
            @RequestParam(defaultValue = "false") boolean refresh) {

        try {
            searchIndexService.indexDocument(documentId, refresh);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "documentId", documentId,
                "refreshed", refresh,
                "message", "Document indexed successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "documentId", documentId,
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/index/query")
    @PreAuthorize("hasRole('ADMIN') or hasRole('EDITOR')")
    @Operation(summary = "Index documents by name",
               description = "Re-index documents whose names contain the provided query text")
    public ResponseEntity<Map<String, Object>> indexByQuery(
            @RequestParam String q,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "false") boolean refresh) {
        int indexed = searchIndexService.indexDocumentsByName(q, limit, refresh);
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "query", q,
            "indexed", indexed,
            "refreshed", refresh
        ));
    }

    @GetMapping("/index/{documentId}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('EDITOR')")
    @Operation(summary = "Check document index status",
               description = "Check if a document is present in the search index")
    public ResponseEntity<Map<String, Object>> getIndexStatus(
            @PathVariable String documentId) {

        boolean indexed = searchIndexService.isDocumentIndexed(documentId);
        return ResponseEntity.ok(Map.of(
            "documentId", documentId,
            "indexed", indexed
        ));
    }

    @DeleteMapping("/index/{documentId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Remove document from index",
               description = "Remove a document from the search index")
    public ResponseEntity<Map<String, Object>> removeFromIndex(
            @PathVariable String documentId) {

        try {
            searchIndexService.deleteDocument(documentId);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "documentId", documentId,
                "message", "Document removed from index"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "documentId", documentId,
                "message", e.getMessage()
            ));
        }
    }

    // ==================== Faceted Search Endpoints ====================

    @PostMapping("/faceted")
    @Operation(summary = "Faceted search",
               description = "Search with faceted navigation and aggregations")
    public ResponseEntity<FacetedSearchResponse> facetedSearch(
            @RequestBody FacetedSearchRequest request) {

        if (request.getPageable() == null) {
            request.setPageable(new SimplePageRequest());
        }

        FacetedSearchResponse response = facetedSearchService.search(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/smart")
    @Operation(summary = "Smart search",
               description = "Intelligent search with automatic query enhancement and facets")
    public ResponseEntity<FacetedSearchResponse> smartSearch(
            @Parameter(description = "Search query")
            @RequestParam String q) {

        FacetedSearchResponse response = facetedSearchService.smartSearch(q);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/spellcheck")
    @Operation(summary = "Spellcheck suggestions",
               description = "Return \"Did you mean\" suggestions for a query")
    public ResponseEntity<List<String>> spellcheck(
            @Parameter(description = "Search query")
            @RequestParam String q,
            @Parameter(description = "Maximum suggestions")
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(facetedSearchService.getSpellcheckSuggestions(q, limit));
    }

    @GetMapping("/folder/{folderPath}")
    @Operation(summary = "Search in folder",
               description = "Search within a specific folder and its subfolders")
    public ResponseEntity<FacetedSearchResponse> searchInFolder(
            @Parameter(description = "Search query")
            @RequestParam String q,
            @Parameter(description = "Folder path")
            @PathVariable String folderPath) {

        FacetedSearchResponse response = facetedSearchService.searchInFolder(q, "/" + folderPath);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/facets")
    @Operation(summary = "Get available facets",
               description = "Get facet values and counts for a query without full search results")
    public ResponseEntity<Map<String, List<FacetValue>>> getAvailableFacets(
            @Parameter(description = "Search query")
            @RequestParam(required = false) String q) {

        Map<String, List<FacetValue>> facets = facetedSearchService.getAvailableFacets(q);
        return ResponseEntity.ok(facets);
    }

    @GetMapping("/suggestions")
    @Operation(summary = "Get search suggestions",
               description = "Get autocomplete suggestions based on prefix")
    public ResponseEntity<List<String>> getSuggestions(
            @Parameter(description = "Search prefix")
            @RequestParam String prefix,
            @Parameter(description = "Maximum suggestions")
            @RequestParam(defaultValue = "10") int limit) {

        List<String> suggestions = facetedSearchService.getSuggestions(prefix, limit);
        return ResponseEntity.ok(suggestions);
    }

    @GetMapping("/similar/{documentId}")
    @Operation(summary = "Find similar documents",
               description = "Find documents similar to a given document")
    public ResponseEntity<List<SearchResult>> findSimilar(
            @Parameter(description = "Document ID")
            @PathVariable String documentId,
            @Parameter(description = "Maximum results")
            @RequestParam(defaultValue = "5") int maxResults) {

        List<SearchResult> similar = facetedSearchService.findSimilar(documentId, maxResults);
        return ResponseEntity.ok(similar);
    }

    @GetMapping("/filters/suggested")
    @Operation(summary = "Get suggested filters",
               description = "Get smart filter suggestions based on query results")
    public ResponseEntity<List<SuggestedFilter>> getSuggestedFilters(
            @Parameter(description = "Search query")
            @RequestParam(required = false) String q) {

        List<SuggestedFilter> filters = facetedSearchService.getSuggestedFilters(q);
        return ResponseEntity.ok(filters);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int countList(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static FacetedSearchRequest buildAdvancedStatsRequest(
        String query,
        SearchFilters filters,
        List<String> facetFields
    ) {
        FacetedSearchRequest statsRequest = new FacetedSearchRequest();
        statsRequest.setQuery(query);
        statsRequest.setFilters(filters);
        SimplePageRequest statsPageable = new SimplePageRequest();
        statsPageable.setPage(0);
        statsPageable.setSize(1);
        statsRequest.setPageable(statsPageable);
        statsRequest.setHighlightEnabled(false);
        statsRequest.setIncludeSuggestions(false);
        statsRequest.setFacetFields(facetFields);
        return statsRequest;
    }

    private SearchRequest toSearchRequest(SearchQueryEnvelopeRequest request) {
        SearchRequest target = new SearchRequest();
        target.setQuery(request.query());
        target.setFilters(request.filters());
        target.setSortBy(request.sortBy());
        target.setSortDirection(request.sortDirection());
        target.setPageable(request.pageable() != null ? request.pageable() : new SimplePageRequest());
        target.setHighlightEnabled(request.highlightEnabled() == null || request.highlightEnabled());
        target.setFacets(request.facets());
        return target;
    }

    private static FacetedSearchRequest buildEnvelopeFacetedRequest(SearchRequest request, List<String> include) {
        FacetedSearchRequest target = new FacetedSearchRequest();
        target.setQuery(request.getQuery());
        target.setFilters(request.getFilters());
        target.setPageable(request.getPageable() != null ? request.getPageable() : new SimplePageRequest());
        target.setHighlightEnabled(request.isHighlightEnabled());
        target.setIncludeSuggestions(include.contains("suggestions"));
        target.setFacetFields(include.contains("facets") ? request.getFacets() : null);
        return target;
    }

    private static List<String> resolveEnvelopeInclude(List<String> include) {
        if (include == null || include.isEmpty()) {
            return List.of("results");
        }
        LinkedHashSet<String> normalized = include.stream()
            .filter(SearchController::hasText)
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .filter(value -> Set.of("results", "context", "stats", "pivot", "facets", "suggestions").contains(value))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        return normalized.isEmpty() ? List.of("results") : List.copyOf(normalized);
    }

    private static boolean requiresFacetedEnvelope(List<String> include) {
        return include.contains("facets") || include.contains("suggestions");
    }

    private SearchQueryEnvelopeRequestEcho buildEnvelopeRequestEcho(SearchRequest request, List<String> include) {
        SearchFilters filters = request.getFilters() != null ? request.getFilters() : new SearchFilters();
        SimplePageRequest pageable = request.getPageable() != null ? request.getPageable() : new SimplePageRequest();
        return new SearchQueryEnvelopeRequestEcho(
            request.getQuery(),
            normalizeWhitespaceOrNull(request.getQuery()),
            filters,
            request.getSortBy(),
            request.getSortDirection(),
            Math.max(0, pageable.getPage()),
            clamp(pageable.getSize(), 1, 200),
            request.isHighlightEnabled(),
            request.getFacets() != null ? request.getFacets() : List.of(),
            include
        );
    }

    private AdvancedSearchContextResponse buildAdvancedSearchContextResponse(SearchRequest request) {
        SearchRequest effectiveRequest = request != null ? request : new SearchRequest();
        SearchFilters filters = effectiveRequest.getFilters() != null ? effectiveRequest.getFilters() : new SearchFilters();
        SimplePageRequest pageable = effectiveRequest.getPageable() != null ? effectiveRequest.getPageable() : new SimplePageRequest();

        Map<String, Integer> filterCounts = buildFilterCounts(filters, effectiveRequest.getFacets());

        List<String> activeFilterKeys = filterCounts.entrySet().stream()
            .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
            .map(Map.Entry::getKey)
            .toList();

        return new AdvancedSearchContextResponse(
            effectiveRequest.getQuery(),
            normalizeWhitespaceOrNull(effectiveRequest.getQuery()),
            !activeFilterKeys.isEmpty(),
            activeFilterKeys,
            filterCounts,
            effectiveRequest.getSortBy(),
            effectiveRequest.getSortDirection(),
            Math.max(0, pageable.getPage()),
            clamp(pageable.getSize(), 1, 200),
            effectiveRequest.isHighlightEnabled(),
            countList(effectiveRequest.getFacets()),
            filters.getFolderId(),
            filters.isIncludeChildren(),
            filters.getPath(),
            Instant.now().toString()
        );
    }

    private AdvancedSearchStatsResponse buildAdvancedSearchStatsResponse(SearchRequest request) {
        SearchRequest effectiveRequest = request != null ? request : new SearchRequest();
        SearchFilters filters = effectiveRequest.getFilters() != null ? effectiveRequest.getFilters() : new SearchFilters();

        FacetedSearchRequest statsRequest = buildAdvancedStatsRequest(
            effectiveRequest.getQuery(),
            filters,
            effectiveRequest.getFacets()
        );

        FacetedSearchResponse statsResponse = facetedSearchService.search(statsRequest);
        Map<String, List<FacetValue>> facets = statsResponse != null && statsResponse.getFacets() != null
            ? statsResponse.getFacets()
            : Map.of();

        return new AdvancedSearchStatsResponse(
            effectiveRequest.getQuery(),
            normalizeWhitespaceOrNull(effectiveRequest.getQuery()),
            hasAnyFilters(filters),
            Math.max(0L, statsResponse != null ? statsResponse.getTotalHits() : 0L),
            facets.size(),
            toOrderedFacetStats(facets.get("previewStatus")),
            toOrderedFacetStats(facets.get("mimeType")),
            toOrderedFacetStats(facets.get("createdBy")),
            toOrderedFacetStats(facets.get("fileSizeRange")),
            toOrderedFacetStats(facets.get("createdDateRange")),
            Instant.now().toString()
        );
    }

    private AdvancedSearchPivotStatsResponse buildAdvancedSearchPivotStatsResponse(SearchRequest request) {
        SearchRequest effectiveRequest = request != null ? request : new SearchRequest();
        SearchFilters filters = effectiveRequest.getFilters() != null ? effectiveRequest.getFilters() : new SearchFilters();

        FacetedSearchResponse bucketResponse = facetedSearchService.search(buildAdvancedStatsRequest(
            effectiveRequest.getQuery(),
            filters,
            List.of("previewStatus", "mimeType")
        ));
        Map<String, List<FacetValue>> facets = bucketResponse != null && bucketResponse.getFacets() != null
            ? bucketResponse.getFacets()
            : Map.of();

        List<AdvancedSearchFacetStat> previewStatusStats = toBoundedPivotFacetStats(
            facets.get("previewStatus"),
            MAX_ADVANCED_STATS_PIVOT_PREVIEW_STATUS
        );
        List<AdvancedSearchFacetStat> mimeTypeStats = toBoundedPivotFacetStats(
            facets.get("mimeType"),
            MAX_ADVANCED_STATS_PIVOT_MIME_TYPE
        );
        List<AdvancedSearchPivotMatrixRow> matrix = buildAdvancedSearchPivotMatrix(
            effectiveRequest.getQuery(),
            filters,
            previewStatusStats,
            mimeTypeStats
        );

        return new AdvancedSearchPivotStatsResponse(
            effectiveRequest.getQuery(),
            normalizeWhitespaceOrNull(effectiveRequest.getQuery()),
            hasAnyFilters(filters),
            Math.max(0L, bucketResponse != null ? bucketResponse.getTotalHits() : 0L),
            previewStatusStats.size(),
            mimeTypeStats.size(),
            previewStatusStats,
            mimeTypeStats,
            matrix,
            Instant.now().toString()
        );
    }

    private static boolean hasAnyFilters(SearchFilters filters) {
        if (filters == null) {
            return false;
        }
        return countList(filters.getNodeTypes()) > 0
            || countList(filters.getMimeTypes()) > 0
            || filters.getLocked() != null
            || hasText(filters.getLockedBy())
            || filters.getCheckedOut() != null
            || hasText(filters.getCheckoutUser())
            || hasText(filters.getCreatedBy())
            || countList(filters.getCreatedByList()) > 0
            || filters.getDateFrom() != null
            || filters.getDateTo() != null
            || filters.getModifiedFrom() != null
            || filters.getModifiedTo() != null
            || filters.getMinSize() != null
            || filters.getMaxSize() != null
            || countList(filters.getTags()) > 0
            || countList(filters.getCategories()) > 0
            || countList(filters.getCorrespondents()) > 0
            || hasText(filters.getPath())
            || hasText(filters.getFolderId())
            || countList(filters.getPreviewStatuses()) > 0
            || filters.isIncludeDeleted();
    }

    private static List<AdvancedSearchFacetStat> toOrderedFacetStats(List<FacetValue> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Map<String, Long> merged = new LinkedHashMap<>();
        for (FacetValue value : values) {
            if (value == null) {
                continue;
            }
            String normalizedValue = normalizeWhitespaceOrNull(value.getValue());
            String key = normalizedValue != null ? normalizedValue : "UNKNOWN";
            long count = Math.max(0, value.getCount());
            merged.merge(key, count, Long::sum);
        }
        return merged.entrySet().stream()
            .sorted(
                Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                    .reversed()
                    .thenComparing(Map.Entry::getKey)
            )
            .map(entry -> new AdvancedSearchFacetStat(entry.getKey(), entry.getValue()))
            .toList();
    }

    private static List<AdvancedSearchFacetStat> toBoundedPivotFacetStats(List<FacetValue> values, int maxSize) {
        return toOrderedFacetStats(values).stream()
            .filter(stat -> stat.value() != null && !"UNKNOWN".equals(stat.value()))
            .limit(Math.max(1, maxSize))
            .toList();
    }

    private List<AdvancedSearchPivotMatrixRow> buildAdvancedSearchPivotMatrix(
        String query,
        SearchFilters baseFilters,
        List<AdvancedSearchFacetStat> previewStatusStats,
        List<AdvancedSearchFacetStat> mimeTypeStats
    ) {
        if (previewStatusStats == null || previewStatusStats.isEmpty()
            || mimeTypeStats == null || mimeTypeStats.isEmpty()) {
            return List.of();
        }

        List<AdvancedSearchPivotMatrixRow> rows = new ArrayList<>();
        for (AdvancedSearchFacetStat previewStatus : previewStatusStats) {
            List<AdvancedSearchPivotMatrixCell> cells = new ArrayList<>();
            for (AdvancedSearchFacetStat mimeType : mimeTypeStats) {
                SearchFilters mergedFilters = mergePivotFilters(baseFilters, previewStatus.value(), mimeType.value());
                FacetedSearchResponse cellResponse = facetedSearchService.search(buildAdvancedStatsRequest(
                    query,
                    mergedFilters,
                    List.of("previewStatus", "mimeType")
                ));
                cells.add(new AdvancedSearchPivotMatrixCell(
                    mimeType.value(),
                    Math.max(0L, cellResponse != null ? cellResponse.getTotalHits() : 0L)
                ));
            }
            rows.add(new AdvancedSearchPivotMatrixRow(previewStatus.value(), cells));
        }
        return rows;
    }

    private static String normalizeWhitespaceOrNull(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private static Map<String, Integer> buildFilterCounts(SearchFilters filters, List<String> requestedFacets) {
        Map<String, Integer> filterCounts = new LinkedHashMap<>();
        filterCounts.put("nodeTypes", countList(filters.getNodeTypes()));
        filterCounts.put("mimeTypes", countList(filters.getMimeTypes()));
        filterCounts.put("locked", filters.getLocked() != null ? 1 : 0);
        filterCounts.put("lockedBy", hasText(filters.getLockedBy()) ? 1 : 0);
        filterCounts.put("checkedOut", filters.getCheckedOut() != null ? 1 : 0);
        filterCounts.put("checkoutUser", hasText(filters.getCheckoutUser()) ? 1 : 0);
        filterCounts.put("createdByList", countList(filters.getCreatedByList()));
        filterCounts.put("tags", countList(filters.getTags()));
        filterCounts.put("categories", countList(filters.getCategories()));
        filterCounts.put("correspondents", countList(filters.getCorrespondents()));
        filterCounts.put("previewStatuses", countList(filters.getPreviewStatuses()));
        filterCounts.put("createdBy", hasText(filters.getCreatedBy()) ? 1 : 0);
        filterCounts.put("dateFrom", filters.getDateFrom() != null ? 1 : 0);
        filterCounts.put("dateTo", filters.getDateTo() != null ? 1 : 0);
        filterCounts.put("modifiedFrom", filters.getModifiedFrom() != null ? 1 : 0);
        filterCounts.put("modifiedTo", filters.getModifiedTo() != null ? 1 : 0);
        filterCounts.put("minSize", filters.getMinSize() != null ? 1 : 0);
        filterCounts.put("maxSize", filters.getMaxSize() != null ? 1 : 0);
        filterCounts.put("path", hasText(filters.getPath()) ? 1 : 0);
        filterCounts.put("folderId", hasText(filters.getFolderId()) ? 1 : 0);
        filterCounts.put("facets", countList(requestedFacets));
        return filterCounts;
    }

    private static boolean isRetryableFailed(SearchResult item) {
        if (item == null || item.getPreviewStatus() == null || !"FAILED".equalsIgnoreCase(item.getPreviewStatus().trim())) {
            return false;
        }
        String category = PreviewStatusFilterHelper.resolveEffectiveFailureCategory(
            item.getPreviewStatus(),
            item.getMimeType(),
            item.getPreviewFailureReason(),
            item.getPreviewFailureCategory()
        );
        return PreviewFailureClassifier.CATEGORY_TEMPORARY.equalsIgnoreCase(category);
    }

    private static String normalizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        String normalized = reason.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized;
    }

    private static SearchFilters copyFilters(SearchFilters source) {
        SearchFilters target = new SearchFilters();
        target.setNodeTypes(source.getNodeTypes());
        target.setMimeTypes(source.getMimeTypes());
        target.setLocked(source.getLocked());
        target.setLockedBy(source.getLockedBy());
        target.setCheckedOut(source.getCheckedOut());
        target.setCheckoutUser(source.getCheckoutUser());
        target.setCreatedBy(source.getCreatedBy());
        target.setCreatedByList(source.getCreatedByList());
        target.setDateFrom(source.getDateFrom());
        target.setDateTo(source.getDateTo());
        target.setModifiedFrom(source.getModifiedFrom());
        target.setModifiedTo(source.getModifiedTo());
        target.setMinSize(source.getMinSize());
        target.setMaxSize(source.getMaxSize());
        target.setTags(source.getTags());
        target.setCategories(source.getCategories());
        target.setCorrespondents(source.getCorrespondents());
        target.setPath(source.getPath());
        target.setFolderId(source.getFolderId());
        target.setIncludeChildren(source.isIncludeChildren());
        target.setIncludeDeleted(source.isIncludeDeleted());
        target.setPreviewStatuses(source.getPreviewStatuses());
        return target;
    }

    private static SearchFilters mergePivotFilters(SearchFilters source, String previewStatus, String mimeType) {
        SearchFilters target = copyFilters(source);
        target.setPreviewStatuses(List.of(previewStatus));
        target.setMimeTypes(List.of(mimeType));
        return target;
    }

    private List<PreviewQueueBySearchReasonCountDto> buildReasonBreakdown(List<SearchResult> matches) {
        return matches.stream()
            .collect(Collectors.groupingBy(
                item -> {
                    String normalized = normalizeReason(item.getPreviewFailureReason());
                    return normalized != null ? normalized : "UNSPECIFIED";
                },
                Collectors.counting()
            ))
            .entrySet()
            .stream()
            .sorted(
                Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                    .reversed()
                    .thenComparing(Map.Entry::getKey)
            )
            .map(entry -> new PreviewQueueBySearchReasonCountDto(
                entry.getKey(),
                Math.toIntExact(entry.getValue())
            ))
            .toList();
    }

    private List<PreviewQueueBySearchSkipCountDto> buildSkipBreakdown(Map<String, Integer> skipCounters) {
        if (skipCounters == null || skipCounters.isEmpty()) {
            return List.of();
        }
        return skipCounters.entrySet().stream()
            .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
            .sorted(
                Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                    .reversed()
                    .thenComparing(Map.Entry::getKey)
            )
            .map(entry -> new PreviewQueueBySearchSkipCountDto(entry.getKey(), entry.getValue()))
            .toList();
    }

    private String buildDryRunExportCsv(
        SearchScopeMatchPlan plan,
        List<PreviewQueueBySearchReasonCountDto> reasonBreakdown,
        int workerCount
    ) {
        StringBuilder csv = new StringBuilder();
        csv.append("metric,value\n");
        appendCsvRow(csv, "query", plan.query());
        appendCsvRow(csv, "reasonFilter", plan.reason());
        appendCsvRow(csv, "maxDocuments", String.valueOf(plan.maxDocuments()));
        appendCsvRow(csv, "totalCandidates", String.valueOf(plan.totalCandidates()));
        appendCsvRow(csv, "scanned", String.valueOf(plan.scanned()));
        appendCsvRow(csv, "matched", String.valueOf(plan.matched()));
        appendCsvRow(csv, "scanSkipped", String.valueOf(sumSkipCounts(plan.skipBreakdown())));
        appendCsvRow(csv, "truncated", String.valueOf(plan.truncated()));
        appendCsvRow(csv, "workerCount", String.valueOf(workerCount));

        csv.append('\n');
        csv.append("reason,count\n");
        for (PreviewQueueBySearchReasonCountDto item : reasonBreakdown) {
            appendCsvRow(csv, item.reason(), String.valueOf(item.count()));
        }

        csv.append('\n');
        csv.append("skipReason,count\n");
        for (PreviewQueueBySearchSkipCountDto item : plan.skipBreakdown()) {
            appendCsvRow(csv, item.reason(), String.valueOf(item.count()));
        }

        csv.append('\n');
        csv.append("documentId,name,previewStatus,previewFailureReason,previewFailureCategory,preflightStatus,preflightSkipReason,preflightRoute,preflightPolicyProfile,preflightPipeline\n");
        for (SearchResult item : plan.matches()) {
            PreviewPreflightResolver.PreflightDecision decision = plan.preflightByDocumentId().get(item.getId());
            appendCsvRow(
                csv,
                item.getId(),
                item.getName(),
                item.getPreviewStatus(),
                item.getPreviewFailureReason(),
                item.getPreviewFailureCategory(),
                decision != null ? decision.preflightStatus() : null,
                decision != null ? decision.skipReason() : null,
                decision != null ? decision.route() : null,
                decision != null ? decision.policyProfileKey() : null,
                decision != null ? decision.pipelineChainSummary() : null
            );
        }
        return csv.toString();
    }

    private static void appendCsvRow(StringBuilder csv, String... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escapeCsv(values[i]));
        }
        csv.append('\n');
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return '"' + escaped + '"';
    }

    private static ResponseEntity<byte[]> buildCsvAttachmentResponse(byte[] payload, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        return ResponseEntity.ok()
            .headers(headers)
            .body(payload);
    }

    private PreviewQueueBySearchRequest copyPreviewQueueBySearchRequest(PreviewQueueBySearchRequest request) {
        if (request == null) {
            return null;
        }
        SearchFilters copiedFilters = request.filters() != null ? copyFilters(request.filters()) : null;
        return new PreviewQueueBySearchRequest(
            request.query(),
            copiedFilters,
            request.sortBy(),
            request.sortDirection(),
            request.reason(),
            request.maxDocuments(),
            request.force(),
            request.workerCount()
        );
    }

    private int resolvePreviewQueueWorkerCount(PreviewQueueBySearchRequest request) {
        int requestedWorkers = request != null && request.workerCount() != null
            ? request.workerCount()
            : DEFAULT_PREVIEW_QUEUE_BATCH_WORKER_COUNT;
        return clamp(requestedWorkers, 1, MAX_PREVIEW_QUEUE_BATCH_WORKER_COUNT);
    }

    private DryRunExportAsyncTask createDryRunExportAsyncTask() {
        String taskId = UUID.randomUUID().toString();
        DryRunExportAsyncTask task = new DryRunExportAsyncTask(
            taskId,
            Instant.now(),
            DryRunExportAsyncStatus.QUEUED,
            null,
            null,
            null,
            null
        );

        synchronized (dryRunExportAsyncTaskLock) {
            dryRunExportAsyncTasks.put(taskId, task);
            dryRunExportAsyncTaskOrder.addLast(taskId);
            trimDryRunExportAsyncTasksLocked();
        }
        return task;
    }

    private void runDryRunExportAsyncTask(DryRunExportAsyncTask initialTask, PreviewQueueBySearchRequest request) {
        dryRunExportAsyncTasks.computeIfPresent(initialTask.taskId(), (key, current) ->
            current.status() == DryRunExportAsyncStatus.QUEUED
                ? current.withStatus(DryRunExportAsyncStatus.RUNNING)
                : current
        );
        DryRunExportAsyncTask current = dryRunExportAsyncTasks.get(initialTask.taskId());
        if (current == null || current.status() == DryRunExportAsyncStatus.CANCELLED) {
            return;
        }
        try {
            SearchScopeMatchPlan plan = collectMatchedRetryableFailures(request);
            int workerCount = resolvePreviewQueueWorkerCount(request);
            List<PreviewQueueBySearchReasonCountDto> reasonBreakdown = buildReasonBreakdown(plan.matches());
            String csvContent = buildDryRunExportCsv(plan, reasonBreakdown, workerCount);
            String filename = "search-preview-dry-run-" + Instant.now().toEpochMilli() + ".csv";
            byte[] payload = csvContent.getBytes(StandardCharsets.UTF_8);
            dryRunExportAsyncTasks.computeIfPresent(initialTask.taskId(), (key, runningTask) ->
                runningTask.status() == DryRunExportAsyncStatus.CANCELLED
                    ? runningTask
                    : runningTask.complete(filename, payload)
            );
        } catch (Exception e) {
            dryRunExportAsyncTasks.computeIfPresent(initialTask.taskId(), (key, runningTask) ->
                runningTask.status() == DryRunExportAsyncStatus.CANCELLED
                    ? runningTask
                    : runningTask.fail(resolveErrorMessage(e))
            );
        } finally {
            synchronized (dryRunExportAsyncTaskLock) {
                trimDryRunExportAsyncTasksLocked();
            }
        }
    }

    private void trimDryRunExportAsyncTasksLocked() {
        if (dryRunExportAsyncTasks.size() <= MAX_DRY_RUN_EXPORT_ASYNC_TASKS) {
            return;
        }
        Iterator<String> iterator = dryRunExportAsyncTaskOrder.iterator();
        while (dryRunExportAsyncTasks.size() > MAX_DRY_RUN_EXPORT_ASYNC_TASKS && iterator.hasNext()) {
            String candidateTaskId = iterator.next();
            DryRunExportAsyncTask candidate = dryRunExportAsyncTasks.get(candidateTaskId);
            if (candidate == null) {
                iterator.remove();
                continue;
            }
            if (!candidate.isTerminal()) {
                continue;
            }
            if (dryRunExportAsyncTasks.remove(candidateTaskId, candidate)) {
                iterator.remove();
            }
        }
    }

    private List<PreviewQueueBySearchDryRunExportAsyncStatusResponse> listDryRunExportAsyncTasks(
        int limit,
        DryRunExportAsyncStatus statusFilter
    ) {
        List<PreviewQueueBySearchDryRunExportAsyncStatusResponse> items = new ArrayList<>();
        synchronized (dryRunExportAsyncTaskLock) {
            Iterator<String> iterator = dryRunExportAsyncTaskOrder.descendingIterator();
            while (iterator.hasNext() && items.size() < limit) {
                String taskId = iterator.next();
                DryRunExportAsyncTask task = dryRunExportAsyncTasks.get(taskId);
                if (task == null) {
                    continue;
                }
                if (statusFilter != null && task.status() != statusFilter) {
                    continue;
                }
                items.add(toAsyncStatusResponse(task));
            }
        }
        return items;
    }

    private AsyncTaskSummarySnapshot buildDryRunExportAsyncSummarySnapshot(
        DryRunExportAsyncStatus statusFilter
    ) {
        int queuedCount = 0;
        int runningCount = 0;
        int completedCount = 0;
        int cancelledCount = 0;
        int failedCount = 0;

        synchronized (dryRunExportAsyncTaskLock) {
            for (DryRunExportAsyncTask task : dryRunExportAsyncTasks.values()) {
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
                }
            }
        }

        int totalCount = queuedCount + runningCount + completedCount + cancelledCount + failedCount;
        int terminalCount = completedCount + cancelledCount + failedCount;
        int activeCount = queuedCount + runningCount;
        return new AsyncTaskSummarySnapshot(
            totalCount,
            activeCount,
            terminalCount,
            queuedCount,
            runningCount,
            completedCount,
            cancelledCount,
            failedCount,
            0,
            0
        );
    }

    private int cancelActiveDryRunExportAsyncTasksLocked(DryRunExportAsyncStatus statusFilter) {
        int cancelledCount = 0;
        for (Map.Entry<String, DryRunExportAsyncTask> entry : dryRunExportAsyncTasks.entrySet()) {
            DryRunExportAsyncTask task = entry.getValue();
            if (task == null || task.isTerminal()) {
                continue;
            }
            if (!isDryRunExportAsyncActiveStatus(task.status())) {
                continue;
            }
            if (statusFilter != null && task.status() != statusFilter) {
                continue;
            }
            entry.setValue(task.cancel("Cancelled by admin bulk action"));
            cancelledCount += 1;
        }
        return cancelledCount;
    }

    private int countActiveDryRunExportAsyncTasksLocked() {
        int activeCount = 0;
        for (DryRunExportAsyncTask task : dryRunExportAsyncTasks.values()) {
            if (task == null || task.isTerminal()) {
                continue;
            }
            if (isDryRunExportAsyncActiveStatus(task.status())) {
                activeCount += 1;
            }
        }
        return activeCount;
    }

    private int cleanupDryRunExportAsyncTasksLocked(DryRunExportAsyncStatus statusFilter) {
        Set<String> taskIdsToDelete = new LinkedHashSet<>();
        for (Map.Entry<String, DryRunExportAsyncTask> entry : dryRunExportAsyncTasks.entrySet()) {
            DryRunExportAsyncTask task = entry.getValue();
            if (task == null) {
                taskIdsToDelete.add(entry.getKey());
                continue;
            }
            if (statusFilter == null) {
                if (task.isTerminal()) {
                    taskIdsToDelete.add(entry.getKey());
                }
                continue;
            }
            if (task.status() == statusFilter) {
                taskIdsToDelete.add(entry.getKey());
            }
        }

        if (taskIdsToDelete.isEmpty()) {
            dryRunExportAsyncTaskOrder.removeIf(taskId -> !dryRunExportAsyncTasks.containsKey(taskId));
            return 0;
        }

        taskIdsToDelete.forEach(dryRunExportAsyncTasks::remove);
        dryRunExportAsyncTaskOrder.removeIf(taskId ->
            taskIdsToDelete.contains(taskId) || !dryRunExportAsyncTasks.containsKey(taskId)
        );
        return taskIdsToDelete.size();
    }

    private DryRunExportAsyncStatus parseDryRunExportAsyncStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return DryRunExportAsyncStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown async export status: " + status);
        }
    }

    private static boolean isDryRunExportAsyncActiveStatus(DryRunExportAsyncStatus status) {
        return status == DryRunExportAsyncStatus.QUEUED
            || status == DryRunExportAsyncStatus.RUNNING;
    }

    private PreviewQueueBySearchDryRunExportAsyncStatusResponse toAsyncStatusResponse(DryRunExportAsyncTask task) {
        return new PreviewQueueBySearchDryRunExportAsyncStatusResponse(
            task.taskId(),
            task.status().name(),
            task.error(),
            task.createdAt(),
            task.finishedAt(),
            task.status() == DryRunExportAsyncStatus.COMPLETED ? task.filename() : null
        );
    }

    private static String resolveErrorMessage(Exception e) {
        if (e == null) {
            return "Unknown export error";
        }
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private static int sumSkipCounts(List<PreviewQueueBySearchSkipCountDto> skipBreakdown) {
        if (skipBreakdown == null || skipBreakdown.isEmpty()) {
            return 0;
        }
        return skipBreakdown.stream()
            .mapToInt(item -> Math.max(0, item != null ? item.count() : 0))
            .sum();
    }

    private BatchExecutor.ItemResult<PreviewQueueSearchBatchItemDto> queueSearchResultPreview(
        SearchResult item,
        boolean force,
        PreviewPreflightResolver.PreflightDecision preflightDecision
    ) {
        if (item == null || item.getId() == null || item.getId().isBlank()) {
            return BatchExecutor.ItemResult.failed(new PreviewQueueSearchBatchItemDto(
                null,
                "FAILED",
                "Missing document id in search result",
                item != null ? item.getPreviewStatus() : null,
                item != null ? item.getPreviewFailureReason() : null,
                item != null ? item.getPreviewFailureCategory() : null,
                null,
                "FAILED",
                0,
                null
            ));
        }
        if (preflightDecision != null && !preflightDecision.accepted()) {
            PreviewQueueSearchBatchItemDto declined = new PreviewQueueSearchBatchItemDto(
                item.getId(),
                "SKIPPED",
                "Preflight declined (" + preflightDecision.skipReason() + "): " + preflightDecision.message(),
                item.getPreviewStatus(),
                item.getPreviewFailureReason(),
                item.getPreviewFailureCategory(),
                null,
                "DECLINED",
                0,
                null
            );
            return BatchExecutor.ItemResult.skipped(declined);
        }
        UUID documentId = UUID.fromString(item.getId());
        PreviewQueueService.PreviewQueueStatus status = previewQueueService.enqueue(documentId, force);
        boolean wasQueued = status.queued();
        PreviewQueueSearchBatchItemDto dto = new PreviewQueueSearchBatchItemDto(
            item.getId(),
            wasQueued ? "QUEUED" : "SKIPPED",
            status.message(),
            status.previewStatus() != null ? status.previewStatus().name() : item.getPreviewStatus(),
            wasQueued ? status.previewFailureReason() : Objects.requireNonNullElse(status.previewFailureReason(), item.getPreviewFailureReason()),
            wasQueued ? status.previewFailureCategory() : Objects.requireNonNullElse(status.previewFailureCategory(), item.getPreviewFailureCategory()),
            status.previewLastUpdated(),
            wasQueued ? "QUEUED" : "DECLINED",
            status.attempts(),
            status.nextAttemptAt()
        );
        return wasQueued
            ? BatchExecutor.ItemResult.succeeded(dto)
            : BatchExecutor.ItemResult.skipped(dto);
    }

    private SearchScopeMatchPlan collectMatchedRetryableFailures(PreviewQueueBySearchRequest request) {
        SearchFilters requestFilters = request != null && request.filters() != null
            ? request.filters()
            : new SearchFilters();
        SearchFilters filters = copyFilters(requestFilters);
        filters.setPreviewStatuses(List.of("FAILED"));

        int maxDocuments = clamp(
            request != null && request.maxDocuments() != null
                ? request.maxDocuments()
                : DEFAULT_PREVIEW_QUEUE_MATCH_LIMIT,
            1,
            MAX_PREVIEW_QUEUE_MATCH_LIMIT
        );
        String normalizedReason = normalizeReason(request != null ? request.reason() : null);
        String query = request != null ? request.query() : null;

        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setQuery(query);
        searchRequest.setFilters(filters);
        searchRequest.setSortBy(request != null ? request.sortBy() : null);
        searchRequest.setSortDirection(request != null ? request.sortDirection() : null);

        int pageNumber = 0;
        int scanned = 0;
        int matched = 0;
        int totalCandidates = 0;
        boolean truncated = false;
        LinkedHashSet<String> deduplicatedIds = new LinkedHashSet<>();
        List<SearchResult> matches = new ArrayList<>();
        Map<String, Integer> skipCounters = new LinkedHashMap<>();
        Map<String, PreviewPreflightResolver.PreflightDecision> preflightByDocumentId = new LinkedHashMap<>();

        while (matched < maxDocuments && scanned < MAX_PREVIEW_QUEUE_SCAN_LIMIT) {
            SimplePageRequest pageable = new SimplePageRequest();
            pageable.setPage(pageNumber);
            pageable.setSize(DEFAULT_PREVIEW_QUEUE_SCAN_PAGE_SIZE);
            searchRequest.setPageable(pageable);

            Page<SearchResult> page = fullTextSearchService.advancedSearch(searchRequest);
            if (page == null || page.isEmpty()) {
                break;
            }
            if (pageNumber == 0) {
                totalCandidates = (int) Math.min(Integer.MAX_VALUE, page.getTotalElements());
            }

            for (SearchResult item : page.getContent()) {
                scanned += 1;
                if (scanned >= MAX_PREVIEW_QUEUE_SCAN_LIMIT) {
                    truncated = true;
                }
                if (!isRetryableFailed(item)) {
                    skipCounters.merge("NON_RETRYABLE", 1, Integer::sum);
                    if (truncated) {
                        break;
                    }
                    continue;
                }
                if (normalizedReason != null && !normalizedReason.equals(normalizeReason(item.getPreviewFailureReason()))) {
                    skipCounters.merge("REASON_MISMATCH", 1, Integer::sum);
                    if (truncated) {
                        break;
                    }
                    continue;
                }
                if (item.getId() == null || item.getId().isBlank()) {
                    skipCounters.merge("MISSING_DOCUMENT_ID", 1, Integer::sum);
                    if (truncated) {
                        break;
                    }
                    continue;
                }
                if (!deduplicatedIds.add(item.getId())) {
                    skipCounters.merge("DUPLICATE_DOCUMENT_ID", 1, Integer::sum);
                    if (truncated) {
                        break;
                    }
                    continue;
                }
                UUID parsedDocumentId;
                try {
                    parsedDocumentId = UUID.fromString(item.getId());
                } catch (IllegalArgumentException ex) {
                    skipCounters.merge("INVALID_DOCUMENT_ID", 1, Integer::sum);
                    if (truncated) {
                        break;
                    }
                    continue;
                }
                PreviewPreflightResolver.PreflightDecision preflightDecision = previewPreflightResolver.evaluateCandidate(
                    parsedDocumentId,
                    item.getName(),
                    item.getMimeType(),
                    item.getFileSize()
                );
                if (!preflightDecision.accepted()) {
                    String preflightSkipReason = preflightDecision.skipReason() != null
                        ? preflightDecision.skipReason()
                        : "DECLINED";
                    skipCounters.merge("PREFLIGHT_" + preflightSkipReason, 1, Integer::sum);
                    if (truncated) {
                        break;
                    }
                    continue;
                }
                matches.add(item);
                preflightByDocumentId.put(item.getId(), preflightDecision);
                matched += 1;
                if (matched >= maxDocuments) {
                    truncated = true;
                    break;
                }
                if (truncated) {
                    break;
                }
            }

            if (!page.hasNext() || truncated || scanned >= MAX_PREVIEW_QUEUE_SCAN_LIMIT) {
                break;
            }
            pageNumber += 1;
        }

        return new SearchScopeMatchPlan(
            query,
            normalizedReason,
            maxDocuments,
            totalCandidates,
            scanned,
            matched,
            truncated,
            matches,
            preflightByDocumentId,
            buildSkipBreakdown(skipCounters)
        );
    }

    private record SearchScopeMatchPlan(
        String query,
        String reason,
        int maxDocuments,
        int totalCandidates,
        int scanned,
        int matched,
        boolean truncated,
        List<SearchResult> matches,
        Map<String, PreviewPreflightResolver.PreflightDecision> preflightByDocumentId,
        List<PreviewQueueBySearchSkipCountDto> skipBreakdown
    ) {
    }

    private enum DryRunExportAsyncStatus {
        QUEUED,
        RUNNING,
        COMPLETED,
        CANCELLED,
        FAILED
    }

    private record DryRunExportAsyncTask(
        String taskId,
        Instant createdAt,
        DryRunExportAsyncStatus status,
        String error,
        Instant finishedAt,
        String filename,
        byte[] csvContent
    ) {
        private DryRunExportAsyncTask withStatus(DryRunExportAsyncStatus nextStatus) {
            return new DryRunExportAsyncTask(
                taskId,
                createdAt,
                nextStatus,
                null,
                null,
                null,
                null
            );
        }

        private DryRunExportAsyncTask complete(String completedFilename, byte[] payload) {
            return new DryRunExportAsyncTask(
                taskId,
                createdAt,
                DryRunExportAsyncStatus.COMPLETED,
                null,
                Instant.now(),
                completedFilename,
                payload != null ? payload.clone() : null
            );
        }

        private DryRunExportAsyncTask fail(String errorMessage) {
            return new DryRunExportAsyncTask(
                taskId,
                createdAt,
                DryRunExportAsyncStatus.FAILED,
                errorMessage,
                Instant.now(),
                null,
                null
            );
        }

        private DryRunExportAsyncTask cancel(String reason) {
            return new DryRunExportAsyncTask(
                taskId,
                createdAt,
                DryRunExportAsyncStatus.CANCELLED,
                reason,
                Instant.now(),
                null,
                null
            );
        }

        private boolean isTerminal() {
            return status == DryRunExportAsyncStatus.COMPLETED
                || status == DryRunExportAsyncStatus.CANCELLED
                || status == DryRunExportAsyncStatus.FAILED;
        }
    }
}
