package com.ecm.core.scheduler;

import java.time.LocalDateTime;

/**
 * One row per registered recurring {@code @Scheduled} job (admin read-only observability).
 *
 * <p>§3A.2: {@code nextRunAt} is NULLABLE — it is populated only where the trigger semantics make it
 * cleanly computable (cron, or fixedRate after a run); otherwise it is {@code null} and
 * {@code scheduleDescription} (always non-null) carries the cron string / {@code fixedDelay=Nms} etc.
 * The endpoint never fabricates a time.
 */
public record SchedulerJobSnapshotDto(
    String jobId,
    LocalDateTime lastRunAt,
    String lastStatus,
    Long lastDurationMs,
    String lastErrorType,
    long runCount,
    long failCount,
    LocalDateTime nextRunAt,
    String scheduleDescription
) {
}
