package com.ecm.core.asynctask;

import java.time.LocalDateTime;
import java.util.List;

public record AsyncTaskGovernanceOverviewSnapshot(
    LocalDateTime generatedAt,
    AsyncTaskGovernanceStatus overallStatus,
    AsyncTaskGovernanceRiskLevel overallRiskLevel,
    int totalDomains,
    int degradedDomainCount,
    AsyncTaskSummarySnapshot summary,
    List<AsyncTaskGovernanceDomainSnapshot> domains
) {}
