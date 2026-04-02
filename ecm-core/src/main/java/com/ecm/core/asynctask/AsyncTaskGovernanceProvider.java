package com.ecm.core.asynctask;

public interface AsyncTaskGovernanceProvider {

    int order();

    String key();

    String label();

    AsyncTaskSummarySnapshot getSummary();
}
