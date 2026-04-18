package com.ecm.core.security.secret;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretCryptoServiceTest {

    private static final String KEY_V1 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    @DisplayName("Protect and reveal round-trip secret values when encryption is enabled")
    void protectAndRevealRoundTrip() {
        SecretCryptoProperties properties = new SecretCryptoProperties();
        properties.setEnabled(true);
        properties.setActiveKeyVersion("v1");
        properties.setKeys(Map.of("v1", KEY_V1));

        SecretCryptoService service = new SecretCryptoService(properties);
        service.register();

        String protectedValue = service.protect("super-secret");

        assertTrue(protectedValue.startsWith("enc:v1:"));
        assertNotEquals("super-secret", protectedValue);
        assertEquals("super-secret", service.reveal(protectedValue));
    }

    @Test
    @DisplayName("Legacy plaintext values remain readable and are encrypted on next write")
    void legacyPlaintextValuesRemainReadable() {
        SecretCryptoProperties properties = new SecretCryptoProperties();
        properties.setEnabled(true);
        properties.setActiveKeyVersion("v1");
        properties.setKeys(Map.of("v1", KEY_V1));

        SecretCryptoService service = new SecretCryptoService(properties);
        service.register();

        assertEquals("legacy-token", service.reveal("legacy-token"));

        String rewritten = service.protect("legacy-token");
        assertTrue(rewritten.startsWith("enc:v1:"));
        assertEquals("legacy-token", service.reveal(rewritten));
    }

    @Test
    @DisplayName("Disabled encryption leaves plaintext untouched")
    void disabledEncryptionLeavesPlaintextUntouched() {
        SecretCryptoProperties properties = new SecretCryptoProperties();
        properties.setEnabled(false);

        SecretCryptoService service = new SecretCryptoService(properties);
        service.register();

        assertEquals("plain-password", service.protect("plain-password"));
        assertEquals("plain-password", service.reveal("plain-password"));
    }
}
