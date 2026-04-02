package com.ecm.core.controller;

import com.ecm.core.service.BatchDownloadAsyncTaskRegistry;
import com.ecm.core.service.BatchDownloadAsyncTaskRegistry.BatchDownloadAsyncStatus;
import com.ecm.core.service.BatchDownloadAsyncTaskRegistry.BatchDownloadAsyncTask;
import com.ecm.core.service.BatchDownloadAsyncTaskRegistry.BatchDownloadSnapshot;
import com.ecm.core.service.BatchDownloadService;
import com.ecm.core.service.BatchDownloadService.BatchDownloadArchiveSummary;
import com.ecm.core.service.BatchDownloadService.BatchDownloadManifest;
import com.ecm.core.service.BatchDownloadService.BatchDownloadPreflightItem;
import com.ecm.core.service.BatchDownloadService.BatchDownloadPreflightSummary;
import com.ecm.core.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipOutputStream;

@Slf4j
@RestController
@RequestMapping("/api/v1/nodes/download")
@RequiredArgsConstructor
@Tag(name = "Batch Download", description = "Download multiple files as ZIP")
public class BatchDownloadController {

    private static final int MAX_ASYNC_LIST_LIMIT = 50;
    private static final DateTimeFormatter FILENAME_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    private final BatchDownloadService batchDownloadService;
    private final BatchDownloadAsyncTaskRegistry batchDownloadAsyncTaskRegistry;
    private final SecurityService securityService;

