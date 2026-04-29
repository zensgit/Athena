package com.ecm.core.repository;

import com.ecm.core.entity.PropertyEncryptionBackfillJob;
import com.ecm.core.entity.PropertyEncryptionBackfillJob.BackfillJobStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.test.context.ContextConfiguration;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.liquibase.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:propertyencryptionbackfilljob;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = PropertyEncryptionBackfillJobRepositoryTest.JpaTestConfig.class)
class PropertyEncryptionBackfillJobRepositoryTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaRepositories(
        basePackageClasses = PropertyEncryptionBackfillJobRepository.class,
        includeFilters = @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = PropertyEncryptionBackfillJobRepository.class
        )
    )
    static class JpaTestConfig {
        @Bean
        PersistenceManagedTypes persistenceManagedTypes() {
            return PersistenceManagedTypes.of(PropertyEncryptionBackfillJob.class.getName());
        }
    }

    @Autowired
    private PropertyEncryptionBackfillJobRepository repository;

    @Test
    @DisplayName("claim and terminal update require expected job status")
    void claimAndTerminalUpdateRequireExpectedStatus() {
        LocalDateTime now = LocalDateTime.now();
        PropertyEncryptionBackfillJob job = new PropertyEncryptionBackfillJob();
        job.setStatus(BackfillJobStatus.PLANNED);
        job.setTargetKeyVersion("v1");
        job.setRequestedBy("admin");
        job.setRequestedAt(now);
        job.setCreatedAt(now);
        job.setWarnings(List.of());
        job.setDefinitionCounts(List.of());

        PropertyEncryptionBackfillJob saved = repository.saveAndFlush(job);
        LocalDateTime startedAt = now.plusMinutes(1);

        assertEquals(1, repository.claimPlannedJob(saved.getId(), startedAt));
        assertEquals(0, repository.claimPlannedJob(saved.getId(), startedAt.plusMinutes(1)));

        PropertyEncryptionBackfillJob running = repository.findById(saved.getId()).orElseThrow();
        assertEquals(BackfillJobStatus.RUNNING, running.getStatus());
        assertEquals(startedAt, running.getStartedAt());

        LocalDateTime finishedAt = now.plusMinutes(2);
        assertEquals(1, repository.markTerminalIfRunning(
            saved.getId(),
            BackfillJobStatus.SUCCEEDED,
            finishedAt,
            2L,
            1L,
            1L,
            0L,
            null
        ));
        assertEquals(0, repository.markTerminalIfRunning(
            saved.getId(),
            BackfillJobStatus.FAILED,
            finishedAt.plusMinutes(1),
            3L,
            1L,
            1L,
            1L,
            "late failure"
        ));

        PropertyEncryptionBackfillJob terminal = repository.findById(saved.getId()).orElseThrow();
        assertEquals(BackfillJobStatus.SUCCEEDED, terminal.getStatus());
        assertEquals(2L, terminal.getProcessedValueCount());
        assertEquals(1L, terminal.getMigratedValueCount());
        assertEquals(1L, terminal.getSkippedValueCount());
        assertEquals(0L, terminal.getFailedValueCount());
        assertEquals(2L, terminal.getVersion());
    }
}
