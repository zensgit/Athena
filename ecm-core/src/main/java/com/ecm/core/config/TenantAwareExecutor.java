package com.ecm.core.config;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Captures TenantContext at submit time and restores it for worker-thread execution.
 */
public final class TenantAwareExecutor implements Executor {

    private final Executor delegate;

    public TenantAwareExecutor(Executor delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command, "command");
        TenantContext.Snapshot snapshot = TenantContext.capture();
        delegate.execute(() -> {
            TenantContext.restore(snapshot);
            try {
                command.run();
            } finally {
                TenantContext.clear();
            }
        });
    }
}
