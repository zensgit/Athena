package com.ecm.core.repository;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
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
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeRepositoryJsonbBackfillSmokeTest {

    @Test
    @DisplayName("PostgreSQL JSONB predicates count backfill-ready encrypted properties")
    void jsonbBackfillPredicatesMatchPostgresSemantics() {
        DockerImageName image = DockerImageName.parse("postgres:15-alpine");
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(image)
            .withDatabaseName("ecm_jsonb")
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
                    NodeRepository repository = context.getBean(NodeRepository.class);

                    Folder plainOnly = folder("plain-only", objectMapOf("cm:title", "plain"), Map.of(), false);
                    Folder plainNullEncrypted = folder("plain-null-encrypted", objectMapOf("cm:title", "plain-null"), null, false);
                    Folder encryptedOnly = folder("encrypted-only", Map.of(), stringMapOf("cm:title", "enc:v1:cipher"), false);
                    Folder dualStorage = folder("dual-storage", objectMapOf("cm:title", "dual"), stringMapOf("cm:title", "enc:v1:dual"), false);
                    Folder orphanEncrypted = folder("orphan-encrypted", Map.of(), stringMapOf("custom:orphan", "enc:v1:orphan"), false);
                    Folder deletedIgnored = folder("deleted-ignored", objectMapOf("cm:title", "deleted"), stringMapOf("cm:title", "enc:v1:deleted"), true);
                    Folder typedNumber = folder("typed-number", objectMapOf("cm:typed", 42), Map.of(), false);
                    Folder typedBoolean = folder("typed-boolean", objectMapOf("cm:typed", true), Map.of(), false);
                    Folder typedObject = folder("typed-object", objectMapOf("cm:typed", objectMapOf("nested", "value")), Map.of(), false);
                    Folder typedArray = folder("typed-array", objectMapOf("cm:typed", new ArrayList<>(List.of("one", "two"))), Map.of(), false);
                    repository.saveAll(List.of(
                        plainOnly,
                        plainNullEncrypted,
                        encryptedOnly,
                        dualStorage,
                        orphanEncrypted,
                        deletedIgnored,
                        typedNumber,
                        typedBoolean,
                        typedObject,
                        typedArray
                    ));
                    repository.flush();

                    assertEquals(3L, repository.countByPropertyKeyAndDeletedFalse("cm:title"));
                    assertEquals(2L, repository.countByEncryptedPropertyKeyAndDeletedFalse("cm:title"));
                    assertEquals(1L, repository.countByPropertyKeyInBothStorageAndDeletedFalse("cm:title"));
                    assertEquals(2L, repository.countBackfillReadyByPropertyKeyAndDeletedFalse("cm:title"));
                    assertEquals(4L, repository.countByPropertyKeyAcrossStorageAndDeletedFalse("cm:title"));

                    assertEquals(0L, repository.countByPropertyKeyAndDeletedFalse("custom:orphan"));
                    assertEquals(1L, repository.countByEncryptedPropertyKeyAndDeletedFalse("custom:orphan"));
                    assertEquals(0L, repository.countBackfillReadyByPropertyKeyAndDeletedFalse("custom:orphan"));

                    List<NodeRepository.PropertyBackfillCandidateRow> candidates =
                        repository.findBackfillCandidatesByPropertyKeyAndDeletedFalse("cm:title", 10);
                    assertEquals(2, candidates.size());
                    assertEquals(Set.of("\"plain\"", "\"plain-null\""), candidates.stream()
                        .map(NodeRepository.PropertyBackfillCandidateRow::getPlaintextJson)
                        .collect(Collectors.toSet()));
                    assertEquals(Set.of("cm:title"), candidates.stream()
                        .map(NodeRepository.PropertyBackfillCandidateRow::getPropertyKey)
                        .collect(Collectors.toSet()));
                    assertTrue(candidates.stream().allMatch(row -> row.getEntityVersion() != null));

                    assertEquals(1, repository.findBackfillCandidatesByPropertyKeyAndDeletedFalse("cm:title", 1).size());

                    List<NodeRepository.PropertyBackfillCandidateRow> typedCandidates =
                        repository.findBackfillCandidatesByPropertyKeyAndDeletedFalse("cm:typed", 10);
                    assertEquals(Set.of("42", "true", "{\"nested\":\"value\"}", "[\"one\",\"two\"]"), typedCandidates.stream()
                        .map(NodeRepository.PropertyBackfillCandidateRow::getPlaintextJson)
                        .map(NodeRepositoryJsonbBackfillSmokeTest::normalizeJsonText)
                        .collect(Collectors.toSet()));

                    NodeRepository.PropertyBackfillCandidateRow plainOnlyCandidate = candidates.stream()
                        .filter(candidate -> "\"plain\"".equals(candidate.getPlaintextJson()))
                        .findFirst()
                        .orElseThrow();
                    LocalDateTime modifiedAt = LocalDateTime.now();
                    assertEquals(1, repository.backfillEncryptedPropertyIfUnchanged(
                        plainOnlyCandidate.getNodeId(),
                        plainOnlyCandidate.getPropertyKey(),
                        plainOnlyCandidate.getPlaintextJson(),
                        plainOnlyCandidate.getEntityVersion(),
                        "enc:v2:plain",
                        modifiedAt,
                        "backfill-test"
                    ));
                    Node migrated = repository.findById(plainOnlyCandidate.getNodeId()).orElseThrow();
                    assertFalse(migrated.getProperties().containsKey("cm:title"));
                    assertEquals("enc:v2:plain", migrated.getEncryptedProperties().get("cm:title"));
                    assertEquals(plainOnlyCandidate.getEntityVersion() + 1, migrated.getEntityVersion());
                    assertEquals("backfill-test", migrated.getLastModifiedBy());

                    NodeRepository.PropertyBackfillCandidateRow plainNullCandidate = candidates.stream()
                        .filter(candidate -> "\"plain-null\"".equals(candidate.getPlaintextJson()))
                        .findFirst()
                        .orElseThrow();
                    assertEquals(0, repository.backfillEncryptedPropertyIfUnchanged(
                        plainNullCandidate.getNodeId(),
                        plainNullCandidate.getPropertyKey(),
                        plainNullCandidate.getPlaintextJson(),
                        plainNullCandidate.getEntityVersion() + 1,
                        "enc:v2:stale",
                        modifiedAt,
                        "backfill-test"
                    ));
                    assertEquals(0, repository.backfillEncryptedPropertyIfUnchanged(
                        dualStorage.getId(),
                        "cm:title",
                        "\"dual\"",
                        dualStorage.getEntityVersion(),
                        "enc:v2:dual",
                        modifiedAt,
                        "backfill-test"
                    ));
                    assertEquals(0, repository.backfillEncryptedPropertyIfUnchanged(
                        encryptedOnly.getId(),
                        "cm:title",
                        "\"missing\"",
                        encryptedOnly.getEntityVersion(),
                        "enc:v2:missing",
                        modifiedAt,
                        "backfill-test"
                    ));
                    assertEquals(0, repository.backfillEncryptedPropertyIfUnchanged(
                        deletedIgnored.getId(),
                        "cm:title",
                        "\"deleted\"",
                        deletedIgnored.getEntityVersion(),
                        "enc:v2:deleted",
                        modifiedAt,
                        "backfill-test"
                    ));

                    assertEquals(4L, repository.countNodesWithEncryptedPropertiesAndDeletedFalse());
                    assertEquals(4L, repository.countEncryptedPropertyValuesAndDeletedFalse());

                    List<Object[]> keyVersionCounts = repository.countEncryptedPropertyValuesByKeyVersionAndDeletedFalse();
                    assertEquals(Map.of("v1", 3L, "v2", 1L), keyVersionCounts.stream()
                        .collect(Collectors.toMap(row -> row[0].toString(), row -> ((Number) row[1]).longValue())));
                });
        }
    }

    private static Folder folder(
        String name,
        Map<String, Object> properties,
        Map<String, String> encryptedProperties,
        boolean deleted
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
        folder.setDeleted(deleted);
        if (deleted) {
            folder.setStatus(Node.NodeStatus.DELETED);
            folder.setDeletedAt(now);
            folder.setDeletedBy("test");
        }
        return folder;
    }

    private static Map<String, Object> objectMapOf(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    private static Map<String, String> stringMapOf(String key, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    private static String normalizeJsonText(String value) {
        return value == null ? null : value.replace(": ", ":").replace(", ", ",");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableJpaAuditing
    @EntityScan(basePackages = {"com.ecm.core.entity", "com.ecm.core.model"})
    @EnableJpaRepositories(
        basePackageClasses = NodeRepository.class,
        includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = NodeRepository.class)
    )
    static class JpaTestConfig {
        @Bean
        AuditorAware<String> auditorAware() {
            return () -> Optional.of("test");
        }
    }
}
