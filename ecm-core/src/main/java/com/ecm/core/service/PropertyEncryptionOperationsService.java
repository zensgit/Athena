package com.ecm.core.service;

import com.ecm.core.entity.AspectDefinition;
import com.ecm.core.entity.PropertyDataType;
import com.ecm.core.entity.PropertyDefinition;
import com.ecm.core.entity.TypeDefinition;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.PropertyDefinitionRepository;
import com.ecm.core.security.secret.SecretCryptoProperties;
import com.ecm.core.security.secret.SecretCryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PropertyEncryptionOperationsService {

    private final PropertyDefinitionRepository propertyDefinitionRepository;
    private final NodeRepository nodeRepository;
    private final SecretCryptoService secretCryptoService;
    private final SecretCryptoProperties secretCryptoProperties;

    @Transactional(readOnly = true)
    public PropertyEncryptionStatus getStatus() {
        List<PropertyDefinition> encryptedDefinitions = propertyDefinitionRepository.findByEncryptedTrue();
        long typeDefinitionCount = encryptedDefinitions.stream()
            .filter(definition -> definition.getTypeDefinition() != null)
            .count();
        long aspectDefinitionCount = encryptedDefinitions.stream()
            .filter(definition -> definition.getAspectDefinition() != null)
            .count();
        long nodesWithEncryptedProperties = nodeRepository.countNodesWithEncryptedPropertiesAndDeletedFalse();
        long encryptedPropertyValueCount = nodeRepository.countEncryptedPropertyValuesAndDeletedFalse();

        boolean secretCryptoEnabled = secretCryptoService.isEnabled();
        String activeKeyVersion = secretCryptoProperties.getActiveKeyVersion();
        List<String> configuredKeyVersions = configuredKeyVersions();
        boolean activeKeyConfigured = activeKeyVersion != null && configuredKeyVersions.contains(activeKeyVersion);

        List<String> warnings = new ArrayList<>();
        if (!secretCryptoEnabled && !encryptedDefinitions.isEmpty()) {
            warnings.add("encrypted_property_definitions_require_secret_crypto");
        }
        if (!secretCryptoEnabled && nodesWithEncryptedProperties > 0) {
            warnings.add("encrypted_node_payloads_require_secret_crypto");
        }
        if (secretCryptoEnabled && !activeKeyConfigured) {
            warnings.add("active_secret_key_version_is_not_configured");
        }

        return new PropertyEncryptionStatus(
            secretCryptoEnabled,
            activeKeyVersion,
            activeKeyConfigured,
            configuredKeyVersions,
            encryptedDefinitions.size(),
            typeDefinitionCount,
            aspectDefinitionCount,
            nodesWithEncryptedProperties,
            encryptedPropertyValueCount,
            List.copyOf(warnings)
        );
    }

    @Transactional(readOnly = true)
    public List<EncryptedPropertyDefinitionSummary> listEncryptedDefinitions() {
        return propertyDefinitionRepository.findByEncryptedTrue().stream()
            .map(this::toDefinitionSummary)
            .sorted(Comparator.comparing(EncryptedPropertyDefinitionSummary::qualifiedName))
            .toList();
    }

    @Transactional(readOnly = true)
    public PropertyEncryptionRewrapDryRunResult dryRunRewrap(PropertyEncryptionRewrapDryRunRequest request) {
        String targetKeyVersion = resolveTargetKeyVersion(request != null ? request.targetKeyVersion() : null);
        boolean secretCryptoEnabled = secretCryptoService.isEnabled();
        List<String> configuredKeyVersions = configuredKeyVersions();
        boolean targetKeyConfigured = targetKeyVersion != null && configuredKeyVersions.contains(targetKeyVersion);

        long candidateNodeCount = nodeRepository.countNodesWithEncryptedPropertiesAndDeletedFalse();
        long encryptedPropertyValueCount = nodeRepository.countEncryptedPropertyValuesAndDeletedFalse();
        List<KeyVersionValueCount> keyVersionCounts = keyVersionCounts();
        long versionedValueCount = keyVersionCounts.stream()
            .mapToLong(KeyVersionValueCount::encryptedPropertyValueCount)
            .sum();
        long alreadyOnTargetKeyCount = keyVersionCounts.stream()
            .filter(count -> count.keyVersion().equals(targetKeyVersion))
            .mapToLong(KeyVersionValueCount::encryptedPropertyValueCount)
            .sum();
        List<String> missingSourceKeyVersions = keyVersionCounts.stream()
            .map(KeyVersionValueCount::keyVersion)
            .filter(keyVersion -> !configuredKeyVersions.contains(keyVersion))
            .sorted()
            .toList();
        long unversionedOrMalformedValueCount = Math.max(0, encryptedPropertyValueCount - versionedValueCount);
        long valuesRequiringRewrapCount = Math.max(0, encryptedPropertyValueCount - alreadyOnTargetKeyCount);

        List<String> warnings = new ArrayList<>();
        if (!secretCryptoEnabled) {
            warnings.add("secret_crypto_disabled");
        }
        if (!hasText(targetKeyVersion)) {
            warnings.add("target_key_version_required");
        } else if (!targetKeyConfigured) {
            warnings.add("target_key_version_not_configured");
        }
        if (unversionedOrMalformedValueCount > 0) {
            warnings.add("encrypted_payloads_without_key_version");
        }
        if (!missingSourceKeyVersions.isEmpty()) {
            warnings.add("source_key_versions_not_configured");
        }

        boolean executable = secretCryptoEnabled
            && targetKeyConfigured
            && missingSourceKeyVersions.isEmpty()
            && unversionedOrMalformedValueCount == 0
            && valuesRequiringRewrapCount > 0;

        return new PropertyEncryptionRewrapDryRunResult(
            targetKeyVersion,
            targetKeyConfigured,
            secretCryptoEnabled,
            candidateNodeCount,
            encryptedPropertyValueCount,
            alreadyOnTargetKeyCount,
            valuesRequiringRewrapCount,
            unversionedOrMalformedValueCount,
            keyVersionCounts,
            missingSourceKeyVersions,
            List.copyOf(warnings),
            executable
        );
    }

    @Transactional(readOnly = true)
    public PropertyEncryptionBackfillDryRunResult dryRunBackfill(PropertyEncryptionBackfillDryRunRequest request) {
        String targetKeyVersion = resolveTargetKeyVersion(request != null ? request.targetKeyVersion() : null);
        boolean secretCryptoEnabled = secretCryptoService.isEnabled();
        List<String> configuredKeyVersions = configuredKeyVersions();
        boolean targetKeyConfigured = targetKeyVersion != null && configuredKeyVersions.contains(targetKeyVersion);

        List<EncryptedPropertyDefinitionSummary> definitions = listEncryptedDefinitions();
        List<PropertyBackfillCount> definitionCounts = definitions.stream()
            .map(this::toBackfillCount)
            .toList();

        long plaintextValueCount = definitionCounts.stream()
            .mapToLong(PropertyBackfillCount::plaintextValueCount)
            .sum();
        long alreadyEncryptedValueCount = definitionCounts.stream()
            .mapToLong(PropertyBackfillCount::alreadyEncryptedValueCount)
            .sum();
        long dualStorageConflictValueCount = definitionCounts.stream()
            .mapToLong(PropertyBackfillCount::dualStorageConflictValueCount)
            .sum();
        long readyValueCount = definitionCounts.stream()
            .mapToLong(PropertyBackfillCount::readyValueCount)
            .sum();
        long totalEncryptedPropertyValueCount = nodeRepository.countEncryptedPropertyValuesAndDeletedFalse();
        long orphanEncryptedValueCount = Math.max(0, totalEncryptedPropertyValueCount - alreadyEncryptedValueCount);

        List<String> warnings = new ArrayList<>();
        if (!secretCryptoEnabled) {
            warnings.add("secret_crypto_disabled");
        }
        if (!hasText(targetKeyVersion)) {
            warnings.add("target_key_version_required");
        } else if (!targetKeyConfigured) {
            warnings.add("target_key_version_not_configured");
        }
        if (definitions.isEmpty()) {
            warnings.add("no_encrypted_property_definitions");
        }
        if (dualStorageConflictValueCount > 0) {
            warnings.add("dual_storage_conflicts_detected");
        }
        if (orphanEncryptedValueCount > 0) {
            warnings.add("orphan_encrypted_payloads_detected");
        }

        boolean executable = secretCryptoEnabled
            && targetKeyConfigured
            && !definitions.isEmpty()
            && dualStorageConflictValueCount == 0
            && readyValueCount > 0;

        return new PropertyEncryptionBackfillDryRunResult(
            targetKeyVersion,
            targetKeyConfigured,
            secretCryptoEnabled,
            definitions.size(),
            plaintextValueCount,
            alreadyEncryptedValueCount,
            dualStorageConflictValueCount,
            readyValueCount,
            orphanEncryptedValueCount,
            definitionCounts,
            List.copyOf(warnings),
            executable
        );
    }

    private EncryptedPropertyDefinitionSummary toDefinitionSummary(PropertyDefinition definition) {
        TypeDefinition typeDefinition = definition.getTypeDefinition();
        AspectDefinition aspectDefinition = definition.getAspectDefinition();

        String ownerKind = "UNASSIGNED";
        String ownerQName = null;
        if (typeDefinition != null) {
            ownerKind = "TYPE";
            ownerQName = typeDefinition.qualifiedName();
        } else if (aspectDefinition != null) {
            ownerKind = "ASPECT";
            ownerQName = aspectDefinition.qualifiedName();
        }

        return new EncryptedPropertyDefinitionSummary(
            definition.getId(),
            definition.qualifiedName(),
            definition.getName(),
            definition.getTitle(),
            ownerKind,
            ownerQName,
            definition.getDataType(),
            definition.isMandatory(),
            definition.isMultiValued(),
            definition.isIndexed()
        );
    }

    private List<String> configuredKeyVersions() {
        Map<String, String> keys = secretCryptoProperties.getKeys();
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        return keys.keySet().stream().sorted().toList();
    }

    private String resolveTargetKeyVersion(String requestedTargetKeyVersion) {
        return hasText(requestedTargetKeyVersion)
            ? requestedTargetKeyVersion.trim()
            : secretCryptoProperties.getActiveKeyVersion();
    }

    private List<KeyVersionValueCount> keyVersionCounts() {
        return nodeRepository.countEncryptedPropertyValuesByKeyVersionAndDeletedFalse().stream()
            .map(this::toKeyVersionValueCount)
            .sorted(Comparator.comparing(KeyVersionValueCount::keyVersion))
            .toList();
    }

    private KeyVersionValueCount toKeyVersionValueCount(Object[] row) {
        String keyVersion = row[0] != null ? row[0].toString() : "";
        return new KeyVersionValueCount(keyVersion, toLong(row[1]));
    }

    private PropertyBackfillCount toBackfillCount(EncryptedPropertyDefinitionSummary definition) {
        String qualifiedName = definition.qualifiedName();
        long plaintextValueCount = nodeRepository.countByPropertyKeyAndDeletedFalse(qualifiedName);
        long alreadyEncryptedValueCount = nodeRepository.countByEncryptedPropertyKeyAndDeletedFalse(qualifiedName);
        long dualStorageConflictValueCount = nodeRepository.countByPropertyKeyInBothStorageAndDeletedFalse(qualifiedName);
        long readyValueCount = nodeRepository.countBackfillReadyByPropertyKeyAndDeletedFalse(qualifiedName);
        return new PropertyBackfillCount(
            qualifiedName,
            definition.ownerKind(),
            definition.ownerQName(),
            plaintextValueCount,
            alreadyEncryptedValueCount,
            dualStorageConflictValueCount,
            readyValueCount
        );
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(value.toString());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record PropertyEncryptionStatus(
        boolean secretCryptoEnabled,
        String activeKeyVersion,
        boolean activeKeyConfigured,
        List<String> configuredKeyVersions,
        long encryptedPropertyDefinitionCount,
        long encryptedTypePropertyDefinitionCount,
        long encryptedAspectPropertyDefinitionCount,
        long nodesWithEncryptedPropertiesCount,
        long encryptedPropertyValueCount,
        List<String> warnings
    ) {
    }

    public record EncryptedPropertyDefinitionSummary(
        UUID id,
        String qualifiedName,
        String name,
        String title,
        String ownerKind,
        String ownerQName,
        PropertyDataType dataType,
        boolean mandatory,
        boolean multiValued,
        boolean indexed
    ) {
    }

    public record PropertyEncryptionRewrapDryRunRequest(
        String targetKeyVersion
    ) {
    }

    public record PropertyEncryptionBackfillDryRunRequest(
        String targetKeyVersion
    ) {
    }

    public record PropertyEncryptionRewrapDryRunResult(
        String targetKeyVersion,
        boolean targetKeyConfigured,
        boolean secretCryptoEnabled,
        long candidateNodeCount,
        long encryptedPropertyValueCount,
        long valuesAlreadyOnTargetKeyCount,
        long valuesRequiringRewrapCount,
        long unversionedOrMalformedValueCount,
        List<KeyVersionValueCount> keyVersionCounts,
        List<String> missingSourceKeyVersions,
        List<String> warnings,
        boolean executable
    ) {
    }

    public record KeyVersionValueCount(
        String keyVersion,
        long encryptedPropertyValueCount
    ) {
    }

    public record PropertyEncryptionBackfillDryRunResult(
        String targetKeyVersion,
        boolean targetKeyConfigured,
        boolean secretCryptoEnabled,
        long encryptedPropertyDefinitionCount,
        long plaintextValueCount,
        long alreadyEncryptedValueCount,
        long dualStorageConflictValueCount,
        long readyValueCount,
        long orphanEncryptedValueCount,
        List<PropertyBackfillCount> definitionCounts,
        List<String> warnings,
        boolean executable
    ) {
    }

    public record PropertyBackfillCount(
        String qualifiedName,
        String ownerKind,
        String ownerQName,
        long plaintextValueCount,
        long alreadyEncryptedValueCount,
        long dualStorageConflictValueCount,
        long readyValueCount
    ) {
    }
}
