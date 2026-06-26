package com.ecm.core.queuebacklog;

import java.time.LocalDateTime;

/**
 * Read-only "Queue Backlog" snapshot across the three subsystems (Day-2 queue backlog observability,
 * taskbook DEVELOPMENT_QUEUE_BACKLOG_OBSERVABILITY_TASKBOOK_20260626). Observability-only; every signal
 * is a cheap, index-backed / O(1) read (§5A), and any unavailable source reports {@code available=false}
 * rather than throwing.
 *
 * <ul>
 *   <li><b>OCR</b> — Redis depth + oldest (the deferred failed/running would need an unindexed scan).</li>
 *   <li><b>Mail</b> — account-level fetch-health (same status semantics as runtime metrics; IMAP pull has no local queue depth).</li>
 *   <li><b>Transfer</b> — full backlog via index-backed {@code countByStatus} / oldest / stuck.</li>
 * </ul>
 */
public record QueueBacklogSummaryDto(
    OcrBacklog ocr,
    MailBacklog mail,
    TransferBacklog transfer
) {

    /** OCR queue (Redis ZSET, or in-memory fallback). {@code oldestPendingAgeSeconds} = age past the oldest item's scheduled time. */
    public record OcrBacklog(
        boolean available,
        long pendingDepth,
        Long oldestPendingAgeSeconds
    ) {}

    /** Mail fetch-health (NOT a queue — IMAP is pull-based). Uses the account-level last-fetch window. */
    public record MailBacklog(
        boolean available,
        LocalDateTime lastSuccessAt,
        double errorRate,
        long errors,
        String status
    ) {}

    /** Transfer replication backlog (DB, index-backed). {@code stuckRunningCount} = RUNNING older than {@code stuckThresholdMinutes}. */
    public record TransferBacklog(
        boolean available,
        long pendingCount,
        long runningCount,
        long failedCount,
        Long oldestPendingAgeSeconds,
        long stuckRunningCount,
        long stuckThresholdMinutes
    ) {}
}
