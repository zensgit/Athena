package com.ecm.core.service;

import com.ecm.core.entity.ContentModelDefinition;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.ModelStatus;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.PropertyDataType;
import com.ecm.core.entity.PropertyDefinition;
import com.ecm.core.entity.PropertyEncryptionBackfillJob.BackfillJobStatus;
import com.ecm.core.entity.TypeDefinition;
import com.ecm.core.repository.ContentModelDefinitionRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.PropertyDefinitionRepository;
import com.ecm.core.repository.PropertyEncryptionBackfillJobRepository;
import com.ecm.core.repository.PropertyEncryptionRewrapJobRepository;
import com.ecm.core.repository.TypeDefinitionRepository;
import com.ecm.core.security.secret.SecretCryptoProperties;
import com.ecm.core.security.secret.SecretCryptoService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertyEncryptionBackfillPostgresIntegrationTest {

    @Test
    @DisplayName("PostgreSQL backfill plan and execution migrate plaintext JSONB property into encrypted storage")
    void backfillJobPlansAndMigratesPlaintextPropertyOnPostgres() {
        DockerImageName image = DockerImageName.parse("postgres:15-alpine");
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(image)
            .withDatabaseName("ecm_property_encryption")
            .withUsername("ecm")
            .withPassword("ecm")) {
            try {
                postgres.start();
            } catch (IllegalStateException e) {
                Assumptions.assumeTrue(false, "Docker not available for Testcontainers: " + e.getMessage());
            }

            new ApplicationContextRunner()
                .withUserConfiguration(JpaTestConfig.class)
                .withPropertyValues(
                    "spring.datasource.url=" + postgres.getJdbcUrl(),
                    "spring.datasource.driver-class-name=org.postgresql.Driver",
                    "spring.datasource.username=" + postgres.getUsername(),
                    "spring.datasource.password=" + postgres.getPassword(),
                    "spring.jpa.hibernate.ddl-auto=create-drop",
                    "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
                    "spring.liquibase.enabled=false"
                )
                .run(context -> {
                    ContentModelDefinitionRepository modelRepository =
                        context.getBean(ContentModelDefinitionRepository.class);
                    TypeDefinitionRepository typeRepository = context.getBean(TypeDefinitionRepository.class);
                    PropertyDefinitionRepository propertyRepository =
                        context.getBean(PropertyDefinitionRepository.class);
                    NodeRepository nodeRepository = context.getBean(NodeRepository.class);
                    PropertyEncryptionOperationsService service =
                        context.getBean(PropertyEncryptionOperationsService.class);
                    EntityManager entityManager = context.getBean(EntityManager.class);

                    seedEncryptedPropertyDefinition(modelRepository, typeRepository, propertyRepository);

                    Folder folder = folder(
                        "case-file",
                        objectMapOf("cm:secretCode", "SEC-42"),
                        Map.of()
                    );
                    nodeRepository.saveAndFlush(folder);

                    PropertyEncryptionOperationsService.PropertyEncryptionBackfillDryRunResult dryRun =
                        service.dryRunBackfill(new PropertyEncryptionOperationsService.PropertyEncryptionBackfillDryRunRequest("v1"));
                    assertTrue(dryRun.executable());
                    assertEquals(1L, dryRun.encryptedPropertyDefinitionCount());
                    assertEquals(1L, dryRun.plaintextValueCount());
                    assertEquals(1L, dryRun.readyValueCount());

                    PropertyEncryptionOperationsService.PropertyEncryptionBackfillJobDto planned =
                        service.planBackfillJob(
                            new PropertyEncryptionOperationsService.PropertyEncryptionBackfillJobPlanRequest("v1"),
                            "admin"
                        );
                    assertEquals(BackfillJobStatus.PLANNED, planned.status());
                    assertEquals(1L, planned.readyValueCount());

                    PropertyEncryptionOperationsService.PropertyEncryptionBackfillJobDto completed =
                        service.runBackfillJob(planned.id(), 25, "admin");
                    assertEquals(BackfillJobStatus.SUCCEEDED, completed.status());
                    assertEquals(1L, completed.processedValueCount());
                    assertEquals(1L, completed.migratedValueCount());
                    assertEquals(0L, completed.failedValueCount());
                    assertNotNull(completed.startedAt());
                    assertNotNull(completed.finishedAt());

                    entityManager.clear();
                    Node migrated = nodeRepository.findById(folder.getId()).orElseThrow();
                    assertFalse(migrated.getProperties().containsKey("cm:secretCode"));
                    String encryptedValue = migrated.getEncryptedProperties().get("cm:secretCode");
                    assertNotNull(encryptedValue);
                    assertTrue(encryptedValue.startsWith("enc:v1:"));
                    assertEquals("\"SEC-42\"", context.getBean(SecretCryptoService.class).reveal(encryptedValue));
                    assertEquals("admin", migrated.getLastModifiedBy());
                });
        }
    }

    private static void seedEncryptedPropertyDefinition(
        ContentModelDefinitionRepository modelRepository,
        TypeDefinitionRepository typeRepository,
        PropertyDefinitionRepository propertyRepository
    ) {
        ContentModelDefinition model = new ContentModelDefinition();
        model.setNamespaceUri("http://example.test/model/content/1.0");
        model.setPrefix("cm");
        model.setName("content");
        model.setStatus(ModelStatus.ACTIVE);
        model = modelRepository.saveAndFlush(model);

        TypeDefinition type = new TypeDefinition();
        type.setModel(model);
        type.setName("folder");
        type.setTitle("Folder");
        type = typeRepository.saveAndFlush(type);

        PropertyDefinition property = new PropertyDefinition();
        property.setTypeDefinition(type);
        property.setName("secretCode");
        property.setTitle("Secret code");
        property.setDataType(PropertyDataType.TEXT);
        property.setEncrypted(true);
        propertyRepository.saveAndFlush(property);
    }

    private static Folder folder(
        String name,
        Map<String, Object> properties,
        Map<String, String> encryptedProperties
    ) {
        LocalDateTime now = LocalDateTime.now();
        Folder folder = new Folder();
        folder.setName(name);
        folder.setTypeQName("cm:folder");
        folder.setCreatedBy("test");
        folder.setCreatedDate(now);
        folder.setLastModifiedDate(now);
        folder.setProperties(properties);
        folder.setEncryptedProperties(encryptedProperties);
        folder.setDeleted(false);
        return folder;
    }

    private static Map<String, Object> objectMapOf(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaAuditing
    @EnableTransactionManagement
    @EntityScan(basePackages = {"com.ecm.core.entity", "com.ecm.core.model"})
    @EnableJpaRepositories(
        basePackageClasses = {
            NodeRepository.class,
            ContentModelDefinitionRepository.class,
            TypeDefinitionRepository.class,
            PropertyDefinitionRepository.class,
            PropertyEncryptionBackfillJobRepository.class,
            PropertyEncryptionRewrapJobRepository.class
        },
        includeFilters = @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = {
                NodeRepository.class,
                ContentModelDefinitionRepository.class,
                TypeDefinitionRepository.class,
                PropertyDefinitionRepository.class,
                PropertyEncryptionBackfillJobRepository.class,
                PropertyEncryptionRewrapJobRepository.class
            }
        )
    )
    static class JpaTestConfig {
        @Bean
        org.springframework.data.domain.AuditorAware<String> auditorAware() {
            return () -> Optional.of("test");
        }

        @Bean
        SecretCryptoProperties secretCryptoProperties() {
            SecretCryptoProperties properties = new SecretCryptoProperties();
            properties.setEnabled(true);
            properties.setActiveKeyVersion("v1");
            properties.setKeys(Map.of("v1", "MDEyMzQ1Njc4OWFiY2RlZg=="));
            return properties;
        }

        @Bean
        SecretCryptoService secretCryptoService(SecretCryptoProperties properties) {
            return new SecretCryptoService(properties);
        }

        @Bean
        PropertyEncryptionOperationsService propertyEncryptionOperationsService(
            PropertyDefinitionRepository propertyDefinitionRepository,
            NodeRepository nodeRepository,
            PropertyEncryptionBackfillJobRepository backfillJobRepository,
            PropertyEncryptionRewrapJobRepository rewrapJobRepository,
            SecretCryptoService secretCryptoService,
            SecretCryptoProperties secretCryptoProperties
        ) {
            return new PropertyEncryptionOperationsService(
                propertyDefinitionRepository,
                nodeRepository,
                backfillJobRepository,
                rewrapJobRepository,
                secretCryptoService,
                secretCryptoProperties
            );
        }
    }
}
