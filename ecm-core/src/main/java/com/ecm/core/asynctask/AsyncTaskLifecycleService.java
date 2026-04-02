package com.ecm.core.asynctask;

import com.ecm.core.controller.BatchDownloadController;
import com.ecm.core.controller.OpsRecoveryController;
import com.ecm.core.controller.PreviewDiagnosticsController;
import com.ecm.core.controller.SearchController;
import com.ecm.core.service.AuditExportAsyncTaskRegistry;
import com.ecm.core.service.BatchDownloadAsyncTaskRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AsyncTaskLifecycleService {

    private static final int DEFAULT_MAX_ITEMS = 20;
    private static final int MAX_MAX_ITEMS = 100;
    private static final int MAX_FETCH_LIMIT = 200;

    private final AuditExportAsyncTaskRegistry auditExportAsyncTaskRegistry;
    private final BatchDownloadAsyncTaskRegistry batchDownloadAsyncTaskRegistry;
    private final SearchController searchController;
    private final PreviewDiagnosticsController previewDiagnosticsController;
    private final OpsRecoveryController opsRecoveryController;
    private final BatchDownloadController batchDownloadController;
    private final AsyncTaskAcknowledgementService asyncTaskAcknowledgementService;

    public AsyncTaskLifecycleListSnapshot listRecentTasks(Integer maxItems, Integer skipCount, String domain, String status) {
        return listRecentTasks(maxItems, skipCount, domain, status, false);
    }

    public AsyncTaskLifecycleListSnapshot listRecentTasks(
        Integer maxItems,
        Integer skipCount,
        String domain,
        String status,
        boolean includeAcknowledged
    ) {
        int boundedMaxItems = clamp(maxItems != null ? maxItems : DEFAULT_MAX_ITEMS, 1, MAX_MAX_ITEMS);
        int boundedSkipCount = Math.max(skipCount != null ? skipCount : 0, 0);
        int fetchLimit = Math.min(MAX_FETCH_LIMIT, boundedMaxItems + boundedSkipCount);
        String normalizedDomain = normalizeDomain(domain);
        String normalizedStatus = normalizeFilter(status);

        List<AsyncTaskStatusSnapshot> mergedItems = new ArrayList<>();
        long totalCount = 0L;

        if (normalizedDomain == null || "audit".equals(normalizedDomain)) {
            totalCount += auditExportAsyncTaskRegistry.summary(parseAuditStatus(normalizedStatus)).totalCount();
            mergedItems.addAll(auditExportAsyncTaskRegistry.list(fetchLimit, parseAuditStatus(normalizedStatus)).stream()
                .map(task -> AsyncTaskLifecycleAdapters.fromAuditExport("audit", "Audit", task))
                .toList());
        }

        if (normalizedDomain == null || "search".equals(normalizedDomain)) {
            totalCount += searchController.summarizeDryRunQueueFailedPreviewsBySearchCsvAsyncTaskSnapshot(normalizedStatus).totalCount();
            ResponseEntity<SearchController.PreviewQueueBySearchDryRunExportAsyncListResponse> response =
                searchController.listDryRunQueueFailedPreviewsBySearchCsvAsyncTasks(fetchLimit, normalizedStatus);
            SearchController.PreviewQueueBySearchDryRunExportAsyncListResponse body = requireBody(response, "search");
            mergedItems.addAll(body.items().stream()
                .map(task -> AsyncTaskLifecycleAdapters.fromSearchDryRun("search", "Search", task))
                .toList());
        }

        if (normalizedDomain == null || "preview".equals(normalizedDomain)) {
            totalCount += previewDiagnosticsController.summarizeRenditionResourcesCsvAsyncExportTaskSnapshot(normalizedStatus).totalCount();
            ResponseEntity<PreviewDiagnosticsController.PreviewRenditionResourcesExportAsyncListResponseDto> response =
                previewDiagnosticsController.listRenditionResourcesCsvAsyncExportTasks(null, fetchLimit, 0, normalizedStatus);
            PreviewDiagnosticsController.PreviewRenditionResourcesExportAsyncListResponseDto body = requireBody(response, "preview");
            mergedItems.addAll(body.items().stream()
                .map(task -> AsyncTaskLifecycleAdapters.fromPreviewRendition("preview", "Preview", task))
                .toList());
        }

        if (normalizedDomain == null || "ops".equals(normalizedDomain)) {
            totalCount += opsRecoveryController.summarizeHistoryExportAsyncTaskSnapshot(null, normalizedStatus).totalCount();
            ResponseEntity<OpsRecoveryController.RecoveryHistoryExportAsyncListResponseDto> response =
                opsRecoveryController.listHistoryExportAsyncTasks(null, fetchLimit, 0, null, normalizedStatus);
            OpsRecoveryController.RecoveryHistoryExportAsyncListResponseDto body = requireBody(response, "ops");
            mergedItems.addAll(body.items().stream()
                .map(task -> AsyncTaskLifecycleAdapters.fromOpsRecovery("ops", "Ops Recovery", task))
                .toList());
        }

        if (normalizedDomain == null || "batchdownload".equals(normalizedDomain)) {
            totalCount += batchDownloadAsyncTaskRegistry.summary(parseBatchStatus(normalizedStatus)).totalCount();
            ResponseEntity<BatchDownloadController.BatchDownloadAsyncListResponse> response =
                batchDownloadController.listBatchDownloadAsyncTasks(fetchLimit, 0, fetchLimit, normalizedStatus, null, null);
            BatchDownloadController.BatchDownloadAsyncListResponse body = requireBody(response, "batchDownload");
            mergedItems.addAll(body.items().stream()
                .map(task -> AsyncTaskLifecycleAdapters.fromBatchDownload("batchDownload", "Batch Download", task))
                .toList());
        }

        mergedItems.sort(Comparator.comparing(AsyncTaskStatusSnapshot::sortTimestamp).reversed()
            .thenComparing(AsyncTaskStatusSnapshot::domainKey)
            .thenComparing(AsyncTaskStatusSnapshot::taskId));

        List<AsyncTaskStatusSnapshot> visibleItems = asyncTaskAcknowledgementService.applyAcknowledgements(
            mergedItems,
            includeAcknowledged
        );

        List<AsyncTaskStatusSnapshot> pageItems = visibleItems.stream()
            .skip(boundedSkipCount)
            .limit(boundedMaxItems)
            .toList();

        return new AsyncTaskLifecycleListSnapshot(
            Instant.now(),
            normalizedDomain,
            normalizedStatus,
            pageItems.size(),
            totalCount,
            boundedSkipCount,
            boundedMaxItems,
            boundedSkipCount + pageItems.size() < visibleItems.size() || boundedSkipCount + pageItems.size() < totalCount,
            pageItems
        );
    }

    public AsyncTaskStatusSnapshot findRecentTask(String domain, String taskId, String fingerprint) {
        String normalizedDomain;
        try {
            normalizedDomain = normalizeDomain(domain);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
        if (normalizedDomain == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Async lifecycle domain is required");
        }
        if (taskId == null || taskId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Async lifecycle taskId is required");
        }

        String trimmedTaskId = taskId.trim();
        String trimmedFingerprint = fingerprint == null || fingerprint.isBlank() ? null : fingerprint.trim();

        return listRecentTasks(MAX_FETCH_LIMIT, 0, normalizedDomain, null, true).items().stream()
            .filter(task -> Objects.equals(normalizedDomain, normalizeDomain(task.domainKey())))
            .filter(task -> Objects.equals(trimmedTaskId, task.taskId()))
            .filter(task -> trimmedFingerprint == null || Objects.equals(trimmedFingerprint, task.fingerprint()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Async lifecycle task not found for domain " + normalizedDomain + " and taskId " + trimmedTaskId
            ));
    }

    private <T> T requireBody(ResponseEntity<T> response, String domainKey) {
        if (response == null || response.getBody() == null) {
            throw new IllegalStateException(domainKey + " lifecycle body is empty");
        }
        return response.getBody();
    }

    private String normalizeDomain(String domain) {
        String normalized = normalizeFilter(domain);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "audit", "search", "preview", "ops", "batchdownload" -> normalized;
            case "batch", "batch-download", "batch_download" -> "batchdownload";
            default -> throw new IllegalArgumentException("Unknown async lifecycle domain: " + domain);
        };
    }

    private String normalizeFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private AuditExportAsyncTaskRegistry.AuditExportAsyncStatus parseAuditStatus(String status) {
        if (status == null) {
            return null;
        }
        try {
            return AuditExportAsyncTaskRegistry.AuditExportAsyncStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Unknown audit async export status: " + status);
        }
    }

    private BatchDownloadAsyncTaskRegistry.BatchDownloadAsyncStatus parseBatchStatus(String status) {
        if (status == null) {
            return null;
        }
        try {
            return BatchDownloadAsyncTaskRegistry.BatchDownloadAsyncStatus.valueOf(status.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Unknown batch download async status: " + status);
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
