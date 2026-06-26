package com.ecm.core.queuebacklog;

import com.ecm.core.entity.ReplicationJob;
import com.ecm.core.integration.mail.model.MailAccount;
import com.ecm.core.integration.mail.repository.MailAccountRepository;
import com.ecm.core.ocr.OcrQueueService;
import com.ecm.core.queuebacklog.QueueBacklogSummaryDto.MailBacklog;
import com.ecm.core.queuebacklog.QueueBacklogSummaryDto.OcrBacklog;
import com.ecm.core.queuebacklog.QueueBacklogSummaryDto.TransferBacklog;
import com.ecm.core.repository.ReplicationJobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Aggregates the read-only "Queue Backlog" snapshot from the three subsystems (OCR / mail / transfer).
 *
 * <p><b>§5A index-first:</b> transfer uses index-backed {@code countByStatus} / oldest / stuck (status +
 * created_at are indexed); mail uses the same account-level fetch-health semantics as runtime metrics without
 * querying {@code mail_processed_messages}; OCR is O(1) Redis ops. Each source is wrapped so a failure yields
 * {@code available=false} instead of throwing — one bad source never sinks the whole card.
 */
@Service
public class QueueBacklogObservabilityService {

    private final OcrQueueService ocrQueueService;
    private final MailAccountRepository mailAccountRepository;
    private final ReplicationJobRepository replicationJobRepository;

    @Value("${ecm.queue-backlog.transfer-stuck-minutes:60}")
    private long transferStuckMinutes;

    public QueueBacklogObservabilityService(OcrQueueService ocrQueueService,
                                            MailAccountRepository mailAccountRepository,
                                            ReplicationJobRepository replicationJobRepository) {
        this.ocrQueueService = ocrQueueService;
        this.mailAccountRepository = mailAccountRepository;
        this.replicationJobRepository = replicationJobRepository;
    }

    public QueueBacklogSummaryDto getSummary() {
        return new QueueBacklogSummaryDto(ocrBacklog(), mailBacklog(), transferBacklog());
    }

    private OcrBacklog ocrBacklog() {
        try {
            OcrQueueService.OcrBacklogSnapshot s = ocrQueueService.getBacklogSnapshot();
            return new OcrBacklog(s.available(), s.pendingDepth(), s.oldestPendingAgeSeconds());
        } catch (RuntimeException ex) {
            return new OcrBacklog(false, 0L, null);
        }
    }

    private MailBacklog mailBacklog() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusMinutes(60);
            List<MailAccount> windowAccounts = mailAccountRepository.findAll().stream()
                .filter(account -> account.getLastFetchAt() != null && !account.getLastFetchAt().isBefore(threshold))
                .toList();

            long attempts = windowAccounts.size();
            long successes = windowAccounts.stream()
                .filter(account -> "SUCCESS".equalsIgnoreCase(account.getLastFetchStatus()))
                .count();
            long errors = windowAccounts.stream()
                .filter(account -> "ERROR".equalsIgnoreCase(account.getLastFetchStatus()))
                .count();
            double errorRate = attempts > 0 ? (double) errors / attempts : 0.0d;
            LocalDateTime lastSuccessAt = windowAccounts.stream()
                .filter(account -> "SUCCESS".equalsIgnoreCase(account.getLastFetchStatus()))
                .map(MailAccount::getLastFetchAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
            String status = attempts == 0
                ? "UNKNOWN"
                : errors == 0
                    ? "HEALTHY"
                    : successes == 0
                        ? "DOWN"
                        : "DEGRADED";
            return new MailBacklog(true, lastSuccessAt, errorRate, errors, status);
        } catch (RuntimeException ex) {
            return new MailBacklog(false, null, 0.0d, 0L, null);
        }
    }

    private TransferBacklog transferBacklog() {
        try {
            long pending = replicationJobRepository.countByStatus(ReplicationJob.ReplicationJobStatus.PENDING);
            long running = replicationJobRepository.countByStatus(ReplicationJob.ReplicationJobStatus.RUNNING);
            long failed = replicationJobRepository.countByStatus(ReplicationJob.ReplicationJobStatus.FAILED);
            Long oldestPendingAgeSeconds = replicationJobRepository
                .findFirstByStatusOrderByCreatedAtAsc(ReplicationJob.ReplicationJobStatus.PENDING)
                .map(ReplicationJob::getCreatedAt)
                .filter(Objects::nonNull)
                .map(createdAt -> Math.max(0L, Duration.between(createdAt, LocalDateTime.now()).getSeconds()))
                .orElse(null);
            long stuckRunning = replicationJobRepository.countByStatusAndStartedAtBefore(
                ReplicationJob.ReplicationJobStatus.RUNNING,
                LocalDateTime.now().minusMinutes(transferStuckMinutes));
            return new TransferBacklog(true, pending, running, failed, oldestPendingAgeSeconds,
                stuckRunning, transferStuckMinutes);
        } catch (RuntimeException ex) {
            return new TransferBacklog(false, 0L, 0L, 0L, null, 0L, transferStuckMinutes);
        }
    }
}
