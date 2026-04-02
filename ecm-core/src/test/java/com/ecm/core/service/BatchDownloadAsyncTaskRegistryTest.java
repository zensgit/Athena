package com.ecm.core.service;

import com.ecm.core.service.BatchDownloadAsyncTaskRegistry.BatchDownloadAsyncStatus;
import com.ecm.core.service.BatchDownloadAsyncTaskRegistry.BatchDownloadAsyncTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchDownloadAsyncTaskRegistryTest {

    private BatchDownloadAsyncTaskRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new BatchDownloadAsyncTaskRegistry();
    }

    @Test
    @DisplayName("Expired terminal async tasks are removed with their archive artifacts")
    void cleanupExpiredTerminalTasksShouldDeleteArchives() throws Exception {
        Path archive = Files.createTempFile("athena-batch-download-test-", ".zip");
        BatchDownloadAsyncTask expiredTask = buildTask(
            "task-expired",
            BatchDownloadAsyncStatus.COMPLETED,
            LocalDateTime.now().minusDays(2),
            LocalDateTime.now().minusDays(2),
            LocalDateTime.now().minusDays(2),
            archive
        );

        registry.register(expiredTask);

        int removed = registry.cleanupExpiredTerminalTasks(Instant.now());

        assertEquals(1, removed);
        assertNull(registry.get("task-expired"));
        assertFalse(Files.exists(archive));
    }

    @Test
    @DisplayName("Active async tasks are retained even when they are old")
    void cleanupExpiredTerminalTasksShouldKeepActiveTasks() throws Exception {
        Path archive = Files.createTempFile("athena-batch-download-test-", ".zip");
        BatchDownloadAsyncTask activeTask = buildTask(
            "task-active",
            BatchDownloadAsyncStatus.RUNNING,
            LocalDateTime.now().minusDays(2),
            LocalDateTime.now().minusDays(2),
            null,
            archive
        );

        registry.register(activeTask);

        int removed = registry.cleanupExpiredTerminalTasks(Instant.now());

        assertEquals(0, removed);
        assertTrue(Files.exists(archive));
        assertEquals(BatchDownloadAsyncStatus.RUNNING, registry.get("task-active").status());
    }

    @Test
    @DisplayName("Summary reflects lifecycle counts")
    void summaryShouldReflectLifecycleCounts() {
        registry.register(buildTask("task-queued", BatchDownloadAsyncStatus.QUEUED, LocalDateTime.now(), LocalDateTime.now(), null, null));
        registry.register(buildTask("task-running", BatchDownloadAsyncStatus.RUNNING, LocalDateTime.now(), LocalDateTime.now(), null, null));
        registry.register(buildTask("task-cancel-requested", BatchDownloadAsyncStatus.CANCEL_REQUESTED, LocalDateTime.now(), LocalDateTime.now(), null, null));
        registry.register(buildTask("task-cancelled", BatchDownloadAsyncStatus.CANCELLED, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null));
        registry.register(buildTask("task-completed", BatchDownloadAsyncStatus.COMPLETED, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null));
        registry.register(buildTask("task-failed", BatchDownloadAsyncStatus.FAILED, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null));

        BatchDownloadAsyncTaskRegistry.BatchDownloadAsyncSummary summary = registry.summary();

        assertEquals(6, summary.totalCount());
        assertEquals(3, summary.activeCount());
        assertEquals(3, summary.terminalCount());
        assertEquals(1, summary.queuedCount());
        assertEquals(1, summary.runningCount());
        assertEquals(1, summary.cancelRequestedCount());
        assertEquals(1, summary.cancelledCount());
        assertEquals(1, summary.completedCount());
        assertEquals(1, summary.failedCount());
    }

    @Test
    @DisplayName("Snapshot supports filtered paging")
    void snapshotShouldSupportFilteredPaging() {
        registry.register(buildTask("task-1", BatchDownloadAsyncStatus.COMPLETED, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null));
        registry.register(buildTask("task-2", BatchDownloadAsyncStatus.COMPLETED, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null));
        registry.register(buildTask("task-3", BatchDownloadAsyncStatus.RUNNING, LocalDateTime.now(), LocalDateTime.now(), null, null));

        BatchDownloadAsyncTaskRegistry.BatchDownloadSnapshot snapshot =
            registry.snapshot(1, 1, BatchDownloadAsyncStatus.COMPLETED, null);

        assertEquals(1, snapshot.items().size());
        assertEquals("task-1", snapshot.items().get(0).taskId());
        assertEquals(3, snapshot.totalCount());
        assertEquals(2, snapshot.filteredCount());
        assertEquals(1, snapshot.skipCount());
        assertEquals(1, snapshot.maxItems());
    }

    @Test
    @DisplayName("Snapshot supports query filtering")
    void snapshotShouldSupportQueryFiltering() {
        registry.register(buildTask("task-alpha", BatchDownloadAsyncStatus.COMPLETED, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null));
        registry.register(new BatchDownloadAsyncTask(
            "task-beta",
            List.of(UUID.randomUUID()),
            "monthly-report",
            "finance",
            "finance-export.zip",
            BatchDownloadAsyncStatus.COMPLETED,
            1,
            1,
            100L,
            100L,
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            null,
            null
        ));

        BatchDownloadAsyncTaskRegistry.BatchDownloadSnapshot snapshot =
            registry.snapshot(10, 0, BatchDownloadAsyncStatus.COMPLETED, "finance");

        assertEquals(1, snapshot.items().size());
        assertEquals("task-beta", snapshot.items().get(0).taskId());
        assertEquals(1, snapshot.filteredCount());
    }

    @Test
    @DisplayName("Snapshot supports owner filtering")
    void snapshotShouldSupportOwnerFiltering() {
        registry.register(buildTask("task-alice", BatchDownloadAsyncStatus.COMPLETED, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null, "alice"));
        registry.register(buildTask("task-bob", BatchDownloadAsyncStatus.COMPLETED, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null, "bob"));

        BatchDownloadAsyncTaskRegistry.BatchDownloadSnapshot snapshot =
            registry.snapshot(10, 0, BatchDownloadAsyncStatus.COMPLETED, null, "alice");

        assertEquals(1, snapshot.items().size());
        assertEquals("task-alice", snapshot.items().get(0).taskId());
        assertEquals(1, snapshot.filteredCount());
    }

    @Test
    @DisplayName("Cancel-active updates queued and running tasks")
    void cancelActiveTasksShouldUpdateQueuedAndRunningTasks() {
        BatchDownloadAsyncTask queuedTask = buildTask(
            "task-queued",
            BatchDownloadAsyncStatus.QUEUED,
            LocalDateTime.now(),
            LocalDateTime.now(),
            null,
            null
        );
        BatchDownloadAsyncTask runningTask = buildTask(
            "task-running",
            BatchDownloadAsyncStatus.RUNNING,
            LocalDateTime.now(),
            LocalDateTime.now(),
            null,
            null
        );
        registry.register(queuedTask);
        registry.register(runningTask);

        int cancelled = registry.cancelActiveTasks(null);

        assertEquals(2, cancelled);
        assertEquals(BatchDownloadAsyncStatus.CANCELLED, registry.get("task-queued").status());
        assertEquals(BatchDownloadAsyncStatus.CANCEL_REQUESTED, registry.get("task-running").status());
        assertEquals(1, registry.activeCount());
    }

    @Test
    @DisplayName("Cleanup terminal tasks removes matching terminal tasks immediately")
    void cleanupTerminalTasksShouldRemoveMatchingTerminalTasks() throws Exception {
        Path archive = Files.createTempFile("athena-batch-download-test-", ".zip");
        BatchDownloadAsyncTask completedTask = buildTask(
            "task-completed",
            BatchDownloadAsyncStatus.COMPLETED,
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            archive
        );
        BatchDownloadAsyncTask cancelledTask = buildTask(
            "task-cancelled",
            BatchDownloadAsyncStatus.CANCELLED,
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            null
        );

        registry.register(completedTask);
        registry.register(cancelledTask);

        int removed = registry.cleanupTerminalTasks(BatchDownloadAsyncStatus.COMPLETED);

        assertEquals(1, removed);
        assertNull(registry.get("task-completed"));
        assertEquals(BatchDownloadAsyncStatus.CANCELLED, registry.get("task-cancelled").status());
        assertFalse(Files.exists(archive));
    }

    @Test
    @DisplayName("Cleanup single terminal task removes its archive artifact")
    void cleanupTaskShouldRemoveSingleTerminalTask() throws Exception {
        Path archive = Files.createTempFile("athena-batch-download-test-", ".zip");
        BatchDownloadAsyncTask completedTask = buildTask(
            "task-cleanup",
            BatchDownloadAsyncStatus.COMPLETED,
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now(),
            archive
        );

        registry.register(completedTask);

        int removed = registry.cleanupTask("task-cleanup");

        assertEquals(1, removed);
        assertNull(registry.get("task-cleanup"));
        assertFalse(Files.exists(archive));
    }

    @Test
    @DisplayName("Batch download task exposes lifecycle helpers")
    void taskLifecycleHelpersShouldExposeMetadata() throws Exception {
        Path archive = Files.createTempFile("athena-batch-download-test-", ".zip");
        Files.writeString(archive, "zip");
        BatchDownloadAsyncTask task = buildTask(
            "task-metadata",
            BatchDownloadAsyncStatus.COMPLETED,
            LocalDateTime.now().minusHours(2),
            LocalDateTime.now().minusHours(1),
            LocalDateTime.now().minusMinutes(30),
            archive
        );

        assertTrue(task.cleanupEligible());
        assertTrue(task.artifactPresent());
        assertEquals(Files.size(archive), task.archiveSizeBytes().longValue());
        assertTrue(task.retentionExpiresAt().isAfter(task.completedAt()));
    }

    private BatchDownloadAsyncTask buildTask(
        String taskId,
        BatchDownloadAsyncStatus status,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Path archivePath
    ) {
        return buildTask(taskId, status, createdAt, startedAt, completedAt, archivePath, "alice");
    }

    private BatchDownloadAsyncTask buildTask(
        String taskId,
        BatchDownloadAsyncStatus status,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Path archivePath,
        String createdBy
    ) {
        return new BatchDownloadAsyncTask(
            taskId,
            List.of(UUID.randomUUID()),
            "selection",
            createdBy,
            "selection.zip",
            status,
            1,
            1,
            100L,
            100L,
            createdAt,
            startedAt,
            completedAt,
            null,
            archivePath
        );
    }
}
