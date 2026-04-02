package com.ecm.core.asynctask;

import com.ecm.core.controller.BatchDownloadController;
import com.ecm.core.controller.OpsRecoveryController;
import com.ecm.core.controller.PreviewDiagnosticsController;
import com.ecm.core.controller.SearchController;
import com.ecm.core.service.AuditExportAsyncTaskRegistry;
import com.ecm.core.service.BatchDownloadAsyncTaskRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncTaskLifecycleServiceTest {

    @Mock
    private SearchController searchController;

    @Mock
    private PreviewDiagnosticsController previewDiagnosticsController;

    @Mock
    private OpsRecoveryController opsRecoveryController;

    @Mock
    private BatchDownloadController batchDownloadController;

    @Mock
    private AsyncTaskAcknowledgementService asyncTaskAcknowledgementService;

    private AuditExportAsyncTaskRegistry auditExportAsyncTaskRegistry;
    private BatchDownloadAsyncTaskRegistry batchDownloadAsyncTaskRegistry;
    private AsyncTaskLifecycleService asyncTaskLifecycleService;
    private TimeZone originalTimeZone;

    @BeforeEach
    void setUp() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        auditExportAsyncTaskRegistry = new AuditExportAsyncTaskRegistry();
        batchDownloadAsyncTaskRegistry = new BatchDownloadAsyncTaskRegistry();
        asyncTaskLifecycleService = new AsyncTaskLifecycleService(
            auditExportAsyncTaskRegistry,
            batchDownloadAsyncTaskRegistry,
            searchController,
            previewDiagnosticsController,
            opsRecoveryController,
            batchDownloadController,
            asyncTaskAcknowledgementService
        );
        lenient().when(asyncTaskAcknowledgementService.applyAcknowledgements(anyList(), eq(false)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(asyncTaskAcknowledgementService.applyAcknowledgements(anyList(), eq(true)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    @DisplayName("Recent lifecycle list merges domain tasks, sorts by recency, and preserves shared actions")
    void listRecentTasksMergesAndSortsAcrossDomains() {
        batchDownloadAsyncTaskRegistry.register(new BatchDownloadAsyncTaskRegistry.BatchDownloadAsyncTask(
            "batch-1",
            List.of(UUID.fromString("00000000-0000-0000-0000-000000000001")),
            "Download 1",
            "admin",
            "batch-1.zip",
            BatchDownloadAsyncTaskRegistry.BatchDownloadAsyncStatus.COMPLETED,
            3,
            3,
            30L,
            30L,
            LocalDateTime.of(2026, 3, 21, 13, 30),
            LocalDateTime.of(2026, 3, 21, 13, 31),
            LocalDateTime.of(2026, 3, 21, 14, 0),
            null,
            null
        ));

        when(searchController.summarizeDryRunQueueFailedPreviewsBySearchCsvAsyncTaskSnapshot(null))
            .thenReturn(AsyncTaskSummarySnapshot.ofBreakdown(0, 0, 1, 0, 0, 0, 0));
        when(searchController.listDryRunQueueFailedPreviewsBySearchCsvAsyncTasks(3, null))
            .thenReturn(ResponseEntity.ok(new SearchController.PreviewQueueBySearchDryRunExportAsyncListResponse(
                1,
                List.of(new SearchController.PreviewQueueBySearchDryRunExportAsyncStatusResponse(
                    "search-1",
                    "COMPLETED",
                    null,
                    Instant.parse("2026-03-21T12:00:00Z"),
                    Instant.parse("2026-03-21T12:05:00Z"),
                    "search.csv"
                ))
            )));

        when(previewDiagnosticsController.summarizeRenditionResourcesCsvAsyncExportTaskSnapshot(null))
            .thenReturn(AsyncTaskSummarySnapshot.ofBreakdown(0, 1, 0, 0, 0, 0, 0));
        when(previewDiagnosticsController.listRenditionResourcesCsvAsyncExportTasks(null, 3, 0, null))
            .thenReturn(ResponseEntity.ok(new PreviewDiagnosticsController.PreviewRenditionResourcesExportAsyncListResponseDto(
                1,
                new PreviewDiagnosticsController.PreviewTaskCenterPagingDto(0, 3, 1, false),
                List.of(new PreviewDiagnosticsController.PreviewRenditionResourcesExportAsyncStatusResponseDto(
                    "preview-1",
                    "RUNNING",
                    null,
                    Instant.parse("2026-03-21T12:30:00Z"),
                    Instant.parse("2026-03-21T12:35:00Z"),
                    Instant.parse("2026-03-21T13:00:00Z"),
                    null,
                    null,
                    null,
                    "preview.csv",
                    "admin",
                    "admin"
                ))
            )));

        when(opsRecoveryController.summarizeHistoryExportAsyncTaskSnapshot(null, null))
            .thenReturn(AsyncTaskSummarySnapshot.ofBreakdown(0, 0, 0, 0, 1, 0, 0));
        when(opsRecoveryController.listHistoryExportAsyncTasks(null, 3, 0, null, null))
            .thenReturn(ResponseEntity.ok(new OpsRecoveryController.RecoveryHistoryExportAsyncListResponseDto(
                1,
                new OpsRecoveryController.RecoveryTaskCenterPagingDto(0, 3, 1, false),
                List.of(new OpsRecoveryController.RecoveryHistoryExportAsyncStatusResponseDto(
                    "ops-1",
                    "HISTORY",
                    "FAILED",
                    "boom",
                    null,
                    Instant.parse("2026-03-21T10:00:00Z"),
                    Instant.parse("2026-03-21T10:05:00Z"),
                    Instant.parse("2026-03-21T11:00:00Z"),
                    null,
                    null,
                    Instant.parse("2026-03-21T11:00:00Z"),
                    "ops.csv",
                    "ops-admin",
                    "ops-admin"
                ))
            )));

        when(batchDownloadController.listBatchDownloadAsyncTasks(3, 0, 3, null, null, null))
            .thenReturn(ResponseEntity.ok(new BatchDownloadController.BatchDownloadAsyncListResponse(
                List.of(new BatchDownloadController.BatchDownloadAsyncStatusResponse(
                    "batch-1",
                    "Download 1",
                    "admin",
                    "batch-1.zip",
                    "COMPLETED",
                    List.of(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                    3,
                    3,
                    30L,
                    30L,
                    LocalDateTime.of(2026, 3, 21, 13, 30),
                    LocalDateTime.of(2026, 3, 21, 13, 31),
                    LocalDateTime.of(2026, 3, 21, 14, 0),
                    null,
                    "/api/v1/nodes/download/batch-async/batch-1/download",
                    "/api/v1/nodes/download/batch-async/cleanup?taskId=batch-1",
                    30L,
                    LocalDateTime.of(2026, 3, 22, 14, 0),
                    true,
                    true,
                    false,
                    true
                )),
                1,
                0,
                new BatchDownloadController.BatchDownloadAsyncPagingResponse(3, 0, 1, false)
            )));

        AsyncTaskLifecycleListSnapshot snapshot = asyncTaskLifecycleService.listRecentTasks(2, 1, null, null);

        assertNotNull(snapshot.generatedAt());
        assertEquals(2, snapshot.count());
        assertEquals(4L, snapshot.totalCount());
        assertEquals(1, snapshot.skipCount());
        assertEquals(2, snapshot.maxItems());
        assertTrue(snapshot.hasMoreItems());

        AsyncTaskStatusSnapshot first = snapshot.items().get(0);
        assertEquals("preview", first.domainKey());
        assertEquals("preview-1", first.taskId());
        assertEquals("/api/v1/preview/diagnostics/renditions/resources/export-async/preview-1/cancel", first.actions().cancelUrl());
        assertFalse(first.actions().cleanupEligible());
        assertFalse(first.actions().downloadReady());

        AsyncTaskStatusSnapshot second = snapshot.items().get(1);
        assertEquals("search", second.domainKey());
        assertEquals("search-1", second.taskId());
        assertEquals("/api/v1/search/preview/queue-failed/dry-run/export-async/search-1/download", second.actions().downloadUrl());
        assertTrue(second.actions().cleanupEligible());
        assertTrue(second.actions().downloadReady());
    }

    @Test
    @DisplayName("Recent lifecycle list supports batch domain aliases and filtered totals")
    void listRecentTasksSupportsBatchAliasAndStatusFilter() {
        batchDownloadAsyncTaskRegistry.register(new BatchDownloadAsyncTaskRegistry.BatchDownloadAsyncTask(
            "batch-completed",
            List.of(UUID.fromString("00000000-0000-0000-0000-000000000002")),
            "Completed batch",
            "admin",
            "completed.zip",
            BatchDownloadAsyncTaskRegistry.BatchDownloadAsyncStatus.COMPLETED,
            2,
            2,
            20L,
            20L,
            LocalDateTime.of(2026, 3, 21, 9, 0),
            LocalDateTime.of(2026, 3, 21, 9, 1),
            LocalDateTime.of(2026, 3, 21, 9, 5),
            null,
            null
        ));
        batchDownloadAsyncTaskRegistry.register(new BatchDownloadAsyncTaskRegistry.BatchDownloadAsyncTask(
            "batch-failed",
            List.of(UUID.fromString("00000000-0000-0000-0000-000000000003")),
            "Failed batch",
            "admin",
            "failed.zip",
            BatchDownloadAsyncTaskRegistry.BatchDownloadAsyncStatus.FAILED,
            2,
            1,
            20L,
            10L,
            LocalDateTime.of(2026, 3, 21, 8, 0),
            LocalDateTime.of(2026, 3, 21, 8, 1),
            LocalDateTime.of(2026, 3, 21, 8, 5),
            "failed",
            null
        ));

        when(batchDownloadController.listBatchDownloadAsyncTasks(10, 0, 10, "completed", null, null))
            .thenReturn(ResponseEntity.ok(new BatchDownloadController.BatchDownloadAsyncListResponse(
                List.of(new BatchDownloadController.BatchDownloadAsyncStatusResponse(
                    "batch-completed",
                    "Completed batch",
                    "admin",
                    "completed.zip",
                    "COMPLETED",
                    List.of(UUID.fromString("00000000-0000-0000-0000-000000000002")),
                    2,
                    2,
                    20L,
                    20L,
                    LocalDateTime.of(2026, 3, 21, 9, 0),
                    LocalDateTime.of(2026, 3, 21, 9, 1),
                    LocalDateTime.of(2026, 3, 21, 9, 5),
                    null,
                    "/api/v1/nodes/download/batch-async/batch-completed/download",
                    "/api/v1/nodes/download/batch-async/cleanup?taskId=batch-completed",
                    20L,
                    LocalDateTime.of(2026, 3, 22, 9, 5),
                    true,
                    true,
                    false,
                    true
                )),
                1,
                0,
                new BatchDownloadController.BatchDownloadAsyncPagingResponse(10, 0, 1, false)
            )));

        AsyncTaskLifecycleListSnapshot snapshot = asyncTaskLifecycleService.listRecentTasks(10, 0, "batch-download", "completed");

        assertEquals("batchdownload", snapshot.domainFilter());
        assertEquals("completed", snapshot.statusFilter());
        assertEquals(1, snapshot.count());
        assertEquals(1L, snapshot.totalCount());
        assertFalse(snapshot.hasMoreItems());
        assertEquals("batchDownload", snapshot.items().get(0).domainKey());
        assertEquals("COMPLETED", snapshot.items().get(0).status());
        assertTrue(snapshot.items().get(0).actions().cleanupEligible());
        assertTrue(snapshot.items().get(0).actions().downloadReady());

        verifyNoInteractions(searchController, previewDiagnosticsController, opsRecoveryController);
    }

    @Test
    @DisplayName("Recent lifecycle list rejects unsupported batch status filters")
    void listRecentTasksRejectsUnknownBatchStatus() {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> asyncTaskLifecycleService.listRecentTasks(10, 0, "batch", "bogus")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Unknown batch download async status"));
    }

    @Test
    @DisplayName("Recent lifecycle list can hide acknowledged tasks while preserving restore view")
    void listRecentTasksSupportsAcknowledgementFiltering() {
        when(searchController.summarizeDryRunQueueFailedPreviewsBySearchCsvAsyncTaskSnapshot("completed"))
            .thenReturn(AsyncTaskSummarySnapshot.ofBreakdown(0, 0, 1, 0, 0, 0, 0));
        when(searchController.listDryRunQueueFailedPreviewsBySearchCsvAsyncTasks(10, "completed"))
            .thenReturn(ResponseEntity.ok(new SearchController.PreviewQueueBySearchDryRunExportAsyncListResponse(
                1,
                List.of(new SearchController.PreviewQueueBySearchDryRunExportAsyncStatusResponse(
                    "search-ack-1",
                    "COMPLETED",
                    null,
                    Instant.parse("2026-03-21T12:00:00Z"),
                    Instant.parse("2026-03-21T12:05:00Z"),
                    "search.csv"
                ))
            )));

        AsyncTaskStatusSnapshot acknowledgedTask = new AsyncTaskStatusSnapshot(
            "search",
            "Search",
            "search-ack-1",
            "COMPLETED",
            null,
            Instant.parse("2026-03-21T12:00:00Z"),
            null,
            null,
            null,
            null,
            Instant.parse("2026-03-21T12:05:00Z"),
            "search.csv",
            null,
            null,
            new AsyncTaskActionSnapshot(
                null,
                "/api/v1/search/preview/queue-failed/dry-run/export-async/search-ack-1/download",
                "/api/v1/search/preview/queue-failed/dry-run/export-async/cleanup",
                false,
                true,
                true
            )
        ).withAcknowledgement(
            "search|search-ack-1|COMPLETED|2026-03-21T12:05:00Z",
            true,
            Instant.parse("2026-03-21T12:06:00Z")
        );

        when(asyncTaskAcknowledgementService.applyAcknowledgements(anyList(), eq(false)))
            .thenReturn(List.of());
        when(asyncTaskAcknowledgementService.applyAcknowledgements(anyList(), eq(true)))
            .thenReturn(List.of(acknowledgedTask));

        AsyncTaskLifecycleListSnapshot hidden = asyncTaskLifecycleService.listRecentTasks(10, 0, "search", "completed", false);
        AsyncTaskLifecycleListSnapshot visible = asyncTaskLifecycleService.listRecentTasks(10, 0, "search", "completed", true);

        assertEquals(0, hidden.count());
        assertEquals(0, hidden.items().size());
        assertEquals(1, visible.count());
        assertTrue(visible.items().get(0).acknowledged());
        assertEquals("search|search-ack-1|COMPLETED|2026-03-21T12:05:00Z", visible.items().get(0).fingerprint());
    }
}
