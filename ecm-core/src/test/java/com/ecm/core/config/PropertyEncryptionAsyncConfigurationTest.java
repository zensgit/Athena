package com.ecm.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PropertyEncryptionAsyncConfigurationTest {

    @Test
    @DisplayName("property encryption backfill executor is bounded and named")
    void backfillExecutorIsBoundedAndNamed() {
        PropertyEncryptionAsyncConfiguration configuration = new PropertyEncryptionAsyncConfiguration();
        ThreadPoolTaskExecutor executor = configuration.propertyEncryptionBackfillTaskExecutor();
        executor.initialize();

        try {
            assertEquals(1, executor.getCorePoolSize());
            assertEquals(2, executor.getMaxPoolSize());
            assertEquals(20, executor.getQueueCapacity());
            assertEquals("prop-enc-backfill-", executor.getThreadNamePrefix());
            assertInstanceOf(
                ThreadPoolExecutor.CallerRunsPolicy.class,
                executor.getThreadPoolExecutor().getRejectedExecutionHandler()
            );
        } finally {
            executor.shutdown();
        }
    }
}
