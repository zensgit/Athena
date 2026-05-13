package com.ecm.core.asynctask;

import com.ecm.core.entity.PropertyEncryptionBackfillJob;
import com.ecm.core.entity.PropertyEncryptionBackfillJob.BackfillJobStatus;
import com.ecm.core.entity.PropertyEncryptionRewrapJob;
import com.ecm.core.entity.PropertyEncryptionRewrapJob.RewrapJobStatus;
import com.ecm.core.repository.PropertyEncryptionBackfillJobRepository;
import com.ecm.core.repository.PropertyEncryptionRewrapJobRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyEncryptionAsyncTaskServiceTest {

    @Mock
    private PropertyEncryptionBackfillJobRepository backfillJobRepository;

    @Mock
    private PropertyEncryptionRewrapJobRepository rewrapJobRepository;

    @Test
    @DisplayName("Summary aggregates backfill and rewrap ledger statuses into shared async buckets")
    void summaryAggregatesBackfillAndRewrapStatuses() {
        PropertyEncryptionAsyncTaskService service = newService();
        when(backfillJobRepository.countByStatus(BackfillJobStatus.PLANNED)).thenReturn(1L);
        when(backfillJobRepository.countByStatus(BackfillJobStatus.RUNNING)).thenReturn(2L);
        when(backfillJobRepository.countByStatus(BackfillJobStatus.CANCEL_REQUESTED)).thenReturn(3L);
        when(backfillJobRepository.countByStatus(BackfillJobStatus.SUCCEEDED)).thenReturn(4L);
        when(backfillJobRepository.countByStatus(BackfillJobStatus.CANCELLED)).thenReturn(5L);
        when(backfillJobRepository.countByStatus(BackfillJobStatus.FAILED)).thenReturn(6L);
        when(rewrapJobRepository.countByStatus(RewrapJobStatus.PLANNED)).thenReturn(10L);
        when(rewrapJobRepository.countByStatus(RewrapJobStatus.RUNNING)).thenReturn(20L);
        when(rewrapJobRepository.countByStatus(RewrapJobStatus.CANCEL_REQUESTED)).thenReturn(30L);
        when(rewrapJobRepository.countByStatus(RewrapJobStatus.SUCCEEDED)).thenReturn(40L);
        when(rewrapJobRepository.countByStatus(RewrapJobStatus.CANCELLED)).thenReturn(50L);
        when(rewrapJobRepository.countByStatus(RewrapJobStatus.FAILED)).thenReturn(60L);

        AsyncTaskSummarySnapshot summary = service.summary(null);

        assertEquals(11L, summary.queuedCount());
        assertEquals(55L, summary.runningCount());
        assertEquals(44L, summary.completedCount());
        assertEquals(55L, summary.cancelledCount());
        assertEquals(66L, summary.failedCount());
        assertEquals(66L, summary.activeCount());
        assertEquals(165L, summary.terminalCount());
        assertEquals(231L, summary.totalCount());
    }

    @Test
    @DisplayName("List recent maps filtered jobs to property-encryption lifecycle snapshots")
    void listRecentMapsFilteredJobsToSnapshots() {
        PropertyEncryptionAsyncTaskService service = newService();
        UUID backfillId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID rewrapId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        PageRequest page = PageRequest.of(0, 20);
        PropertyEncryptionBackfillJob backfill = backfillJob(backfillId, BackfillJobStatus.RUNNING);
        PropertyEncryptionRewrapJob rewrap = rewrapJob(rewrapId, RewrapJobStatus.CANCEL_REQUESTED);
        when(backfillJobRepository.findByStatusInOrderByRequestedAtDesc(
            List.of(BackfillJobStatus.RUNNING, BackfillJobStatus.CANCEL_REQUESTED),
            page
        )).thenReturn(List.of(backfill));
        when(rewrapJobRepository.findByStatusInOrderByRequestedAtDesc(
            List.of(RewrapJobStatus.RUNNING, RewrapJobStatus.CANCEL_REQUESTED),
            page
        )).thenReturn(List.of(rewrap));

        List<AsyncTaskStatusSnapshot> items = service.listRecent(20, "running");

        assertEquals(2, items.size());
        AsyncTaskStatusSnapshot backfillSnapshot = items.get(0);
        assertEquals(PropertyEncryptionAsyncTaskService.DOMAIN_KEY, backfillSnapshot.domainKey());
        assertEquals("backfill:" + backfillId, backfillSnapshot.taskId());
        assertEquals("RUNNING", backfillSnapshot.status());
        assertTrue(backfillSnapshot.actions().cancellable());
        assertEquals(
            "/api/v1/admin/property-encryption/backfill-jobs/" + backfillId + "/cancel",
            backfillSnapshot.actions().cancelUrl()
        );

        AsyncTaskStatusSnapshot rewrapSnapshot = items.get(1);
        assertEquals("rewrap:" + rewrapId, rewrapSnapshot.taskId());
        assertEquals("RUNNING", rewrapSnapshot.status());
        assertFalse(rewrapSnapshot.actions().cancellable());
        assertEquals(null, rewrapSnapshot.actions().cancelUrl());
    }

    @Test
    @DisplayName("Unknown status filters are rejected before repository access")
    void unknownStatusFiltersAreRejected() {
        PropertyEncryptionAsyncTaskService service = newService();

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.summary("bogus")
        );

        assertEquals(400, exception.getStatusCode().value());
    }

    private PropertyEncryptionAsyncTaskService newService() {
        return new PropertyEncryptionAsyncTaskService(backfillJobRepository, rewrapJobRepository);
    }

    private PropertyEncryptionBackfillJob backfillJob(UUID id, BackfillJobStatus status) {
        PropertyEncryptionBackfillJob job = new PropertyEncryptionBackfillJob();
        job.setId(id);
        job.setStatus(status);
        job.setRequestedBy("admin");
        job.setRequestedAt(LocalDateTime.of(2026, 5, 12, 10, 0));
        job.setStartedAt(LocalDateTime.of(2026, 5, 12, 10, 1));
        job.setUpdatedAt(LocalDateTime.of(2026, 5, 12, 10, 2));
        return job;
    }

    private PropertyEncryptionRewrapJob rewrapJob(UUID id, RewrapJobStatus status) {
        PropertyEncryptionRewrapJob job = new PropertyEncryptionRewrapJob();
        job.setId(id);
        job.setStatus(status);
        job.setRequestedBy("admin");
        job.setRequestedAt(LocalDateTime.of(2026, 5, 12, 11, 0));
        job.setStartedAt(LocalDateTime.of(2026, 5, 12, 11, 1));
        job.setUpdatedAt(LocalDateTime.of(2026, 5, 12, 11, 2));
        return job;
    }
}
