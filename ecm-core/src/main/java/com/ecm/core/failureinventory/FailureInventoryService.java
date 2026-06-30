package com.ecm.core.failureinventory;

import com.ecm.core.failureinventory.FailureInventorySummaryDto.MailFetchErrors;
import com.ecm.core.failureinventory.FailureInventorySummaryDto.MailProcessedErrors;
import com.ecm.core.failureinventory.FailureInventorySummaryDto.OcrFailures;
import com.ecm.core.failureinventory.FailureInventorySummaryDto.PreviewDeadLetter;
import com.ecm.core.failureinventory.FailureInventorySummaryDto.TransferFailures;
import com.ecm.core.integration.mail.model.ProcessedMail;
import com.ecm.core.integration.mail.repository.ProcessedMailRepository;
import com.ecm.core.preview.PreviewDeadLetterRegistry;
import com.ecm.core.preview.PreviewDeadLetterRegistry.DeadLetterEntry;
import com.ecm.core.queuebacklog.QueueBacklogObservabilityService;
import com.ecm.core.queuebacklog.QueueBacklogSummaryDto;
import com.ecm.core.repository.DocumentRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Read-only cross-subsystem failure-inventory aggregator (taskbook §4 first-cut, §7 ratified).
 *
 * <p>Deliberately thin. Three NEW cheap computations, each index-first: the preview dead-letter count (+ a
 * non-PII category tally and the latest failure time) read O(1) from {@link PreviewDeadLetterRegistry}; the OCR
 * FAILED/PROCESSING document counts (taskbook #3, Option A) via {@link DocumentRepository#countByOcrStatus(String)}
 * ({@code idx_document_ocr_status}, NOT a {@code nodes.metadata} jsonb scan); and the mail per-message ERROR count
 * (taskbook #4, Option A) via {@link ProcessedMailRepository#countByStatus} ({@code idx_mail_processed_status},
 * NOT a full {@code mail_processed_messages} scan — count only, no {@code subject}/{@code error_message}). Transfer
 * FAILED and mail <i>account-level</i> ERROR counts are <b>reused</b> from
 * {@link QueueBacklogObservabilityService#getSummary()} (called once; its per-source {@code available} flags are
 * propagated) — this service never re-implements or re-indexes those. Note the mail per-message ERROR count is a
 * DIFFERENT axis from the reused account-level fetch-health count, not a duplicate.
 *
 * <p>Every source is isolated: a failure of one source yields {@code available=false} for that source only
 * and never throws (§6 / §5A), keeping the AdminDashboard card stable.
 */
@Service
public class FailureInventoryService {

    /** {@code list(int)} clamps to 1..500; this bounds the category-tally sample cheaply. */
    private static final int CATEGORY_SAMPLE_LIMIT = 500;

    /** OCR states mirrored onto {@code documents.ocr_status} by {@code OcrQueueService}; only failures/in-flight are counted. */
    private static final String OCR_STATUS_FAILED = "FAILED";
    private static final String OCR_STATUS_PROCESSING = "PROCESSING";

    private final QueueBacklogObservabilityService queueBacklogObservabilityService;
    private final PreviewDeadLetterRegistry previewDeadLetterRegistry;
    private final DocumentRepository documentRepository;
    private final ProcessedMailRepository processedMailRepository;

    public FailureInventoryService(
        QueueBacklogObservabilityService queueBacklogObservabilityService,
        PreviewDeadLetterRegistry previewDeadLetterRegistry,
        DocumentRepository documentRepository,
        ProcessedMailRepository processedMailRepository
    ) {
        this.queueBacklogObservabilityService = queueBacklogObservabilityService;
        this.previewDeadLetterRegistry = previewDeadLetterRegistry;
        this.documentRepository = documentRepository;
        this.processedMailRepository = processedMailRepository;
    }

    public FailureInventorySummaryDto getSummary() {
        // Reuse the queue-backlog snapshot ONCE for the already-computed transfer/mail counts. Its own
        // per-source guards mean getSummary() does not throw; a defensive catch keeps us null-safe anyway.
        QueueBacklogSummaryDto backlog;
        try {
            backlog = queueBacklogObservabilityService.getSummary();
        } catch (RuntimeException ex) {
            backlog = null;
        }
        return new FailureInventorySummaryDto(
            previewDeadLetter(),
            transferFailures(backlog),
            mailFetchErrors(backlog),
            ocrFailures(),
            mailProcessedErrors()
        );
    }

    /** NEW signal: O(1) dead-letter count + non-PII category tally + latest failure time. */
    private PreviewDeadLetter previewDeadLetter() {
        try {
            long count = previewDeadLetterRegistry.getItemCount();
            List<DeadLetterEntry> sample = previewDeadLetterRegistry.list(CATEGORY_SAMPLE_LIMIT);
            Map<String, Long> categoryTally = sample.stream()
                .collect(Collectors.groupingBy(
                    entry -> entry.category() == null ? "UNKNOWN" : entry.category(),
                    Collectors.counting()));
            Instant latestFailedAt = sample.stream()
                .map(DeadLetterEntry::failedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
            return new PreviewDeadLetter(true, count, categoryTally, latestFailedAt);
        } catch (RuntimeException ex) {
            return new PreviewDeadLetter(false, 0L, Map.of(), null);
        }
    }

    /** REUSE: transfer FAILED count from the queue-backlog snapshot (index-backed countByStatus). Count only. */
    private TransferFailures transferFailures(QueueBacklogSummaryDto backlog) {
        if (backlog == null || backlog.transfer() == null || !backlog.transfer().available()) {
            return new TransferFailures(false, 0L);
        }
        return new TransferFailures(true, backlog.transfer().failedCount());
    }

    /** REUSE: mail account-level ERROR count from the queue-backlog snapshot. Count only (no subject/errorMessage). */
    private MailFetchErrors mailFetchErrors(QueueBacklogSummaryDto backlog) {
        if (backlog == null || backlog.mail() == null || !backlog.mail().available()) {
            return new MailFetchErrors(false, 0L);
        }
        return new MailFetchErrors(true, backlog.mail().errors());
    }

    /**
     * NEW signal (taskbook #3, Option A): OCR FAILED / PROCESSING document counts read index-first from
     * {@code idx_document_ocr_status} (two O(1) counts, no jsonb metadata scan). Count only — no document
     * name, text, or failure reason. Isolated: any failure yields {@code available=false} and never throws.
     */
    private OcrFailures ocrFailures() {
        try {
            long failed = documentRepository.countByOcrStatus(OCR_STATUS_FAILED);
            long running = documentRepository.countByOcrStatus(OCR_STATUS_PROCESSING);
            return new OcrFailures(true, failed, running);
        } catch (RuntimeException ex) {
            return new OcrFailures(false, 0L, 0L);
        }
    }

    /**
     * NEW signal (taskbook #4, Option A): per-message mail ERROR count read index-first from
     * {@code idx_mail_processed_status} via {@link ProcessedMailRepository#countByStatus} (O(index), no full
     * {@code mail_processed_messages} scan). Count only — no {@code subject}/{@code error_message} (those stay
     * in the ADMIN-gated mail diagnostics surface). A DIFFERENT axis from the reused account-level
     * {@link MailFetchErrors}. Isolated: any failure yields {@code available=false} and never throws.
     */
    private MailProcessedErrors mailProcessedErrors() {
        try {
            long errorCount = processedMailRepository.countByStatus(ProcessedMail.Status.ERROR);
            return new MailProcessedErrors(true, errorCount);
        } catch (RuntimeException ex) {
            return new MailProcessedErrors(false, 0L);
        }
    }
}
