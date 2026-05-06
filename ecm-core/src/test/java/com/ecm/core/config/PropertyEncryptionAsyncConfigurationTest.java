package com.ecm.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class PropertyEncryptionAsyncConfigurationTest {

    @Test
    @DisplayName("application executor keeps Flowable and default async aliases available")
    void applicationExecutorKeepsFlowableAndDefaultAsyncAliasesAvailable() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(PropertyEncryptionAsyncConfiguration.class)) {
            ThreadPoolTaskExecutor applicationExecutor = assertInstanceOf(
                ThreadPoolTaskExecutor.class,
                context.getBean(PropertyEncryptionAsyncConfiguration.APPLICATION_TASK_EXECUTOR)
            );

            assertSame(applicationExecutor, context.getBean(PropertyEncryptionAsyncConfiguration.TASK_EXECUTOR));
            assertEquals(2, applicationExecutor.getCorePoolSize());
            assertEquals(8, applicationExecutor.getMaxPoolSize());
            assertEquals(100, applicationExecutor.getQueueCapacity());
            assertEquals("athena-async-", applicationExecutor.getThreadNamePrefix());
            assertInstanceOf(
                ThreadPoolExecutor.AbortPolicy.class,
                applicationExecutor.getThreadPoolExecutor().getRejectedExecutionHandler()
            );
        }
    }

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
                ThreadPoolExecutor.AbortPolicy.class,
                executor.getThreadPoolExecutor().getRejectedExecutionHandler()
            );
        } finally {
            executor.shutdown();
        }
    }
}
