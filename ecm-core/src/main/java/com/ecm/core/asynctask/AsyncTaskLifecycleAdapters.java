package com.ecm.core.asynctask;

import com.ecm.core.controller.BatchDownloadController;
import com.ecm.core.controller.OpsRecoveryController;
import com.ecm.core.controller.PreviewDiagnosticsController;
import com.ecm.core.controller.SearchController;
import com.ecm.core.service.AuditExportAsyncTaskRegistry;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

public final class AsyncTaskLifecycleAdapters {

    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private AsyncTaskLifecycleAdapters() {
    }

    public static AsyncTaskStatusSnapshot fromAuditExport(
        String domainKey,
        String domainLabel,
        AuditExportAsyncTaskRegistry.AuditExportAsyncTask task
    ) {
        Objects.requireNonNull(task, "audit export task must not be null");
        String basePath = "/api/v1/analytics/audit/export-async/" + task.taskId();
        boolean completed = task.status() == AuditExportAsyncTaskRegistry.AuditExportAsyncStatus.COMPLETED;
        boolean terminal = task.isTerminal();
        boolean cancellable = !terminal;
        return new AsyncTaskStatusSnapshot(
            domainKey,
            domainLabel,
            task.taskId(),
            task.status().name(),
            task.error(),
            toInstant(task.createdAt()),
            null,
            null,
            null,
            null,
            toInstant(task.finishedAt()),
            task.filename(),
            null,
            null,
            new AsyncTaskActionSnapshot(
                cancellable ? basePath + "/cancel" : null,
                completed ? basePath + "/download" : null,
                terminal ? "/api/v1/analytics/audit/export-async/cleanup" : null,
                cancellable,
                terminal,
                completed
            )
        );
    }

    public static AsyncTaskStatusSnapshot fromSearchDryRun(
        String domainKey,
        String domainLabel,
        SearchController.PreviewQueueBySearchDryRunExportAsyncStatusResponse task
    ) {
        Objects.requireNonNull(task, "search dry-run task must not be null");
        String status = task.status();
        boolean completed = equalsIgnoreCase(status, "COMPLETED");
        boolean terminal = completed || equalsIgnoreCase(status, "FAILED") || equalsIgnoreCase(status, "CANCELLED");
        boolean cancellable = !terminal;
        String basePath = "/api/v1/search/preview/queue-failed/dry-run/export-async/" + task.taskId();
        return new AsyncTaskStatusSnapshot(
            domainKey,
            domainLabel,
            task.taskId(),
            status,
            task.error(),
            task.createdAt(),
            null,
            null,
            null,
            null,
            task.finishedAt(),
            task.filename(),
            null,
            null,
            new AsyncTaskActionSnapshot(
                cancellable ? basePath + "/cancel" : null,
                completed ? basePath + "/download" : null,
                terminal ? "/api/v1/search/preview/queue-failed/dry-run/export-async/cleanup" : null,
                cancellable,
                terminal,
                completed
            )
        );
    }

    public static AsyncTaskStatusSnapshot fromPreviewRendition(
        String domainKey,
        String domainLabel,
        PreviewDiagnosticsController.PreviewRenditionResourcesExportAsyncStatusResponseDto task
    ) {
        Objects.requireNonNull(task, "preview rendition task must not be null");
        String status = task.status();
        boolean completed = equalsIgnoreCase(status, "COMPLETED");
        boolean active = equalsIgnoreCase(status, "QUEUED") || equalsIgnoreCase(status, "RUNNING");
        boolean terminal = !active;
        String basePath = "/api/v1/preview/diagnostics/renditions/resources/export-async/" + task.taskId();
        return new AsyncTaskStatusSnapshot(
            domainKey,
            domainLabel,
            task.taskId(),
            status,
            task.error(),
            task.createdAt(),
            task.startedAt(),
            task.updatedAt(),
            task.timeoutAt(),
            task.expiresAt(),
            task.finishedAt(),
            task.filename(),
            task.createdBy(),
            task.updatedBy(),
            new AsyncTaskActionSnapshot(
                active ? basePath + "/cancel" : null,
                completed ? basePath + "/download" : null,
                terminal ? "/api/v1/preview/diagnostics/renditions/resources/export-async/cleanup" : null,
                active,
                terminal,
                completed
            )
        );
    }

    public static AsyncTaskStatusSnapshot fromOpsRecovery(
        String domainKey,
        String domainLabel,
        OpsRecoveryController.RecoveryHistoryExportAsyncStatusResponseDto task
    ) {
        Objects.requireNonNull(task, "ops recovery task must not be null");
        String status = task.status();
        boolean completed = equalsIgnoreCase(status, "COMPLETED");
        boolean active = equalsIgnoreCase(status, "QUEUED") || equalsIgnoreCase(status, "RUNNING");
        boolean terminal = !active;
        String basePath = "/api/v1/ops/recovery/history/export-async/" + task.taskId();
        return new AsyncTaskStatusSnapshot(
            domainKey,
            domainLabel,
            task.taskId(),
            status,
            task.error(),
            task.createdAt(),
            task.startedAt(),
            task.updatedAt(),
            task.timeoutAt(),
            task.expiresAt(),
            task.finishedAt(),
            task.filename(),
            task.createdBy(),
            task.updatedBy(),
            new AsyncTaskActionSnapshot(
                active ? basePath + "/cancel" : null,
                completed ? basePath + "/download" : null,
                terminal ? "/api/v1/ops/recovery/history/export-async/cleanup" : null,
                active,
                terminal,
                completed
            )
        );
    }

    public static AsyncTaskStatusSnapshot fromBatchDownload(
        String domainKey,
        String domainLabel,
        BatchDownloadController.BatchDownloadAsyncStatusResponse task
    ) {
        Objects.requireNonNull(task, "batch download task must not be null");
        return new AsyncTaskStatusSnapshot(
            domainKey,
            domainLabel,
            task.taskId(),
            task.status(),
            task.errorMessage(),
            toInstant(task.createdAt()),
            toInstant(task.startedAt()),
            null,
            null,
            toInstant(task.retentionExpiresAt()),
            toInstant(task.completedAt()),
            task.filename(),
            task.createdBy(),
            null,
            new AsyncTaskActionSnapshot(
                task.cancellable() ? "/api/v1/nodes/download/batch-async/" + task.taskId() + "/cancel" : null,
                task.downloadUrl(),
                task.cleanupUrl(),
                task.cancellable(),
                task.cleanupEligible(),
                task.downloadReady()
            )
        );
    }

    private static Instant toInstant(LocalDateTime value) {
        return value != null ? value.atZone(SYSTEM_ZONE).toInstant() : null;
    }

    private static boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }
}
