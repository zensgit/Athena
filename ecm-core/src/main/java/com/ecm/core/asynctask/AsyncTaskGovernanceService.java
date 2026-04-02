package com.ecm.core.asynctask;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AsyncTaskGovernanceService {

    private final AsyncTaskGovernanceProviderRegistry providerRegistry;

    public AsyncTaskGovernanceOverviewSnapshot buildOverview() {
        List<AsyncTaskGovernanceDomainSnapshot> domains = new ArrayList<>();
        for (AsyncTaskGovernanceProvider provider : providerRegistry.getProviders()) {
            domains.add(buildDomain(provider));
        }

        long queuedCount = 0L;
        long runningCount = 0L;
        long completedCount = 0L;
        long cancelledCount = 0L;
        long failedCount = 0L;
        long timedOutCount = 0L;
        long expiredCount = 0L;
        int degradedDomainCount = 0;
        AsyncTaskGovernanceRiskLevel overallRiskLevel = AsyncTaskGovernanceRiskLevel.LOW;

        for (AsyncTaskGovernanceDomainSnapshot domain : domains) {
            AsyncTaskSummarySnapshot summary = domain.summary();
            queuedCount += summary.queuedCount();
            runningCount += summary.runningCount();
            completedCount += summary.completedCount();
            cancelledCount += summary.cancelledCount();
            failedCount += summary.failedCount();
            timedOutCount += summary.timedOutCount();
            expiredCount += summary.expiredCount();
            if (domain.status() == AsyncTaskGovernanceStatus.DEGRADED) {
                degradedDomainCount += 1;
            }
            overallRiskLevel = maxRiskLevel(overallRiskLevel, domain.riskLevel());
        }

        if (degradedDomainCount > 0) {
            overallRiskLevel = AsyncTaskGovernanceRiskLevel.CRITICAL;
        }

        AsyncTaskSummarySnapshot summary = AsyncTaskSummarySnapshot.ofBreakdown(
            queuedCount,
            runningCount,
            completedCount,
            cancelledCount,
            failedCount,
            timedOutCount,
            expiredCount
        );

        AsyncTaskGovernanceStatus overallStatus = degradedDomainCount > 0
            ? AsyncTaskGovernanceStatus.DEGRADED
            : AsyncTaskGovernanceStatus.HEALTHY;

        return new AsyncTaskGovernanceOverviewSnapshot(
            LocalDateTime.now(),
            overallStatus,
            overallRiskLevel,
            domains.size(),
            degradedDomainCount,
            summary,
            domains
        );
    }

    private AsyncTaskGovernanceDomainSnapshot buildDomain(AsyncTaskGovernanceProvider provider) {
        try {
            AsyncTaskSummarySnapshot summary = provider.getSummary();
            return new AsyncTaskGovernanceDomainSnapshot(
                provider.key(),
                provider.label(),
                AsyncTaskGovernanceStatus.HEALTHY,
                determineRiskLevel(summary),
                null,
                summary
            );
        } catch (Exception ex) {
            return new AsyncTaskGovernanceDomainSnapshot(
                provider.key(),
                provider.label(),
                AsyncTaskGovernanceStatus.DEGRADED,
                AsyncTaskGovernanceRiskLevel.CRITICAL,
                resolveErrorMessage(ex),
                AsyncTaskSummarySnapshot.ofBreakdown(0L, 0L, 0L, 0L, 0L, 0L, 0L)
            );
        }
    }

    private AsyncTaskGovernanceRiskLevel determineRiskLevel(AsyncTaskSummarySnapshot summary) {
        if (summary.timedOutCount() > 0 || summary.expiredCount() > 0) {
            return AsyncTaskGovernanceRiskLevel.HIGH;
        }
        if (summary.activeCount() >= 20 || summary.failureRate() >= 0.35D) {
            return AsyncTaskGovernanceRiskLevel.HIGH;
        }
        if (summary.activeCount() >= 8 || summary.failureCount() > 0 || summary.cancelledCount() >= 5) {
            return AsyncTaskGovernanceRiskLevel.MEDIUM;
        }
        return AsyncTaskGovernanceRiskLevel.LOW;
    }

    private AsyncTaskGovernanceRiskLevel maxRiskLevel(
        AsyncTaskGovernanceRiskLevel left,
        AsyncTaskGovernanceRiskLevel right
    ) {
        return left.ordinal() >= right.ordinal() ? left : right;
    }

    private String resolveErrorMessage(Exception ex) {
        if (ex instanceof ResponseStatusException statusException
            && statusException.getReason() != null
            && !statusException.getReason().isBlank()) {
            return statusException.getReason();
        }
        String message = ex != null ? ex.getMessage() : null;
        if (message == null || message.isBlank()) {
            return ex != null ? ex.getClass().getSimpleName() : "unknown";
        }
        return message;
    }
}
