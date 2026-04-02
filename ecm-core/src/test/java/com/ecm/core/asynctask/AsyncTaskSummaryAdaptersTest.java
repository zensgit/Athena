package com.ecm.core.asynctask;

import com.ecm.core.controller.OpsRecoveryController;
import com.ecm.core.controller.PreviewDiagnosticsController;
import com.ecm.core.controller.SearchController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsyncTaskSummaryAdaptersTest {

    @Test
    @DisplayName("Search dry-run summary response preserves normalized lifecycle counts")
    void toSearchDryRunMapsSnapshot() {
        AsyncTaskSummarySnapshot summary = AsyncTaskSummarySnapshot.ofBreakdown(2, 3, 4, 1, 5, 0, 0);

        SearchController.PreviewQueueBySearchDryRunExportAsyncSummaryResponse response =
            AsyncTaskSummaryAdapters.toSearchDryRun(summary);

        assertEquals(15, response.total());
        assertEquals(2, response.queued());
        assertEquals(3, response.running());
        assertEquals(4, response.completed());
        assertEquals(1, response.cancelled());
        assertEquals(5, response.failed());
        assertEquals(10, response.terminal());
        assertEquals(5, response.active());
    }

    @Test
    @DisplayName("Preview rendition summary response preserves timeout and expiry buckets")
    void toPreviewRenditionsMapsSnapshot() {
        AsyncTaskSummarySnapshot summary = AsyncTaskSummarySnapshot.ofBreakdown(1, 2, 3, 4, 5, 6, 7);

        PreviewDiagnosticsController.PreviewRenditionResourcesExportAsyncSummaryResponseDto response =
            AsyncTaskSummaryAdapters.toPreviewRenditions(summary);

        assertEquals(28L, response.totalCount());
        assertEquals(1L, response.queuedCount());
        assertEquals(2L, response.runningCount());
        assertEquals(3L, response.completedCount());
        assertEquals(4L, response.cancelledCount());
        assertEquals(5L, response.failedCount());
        assertEquals(6L, response.timedOutCount());
        assertEquals(7L, response.expiredCount());
        assertEquals(3L, response.activeCount());
        assertEquals(25L, response.terminalCount());
    }

    @Test
    @DisplayName("Ops recovery summary response preserves shared snapshot counts")
    void toOpsRecoveryMapsSnapshot() {
        AsyncTaskSummarySnapshot summary = AsyncTaskSummarySnapshot.ofBreakdown(1, 1, 2, 3, 4, 5, 6);

        OpsRecoveryController.RecoveryHistoryExportAsyncSummaryResponseDto response =
            AsyncTaskSummaryAdapters.toOpsRecovery(summary);

        assertEquals(22, response.totalCount());
        assertEquals(1, response.queuedCount());
        assertEquals(1, response.runningCount());
        assertEquals(2, response.completedCount());
        assertEquals(3, response.cancelledCount());
        assertEquals(4, response.failedCount());
        assertEquals(5, response.timedOutCount());
        assertEquals(6, response.expiredCount());
        assertEquals(2, response.activeCount());
        assertEquals(20, response.terminalCount());
    }
}
