package com.ecm.core.queuebacklog;

import com.ecm.core.entity.ReplicationJob;
import com.ecm.core.integration.mail.model.MailAccount;
import com.ecm.core.integration.mail.repository.MailAccountRepository;
import com.ecm.core.ocr.OcrQueueService;
import com.ecm.core.repository.ReplicationJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueueBacklogObservabilityServiceTest {

    private final OcrQueueService ocrQueueService = mock(OcrQueueService.class);
    private final MailAccountRepository mailAccountRepository = mock(MailAccountRepository.class);
    private final ReplicationJobRepository replicationJobRepository = mock(ReplicationJobRepository.class);

    private QueueBacklogObservabilityService service;

    @BeforeEach
    void setUp() {
        service = new QueueBacklogObservabilityService(ocrQueueService, mailAccountRepository, replicationJobRepository);
        ReflectionTestUtils.setField(service, "transferStuckMinutes", 60L);
    }

    private static MailAccount account(String status, LocalDateTime lastFetchAt) {
        MailAccount account = new MailAccount();
        account.setLastFetchStatus(status);
        account.setLastFetchAt(lastFetchAt);
        return account;
    }

    @Test
    @DisplayName("aggregates OCR depth/oldest, mail fetch-health, and transfer counts/oldest/stuck")
    void aggregatesAllThree() {
        when(ocrQueueService.getBacklogSnapshot())
            .thenReturn(new OcrQueueService.OcrBacklogSnapshot(true, 7L, 120L));
        LocalDateTime lastSuccess = LocalDateTime.now().minusMinutes(5);
        when(mailAccountRepository.findAll()).thenReturn(List.of(
            account("SUCCESS", lastSuccess),
            account("ERROR", LocalDateTime.now().minusMinutes(3)),
            account("ERROR", LocalDateTime.now().minusMinutes(2)),
            account("SUCCESS", LocalDateTime.now().minusMinutes(70))));

        when(replicationJobRepository.countByStatus(ReplicationJob.ReplicationJobStatus.PENDING)).thenReturn(3L);
        when(replicationJobRepository.countByStatus(ReplicationJob.ReplicationJobStatus.RUNNING)).thenReturn(1L);
        when(replicationJobRepository.countByStatus(ReplicationJob.ReplicationJobStatus.FAILED)).thenReturn(2L);
        ReplicationJob oldest = new ReplicationJob();
        oldest.setStatus(ReplicationJob.ReplicationJobStatus.PENDING);
        oldest.setCreatedAt(LocalDateTime.now().minusMinutes(30));
        when(replicationJobRepository.findFirstByStatusOrderByCreatedAtAsc(ReplicationJob.ReplicationJobStatus.PENDING))
            .thenReturn(Optional.of(oldest));
        when(replicationJobRepository.countByStatusAndStartedAtBefore(eq(ReplicationJob.ReplicationJobStatus.RUNNING), any()))
            .thenReturn(1L);

        QueueBacklogSummaryDto dto = service.getSummary();

        assertThat(dto.ocr().available()).isTrue();
        assertThat(dto.ocr().pendingDepth()).isEqualTo(7L);
        assertThat(dto.ocr().oldestPendingAgeSeconds()).isEqualTo(120L);

        assertThat(dto.mail().available()).isTrue();
        assertThat(dto.mail().errors()).isEqualTo(2L);
        assertThat(dto.mail().errorRate()).isEqualTo(2.0d / 3.0d);
        assertThat(dto.mail().status()).isEqualTo("DEGRADED");
        assertThat(dto.mail().lastSuccessAt()).isEqualTo(lastSuccess);

        assertThat(dto.transfer().available()).isTrue();
        assertThat(dto.transfer().pendingCount()).isEqualTo(3L);
        assertThat(dto.transfer().runningCount()).isEqualTo(1L);
        assertThat(dto.transfer().failedCount()).isEqualTo(2L);
        assertThat(dto.transfer().stuckRunningCount()).isEqualTo(1L);
        assertThat(dto.transfer().stuckThresholdMinutes()).isEqualTo(60L);
        // oldest PENDING createdAt was ~30m ago.
        assertThat(dto.transfer().oldestPendingAgeSeconds()).isBetween(1700L, 1900L);
    }

    @Test
    @DisplayName("a failing source reports available=false and the service never throws")
    void failingSourceIsIsolated() {
        when(ocrQueueService.getBacklogSnapshot()).thenThrow(new RuntimeException("redis down"));
        when(mailAccountRepository.findAll()).thenThrow(new RuntimeException("mail down"));
        when(replicationJobRepository.countByStatus(any())).thenThrow(new RuntimeException("db down"));

        QueueBacklogSummaryDto dto = service.getSummary();

        assertThat(dto.ocr().available()).isFalse();
        assertThat(dto.ocr().pendingDepth()).isZero();
        assertThat(dto.mail().available()).isFalse();
        assertThat(dto.mail().lastSuccessAt()).isNull();
        assertThat(dto.transfer().available()).isFalse();
        assertThat(dto.transfer().pendingCount()).isZero();
        // stuck threshold still surfaces even when the source is unavailable.
        assertThat(dto.transfer().stuckThresholdMinutes()).isEqualTo(60L);
    }
}