    @GetMapping("/batch")
    @Operation(summary = "Batch Download", description = "Download multiple documents/folders as a single ZIP file")
    public ResponseEntity<StreamingResponseBody> batchDownload(
            @Parameter(description = "List of Node IDs") @RequestParam("ids") List<UUID> ids,
            @Parameter(description = "Name for the ZIP file") @RequestParam(required = false, defaultValue = "archive") String name) {

        StreamingResponseBody stream = outputStream -> {
            try (ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
                batchDownloadService.streamNodesAsZip(ids, zipOut);
            } catch (Exception e) {
                log.error("Error streaming zip", e);
            }
        };

        String filename = name + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".zip";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(filename, StandardCharsets.UTF_8)
            .build());
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
            .headers(headers)
            .body(stream);
    }

    @PostMapping("/batch-async")
    @Operation(summary = "Start async batch download", description = "Create an async ZIP build task for selected nodes")
    public ResponseEntity<BatchDownloadAsyncStatusResponse> startBatchDownloadAsync(
        @RequestBody BatchDownloadAsyncRequest request
    ) {
        if (request == null || request.nodeIds() == null || request.nodeIds().isEmpty()) {
            throw new IllegalArgumentException("At least one nodeId is required");
        }
        BatchDownloadPreflightSummary preflight = batchDownloadService.inspectNodesPreflight(request.nodeIds());
        List<UUID> nodeIds = preflight.includedNodeIds();
        if (nodeIds.isEmpty() || !preflight.executable()) {
            throw new IllegalArgumentException(preflight.message());
        }

        String safeName = normalizeName(request.name());
        String createdBy = securityService.getCurrentUser();
        LocalDateTime createdAt = LocalDateTime.now();
        BatchDownloadManifest manifest = new BatchDownloadManifest(preflight.includedFileCount(), preflight.includedBytes());
        BatchDownloadAsyncTask task = new BatchDownloadAsyncTask(
            UUID.randomUUID().toString(),
            nodeIds,
            safeName,
            createdBy,
            buildFilename(safeName, createdAt),
            BatchDownloadAsyncStatus.QUEUED,
            manifest.totalFiles(),
            0,
            manifest.totalBytes(),
            0L,
            createdAt,
            null,
            null,
            null,
            null
        );

        batchDownloadAsyncTaskRegistry.register(task);
        CompletableFuture.runAsync(() -> runBatchDownloadAsyncTask(task.taskId(), nodeIds));

        String location = "/api/v1/nodes/download/batch-async/" + task.taskId();
        return ResponseEntity.accepted()
            .header(HttpHeaders.LOCATION, location)
            .body(toStatusResponse(task));
    }

    @PostMapping("/batch-async/preflight")
    @Operation(
        summary = "Preflight async batch download",
        description = "Inspect selected nodes before queueing an async ZIP build task and return structured warnings."
    )
    public ResponseEntity<BatchDownloadPreflightResponse> preflightBatchDownloadAsync(
        @RequestBody BatchDownloadAsyncRequest request
    ) {
        if (request == null || request.nodeIds() == null || request.nodeIds().isEmpty()) {
            throw new IllegalArgumentException("At least one nodeId is required");
        }
        return ResponseEntity.ok(toPreflightResponse(batchDownloadService.inspectNodesPreflight(request.nodeIds())));
    }

    @GetMapping("/batch-async")
    @Operation(summary = "List async batch download tasks", description = "List recent async ZIP build tasks")
    public ResponseEntity<BatchDownloadAsyncListResponse> listBatchDownloadAsyncTasks(
        @RequestParam(required = false) Integer maxItems,
        @RequestParam(defaultValue = "0") int skipCount,
        @RequestParam(defaultValue = "10") int limit,
        @RequestParam(required = false) String status,
        @RequestParam(required = false, name = "q") String query,
        @RequestParam(required = false) String owner
    ) {
        int requestedLimit = maxItems != null ? maxItems : limit;
        int boundedLimit = Math.max(1, Math.min(requestedLimit, MAX_ASYNC_LIST_LIMIT));
        int boundedSkipCount = Math.max(0, skipCount);
        BatchDownloadAsyncStatus statusFilter = parseStatus(status);
        BatchDownloadSnapshot snapshot = batchDownloadAsyncTaskRegistry.snapshot(
            boundedLimit,
            boundedSkipCount,
            statusFilter,
            query,
            owner
        );
        List<BatchDownloadAsyncStatusResponse> items = snapshot.items().stream()
            .map(this::toStatusResponse)
            .toList();

        return ResponseEntity.ok(new BatchDownloadAsyncListResponse(
            items,
            snapshot.totalCount(),
            snapshot.activeCount(),
            new BatchDownloadAsyncPagingResponse(
                snapshot.maxItems(),
                snapshot.skipCount(),
                snapshot.filteredCount(),
                snapshot.skipCount() + items.size() < snapshot.filteredCount()
            )
        ));
    }

    @GetMapping("/batch-async/summary")
    @Operation(summary = "Async batch download summary", description = "Get async ZIP build task counts by lifecycle state")
    public ResponseEntity<BatchDownloadAsyncSummaryResponse> getBatchDownloadAsyncSummary() {
        return ResponseEntity.ok(BatchDownloadAsyncSummaryResponse.from(batchDownloadAsyncTaskRegistry.summary()));
    }

    @PostMapping("/batch-async/cleanup")
    @Operation(summary = "Cleanup terminal batch download tasks", description = "Remove terminal async ZIP build tasks and their artifacts")
    public ResponseEntity<BatchDownloadAsyncCleanupResponse> cleanupBatchDownloadAsyncTasks(
        @RequestParam(required = false) String status
    ) {
        BatchDownloadAsyncStatus statusFilter = parseStatus(status);
        if (statusFilter != null && statusFilter.isActive()) {
            throw new IllegalArgumentException("Cleanup status filter must be terminal");
        }

        int deletedCount = batchDownloadAsyncTaskRegistry.cleanupTerminalTasks(statusFilter);
        int remainingCount = batchDownloadAsyncTaskRegistry.size();
        String statusLabel = statusFilter != null ? statusFilter.name() : null;
        String message = deletedCount > 0
            ? "Cleaned " + deletedCount + " batch download tasks"
            : "No batch download tasks matched cleanup filter";
        return ResponseEntity.ok(new BatchDownloadAsyncCleanupResponse(deletedCount, remainingCount, statusLabel, message));
    }

    @PostMapping("/batch-async/cancel-active")
    @Operation(summary = "Cancel active batch download tasks", description = "Cancel queued or running async ZIP build tasks")
    public ResponseEntity<BatchDownloadAsyncCancelActiveResponse> cancelActiveBatchDownloadAsyncTasks(
        @RequestParam(required = false) String status
    ) {
        BatchDownloadAsyncStatus statusFilter = parseStatus(status);
        if (statusFilter != null && !statusFilter.isActive()) {
            throw new IllegalArgumentException("Cancel-active status filter must be active");
        }

        int cancelledCount = batchDownloadAsyncTaskRegistry.cancelActiveTasks(statusFilter);
        long remainingActiveCount = batchDownloadAsyncTaskRegistry.activeCount();
        String statusLabel = statusFilter != null ? statusFilter.name() : null;
        String message = cancelledCount > 0
            ? "Cancelled " + cancelledCount + " batch download tasks"
            : "No active batch download tasks matched cancel filter";
        return ResponseEntity.ok(new BatchDownloadAsyncCancelActiveResponse(
            cancelledCount,
            remainingActiveCount,
            statusLabel,
            message
        ));
    }

    @GetMapping("/batch-async/{taskId}")
    @Operation(summary = "Get async batch download task", description = "Get current state of an async ZIP build task")
    public ResponseEntity<BatchDownloadAsyncStatusResponse> getBatchDownloadAsyncTask(
        @PathVariable String taskId
    ) {
        BatchDownloadAsyncTask task = batchDownloadAsyncTaskRegistry.get(taskId);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toStatusResponse(task));
    }

    @PostMapping("/batch-async/{taskId}/cancel")
    @Operation(summary = "Cancel async batch download task", description = "Cancel a queued or running async ZIP build task")
    public ResponseEntity<BatchDownloadAsyncStatusResponse> cancelBatchDownloadAsyncTask(
        @PathVariable String taskId
    ) {
        BatchDownloadAsyncTask existing = batchDownloadAsyncTaskRegistry.get(taskId);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }

        LocalDateTime now = LocalDateTime.now();
        BatchDownloadAsyncTask updated = batchDownloadAsyncTaskRegistry.update(taskId, current -> switch (current.status()) {
            case QUEUED -> current.cancelled("Cancelled by user", now);
            case RUNNING -> current.cancelRequested("Cancellation requested");
            default -> current;
        });

        if (updated != null && updated.status() == BatchDownloadAsyncStatus.CANCELLED) {
            batchDownloadAsyncTaskRegistry.deleteArchiveIfPresent(updated.archivePath());
        }

        return ResponseEntity.ok(toStatusResponse(updated != null ? updated : existing));
    }

    @PostMapping("/batch-async/{taskId}/cleanup")
    @Operation(summary = "Cleanup async batch download task", description = "Remove a terminal async ZIP build task and its artifact")
    public ResponseEntity<BatchDownloadAsyncTaskCleanupResponse> cleanupBatchDownloadAsyncTask(
        @PathVariable String taskId
    ) {
        BatchDownloadAsyncTask existing = batchDownloadAsyncTaskRegistry.get(taskId);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        if (!existing.status().isTerminal()) {
            throw new IllegalArgumentException("Cleanup is only available for terminal batch download tasks");
        }

        int deletedCount = batchDownloadAsyncTaskRegistry.cleanupTask(taskId);
        int remainingCount = batchDownloadAsyncTaskRegistry.size();
        String message = deletedCount > 0
            ? "Cleaned batch download task " + taskId
            : "Batch download task already cleaned";
        return ResponseEntity.ok(new BatchDownloadAsyncTaskCleanupResponse(taskId, deletedCount, remainingCount, message));
    }

    @GetMapping(value = "/batch-async/{taskId}/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Download async batch ZIP", description = "Download the completed ZIP artifact for an async batch download task")
    public ResponseEntity<InputStreamResource> downloadBatchDownloadAsyncTask(
        @PathVariable String taskId
    ) {
        BatchDownloadAsyncTask task = batchDownloadAsyncTaskRegistry.get(taskId);
        if (task == null || task.status() != BatchDownloadAsyncStatus.COMPLETED || task.archivePath() == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            if (!Files.exists(task.archivePath())) {
                return ResponseEntity.notFound().build();
            }

            InputStream inputStream = Files.newInputStream(task.archivePath());
            InputStreamResource resource = new InputStreamResource(inputStream);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(ContentDisposition.attachment()
                .filename(task.filename(), StandardCharsets.UTF_8)
                .build());
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentLength(Files.size(task.archivePath()));
            return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
        } catch (Exception ex) {
            log.error("Failed to read async batch download artifact {}", taskId, ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    private void runBatchDownloadAsyncTask(String taskId, List<UUID> nodeIds) {
        BatchDownloadAsyncTask current = batchDownloadAsyncTaskRegistry.get(taskId);
        if (current == null || current.status() == BatchDownloadAsyncStatus.CANCELLED) {
            return;
        }

        batchDownloadAsyncTaskRegistry.update(taskId, task -> task.status() == BatchDownloadAsyncStatus.CANCELLED
            ? task
            : task.started(LocalDateTime.now()));

        Path archivePath = null;
        try {
            archivePath = Files.createTempFile("athena-batch-download-" + taskId + "-", ".zip");
            try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(archivePath))) {
                Path finalArchivePath = archivePath;
                BatchDownloadArchiveSummary summary = batchDownloadService.writeNodesAsZip(
                    nodeIds,
                    zipOut,
                    new BatchDownloadService.BatchDownloadProgressListener() {
                        @Override
                        public boolean isCancellationRequested() {
                            BatchDownloadAsyncTask task = batchDownloadAsyncTaskRegistry.get(taskId);
                            return task == null || task.status() == BatchDownloadAsyncStatus.CANCEL_REQUESTED || task.status() == BatchDownloadAsyncStatus.CANCELLED;
                        }

                        @Override
                        public void onFileAdded(UUID nodeId, String entryPath, long bytesWritten, int filesAdded, long totalBytesAdded) {
                            batchDownloadAsyncTaskRegistry.update(taskId, task -> task.progress(filesAdded, totalBytesAdded));
                        }
                    }
                );

                BatchDownloadAsyncTask latest = batchDownloadAsyncTaskRegistry.get(taskId);
                if (summary.cancelled() || latest == null || latest.status() == BatchDownloadAsyncStatus.CANCEL_REQUESTED || latest.status() == BatchDownloadAsyncStatus.CANCELLED) {
                    batchDownloadAsyncTaskRegistry.deleteArchiveIfPresent(finalArchivePath);
                    batchDownloadAsyncTaskRegistry.update(taskId, task -> task.cancelled("Cancelled by user", LocalDateTime.now()));
                    return;
                }

                batchDownloadAsyncTaskRegistry.update(taskId, task ->
                    task.completed(summary.filesAdded(), summary.bytesAdded(), finalArchivePath, LocalDateTime.now())
                );
            }
        } catch (Exception ex) {
            batchDownloadAsyncTaskRegistry.deleteArchiveIfPresent(archivePath);
            log.error("Async batch download task {} failed", taskId, ex);
            batchDownloadAsyncTaskRegistry.update(taskId, task -> task.failed(ex.getMessage(), LocalDateTime.now()));
        }
    }

    private String normalizeName(String rawName) {
        String safe = rawName == null ? "" : rawName.trim();
        if (safe.isEmpty()) {
            return "archive";
        }
        return safe.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private List<UUID> normalizeNodeIds(List<UUID> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return List.of();
        }
        return nodeIds.stream().filter(id -> id != null).distinct().toList();
    }

    private String buildFilename(String name, LocalDateTime createdAt) {
        return name + "_" + createdAt.format(FILENAME_TIMESTAMP) + ".zip";
    }

    private BatchDownloadAsyncStatus parseStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        try {
            return BatchDownloadAsyncStatus.valueOf(rawStatus.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported status filter: " + rawStatus);
        }
    }

    private BatchDownloadAsyncStatusResponse toStatusResponse(BatchDownloadAsyncTask task) {
        String basePath = "/api/v1/nodes/download/batch-async/" + task.taskId();
        return new BatchDownloadAsyncStatusResponse(
            task.taskId(),
            task.name(),
            task.createdBy(),
            task.filename(),
            task.status().name(),
            task.nodeIds(),
            task.totalFiles(),
            task.filesAdded(),
            task.totalBytes(),
            task.bytesAdded(),
            task.createdAt(),
            task.startedAt(),
            task.completedAt(),
            task.errorMessage(),
            task.status() == BatchDownloadAsyncStatus.COMPLETED ? basePath + "/download" : null,
            task.status().isTerminal() ? basePath + "/cleanup" : null,
            task.archiveSizeBytes(),
            task.retentionExpiresAt(),
            task.cleanupEligible(),
            task.artifactPresent(),
            task.status().isActive(),
            task.status() == BatchDownloadAsyncStatus.COMPLETED
        );
    }

    private BatchDownloadPreflightResponse toPreflightResponse(BatchDownloadPreflightSummary preflight) {
        return new BatchDownloadPreflightResponse(
            preflight.requestedCount(),
            preflight.distinctCount(),
            preflight.duplicateCount(),
            preflight.includedNodeIds().stream().map(UUID::toString).toList(),
            preflight.includedNodeCount(),
            preflight.includedFileCount(),
            preflight.includedBytes(),
            preflight.missingCount(),
            preflight.deletedCount(),
            preflight.forbiddenCount(),
            preflight.emptyFolderCount(),
            preflight.skippedCount(),
            preflight.executable(),
            preflight.decision().name(),
            preflight.primaryReason().name(),
            preflight.message(),
            preflight.warnings(),
            preflight.items().stream().map(this::toPreflightItemResponse).toList()
        );
    }

    private BatchDownloadPreflightItemResponse toPreflightItemResponse(BatchDownloadPreflightItem item) {
        return new BatchDownloadPreflightItemResponse(
            item.nodeId() != null ? item.nodeId().toString() : null,
            item.nodeName(),
            item.nodeType(),
            item.outcome().name(),
            item.includedFiles(),
            item.includedBytes(),
            item.message()
        );
    }

    public record BatchDownloadAsyncRequest(List<UUID> nodeIds, String name) {}

    public record BatchDownloadPreflightResponse(
        int requestedCount,
        int distinctCount,
        int duplicateCount,
        List<String> includedNodeIds,
        int includedNodeCount,
        int includedFileCount,
        long includedBytes,
        int missingCount,
        int deletedCount,
        int forbiddenCount,
        int emptyFolderCount,
        int skippedCount,
        boolean executable,
        String decision,
        String primaryReason,
        String message,
        List<String> warnings,
        List<BatchDownloadPreflightItemResponse> items
    ) {}

    public record BatchDownloadPreflightItemResponse(
        String nodeId,
        String nodeName,
        String nodeType,
        String outcome,
        int includedFiles,
        long includedBytes,
        String message
    ) {}

    public record BatchDownloadAsyncStatusResponse(
        String taskId,
        String name,
        String createdBy,
        String filename,
        String status,
        List<UUID> nodeIds,
        int totalFiles,
        int filesAdded,
        long totalBytes,
        long bytesAdded,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String errorMessage,
        String downloadUrl,
        String cleanupUrl,
        Long archiveSizeBytes,
        LocalDateTime retentionExpiresAt,
        boolean cleanupEligible,
        boolean artifactPresent,
        boolean cancellable,
        boolean downloadReady
    ) {}

    public record BatchDownloadAsyncListResponse(
        List<BatchDownloadAsyncStatusResponse> items,
        int totalCount,
        long activeCount,
        BatchDownloadAsyncPagingResponse paging
    ) {}

    public record BatchDownloadAsyncPagingResponse(
        int maxItems,
        int skipCount,
        int totalItems,
        boolean hasMoreItems
    ) {}

    public record BatchDownloadAsyncSummaryResponse(
        int totalCount,
        long activeCount,
        long terminalCount,
        long queuedCount,
        long runningCount,
        long cancelRequestedCount,
        long cancelledCount,
        long completedCount,
        long failedCount
    ) {
        static BatchDownloadAsyncSummaryResponse from(BatchDownloadAsyncTaskRegistry.BatchDownloadAsyncSummary summary) {
            return new BatchDownloadAsyncSummaryResponse(
                summary.totalCount(),
                summary.activeCount(),
                summary.terminalCount(),
                summary.queuedCount(),
                summary.runningCount(),
                summary.cancelRequestedCount(),
                summary.cancelledCount(),
                summary.completedCount(),
                summary.failedCount()
            );
        }
    }

    public record BatchDownloadAsyncCleanupResponse(
        int deletedCount,
        int remainingCount,
        String statusFilter,
        String message
    ) {}

    public record BatchDownloadAsyncTaskCleanupResponse(
        String taskId,
        int deletedCount,
        int remainingCount,
        String message
    ) {}

    public record BatchDownloadAsyncCancelActiveResponse(
        int cancelledCount,
        long remainingActiveCount,
        String statusFilter,
        String message
    ) {}
}
