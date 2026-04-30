package com.ecm.core.service;

import com.ecm.core.config.PropertyEncryptionAsyncConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PropertyEncryptionBackfillRunnerAsyncProxyTest {

    @Test
    @DisplayName("runner bean is proxied for async execution")
    void runnerBeanIsAsyncProxy() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            assertInstanceOf(
                ThreadPoolTaskExecutor.class,
                context.getBean(PropertyEncryptionAsyncConfiguration.PROPERTY_ENCRYPTION_BACKFILL_TASK_EXECUTOR)
            );

            PropertyEncryptionBackfillRunner runner = context.getBean(PropertyEncryptionBackfillRunner.class);

            assertTrue(AopUtils.isAopProxy(runner));
        }
    }

    @Configuration
    @EnableAsync
    @Import(PropertyEncryptionAsyncConfiguration.class)
    static class TestConfig {

        @Bean
        PropertyEncryptionOperationsService propertyEncryptionOperationsService() {
            return mock(PropertyEncryptionOperationsService.class);
        }

        @Bean
        PropertyEncryptionBackfillRunner propertyEncryptionBackfillRunner(
            PropertyEncryptionOperationsService propertyEncryptionOperationsService
        ) {
            return new PropertyEncryptionBackfillRunner(propertyEncryptionOperationsService);
        }
    }
}
