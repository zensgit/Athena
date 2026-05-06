package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.ContentModelDefinition;
import com.ecm.core.entity.PropertyDefinition;
import com.ecm.core.entity.TypeDefinition;
import com.ecm.core.security.secret.SecretCryptoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodePropertyEncryptionServiceTest {

    @Mock
    private DictionaryService dictionaryService;

    @Mock
    private SecretCryptoService secretCryptoService;

    private NodePropertyEncryptionService nodePropertyEncryptionService;

    @BeforeEach
    void setUp() {
        nodePropertyEncryptionService = new NodePropertyEncryptionService(
            dictionaryService,
            secretCryptoService,
            new ObjectMapper()
        );
    }

    @Test
    @DisplayName("prepareForPersistence moves encrypted model properties into encryptedProperties")
    void prepareForPersistenceMovesEncryptedProperties() {
        Document document = new Document();
        document.setTypeQName("acme:contract");
        document.setProperties(new HashMap<>(Map.of(
            "acme:publicCode", "PUB-1",
            "acme:secretCode", "SEC-42"
        )));

        PropertyDefinition encrypted = typedProperty("secretCode", true);
        PropertyDefinition plain = typedProperty("publicCode", false);

        when(dictionaryService.getPropertiesForType("acme:contract")).thenReturn(List.of(plain, encrypted));
        when(secretCryptoService.isEnabled()).thenReturn(true);
        when(secretCryptoService.protect("\"SEC-42\"")).thenReturn("enc:v1:secret");

        nodePropertyEncryptionService.prepareForPersistence(document);

        assertEquals(Map.of("acme:publicCode", "PUB-1"), document.getProperties());
        assertEquals(Map.of("acme:secretCode", "enc:v1:secret"), document.getEncryptedProperties());
    }

    @Test
    @DisplayName("resolveReadableProperties decrypts encrypted properties and keeps plain ones")
    void resolveReadablePropertiesDecryptsEncryptedValues() {
        Document document = new Document();
        document.setTypeQName("acme:contract");
        document.setProperties(new HashMap<>(Map.of("acme:publicCode", "PUB-1")));
        document.setEncryptedProperties(new HashMap<>(Map.of("acme:secretCode", "enc:v1:secret")));

        PropertyDefinition encrypted = typedProperty("secretCode", true);
        PropertyDefinition plain = typedProperty("publicCode", false);

        when(dictionaryService.getPropertiesForType("acme:contract")).thenReturn(List.of(plain, encrypted));
        when(secretCryptoService.reveal("enc:v1:secret")).thenReturn("\"SEC-42\"");

        Map<String, Object> readable = nodePropertyEncryptionService.resolveReadableProperties(document);

        assertEquals("PUB-1", readable.get("acme:publicCode"));
        assertEquals("SEC-42", readable.get("acme:secretCode"));
    }

    @Test
    @DisplayName("resolveReadableProperties redacts protected payloads from legacy plain properties")
    void resolveReadablePropertiesRedactsProtectedPayloadsFromPlainProperties() {
        Document document = new Document();
        document.setTypeQName("acme:contract");
        document.setProperties(new HashMap<>(Map.of(
            "acme:publicCode", "PUB-1",
            "acme:legacySecret", "enc:v1:legacy-payload",
            "acme:nested", Map.of("token", "enc:v2:nested-payload"),
            "acme:list", List.of("safe", "enc:v3:list-payload")
        )));

        Map<String, Object> readable = nodePropertyEncryptionService.resolveReadableProperties(document);

        assertEquals("PUB-1", readable.get("acme:publicCode"));
        assertEquals(NodePropertyEncryptionService.REDACTED_PROTECTED_PAYLOAD, readable.get("acme:legacySecret"));
        assertEquals(
            Map.of("token", NodePropertyEncryptionService.REDACTED_PROTECTED_PAYLOAD),
            readable.get("acme:nested")
        );
        assertEquals(
            List.of("safe", NodePropertyEncryptionService.REDACTED_PROTECTED_PAYLOAD),
            readable.get("acme:list")
        );
    }

    @Test
    @DisplayName("resolveIndexableProperties excludes encrypted model properties")
    void resolveIndexablePropertiesExcludesEncryptedValues() {
        Document document = new Document();
        document.setTypeQName("acme:contract");
        document.setProperties(new HashMap<>(Map.of(
            "acme:publicCode", "PUB-1",
            "acme:secretCode", "SEC-42"
        )));

        PropertyDefinition encrypted = typedProperty("secretCode", true);
        when(dictionaryService.getPropertiesForType("acme:contract")).thenReturn(List.of(encrypted));

        Map<String, Object> indexable = nodePropertyEncryptionService.resolveIndexableProperties(document);

        assertEquals("PUB-1", indexable.get("acme:publicCode"));
        assertFalse(indexable.containsKey("acme:secretCode"));
        assertTrue(document.getProperties().containsKey("acme:secretCode"));
    }

    @Test
    @DisplayName("resolveIndexableProperties redacts protected payloads from legacy plain properties")
    void resolveIndexablePropertiesRedactsProtectedPayloads() {
        Document document = new Document();
        document.setTypeQName("acme:contract");
        document.setProperties(new HashMap<>(Map.of(
            "acme:legacySecret", "enc:v1:legacy-payload",
            "acme:publicCode", "PUB-1"
        )));

        when(dictionaryService.getPropertiesForType("acme:contract")).thenReturn(List.of());

        Map<String, Object> indexable = nodePropertyEncryptionService.resolveIndexableProperties(document);

        assertEquals(NodePropertyEncryptionService.REDACTED_PROTECTED_PAYLOAD, indexable.get("acme:legacySecret"));
        assertEquals("PUB-1", indexable.get("acme:publicCode"));
    }

    private PropertyDefinition typedProperty(String name, boolean encrypted) {
        ContentModelDefinition model = new ContentModelDefinition();
        model.setPrefix("acme");
        TypeDefinition typeDefinition = new TypeDefinition();
        typeDefinition.setName("contract");
        typeDefinition.setModel(model);

        PropertyDefinition propertyDefinition = new PropertyDefinition();
        propertyDefinition.setName(name);
        propertyDefinition.setEncrypted(encrypted);
        propertyDefinition.setTypeDefinition(typeDefinition);
        return propertyDefinition;
    }
}
