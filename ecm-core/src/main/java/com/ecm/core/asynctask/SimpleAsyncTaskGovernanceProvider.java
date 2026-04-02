package com.ecm.core.asynctask;

import java.util.Objects;
import java.util.function.Supplier;

public final class SimpleAsyncTaskGovernanceProvider implements AsyncTaskGovernanceProvider {

    private final int order;
    private final String key;
    private final String label;
    private final Supplier<AsyncTaskSummarySnapshot> summarySupplier;

    public SimpleAsyncTaskGovernanceProvider(
        int order,
        String key,
        String label,
        Supplier<AsyncTaskSummarySnapshot> summarySupplier
    ) {
        this.order = order;
        this.key = Objects.requireNonNull(key, "key must not be null");
        this.label = Objects.requireNonNull(label, "label must not be null");
        this.summarySupplier = Objects.requireNonNull(summarySupplier, "summarySupplier must not be null");
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public AsyncTaskSummarySnapshot getSummary() {
        AsyncTaskSummarySnapshot summary = summarySupplier.get();
        if (summary == null) {
            throw new IllegalStateException("async task summary is empty");
        }
        return summary;
    }
}
