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
}
