package com.ecm.core.service;

import com.ecm.core.entity.AspectDefinition;
import com.ecm.core.entity.ContentModelDefinition;
import com.ecm.core.entity.PropertyDataType;
import com.ecm.core.entity.PropertyDefinition;
import com.ecm.core.entity.TypeDefinition;
import com.ecm.core.repository.NodeRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyEncryptionOperationsServiceTest {

    @Mock private PropertyDefinitionRepository propertyDefinitionRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private SecretCryptoService secretCryptoService;

    private SecretCryptoProperties secretCryptoProperties;
    private PropertyEncryptionOperationsService service;

    @BeforeEach
    void setUp() {
        secretCryptoProperties = new SecretCryptoProperties();
        service = new PropertyEncryptionOperationsService(
            propertyDefinitionRepository,
            nodeRepository,
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
