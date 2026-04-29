package com.ecm.core.service;

import com.ecm.core.entity.AspectDefinition;
import com.ecm.core.entity.ContentModelDefinition;
import com.ecm.core.entity.PropertyEncryptionBackfillJob;
import com.ecm.core.entity.PropertyEncryptionBackfillJob.BackfillJobStatus;
import com.ecm.core.entity.PropertyDataType;
import com.ecm.core.entity.PropertyDefinition;
import com.ecm.core.entity.TypeDefinition;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.NodeRepository.PropertyBackfillCandidateRow;
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

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    @Test
    @DisplayName("backfill candidate preview uses bounded repository predicate without returning plaintext")
    void backfillCandidatePreviewReturnsNodeRefsOnly() {
        UUID firstNodeId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID secondNodeId = UUID.fromString("66666666-7777-8888-9999-000000000000");
        when(nodeRepository.findBackfillCandidatesByPropertyKeyAndDeletedFalse("acme:secretCode", 1000))
            .thenReturn(List.of(
                candidate(firstNodeId, "acme:secretCode", "sensitive-one"),
                candidate(secondNodeId, "acme:secretCode", "sensitive-two")
            ));

        PropertyEncryptionOperationsService.PropertyEncryptionBackfillCandidateBatch result =
            service.previewBackfillCandidates(
                new PropertyEncryptionOperationsService.PropertyEncryptionBackfillCandidatePreviewRequest(
                    " acme:secretCode ",
                    5000
                )
            );

        assertEquals("acme:secretCode", result.qualifiedName());
        assertEquals(1000, result.limit());
        assertEquals(2, result.candidateCount());
        assertEquals(List.of(firstNodeId, secondNodeId), result.candidates().stream()
            .map(PropertyEncryptionOperationsService.PropertyEncryptionBackfillCandidateRef::nodeId)
            .toList());
        assertEquals(List.of("acme:secretCode", "acme:secretCode"), result.candidates().stream()
            .map(PropertyEncryptionOperationsService.PropertyEncryptionBackfillCandidateRef::qualifiedName)
            .toList());
    }

    @Test
    @DisplayName("backfill candidate preview rejects blank qualified name")
    void backfillCandidatePreviewRejectsBlankQualifiedName() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.previewBackfillCandidates(
                new PropertyEncryptionOperationsService.PropertyEncryptionBackfillCandidatePreviewRequest(" ", 10)
            ));

        assertTrue(ex.getMessage().contains("qualifiedName"));
        verify(nodeRepository, never()).findBackfillCandidatesByPropertyKeyAndDeletedFalse(any(), anyInt());
    }

    @Test
    @DisplayName("backfill candidate update applies encrypted payload through repository CAS")
    void backfillCandidateUpdateAppliesEncryptedPayloadThroughCas() {
        UUID nodeId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PropertyBackfillCandidateRow candidate = candidate(nodeId, " acme:secretCode ", "\"SEC-42\"", 7L);
        when(secretCryptoService.isEnabled()).thenReturn(true);
        when(secretCryptoService.protect("\"SEC-42\"")).thenReturn("enc:v1:secret");
        when(secretCryptoService.isEncrypted("enc:v1:secret")).thenReturn(true);
        when(nodeRepository.backfillEncryptedPropertyIfUnchanged(
            eq(nodeId),
            eq("acme:secretCode"),
            eq("\"SEC-42\""),
            eq(7L),
            eq("enc:v1:secret"),
            any(),
            eq("operator")
        )).thenReturn(1);

        PropertyEncryptionOperationsService.PropertyEncryptionBackfillCandidateUpdateResult result =
            service.applyBackfillCandidateUpdate(candidate, " operator ");

        assertEquals(nodeId, result.nodeId());
        assertEquals("acme:secretCode", result.qualifiedName());
        assertTrue(result.migrated());
    }

    @Test
    @DisplayName("backfill candidate update reports CAS miss without exposing plaintext")
    void backfillCandidateUpdateReportsCasMiss() {
        UUID nodeId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PropertyBackfillCandidateRow candidate = candidate(nodeId, "acme:secretCode", "\"SEC-42\"", 7L);
        when(secretCryptoService.isEnabled()).thenReturn(true);
        when(secretCryptoService.protect("\"SEC-42\"")).thenReturn("enc:v1:secret");
        when(secretCryptoService.isEncrypted("enc:v1:secret")).thenReturn(true);
        when(nodeRepository.backfillEncryptedPropertyIfUnchanged(
            eq(nodeId),
            eq("acme:secretCode"),
            eq("\"SEC-42\""),
            eq(7L),
            eq("enc:v1:secret"),
            any(),
            eq("property-encryption-backfill")
        )).thenReturn(0);

        PropertyEncryptionOperationsService.PropertyEncryptionBackfillCandidateUpdateResult result =
            service.applyBackfillCandidateUpdate(candidate, " ");

        assertEquals(nodeId, result.nodeId());
        assertEquals("acme:secretCode", result.qualifiedName());
        assertFalse(result.migrated());
    }

    @Test
    @DisplayName("backfill candidate update rejects when crypto is disabled")
    void backfillCandidateUpdateRejectsDisabledCrypto() {
        PropertyBackfillCandidateRow candidate = candidate(
            UUID.fromString("11111111-2222-3333-4444-555555555555"),
            "acme:secretCode",
            "\"SEC-42\"",
            7L
        );
        when(secretCryptoService.isEnabled()).thenReturn(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            service.applyBackfillCandidateUpdate(candidate, "operator")
        );

        assertTrue(ex.getMessage().contains("Secret crypto"));
        verify(nodeRepository, never()).backfillEncryptedPropertyIfUnchanged(
            any(),
            any(),
            any(),
            anyLong(),
            any(),
            any(),
            any()
        );
    }

    @Test
    @DisplayName("backfill candidate update rejects when encryption output is not protected")
    void backfillCandidateUpdateRejectsUnprotectedCryptoOutput() {
        PropertyBackfillCandidateRow candidate = candidate(
            UUID.fromString("11111111-2222-3333-4444-555555555555"),
            "acme:secretCode",
            "\"SEC-42\"",
            7L
        );
        when(secretCryptoService.isEnabled()).thenReturn(true);
        when(secretCryptoService.protect("\"SEC-42\"")).thenReturn("\"SEC-42\"");
        when(secretCryptoService.isEncrypted("\"SEC-42\"")).thenReturn(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            service.applyBackfillCandidateUpdate(candidate, "operator")
        );

        assertTrue(ex.getMessage().contains("encrypted payload"));
        verify(nodeRepository, never()).backfillEncryptedPropertyIfUnchanged(
            any(),
            any(),
            any(),
            anyLong(),
            any(),
            any(),
            any()
        );
    }

    @Test
    @DisplayName("backfill job executor migrates, skips CAS misses, and marks job succeeded")
    void backfillJobExecutorMigratesSkipsAndSucceeds() {
        UUID jobId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        UUID firstNodeId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID secondNodeId = UUID.fromString("66666666-7777-8888-9999-000000000000");
        PropertyEncryptionBackfillJob job = plannedBackfillJob(jobId, 2);
        secretCryptoProperties.setActiveKeyVersion("v1");
        secretCryptoProperties.setKeys(new LinkedHashMap<>(Map.of("v1", "base64-secret-not-exposed")));
        when(secretCryptoService.isEnabled()).thenReturn(true);
        when(backfillJobRepository.claimPlannedJob(eq(jobId), any())).thenAnswer(invocation -> {
            job.setStatus(BackfillJobStatus.RUNNING);
            job.setStartedAt(invocation.getArgument(1));
            return 1;
        });
        when(backfillJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(backfillJobRepository.markTerminalIfRunning(
            eq(jobId),
            eq(BackfillJobStatus.SUCCEEDED),
            any(),
            eq(2L),
            eq(1L),
            eq(1L),
            eq(0L),
            eq(null)
        )).thenReturn(1);
        when(nodeRepository.countByPropertyKeyInBothStorageAndDeletedFalse("acme:secretCode")).thenReturn(0L, 0L);
        when(nodeRepository.countBackfillReadyByPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(2L, 0L);
        when(nodeRepository.findBackfillCandidatesByPropertyKeyAndDeletedFalse("acme:secretCode", 2))
            .thenReturn(List.of(
                candidate(firstNodeId, "acme:secretCode", "\"SEC-1\"", 3L),
                candidate(secondNodeId, "acme:secretCode", "\"SEC-2\"", 4L)
            ));
        when(secretCryptoService.protect("\"SEC-1\"")).thenReturn("enc:v1:one");
        when(secretCryptoService.protect("\"SEC-2\"")).thenReturn("enc:v1:two");
        when(secretCryptoService.isEncrypted("enc:v1:one")).thenReturn(true);
        when(secretCryptoService.isEncrypted("enc:v1:two")).thenReturn(true);
        when(nodeRepository.backfillEncryptedPropertyIfUnchanged(
            eq(firstNodeId),
            eq("acme:secretCode"),
            eq("\"SEC-1\""),
            eq(3L),
            eq("enc:v1:one"),
            any(),
            eq("admin")
        )).thenReturn(1);
        when(nodeRepository.backfillEncryptedPropertyIfUnchanged(
            eq(secondNodeId),
            eq("acme:secretCode"),
            eq("\"SEC-2\""),
            eq(4L),
            eq("enc:v1:two"),
            any(),
            eq("admin")
        )).thenReturn(0);

        PropertyEncryptionOperationsService.PropertyEncryptionBackfillJobDto result =
            service.runBackfillJob(jobId, 2, " admin ");

        assertEquals(BackfillJobStatus.SUCCEEDED, result.status());
        assertEquals(2L, result.processedValueCount());
        assertEquals(1L, result.migratedValueCount());
        assertEquals(1L, result.skippedValueCount());
        assertEquals(0L, result.failedValueCount());
        assertNull(result.lastError());
        assertTrue(result.startedAt() != null);
        assertTrue(result.finishedAt() != null);
    }

    @Test
    @DisplayName("backfill job executor records candidate failures and marks job failed")
    void backfillJobExecutorRecordsCandidateFailures() {
        UUID jobId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        UUID nodeId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PropertyEncryptionBackfillJob job = plannedBackfillJob(jobId, 1);
        secretCryptoProperties.setActiveKeyVersion("v1");
        secretCryptoProperties.setKeys(new LinkedHashMap<>(Map.of("v1", "base64-secret-not-exposed")));
        when(secretCryptoService.isEnabled()).thenReturn(true);
        when(backfillJobRepository.claimPlannedJob(eq(jobId), any())).thenAnswer(invocation -> {
            job.setStatus(BackfillJobStatus.RUNNING);
            job.setStartedAt(invocation.getArgument(1));
            return 1;
        });
        when(backfillJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(backfillJobRepository.markTerminalIfRunning(
            eq(jobId),
            eq(BackfillJobStatus.FAILED),
            any(),
            eq(1L),
            eq(0L),
            eq(0L),
            eq(1L),
            eq("encrypt failed")
        )).thenReturn(1);
        when(nodeRepository.countByPropertyKeyInBothStorageAndDeletedFalse("acme:secretCode")).thenReturn(0L);
        when(nodeRepository.countBackfillReadyByPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(1L);
        when(nodeRepository.findBackfillCandidatesByPropertyKeyAndDeletedFalse("acme:secretCode", 1))
            .thenReturn(List.of(candidate(nodeId, "acme:secretCode", "\"SEC-FAIL\"", 3L)));
        when(secretCryptoService.protect("\"SEC-FAIL\"")).thenThrow(new IllegalStateException("encrypt failed"));

        PropertyEncryptionOperationsService.PropertyEncryptionBackfillJobDto result =
            service.runBackfillJob(jobId, 1, "admin");

        assertEquals(BackfillJobStatus.FAILED, result.status());
        assertEquals(1L, result.processedValueCount());
        assertEquals(0L, result.migratedValueCount());
        assertEquals(0L, result.skippedValueCount());
        assertEquals(1L, result.failedValueCount());
        assertEquals("encrypt failed", result.lastError());
    }

    @Test
    @DisplayName("backfill job executor fails preflight when target key is no longer active")
    void backfillJobExecutorFailsWhenTargetKeyIsNotActive() {
        UUID jobId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        PropertyEncryptionBackfillJob job = plannedBackfillJob(jobId, 2);
        secretCryptoProperties.setActiveKeyVersion("v2");
        secretCryptoProperties.setKeys(new LinkedHashMap<>(Map.of(
            "v1", "base64-secret-not-exposed",
            "v2", "another-base64-secret-not-exposed"
        )));
        when(secretCryptoService.isEnabled()).thenReturn(true);
        when(backfillJobRepository.claimPlannedJob(eq(jobId), any())).thenAnswer(invocation -> {
            job.setStatus(BackfillJobStatus.RUNNING);
            job.setStartedAt(invocation.getArgument(1));
            return 1;
        });
        when(backfillJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(backfillJobRepository.markTerminalIfRunning(
            eq(jobId),
            eq(BackfillJobStatus.FAILED),
            any(),
            eq(0L),
            eq(0L),
            eq(0L),
            eq(0L),
            eq("Backfill job target key version must match the active key version")
        )).thenReturn(1);

        PropertyEncryptionOperationsService.PropertyEncryptionBackfillJobDto result =
            service.runBackfillJob(jobId, 2, "admin");

        assertEquals(BackfillJobStatus.FAILED, result.status());
        assertEquals(0L, result.processedValueCount());
        assertTrue(result.lastError().contains("active key version"));
        verify(nodeRepository, never()).findBackfillCandidatesByPropertyKeyAndDeletedFalse(any(), anyInt());
    }

    @Test
    @DisplayName("backfill job executor fails when candidates run out but ready values remain")
    void backfillJobExecutorFailsWhenReadyValuesRemain() {
        UUID jobId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        PropertyEncryptionBackfillJob job = plannedBackfillJob(jobId, 1);
        secretCryptoProperties.setActiveKeyVersion("v1");
        secretCryptoProperties.setKeys(new LinkedHashMap<>(Map.of("v1", "base64-secret-not-exposed")));
        when(secretCryptoService.isEnabled()).thenReturn(true);
        when(backfillJobRepository.claimPlannedJob(eq(jobId), any())).thenAnswer(invocation -> {
            job.setStatus(BackfillJobStatus.RUNNING);
            job.setStartedAt(invocation.getArgument(1));
            return 1;
        });
        when(backfillJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(backfillJobRepository.markTerminalIfRunning(
            eq(jobId),
            eq(BackfillJobStatus.FAILED),
            any(),
            eq(0L),
            eq(0L),
            eq(0L),
            eq(0L),
            eq("Backfill job ended with remaining ready values")
        )).thenReturn(1);
        when(nodeRepository.countByPropertyKeyInBothStorageAndDeletedFalse("acme:secretCode")).thenReturn(0L, 0L);
        when(nodeRepository.countBackfillReadyByPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(1L, 1L);
        when(nodeRepository.findBackfillCandidatesByPropertyKeyAndDeletedFalse("acme:secretCode", 1))
            .thenReturn(List.of());

        PropertyEncryptionOperationsService.PropertyEncryptionBackfillJobDto result =
            service.runBackfillJob(jobId, 1, "admin");

        assertEquals(BackfillJobStatus.FAILED, result.status());
        assertEquals(0L, result.processedValueCount());
        assertEquals("Backfill job ended with remaining ready values", result.lastError());
    }

    @Test
    @DisplayName("backfill job executor does not reprocess duplicate candidates in one run")
    void backfillJobExecutorDoesNotReprocessDuplicateCandidates() {
        UUID jobId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        UUID nodeId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        PropertyBackfillCandidateRow candidate = candidate(nodeId, "acme:secretCode", "\"SEC-1\"", 3L);
        PropertyEncryptionBackfillJob job = plannedBackfillJob(jobId, 2);
        secretCryptoProperties.setActiveKeyVersion("v1");
        secretCryptoProperties.setKeys(new LinkedHashMap<>(Map.of("v1", "base64-secret-not-exposed")));
        when(secretCryptoService.isEnabled()).thenReturn(true);
        when(backfillJobRepository.claimPlannedJob(eq(jobId), any())).thenAnswer(invocation -> {
            job.setStatus(BackfillJobStatus.RUNNING);
            job.setStartedAt(invocation.getArgument(1));
            return 1;
        });
        when(backfillJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(backfillJobRepository.markTerminalIfRunning(
            eq(jobId),
            eq(BackfillJobStatus.FAILED),
            any(),
            eq(1L),
            eq(0L),
            eq(1L),
            eq(0L),
            eq("Backfill job ended with remaining ready values")
        )).thenReturn(1);
        when(nodeRepository.countByPropertyKeyInBothStorageAndDeletedFalse("acme:secretCode")).thenReturn(0L, 0L);
        when(nodeRepository.countBackfillReadyByPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(2L, 1L);
        when(nodeRepository.findBackfillCandidatesByPropertyKeyAndDeletedFalse("acme:secretCode", 1))
            .thenReturn(List.of(candidate), List.of(candidate));
        when(secretCryptoService.protect("\"SEC-1\"")).thenReturn("enc:v1:one");
        when(secretCryptoService.isEncrypted("enc:v1:one")).thenReturn(true);
        when(nodeRepository.backfillEncryptedPropertyIfUnchanged(
            eq(nodeId),
            eq("acme:secretCode"),
            eq("\"SEC-1\""),
            eq(3L),
            eq("enc:v1:one"),
            any(),
            eq("admin")
        )).thenReturn(0);

        PropertyEncryptionOperationsService.PropertyEncryptionBackfillJobDto result =
            service.runBackfillJob(jobId, 1, "admin");

        assertEquals(BackfillJobStatus.FAILED, result.status());
        assertEquals(1L, result.processedValueCount());
        assertEquals(0L, result.migratedValueCount());
        assertEquals(1L, result.skippedValueCount());
        assertEquals(0L, result.failedValueCount());
        assertEquals("Backfill job ended with remaining ready values", result.lastError());
        verify(nodeRepository, times(1)).backfillEncryptedPropertyIfUnchanged(
            eq(nodeId),
            eq("acme:secretCode"),
            eq("\"SEC-1\""),
            eq(3L),
            eq("enc:v1:one"),
            any(),
            eq("admin")
        );
    }

    @Test
    @DisplayName("backfill job executor fails preflight when dual-storage conflicts exist")
    void backfillJobExecutorFailsWhenDualStorageConflictsExist() {
        UUID jobId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        PropertyEncryptionBackfillJob job = plannedBackfillJob(jobId, 1);
        secretCryptoProperties.setActiveKeyVersion("v1");
        secretCryptoProperties.setKeys(new LinkedHashMap<>(Map.of("v1", "base64-secret-not-exposed")));
        when(secretCryptoService.isEnabled()).thenReturn(true);
        when(backfillJobRepository.claimPlannedJob(eq(jobId), any())).thenAnswer(invocation -> {
            job.setStatus(BackfillJobStatus.RUNNING);
            job.setStartedAt(invocation.getArgument(1));
            return 1;
        });
        when(backfillJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(backfillJobRepository.markTerminalIfRunning(
            eq(jobId),
            eq(BackfillJobStatus.FAILED),
            any(),
            eq(0L),
            eq(0L),
            eq(0L),
            eq(0L),
            eq("Backfill job cannot execute while dual-storage conflicts exist")
        )).thenReturn(1);
        when(nodeRepository.countByPropertyKeyInBothStorageAndDeletedFalse("acme:secretCode")).thenReturn(1L);
        when(nodeRepository.countBackfillReadyByPropertyKeyAndDeletedFalse("acme:secretCode")).thenReturn(1L);

        PropertyEncryptionOperationsService.PropertyEncryptionBackfillJobDto result =
            service.runBackfillJob(jobId, 1, "admin");

        assertEquals(BackfillJobStatus.FAILED, result.status());
        assertEquals(0L, result.processedValueCount());
        assertEquals("Backfill job cannot execute while dual-storage conflicts exist", result.lastError());
        verify(nodeRepository, never()).findBackfillCandidatesByPropertyKeyAndDeletedFalse(any(), anyInt());
    }

    @Test
    @DisplayName("backfill job executor rejects jobs that are not planned")
    void backfillJobExecutorRejectsNonPlannedJob() {
        UUID jobId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        PropertyEncryptionBackfillJob job = plannedBackfillJob(jobId, 1);
        job.setStatus(BackfillJobStatus.RUNNING);
        when(backfillJobRepository.claimPlannedJob(eq(jobId), any())).thenReturn(0);
        when(backfillJobRepository.findById(jobId)).thenReturn(Optional.of(job));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            service.runBackfillJob(jobId, 1, "admin")
        );

        assertTrue(ex.getMessage().contains("PLANNED"));
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

    private PropertyEncryptionBackfillJob plannedBackfillJob(UUID jobId, long readyValueCount) {
        LocalDateTime now = LocalDateTime.now();
        PropertyEncryptionBackfillJob job = new PropertyEncryptionBackfillJob();
        job.setId(jobId);
        job.setStatus(BackfillJobStatus.PLANNED);
        job.setTargetKeyVersion("v1");
        job.setRequestedBy("admin");
        job.setRequestedAt(now);
        job.setCreatedAt(now);
        job.setEncryptedPropertyDefinitionCount(1L);
        job.setPlaintextValueCount(readyValueCount);
        job.setReadyValueCount(readyValueCount);
        job.setDefinitionCounts(List.of(new PropertyEncryptionBackfillJob.BackfillDefinitionCountSnapshot(
            "acme:secretCode",
            "TYPE",
            "acme:contract",
            readyValueCount,
            0L,
            0L,
            readyValueCount
        )));
        return job;
    }

    private PropertyBackfillCandidateRow candidate(UUID nodeId, String propertyKey, String plaintextJson) {
        return candidate(nodeId, propertyKey, plaintextJson, 0L);
    }

    private PropertyBackfillCandidateRow candidate(UUID nodeId, String propertyKey, String plaintextJson, Long entityVersion) {
        return new PropertyBackfillCandidateRow() {
            @Override
            public UUID getNodeId() {
                return nodeId;
            }

            @Override
            public String getPropertyKey() {
                return propertyKey;
            }

            @Override
            public String getPlaintextJson() {
                return plaintextJson;
            }

            @Override
            public Long getEntityVersion() {
                return entityVersion;
            }
        };
    }
}
