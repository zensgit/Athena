package com.ecm.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableJpaRepositories
@EnableJpaAuditing
@EnableElasticsearchRepositories
@EnableTransactionManagement
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableAspectJAutoProxy
public class EcmCoreApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EcmCoreApplication.class, args);
    }
}