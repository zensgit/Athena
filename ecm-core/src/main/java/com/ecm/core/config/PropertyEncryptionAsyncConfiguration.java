package com.ecm.core.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class PropertyEncryptionAsyncConfiguration {

    public static final String PROPERTY_ENCRYPTION_BACKFILL_TASK_EXECUTOR =
        "propertyEncryptionBackfillTaskExecutor";
    public static final String PROPERTY_ENCRYPTION_REWRAP_TASK_EXECUTOR =
        "propertyEncryptionRewrapTaskExecutor";
    public static final String APPLICATION_TASK_EXECUTOR = "applicationTaskExecutor";
    public static final String TASK_EXECUTOR = "taskExecutor";

    @Bean(name = {APPLICATION_TASK_EXECUTOR, TASK_EXECUTOR})
    @Primary
    @ConditionalOnMissingBean(name = {APPLICATION_TASK_EXECUTOR, TASK_EXECUTOR})
    public ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("athena-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    @Bean(name = PROPERTY_ENCRYPTION_BACKFILL_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor propertyEncryptionBackfillTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("prop-enc-backfill-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }

    @Bean(name = PROPERTY_ENCRYPTION_REWRAP_TASK_EXECUTOR)
    public ThreadPoolTaskExecutor propertyEncryptionRewrapTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("prop-enc-rewrap-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        return executor;
    }
}
