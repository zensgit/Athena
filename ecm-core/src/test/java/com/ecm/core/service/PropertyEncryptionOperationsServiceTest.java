package com.ecm.core.service;

import com.ecm.core.entity.AspectDefinition;
import com.ecm.core.entity.ContentModelDefinition;
import com.ecm.core.entity.PropertyEncryptionBackfillJob;
import com.ecm.core.entity.PropertyEncryptionBackfillJob.BackfillJobStatus;
import com.ecm.core.entity.PropertyDataType;
import com.ecm.core.entity.PropertyDefinition;
import com.ecm.core.entity.TypeDefinition;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.PropertyEncryptionBackfillJobRepository;
import com.ecm.core.repository.PropertyDefinitionRepository;
import com.ecm.core.security.secret.SecretCryptoProperties;
import com.ecm.core.security.secret.SecretCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyEncryptionOperationsServiceTest {

    @Mock private PropertyDefinitionRepository propertyDefinitionRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private PropertyEncryptionBackfillJobRepository backfillJobRepository;
    @Mock private SecretCryptoService secretCryptoService;

    private SecretCryptoProperties secretCryptoProperties;
    private PropertyEncryptionOperationsService service;

    @BeforeEach
    void setUp() {
        secretCryptoProperties = new SecretCryptoProperties();
        service = new PropertyEncryptionOperationsService(
            propertyDefinitionRepository,
            nodeRepository,
            backfillJobRepository,
            secretCryptoService,
            secretCryptoProperties
        );
    }

    @Test
    @DisplayName("status reports encrypted definition and storage counts without key material")
    void statusReportsCountsAndWarningsWithoutKeyMaterial() {
        secretCryptoProperties.setActiveKeyVersion("v2");
        secretCryptoProperties.setKeys(new LinkedHashMap<>(Map.of("v1", "base64-secret-not-exposed")));
        when(secretCryptoService.isEnabled()).thenReturn(false);
        when(propertyDefinitionRepository.findByEncryptedTrue()).thenReturn(List.of(
            typeProperty("secretCode"),
            aspectProperty("caseNumber")
        ));
        when(nodeRepository.countNodesWithEncryptedPropertiesAndDeletedFalse()).thenReturn(3L);
        when(nodeRepository.countEncryptedPropertyValuesAndDeletedFalse()).thenReturn(5L);

        PropertyEncryptionOperationsService.PropertyEncryptionStatus status = service.getStatus();

        assertFalse(status.secretCryptoEnabled());
        assertEquals("v2", status.activeKeyVersion());
        assertFalse(status.activeKeyConfigured());
        assertEquals(List.of("v1"), status.configuredKeyVersions());
        assertEquals(2L, status.encryptedPropertyDefinitionCount());
        assertEquals(1L, status.encryptedTypePropertyDefinitionCount());
        assertEquals(1L, status.encryptedAspectPropertyDefinitionCount());
        assertEquals(3L, status.nodesWithEncryptedPropertiesCount());
        assertEquals(5L, status.encryptedPropertyValueCount());
        assertTrue(status.warnings().contains("encrypted_property_definitions_require_secret_crypto"));
        assertTrue(status.warnings().contains("encrypted_node_payloads_require_secret_crypto"));
        assertFalse(status.configuredKeyVersions().contains("base64-secret-not-exposed"));
    }

    @Test
    @DisplayName("status warns when secret crypto is enabled but active key version is missing")
    void statusWarnsWhenActiveKeyVersionIsMissing() {
        secretCryptoProperties.setActiveKeyVersion("v2");
        secretCryptoProperties.setKeys(new LinkedHashMap<>(Map.of("v1", "base64-secret-not-exposed")));
        when(secretCryptoService.isEnabled()).thenReturn(true);
        when(propertyDefinitionRepository.findByEncryptedTrue()).thenReturn(List.of());
        when(nodeRepository.countNodesWithEncryptedPropertiesAndDeletedFalse()).thenReturn(0L);
        when(nodeRepository.countEncryptedPropertyValuesAndDeletedFalse()).thenReturn(0L);

        PropertyEncryptionOperationsService.PropertyEncryptionStatus status = service.getStatus();

        assertTrue(status.secretCryptoEnabled());
        assertFalse(status.activeKeyConfigured());
        assertEquals(List.of("active_secret_key_version_is_not_configured"), status.warnings());
    }

    @Test
    @DisplayName("encrypted definitions are mapped with owner kind and sorted by qualified name")
    void encryptedDefinitionsAreMappedAndSorted() {
        PropertyDefinition aspectProperty = aspectProperty("caseNumber");
        PropertyDefinition typeProperty = typeProperty("secretCode");
        when(propertyDefinitionRepository.findByEncryptedTrue()).thenReturn(List.of(typeProperty, aspectProperty));

        List<PropertyEncryptionOperationsService.EncryptedPropertyDefinitionSummary> summaries =
            service.listEncryptedDefinitions();

        assertEquals(List.of("acme:caseNumber", "acme:secretCode"), summaries.stream()
            .map(PropertyEncryptionOperationsService.EncryptedPropertyDefinitionSummary::qualifiedName)
            .toList());
        assertEquals("ASPECT", summaries.get(0).ownerKind());
        assertEquals("acme:secure", summaries.get(0).ownerQName());
        assertEquals("TYPE", summaries.get(1).ownerKind());
        assertEquals("acme:contract", summaries.get(1).ownerQName());
        assertEquals(PropertyDataType.TEXT, summaries.get(1).dataType());
    }

    @Test
    @DisplayName("rewrap dry-run uses active key by default and reports values requiring rewrap")
    void rewrapDryRunUsesActiveKeyAndReportsImpact() {
        secretCryptoProperties.setActiveKeyVersion("v2");
        secretCryptoProperties.setKeys(new LinkedHashMap<>(Map.of(
            "v1", "base64-secret-not-exposed",
            "v2", "another-base64-secret-not-exposed"
        )));
        when(secretCryptoService.isEnabled()).thenReturn(true);
        when(nodeRepository.countNodesWithEncryptedPropertiesAndDeletedFalse()).thenReturn(4L);
        when(nodeRepository.countEncryptedPropertyValuesAndDeletedFalse()).thenReturn(7L);
        when(nodeRepository.countEncryptedPropertyValuesByKeyVersionAndDeletedFalse()).thenReturn(List.of(
            new Object[] {"v1", 5L},
            new Object[] {"v2", 2L}
        ));

        PropertyEncryptionOperationsService.PropertyEncryptionRewrapDryRunResult result =
            service.dryRunRewrap(null);

        assertEquals("v2", result.targetKeyVersion());
        assertTrue(result.targetKeyConfigured());
        assertTrue(result.secretCryptoEnabled());
        assertEquals(4L, result.candidateNodeCount());
        assertEquals(7L, result.encryptedPropertyValueCount());
        assertEquals(2L, result.valuesAlreadyOnTargetKeyCount());
        assertEquals(5L, result.valuesRequiringRewrapCount());
        assertEquals(0L, result.unversionedOrMalformedValueCount());
        assertEquals(List.of("v1", "v2"), result.keyVersionCounts().stream()
            .map(PropertyEncryptionOperationsService.KeyVersionValueCount::keyVersion)
            .toList());
        assertEquals(List.of(), result.missingSourceKeyVersions());
        assertEquals(List.of(), result.warnings());
        assertTrue(result.executable());
    }

    @Test
    @DisplayName("rewrap dry-run blocks execution when target key is missing, source key is missing, or payloads are malformed")
    void rewrapDryRunWarnsForMissingTargetMissingSourceAndMalformedPayloads() {
        secretCryptoProperties.setActiveKeyVersion("v1");
        secretCryptoProperties.setKeys(new LinkedHashMap<>(Map.of("v1", "base64-secret-not-exposed")));
        when(secretCryptoService.isEnabled()).thenReturn(false);
        when(nodeRepository.countNodesWithEncryptedPropertiesAndDeletedFalse()).thenReturn(3L);
        when(nodeRepository.countEncryptedPropertyValuesAndDeletedFalse()).thenReturn(5L);
        when(nodeRepository.countEncryptedPropertyValuesByKeyVersionAndDeletedFalse()).thenReturn(List.<Object[]>of(
            new Object[] {"v1", 2L},
            new Object[] {"v9", 1L}
        ));

        PropertyEncryptionOperationsService.PropertyEncryptionRewrapDryRunResult result =
            service.dryRunRewrap(new PropertyEncryptionOperationsService.PropertyEncryptionRewrapDryRunRequest("v3"));

        assertEquals("v3", result.targetKeyVersion());
        assertFalse(result.targetKeyConfigured());
        assertFalse(result.secretCryptoEnabled());
        assertEquals(0L, result.valuesAlreadyOnTargetKeyCount());
        assertEquals(5L, result.valuesRequiringRewrapCount());
        assertEquals(2L, result.unversionedOrMalformedValueCount());
        assertEquals(List.of("v9"), result.missingSourceKeyVersions());
        assertTrue(result.warnings().contains("secret_crypto_disabled"));
        assertTrue(result.warnings().contains("target_key_version_not_configured"));
        assertTrue(result.warnings().contains("encrypted_payloads_without_key_version"));
        assertTrue(result.warnings().contains("source_key_versions_not_configured"));
        assertFalse(result.executable());
    }

    @Test
    @DisplayName("backfill dry-run reports plaintext encrypted-property values ready for backfill")
    void backfillDryRunReportsReadyPlaintextValues() {
        secretCryptoProperties.setActiveKeyVersion("v2");
        secretCryptoProperties.setKeys(new LinkedHashMap<>(Map.of(
            "v1", "base64-secret-not-exposed",
            "v2", "another-base64-secret-not-exposed"
        )));
        when(secretCryptoService.isEnabled()).thenReturn(true);
        when(propertyDefinitionRepository.findByEncryptedTrue()).thenReturn(List.of(
            typeProperty("secretCode"),
            aspectProperty("caseNumber")
        ));
        when(nodeRepository.countByPropertyKeyAndDeletedFalse("acme:caseNumber")).thenReturn(2L);
        when(nodeRepository.countByEncryptedPropertyKeyAndDeletedFalse("acme:caseNumber")).thenReturn(1L);
        when(nodeRepository.countByPropertyKeyInBothStorageAndDeletedFalse("acme:caseNumber")).thenReturn(0L);
        when(nodeRepository.countBackfillReadyByPropertyKeyAndDeletedFalse("acme:caseNumber")).thenReturn(2L);
        when(nodeRepository.countByPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(4L);
        when(nodeRepository.countByEncryptedPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(2L);
        when(nodeRepository.countByPropertyKeyInBothStorageAndDeletedFalse("acme:secretCode")).thenReturn(0L);
        when(nodeRepository.countBackfillReadyByPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(4L);
        when(nodeRepository.countEncryptedPropertyValuesAndDeletedFalse()).thenReturn(3L);

        PropertyEncryptionOperationsService.PropertyEncryptionBackfillDryRunResult result =
            service.dryRunBackfill(null);

        assertEquals("v2", result.targetKeyVersion());
        assertTrue(result.targetKeyConfigured());
        assertTrue(result.secretCryptoEnabled());
        assertEquals(2L, result.encryptedPropertyDefinitionCount());
        assertEquals(6L, result.plaintextValueCount());
        assertEquals(3L, result.alreadyEncryptedValueCount());
        assertEquals(0L, result.dualStorageConflictValueCount());
        assertEquals(6L, result.readyValueCount());
        assertEquals(0L, result.orphanEncryptedValueCount());
        assertEquals(List.of("acme:caseNumber", "acme:secretCode"), result.definitionCounts().stream()
            .map(PropertyEncryptionOperationsService.PropertyBackfillCount::qualifiedName)
            .toList());
        assertEquals(List.of(), result.warnings());
        assertTrue(result.executable());
    }

    @Test
    @DisplayName("backfill dry-run blocks execution when crypto or storage state is unsafe")
    void backfillDryRunBlocksUnsafeState() {
        secretCryptoProperties.setActiveKeyVersion("v1");
        secretCryptoProperties.setKeys(new LinkedHashMap<>(Map.of("v1", "base64-secret-not-exposed")));
        when(secretCryptoService.isEnabled()).thenReturn(false);
        when(propertyDefinitionRepository.findByEncryptedTrue()).thenReturn(List.of(typeProperty("secretCode")));
        when(nodeRepository.countByPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(4L);
        when(nodeRepository.countByEncryptedPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(1L);
        when(nodeRepository.countByPropertyKeyInBothStorageAndDeletedFalse("acme:secretCode")).thenReturn(2L);
        when(nodeRepository.countBackfillReadyByPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(2L);
        when(nodeRepository.countEncryptedPropertyValuesAndDeletedFalse()).thenReturn(3L);

        PropertyEncryptionOperationsService.PropertyEncryptionBackfillDryRunResult result =
            service.dryRunBackfill(new PropertyEncryptionOperationsService.PropertyEncryptionBackfillDryRunRequest("v3"));

        assertEquals("v3", result.targetKeyVersion());
        assertFalse(result.targetKeyConfigured());
        assertFalse(result.secretCryptoEnabled());
        assertEquals(4L, result.plaintextValueCount());
        assertEquals(1L, result.alreadyEncryptedValueCount());
        assertEquals(2L, result.dualStorageConflictValueCount());
        assertEquals(2L, result.readyValueCount());
        assertEquals(2L, result.orphanEncryptedValueCount());
        assertTrue(result.warnings().contains("secret_crypto_disabled"));
        assertTrue(result.warnings().contains("target_key_version_not_configured"));
        assertTrue(result.warnings().contains("dual_storage_conflicts_detected"));
        assertTrue(result.warnings().contains("orphan_encrypted_payloads_detected"));
        assertFalse(result.executable());
    }

    @Test
    @DisplayName("backfill dry-run blocks execution when no values are ready")
    void backfillDryRunBlocksNoReadyValues() {
        secretCryptoProperties.setActiveKeyVersion("v1");
        secretCryptoProperties.setKeys(new LinkedHashMap<>(Map.of("v1", "base64-secret-not-exposed")));
        when(secretCryptoService.isEnabled()).thenReturn(true);
        when(propertyDefinitionRepository.findByEncryptedTrue()).thenReturn(List.of(typeProperty("secretCode")));
        when(nodeRepository.countByPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(0L);
        when(nodeRepository.countByEncryptedPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(3L);
        when(nodeRepository.countByPropertyKeyInBothStorageAndDeletedFalse("acme:secretCode")).thenReturn(0L);
        when(nodeRepository.countBackfillReadyByPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(0L);
        when(nodeRepository.countEncryptedPropertyValuesAndDeletedFalse()).thenReturn(3L);

        PropertyEncryptionOperationsService.PropertyEncryptionBackfillDryRunResult result =
            service.dryRunBackfill(new PropertyEncryptionOperationsService.PropertyEncryptionBackfillDryRunRequest(" "));

        assertEquals("v1", result.targetKeyVersion());
        assertTrue(result.targetKeyConfigured());
        assertEquals(0L, result.readyValueCount());
        assertEquals(0L, result.orphanEncryptedValueCount());
        assertEquals(List.of(), result.warnings());
        assertFalse(result.executable());
    }

    @Test
    @DisplayName("backfill dry-run treats orphan encrypted payloads as warning-only")
    void backfillDryRunAllowsOrphanWarningOnlyWhenReadyValuesExist() {
        secretCryptoProperties.setActiveKeyVersion("v1");
        secretCryptoProperties.setKeys(new LinkedHashMap<>(Map.of("v1", "base64-secret-not-exposed")));
        when(secretCryptoService.isEnabled()).thenReturn(true);
        when(propertyDefinitionRepository.findByEncryptedTrue()).thenReturn(List.of(typeProperty("secretCode")));
        when(nodeRepository.countByPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(2L);
        when(nodeRepository.countByEncryptedPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(1L);
        when(nodeRepository.countByPropertyKeyInBothStorageAndDeletedFalse("acme:secretCode")).thenReturn(0L);
        when(nodeRepository.countBackfillReadyByPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(2L);
        when(nodeRepository.countEncryptedPropertyValuesAndDeletedFalse()).thenReturn(4L);

        PropertyEncryptionOperationsService.PropertyEncryptionBackfillDryRunResult result =
            service.dryRunBackfill(null);

        assertEquals(3L, result.orphanEncryptedValueCount());
        assertTrue(result.warnings().contains("orphan_encrypted_payloads_detected"));
        assertTrue(result.executable());
    }

    @Test
    @DisplayName("backfill dry-run blocks execution when no encrypted property definitions exist")
    void backfillDryRunBlocksNoDefinitions() {
        secretCryptoProperties.setActiveKeyVersion("v1");
        secretCryptoProperties.setKeys(new LinkedHashMap<>(Map.of("v1", "base64-secret-not-exposed")));
        when(secretCryptoService.isEnabled()).thenReturn(true);
        when(propertyDefinitionRepository.findByEncryptedTrue()).thenReturn(List.of());
        when(nodeRepository.countEncryptedPropertyValuesAndDeletedFalse()).thenReturn(0L);

        PropertyEncryptionOperationsService.PropertyEncryptionBackfillDryRunResult result =
            service.dryRunBackfill(null);

        assertEquals(0L, result.encryptedPropertyDefinitionCount());
        assertEquals(0L, result.readyValueCount());
        assertTrue(result.warnings().contains("no_encrypted_property_definitions"));
        assertFalse(result.executable());
    }

    @Test
    @DisplayName("backfill job plan persists an executable dry-run snapshot without processing nodes")
    void backfillJobPlanPersistsExecutableSnapshot() {
        secretCryptoProperties.setActiveKeyVersion("v1");
        secretCryptoProperties.setKeys(new LinkedHashMap<>(Map.of("v1", "base64-secret-not-exposed")));
        when(secretCryptoService.isEnabled()).thenReturn(true);
        when(propertyDefinitionRepository.findByEncryptedTrue()).thenReturn(List.of(typeProperty("secretCode")));
        when(nodeRepository.countByPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(2L);
        when(nodeRepository.countByEncryptedPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(1L);
        when(nodeRepository.countByPropertyKeyInBothStorageAndDeletedFalse("acme:secretCode")).thenReturn(0L);
        when(nodeRepository.countBackfillReadyByPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(2L);
        when(nodeRepository.countEncryptedPropertyValuesAndDeletedFalse()).thenReturn(1L);
        when(backfillJobRepository.save(any(PropertyEncryptionBackfillJob.class))).thenAnswer(invocation -> {
            PropertyEncryptionBackfillJob job = invocation.getArgument(0);
            job.setId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
            return job;
        });

        PropertyEncryptionOperationsService.PropertyEncryptionBackfillJobDto result = service.planBackfillJob(
            new PropertyEncryptionOperationsService.PropertyEncryptionBackfillJobPlanRequest(null),
            "admin"
        );

        assertEquals(UUID.fromString("11111111-2222-3333-4444-555555555555"), result.id());
        assertEquals(BackfillJobStatus.PLANNED, result.status());
        assertEquals("v1", result.targetKeyVersion());
        assertEquals("admin", result.requestedBy());
        assertEquals(1L, result.encryptedPropertyDefinitionCount());
        assertEquals(2L, result.plaintextValueCount());
        assertEquals(1L, result.alreadyEncryptedValueCount());
        assertEquals(0L, result.dualStorageConflictValueCount());
        assertEquals(2L, result.readyValueCount());
        assertEquals(0L, result.processedValueCount());
        assertEquals(0L, result.migratedValueCount());
        assertEquals(0L, result.failedValueCount());
        assertEquals(1, result.definitionCounts().size());
        assertEquals("acme:secretCode", result.definitionCounts().get(0).qualifiedName());
    }

    @Test
    @DisplayName("backfill job plan rejects non-executable dry-run state")
    void backfillJobPlanRejectsNonExecutableDryRun() {
        secretCryptoProperties.setActiveKeyVersion("v1");
        secretCryptoProperties.setKeys(new LinkedHashMap<>(Map.of("v1", "base64-secret-not-exposed")));
        when(secretCryptoService.isEnabled()).thenReturn(false);
        when(propertyDefinitionRepository.findByEncryptedTrue()).thenReturn(List.of(typeProperty("secretCode")));
        when(nodeRepository.countByPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(2L);
        when(nodeRepository.countByEncryptedPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(0L);
        when(nodeRepository.countByPropertyKeyInBothStorageAndDeletedFalse("acme:secretCode")).thenReturn(0L);
        when(nodeRepository.countBackfillReadyByPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(2L);
        when(nodeRepository.countEncryptedPropertyValuesAndDeletedFalse()).thenReturn(0L);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.planBackfillJob(
            new PropertyEncryptionOperationsService.PropertyEncryptionBackfillJobPlanRequest(null),
            "admin"
        ));

        assertTrue(ex.getMessage().contains("secret_crypto_disabled"));
        verify(backfillJobRepository, never()).save(any(PropertyEncryptionBackfillJob.class));
    }

    private PropertyDefinition typeProperty(String name) {
        ContentModelDefinition model = model();
        TypeDefinition typeDefinition = new TypeDefinition();
        typeDefinition.setName("contract");
        typeDefinition.setModel(model);

        PropertyDefinition propertyDefinition = property(name);
        propertyDefinition.setTypeDefinition(typeDefinition);
        return propertyDefinition;
    }

    private PropertyDefinition aspectProperty(String name) {
        ContentModelDefinition model = model();
        AspectDefinition aspectDefinition = new AspectDefinition();
        aspectDefinition.setName("secure");
        aspectDefinition.setModel(model);

        PropertyDefinition propertyDefinition = property(name);
        propertyDefinition.setAspectDefinition(aspectDefinition);
        return propertyDefinition;
    }

    private PropertyDefinition property(String name) {
        PropertyDefinition propertyDefinition = new PropertyDefinition();
        propertyDefinition.setId(UUID.randomUUID());
        propertyDefinition.setName(name);
        propertyDefinition.setTitle("Encrypted " + name);
        propertyDefinition.setDataType(PropertyDataType.TEXT);
        propertyDefinition.setEncrypted(true);
        propertyDefinition.setIndexed(false);
        return propertyDefinition;
    }

    private ContentModelDefinition model() {
        ContentModelDefinition model = new ContentModelDefinition();
        model.setPrefix("acme");
        model.setName("content");
        return model;
    }
}
