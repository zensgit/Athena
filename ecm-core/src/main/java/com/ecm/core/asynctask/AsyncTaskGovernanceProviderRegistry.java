package com.ecm.core.asynctask;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class AsyncTaskGovernanceProviderRegistry {

    private final List<AsyncTaskGovernanceProvider> providers;

    public AsyncTaskGovernanceProviderRegistry(List<AsyncTaskGovernanceProvider> providers) {
        this.providers = providers.stream()
            .sorted(Comparator.comparingInt(AsyncTaskGovernanceProvider::order)
                .thenComparing(AsyncTaskGovernanceProvider::key))
            .toList();
    }

    public List<AsyncTaskGovernanceProvider> getProviders() {
        return providers;
    }
}
