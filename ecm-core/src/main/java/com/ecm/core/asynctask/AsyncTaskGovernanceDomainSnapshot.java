package com.ecm.core.asynctask;

public record AsyncTaskGovernanceDomainSnapshot(
    String key,
    String label,
    AsyncTaskGovernanceStatus status,
    AsyncTaskGovernanceRiskLevel riskLevel,
    String error,
    AsyncTaskSummarySnapshot summary
) {}
