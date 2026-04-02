package com.ecm.core.asynctask;

import com.ecm.core.controller.BatchDownloadController;
import com.ecm.core.controller.OpsRecoveryController;
import com.ecm.core.controller.PreviewDiagnosticsController;
import com.ecm.core.controller.SearchController;
import com.ecm.core.service.AuditExportAsyncTaskRegistry;
import com.ecm.core.service.BatchDownloadAsyncTaskRegistry;

import java.util.Objects;

/**
 * Centralizes conversion from controller-specific async summary responses into
 * the shared snapshot used by governance aggregation.
 */
public final class AsyncTaskSummaryAdapters {

    private AsyncTaskSummaryAdapters() {
    }

    public static AsyncTaskSummarySnapshot fromAuditExport(
        AuditExportAsyncTaskRegistry.AuditExportAsyncSummary summary
    ) {
        Objects.requireNonNull(summary, "audit export summary must not be null");
        return AsyncTaskSummarySnapshot.ofBreakdown(
            summary.queuedCount(),
            summary.runningCount(),
            summary.completedCount(),
            summary.cancelledCount(),
            summary.failedCount(),
            0L,
            0L
        );
    }

    public static SearchController.PreviewQueueBySearchDryRunExportAsyncSummaryResponse toSearchDryRun(
        AsyncTaskSummarySnapshot summary
    ) {
        Objects.requireNonNull(summary, "search dry-run summary must not be null");
        return new SearchController.PreviewQueueBySearchDryRunExportAsyncSummaryResponse(
            Math.toIntExact(summary.totalCount()),
            Math.toIntExact(summary.queuedCount()),
            Math.toIntExact(summary.runningCount()),
            Math.toIntExact(summary.completedCount()),
            Math.toIntExact(summary.cancelledCount()),
            Math.toIntExact(summary.failedCount()),
            Math.toIntExact(summary.terminalCount()),
            Math.toIntExact(summary.activeCount())
        );
    }

    public static AsyncTaskSummarySnapshot fromOpsRecovery(
        OpsRecoveryController.RecoveryHistoryExportAsyncSummaryResponseDto summary
    ) {
        Objects.requireNonNull(summary, "ops recovery summary must not be null");
        return AsyncTaskSummarySnapshot.ofBreakdown(
            summary.queuedCount(),
            summary.runningCount(),
            summary.completedCount(),
            summary.cancelledCount(),
            summary.failedCount(),
            summary.timedOutCount(),
            summary.expiredCount()
        );
    }

    public static AsyncTaskSummarySnapshot fromSearchDryRun(
        SearchController.PreviewQueueBySearchDryRunExportAsyncSummaryResponse summary
    ) {
        Objects.requireNonNull(summary, "search dry-run summary must not be null");
        return AsyncTaskSummarySnapshot.ofBreakdown(
            summary.queued(),
            summary.running(),
            summary.completed(),
            summary.cancelled(),
            summary.failed(),
            0L,
            0L
        );
    }

    public static AsyncTaskSummarySnapshot fromPreviewRenditions(
        PreviewDiagnosticsController.PreviewRenditionResourcesExportAsyncSummaryResponseDto summary
    ) {
        Objects.requireNonNull(summary, "preview rendition summary must not be null");
        return AsyncTaskSummarySnapshot.ofBreakdown(
            summary.queuedCount(),
            summary.runningCount(),
            summary.completedCount(),
            summary.cancelledCount(),
            summary.failedCount(),
            summary.timedOutCount(),
            summary.expiredCount()
        );
    }

    public static PreviewDiagnosticsController.PreviewRenditionResourcesExportAsyncSummaryResponseDto toPreviewRenditions(
        AsyncTaskSummarySnapshot summary
    ) {
        Objects.requireNonNull(summary, "preview rendition summary must not be null");
        return new PreviewDiagnosticsController.PreviewRenditionResourcesExportAsyncSummaryResponseDto(
            summary.totalCount(),
            summary.queuedCount(),
            summary.runningCount(),
            summary.completedCount(),
            summary.cancelledCount(),
            summary.failedCount(),
            summary.timedOutCount(),
            summary.expiredCount(),
            summary.activeCount(),
            summary.terminalCount()
        );
    }

    public static OpsRecoveryController.RecoveryHistoryExportAsyncSummaryResponseDto toOpsRecovery(
        AsyncTaskSummarySnapshot summary
    ) {
        Objects.requireNonNull(summary, "ops recovery summary must not be null");
        return new OpsRecoveryController.RecoveryHistoryExportAsyncSummaryResponseDto(
            Math.toIntExact(summary.totalCount()),
            Math.toIntExact(summary.queuedCount()),
            Math.toIntExact(summary.runningCount()),
            Math.toIntExact(summary.completedCount()),
            Math.toIntExact(summary.cancelledCount()),
            Math.toIntExact(summary.failedCount()),
            Math.toIntExact(summary.timedOutCount()),
            Math.toIntExact(summary.expiredCount()),
            Math.toIntExact(summary.activeCount()),
            Math.toIntExact(summary.terminalCount())
        );
    }

    public static AsyncTaskSummarySnapshot fromBatchDownload(
        BatchDownloadController.BatchDownloadAsyncSummaryResponse summary
    ) {
        Objects.requireNonNull(summary, "batch download summary must not be null");
        // CANCEL_REQUESTED is active work, so normalize it into the running bucket.
        return AsyncTaskSummarySnapshot.ofBreakdown(
            summary.queuedCount(),
            summary.runningCount() + summary.cancelRequestedCount(),
            summary.completedCount(),
            summary.cancelledCount(),
            summary.failedCount(),
            0L,
            0L
        );
    }

    public static AsyncTaskSummarySnapshot fromBatchDownload(
        BatchDownloadAsyncTaskRegistry.BatchDownloadAsyncSummary summary
    ) {
        Objects.requireNonNull(summary, "batch download summary must not be null");
        return AsyncTaskSummarySnapshot.ofBreakdown(
            summary.queuedCount(),
            summary.runningCount() + summary.cancelRequestedCount(),
            summary.completedCount(),
            summary.cancelledCount(),
            summary.failedCount(),
            0L,
            0L
        );
    }
}
