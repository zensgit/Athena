package com.ecm.core.failureinventory;

import java.time.Instant;
import java.util.Map;

/**
 * Read-only cross-subsystem <b>failure inventory</b> snapshot (Day-2 of taskbook
 * DEVELOPMENT_CROSS_SUBSYSTEM_FAILURE_DEADLETTER_OBSERVABILITY_TASKBOOK_20260627, §4 first-cut).
 *
 * <p>This is the Axis-B (underlying content-processing failures / dead-letters) inventory, deliberately
 * narrow: the ONE genuinely new cheap signal is the <b>preview dead-letter count</b>; transfer FAILED and
 * mail account-level ERROR counts are <b>reused</b> from {@code QueueBacklogObservabilityService} (not
 * recomputed), and the deep ADMIN-gated surfaces (preview diagnostics, mail diagnostics, transfer jobs)
 * are linked out to by the card — this DTO co-locates counts, it does not pile on new data.
 *
 * <p><b>§5A guards baked into the shape:</b>
 * <ul>
 *   <li><b>Index-first / O(1):</b> preview via the registry ({@code getItemCount} = Redis ZCARD or
 *       in-memory size), transfer via {@code idx_replication_job_status}, mail via account rows — no
 *       {@code ProcessedMail.status} scan, no {@code Document.metadata} scan.</li>
 *   <li><b>PII-safe:</b> count + timestamp + type/category ONLY. This record intentionally carries NO
 *       raw failure text — no {@code reason}, {@code subject}, {@code errorMessage}, {@code transportMessage},
 *       {@code errorLog}, {@code lastMessage}, or {@code entryReport}. Raw text stays in the existing
 *       ADMIN-gated deep surfaces the card links out to.</li>
 * </ul>
 *
 * <p>Any unavailable source reports {@code available=false} (with zeroed/null counts) rather than throwing.
 */
public record FailureInventorySummaryDto(
    PreviewDeadLetter preview,
    TransferFailures transfer,
    MailFetchErrors mail,
    OcrFailures ocr
) {

    /**
     * Preview rendering dead-letters — the one new cheap signal (absent from the queue-backlog card AND
     * from the async {@code failedCount}). Count is O(1); {@code categoryTally} groups a bounded sample by
     * the normalized, non-PII {@code category} (e.g. {@code TIMEOUT}, {@code UNKNOWN}); {@code latestFailedAt}
     * is the most recent terminal failure time. The dead-letter {@code reason} text is NOT exposed here.
     */
    public record PreviewDeadLetter(
        boolean available,
        long deadLetterCount,
        Map<String, Long> categoryTally,
        Instant latestFailedAt
    ) {}

    /** Transfer replication FAILED count — reused from {@code QueueBacklogObservabilityService} (index-backed). Count only. */
    public record TransferFailures(
        boolean available,
        long failedCount
    ) {}

    /** Mail account-level fetch ERROR count — reused from {@code QueueBacklogObservabilityService}. Count only (no subject/errorMessage). */
    public record MailFetchErrors(
        boolean available,
        long errorAccountCount
    ) {}

    /**
     * OCR document-processing failures (taskbook #3, Option A) — index-first counts of documents stuck in
     * {@code FAILED} / {@code PROCESSING}, read O(1) from {@code idx_document_ocr_status} (NOT a jsonb
     * metadata scan). Count only — carries no document name, text, or failure reason (those stay in the
     * deep OCR/document surfaces). {@code available=false} if the count source is unreachable.
     */
    public record OcrFailures(
        boolean available,
        long failedCount,
        long runningCount
    ) {}
}
