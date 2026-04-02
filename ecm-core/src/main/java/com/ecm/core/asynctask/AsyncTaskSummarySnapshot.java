package com.ecm.core.asynctask;

/**
 * Shared async task summary snapshot used by governance and admin aggregation
 * paths. Totals are normalized from a common lifecycle breakdown so overview
 * consumers see coherent active/terminal math across different task centers.
 */
public record AsyncTaskSummarySnapshot(
    long totalCount,
    long activeCount,
    long terminalCount,
    long queuedCount,
    long runningCount,
    long completedCount,
    long cancelledCount,
    long failedCount,
    long timedOutCount,
    long expiredCount
) {

    public static AsyncTaskSummarySnapshot ofBreakdown(
        long queuedCount,
        long runningCount,
        long completedCount,
        long cancelledCount,
        long failedCount,
        long timedOutCount,
        long expiredCount
    ) {
        long activeCount = queuedCount + runningCount;
        long terminalCount = completedCount + cancelledCount + failedCount + timedOutCount + expiredCount;
        long totalCount = activeCount + terminalCount;
        return new AsyncTaskSummarySnapshot(
            totalCount,
            activeCount,
            terminalCount,
            queuedCount,
            runningCount,
            completedCount,
            cancelledCount,
            failedCount,
            timedOutCount,
            expiredCount
        );
    }

    public long failureCount() {
        return failedCount + timedOutCount + expiredCount;
    }

    public double failureRate() {
        return totalCount > 0 ? (double) failureCount() / (double) totalCount : 0D;
    }
}
