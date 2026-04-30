package com.ecm.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class PropertyEncryptionAsyncConfiguration {

    public static final String PROPERTY_ENCRYPTION_BACKFILL_TASK_EXECUTOR =
        "propertyEncryptionBackfillTaskExecutor";

    @Bean(name = PROPERTY_ENCRYPTION_BACKFILL_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor propertyEncryptionBackfillTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("prop-enc-backfill-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return executor;
    }
}
