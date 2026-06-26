package com.ecm.core.scheduler;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, since-boot registry of recurring {@code @Scheduled} job runs (Day-2 scheduler-run
 * observability, taskbook DEVELOPMENT_SCHEDULER_WORKER_OBSERVABILITY_TASKBOOK_20260625).
 *
 * <p>Records are kept per stable {@code jobId} (see {@link SchedulerJobIds}). Mutations go through
 * {@link ConcurrentHashMap#compute} on an immutable {@link RunRecord} so updates are atomic and
 * thread-safe; only the exception TYPE is stored (never a message/stack), per the sensitive-data
 * logging line.
 */
@Component
public class SchedulerRunRegistry {

    public enum Status { RUNNING, SUCCESS, FAILED }

    public record RunRecord(
        LocalDateTime lastRunAt,
        Status lastStatus,
        Long lastDurationMs,
        String lastErrorType,
        long runCount,
        long failCount
    ) {}

    private final Map<String, RunRecord> records = new ConcurrentHashMap<>();

    public void recordStart(String jobId) {
        records.compute(jobId, (k, old) -> new RunRecord(
            LocalDateTime.now(),
            Status.RUNNING,
            old == null ? null : old.lastDurationMs(),
            old == null ? null : old.lastErrorType(),
            old == null ? 0L : old.runCount(),
            old == null ? 0L : old.failCount()
        ));
    }

    public void recordSuccess(String jobId, long durationMs) {
        records.compute(jobId, (k, old) -> new RunRecord(
            old == null ? LocalDateTime.now() : old.lastRunAt(),
            Status.SUCCESS,
            durationMs,
            null,
            (old == null ? 0L : old.runCount()) + 1,
            old == null ? 0L : old.failCount()
        ));
    }

    public void recordFailure(String jobId, long durationMs, String errorType) {
        records.compute(jobId, (k, old) -> new RunRecord(
            old == null ? LocalDateTime.now() : old.lastRunAt(),
            Status.FAILED,
            durationMs,
            errorType,
            (old == null ? 0L : old.runCount()) + 1,
            (old == null ? 0L : old.failCount()) + 1
        ));
    }

    public RunRecord get(String jobId) {
        return records.get(jobId);
    }

    public Map<String, RunRecord> snapshot() {
        return Map.copyOf(records);
    }
}
