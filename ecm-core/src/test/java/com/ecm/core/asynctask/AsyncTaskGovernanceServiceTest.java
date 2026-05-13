package com.ecm.core.asynctask;

import com.ecm.core.service.BatchDownloadAsyncTaskRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AsyncTaskGovernanceServiceTest {

    @Test
    @DisplayName("Overview aggregates provider summaries in registry order and normalizes batch cancel requested work")
    void buildOverviewAggregatesProviderSummaries() {
        AsyncTaskGovernanceProviderRegistry registry = new AsyncTaskGovernanceProviderRegistry(List.of(
            new SimpleAsyncTaskGovernanceProvider(
                30,
                "preview",
                "Preview",
                () -> AsyncTaskSummarySnapshot.ofBreakdown(0, 1, 1, 0, 0, 1, 0)
            ),
            new SimpleAsyncTaskGovernanceProvider(
                10,
                "audit",
                "Audit",
                () -> AsyncTaskSummarySnapshot.ofBreakdown(1, 0, 1, 0, 0, 0, 0)
            ),
            new SimpleAsyncTaskGovernanceProvider(
                20,
                "batchDownload",
                "Batch Download",
                () -> AsyncTaskSummaryAdapters.fromBatchDownload(
                    new BatchDownloadAsyncTaskRegistry.BatchDownloadAsyncSummary(
                        4,
                        3,
                        1,
                        1,
                        1,
                        1,
                        1,
                        0,
                        0
                    )
                )
            ),
            new SimpleAsyncTaskGovernanceProvider(
                60,
                "propertyEncryption",
                "Property Encryption",
                () -> AsyncTaskSummarySnapshot.ofBreakdown(2, 0, 0, 0, 1, 0, 0)
            )
        ));
        AsyncTaskGovernanceService service = new AsyncTaskGovernanceService(registry);

        AsyncTaskGovernanceOverviewSnapshot overview = service.buildOverview();

        assertEquals(AsyncTaskGovernanceStatus.HEALTHY, overview.overallStatus());
        assertEquals(AsyncTaskGovernanceRiskLevel.HIGH, overview.overallRiskLevel());
        assertEquals(4, overview.totalDomains());
        assertEquals(0, overview.degradedDomainCount());
        assertEquals(List.of("audit", "batchDownload", "preview", "propertyEncryption"), overview.domains().stream()
            .map(AsyncTaskGovernanceDomainSnapshot::key)
            .toList());

        AsyncTaskSummarySnapshot summary = overview.summary();
        assertEquals(12L, summary.totalCount());
        assertEquals(7L, summary.activeCount());
        assertEquals(5L, summary.terminalCount());
        assertEquals(4L, summary.queuedCount());
        assertEquals(3L, summary.runningCount());
        assertEquals(2L, summary.completedCount());
        assertEquals(1L, summary.cancelledCount());
        assertEquals(1L, summary.failedCount());
        assertEquals(1L, summary.timedOutCount());
        assertEquals(0L, summary.expiredCount());

        AsyncTaskGovernanceDomainSnapshot batchDomain = overview.domains().get(1);
        assertEquals(AsyncTaskGovernanceRiskLevel.LOW, batchDomain.riskLevel());
        assertNull(batchDomain.error());
        assertEquals(3L, batchDomain.summary().activeCount());
        assertEquals(2L, batchDomain.summary().runningCount());

        AsyncTaskGovernanceDomainSnapshot propertyEncryptionDomain = overview.domains().get(3);
        assertEquals(AsyncTaskGovernanceRiskLevel.MEDIUM, propertyEncryptionDomain.riskLevel());
        assertEquals(2L, propertyEncryptionDomain.summary().queuedCount());
        assertEquals(1L, propertyEncryptionDomain.summary().failedCount());
    }

    @Test
    @DisplayName("Overview degrades only the failing provider and escalates overall risk")
    void buildOverviewMarksFailingProvidersAsDegraded() {
        AsyncTaskGovernanceProviderRegistry registry = new AsyncTaskGovernanceProviderRegistry(List.of(
            new SimpleAsyncTaskGovernanceProvider(
                10,
                "audit",
                "Audit",
                () -> AsyncTaskSummarySnapshot.ofBreakdown(0, 0, 1, 0, 0, 0, 0)
            ),
            new SimpleAsyncTaskGovernanceProvider(
                20,
                "preview",
                "Preview",
                () -> {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "preview summary unavailable");
                }
            )
        ));
        AsyncTaskGovernanceService service = new AsyncTaskGovernanceService(registry);

        AsyncTaskGovernanceOverviewSnapshot overview = service.buildOverview();

        assertEquals(AsyncTaskGovernanceStatus.DEGRADED, overview.overallStatus());
        assertEquals(AsyncTaskGovernanceRiskLevel.CRITICAL, overview.overallRiskLevel());
        assertEquals(2, overview.totalDomains());
        assertEquals(1, overview.degradedDomainCount());
        assertEquals(1L, overview.summary().totalCount());
        assertEquals(1L, overview.summary().completedCount());

        AsyncTaskGovernanceDomainSnapshot degradedDomain = overview.domains().get(1);
        assertEquals("preview", degradedDomain.key());
        assertEquals(AsyncTaskGovernanceStatus.DEGRADED, degradedDomain.status());
        assertEquals(AsyncTaskGovernanceRiskLevel.CRITICAL, degradedDomain.riskLevel());
        assertEquals("preview summary unavailable", degradedDomain.error());
        assertEquals(0L, degradedDomain.summary().totalCount());
    }
}
