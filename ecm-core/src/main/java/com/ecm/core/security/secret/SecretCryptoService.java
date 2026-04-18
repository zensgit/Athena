package com.ecm.core.security.secret;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class SecretCryptoService {

    private static final String PREFIX = "enc:";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretCryptoProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, SecretKeySpec> keyCache = new ConcurrentHashMap<>();

    @PostConstruct
    void register() {
        EncryptedSecretConverter.setSecretCryptoService(this);
    }

    public String protect(String value) {
        if (!StringUtils.hasText(value) || isEncrypted(value)) {
            return value;
        }
        if (!properties.isEnabled()) {
            return value;
        }

        String keyVersion = properties.getActiveKeyVersion();
        SecretKeySpec key = resolveKey(keyVersion);
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            ByteBuffer payload = ByteBuffer.allocate(1 + iv.length + ciphertext.length);
            payload.put((byte) iv.length);
            payload.put(iv);
            payload.put(ciphertext);
            return PREFIX + keyVersion + ":" + Base64.getEncoder().encodeToString(payload.array());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt secret value", ex);
        }
    }

    public String reveal(String value) {
        if (!StringUtils.hasText(value) || !isEncrypted(value)) {
            return value;
        }

        ParsedSecret parsed = parse(value);
        SecretKeySpec key = resolveKey(parsed.keyVersion());
        byte[] payload = Base64.getDecoder().decode(parsed.payload());
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        int ivLength = Byte.toUnsignedInt(buffer.get());
        byte[] iv = new byte[ivLength];
        buffer.get(iv);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt secret value", ex);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    private SecretKeySpec resolveKey(String keyVersion) {
        return keyCache.computeIfAbsent(keyVersion, this::loadKey);
    }

    private SecretKeySpec loadKey(String keyVersion) {
        String encodedKey = properties.getKeys().get(keyVersion);
        if (!StringUtils.hasText(encodedKey)) {
            throw new IllegalStateException("Missing secret encryption key for version " + keyVersion);
        }
        byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
        if (!(keyBytes.length == 16 || keyBytes.length == 24 || keyBytes.length == 32)) {
            throw new IllegalStateException("Invalid AES key length for version " + keyVersion);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    private ParsedSecret parse(String value) {
        String remainder = value.substring(PREFIX.length());
        int delimiterIndex = remainder.indexOf(':');
        if (delimiterIndex <= 0 || delimiterIndex == remainder.length() - 1) {
            throw new IllegalStateException("Invalid encrypted secret payload format");
        }
        String keyVersion = remainder.substring(0, delimiterIndex);
        String payload = remainder.substring(delimiterIndex + 1);
        return new ParsedSecret(keyVersion, payload);
    }

    private record ParsedSecret(String keyVersion, String payload) {
    }
}
