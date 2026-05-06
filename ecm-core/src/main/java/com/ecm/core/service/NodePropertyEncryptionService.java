package com.ecm.core.service;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.PropertyDefinition;
import com.ecm.core.security.secret.SecretCryptoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NodePropertyEncryptionService {

    public static final String REDACTED_PROTECTED_PAYLOAD = "[encrypted]";

    private final DictionaryService dictionaryService;
    private final SecretCryptoService secretCryptoService;
    private final ObjectMapper objectMapper;

    public Map<String, Object> resolveReadableProperties(Node node) {
        if (node == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> resolved = redactProtectedPayloads(node.getProperties());
        if (node.getEncryptedProperties() == null || node.getEncryptedProperties().isEmpty()) {
            return resolved;
        }

        for (String key : resolveEncryptedPropertyKeys(node)) {
            String protectedValue = node.getEncryptedProperties().get(key);
            if (protectedValue != null) {
                resolved.put(key, deserializeValue(secretCryptoService.reveal(protectedValue)));
            }
        }
        return resolved;
    }

    public void materializeReadableProperties(Node node) {
        if (node == null) {
            return;
        }
        node.setProperties(resolveReadableProperties(node));
    }

    public void prepareForPersistence(Node node) {
        if (node == null) {
            return;
        }

        Set<String> encryptedKeys = resolveEncryptedPropertyKeys(node);
        if (!encryptedKeys.isEmpty() && !secretCryptoService.isEnabled()) {
            throw new IllegalStateException("Encrypted node properties require ecm.security.secret.enabled=true");
        }
        Map<String, Object> properties = node.getProperties() != null
            ? new LinkedHashMap<>(node.getProperties())
            : new LinkedHashMap<>();
        Map<String, String> encryptedProperties = node.getEncryptedProperties() != null
            ? new LinkedHashMap<>(node.getEncryptedProperties())
            : new LinkedHashMap<>();

        encryptedProperties.keySet().removeIf(existingKey -> !encryptedKeys.contains(existingKey));

        for (String encryptedKey : encryptedKeys) {
            if (!properties.containsKey(encryptedKey) || properties.get(encryptedKey) == null) {
                properties.remove(encryptedKey);
                encryptedProperties.remove(encryptedKey);
                continue;
            }
            encryptedProperties.put(encryptedKey, secretCryptoService.protect(serializeValue(properties.remove(encryptedKey))));
        }

        node.setProperties(properties);
        node.setEncryptedProperties(encryptedProperties);
    }

    public Map<String, Object> resolveIndexableProperties(Node node) {
        Map<String, Object> indexable = redactProtectedPayloads(node != null ? node.getProperties() : null);
        for (String key : resolveEncryptedPropertyKeys(node)) {
            indexable.remove(key);
        }
        return indexable;
    }

    public Map<String, Object> redactProtectedPayloads(Map<String, Object> properties) {
        Map<String, Object> redacted = new LinkedHashMap<>();
        if (properties == null || properties.isEmpty()) {
            return redacted;
        }
        properties.forEach((key, value) -> redacted.put(key, redactProtectedPayloadValue(value)));
        return redacted;
    }

    public Set<String> resolveEncryptedPropertyKeys(Node node) {
        if (node == null) {
            return Set.of();
        }

        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(resolveEncryptedTypeProperties(node));
        keys.addAll(resolveEncryptedAspectProperties(node));
        return keys;
    }

    private List<String> resolveEncryptedTypeProperties(Node node) {
        if (node.getTypeQName() == null || node.getTypeQName().isBlank()) {
            return List.of();
        }
        try {
            return dictionaryService.getPropertiesForType(node.getTypeQName()).stream()
                .filter(PropertyDefinition::isEncrypted)
                .map(PropertyDefinition::qualifiedName)
                .toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> resolveEncryptedAspectProperties(Node node) {
        if (node.getAspects() == null || node.getAspects().isEmpty()) {
            return List.of();
        }

        Set<String> keys = new LinkedHashSet<>();
        for (String aspectName : node.getAspects()) {
            try {
                dictionaryService.getPropertiesForAspect(aspectName).stream()
                    .filter(PropertyDefinition::isEncrypted)
                    .map(PropertyDefinition::qualifiedName)
                    .forEach(keys::add);
            } catch (Exception ignored) {
                // unmanaged aspect
            }
        }
        return List.copyOf(keys);
    }

    private String serializeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize encrypted property value", ex);
        }
    }

    private Object deserializeValue(String value) {
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (Exception ex) {
            return value;
        }
    }

    private Object redactProtectedPayloadValue(Object value) {
        if (value instanceof String stringValue) {
            return isProtectedPayload(stringValue) ? REDACTED_PROTECTED_PAYLOAD : stringValue;
        }
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            mapValue.forEach((key, nestedValue) ->
                redacted.put(String.valueOf(key), redactProtectedPayloadValue(nestedValue))
            );
            return redacted;
        }
        if (value instanceof Collection<?> collectionValue) {
            List<Object> redacted = new ArrayList<>();
            for (Object nestedValue : collectionValue) {
                redacted.add(redactProtectedPayloadValue(nestedValue));
            }
            return redacted;
        }
        return value;
    }

    private boolean isProtectedPayload(String value) {
        if (value == null || !value.startsWith("enc:")) {
            return false;
        }
        String remainder = value.substring("enc:".length());
        int delimiterIndex = remainder.indexOf(':');
        return delimiterIndex > 0 && delimiterIndex < remainder.length() - 1;
    }
}
