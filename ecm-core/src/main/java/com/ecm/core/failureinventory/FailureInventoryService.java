package com.ecm.core.failureinventory;

import com.ecm.core.failureinventory.FailureInventorySummaryDto.MailFetchErrors;
import com.ecm.core.failureinventory.FailureInventorySummaryDto.PreviewDeadLetter;
import com.ecm.core.failureinventory.FailureInventorySummaryDto.TransferFailures;
import com.ecm.core.preview.PreviewDeadLetterRegistry;
import com.ecm.core.preview.PreviewDeadLetterRegistry.DeadLetterEntry;
import com.ecm.core.queuebacklog.QueueBacklogObservabilityService;
import com.ecm.core.queuebacklog.QueueBacklogSummaryDto;
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
 * <p>Deliberately thin: the only NEW computation is the preview dead-letter count (+ a non-PII category
 * tally and the latest failure time) read O(1) from {@link PreviewDeadLetterRegistry}. Transfer FAILED and
 * mail account-level ERROR counts are <b>reused</b> from {@link QueueBacklogObservabilityService#getSummary()}
 * (called once; its per-source {@code available} flags are propagated) — this service never re-implements or
 * re-indexes those, and never reads {@code mail_processed_messages} or {@code nodes.metadata}.
 *
 * <p>Every source is isolated: a failure of one source yields {@code available=false} for that source only
 * and never throws (§6 / §5A), keeping the AdminDashboard card stable.
 */
@Service
public class FailureInventoryService {

    /** {@code list(int)} clamps to 1..500; this bounds the category-tally sample cheaply. */
    private static final int CATEGORY_SAMPLE_LIMIT = 500;

    private final QueueBacklogObservabilityService queueBacklogObservabilityService;
    private final PreviewDeadLetterRegistry previewDeadLetterRegistry;

    public FailureInventoryService(
        QueueBacklogObservabilityService queueBacklogObservabilityService,
        PreviewDeadLetterRegistry previewDeadLetterRegistry
    ) {
        this.queueBacklogObservabilityService = queueBacklogObservabilityService;
        this.previewDeadLetterRegistry = previewDeadLetterRegistry;
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
            mailFetchErrors(backlog)
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
}
