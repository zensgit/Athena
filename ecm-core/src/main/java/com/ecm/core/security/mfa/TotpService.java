package com.ecm.core.security.mfa;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;

@Slf4j
@Service
public class TotpService {

    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int DEFAULT_PERIOD_SECONDS = 30;
    private static final int DEFAULT_DIGITS = 6;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateSecret() {
        byte[] buffer = new byte[20];
        secureRandom.nextBytes(buffer);
        return base32Encode(buffer);
    }

    public boolean verifyCode(String secret, String code) {
        if (secret == null || code == null || code.isBlank()) {
            return false;
        }
        String normalized = code.trim();
        if (!normalized.matches("\\d{6}")) {
            return false;
        }
        long currentWindow = Instant.now().getEpochSecond() / DEFAULT_PERIOD_SECONDS;
        for (int offset = -1; offset <= 1; offset++) {
            String expected = generateCode(secret, currentWindow + offset);
            if (normalized.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    public String buildOtpAuthUri(String issuer, String username, String secret) {
        String safeIssuer = issuer != null && !issuer.isBlank() ? issuer : "Athena ECM";
        String safeUsername = username != null ? username : "user";
        String label = urlEncode(safeIssuer + ":" + safeUsername);
        return String.format(
            "otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d",
            label,
            secret,
            urlEncode(safeIssuer),
            DEFAULT_DIGITS,
            DEFAULT_PERIOD_SECONDS
        );
    }

    private String generateCode(String secret, long counter) {
        byte[] key = base32Decode(secret);
        ByteBuffer buffer = ByteBuffer.allocate(8).putLong(counter);
        byte[] hash = hmacSha1(key, buffer.array());
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
            | ((hash[offset + 1] & 0xFF) << 16)
            | ((hash[offset + 2] & 0xFF) << 8)
            | (hash[offset + 3] & 0xFF);
        int otp = binary % (int) Math.pow(10, DEFAULT_DIGITS);
        return String.format("%0" + DEFAULT_DIGITS + "d", otp);
    }

    private byte[] hmacSha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(data);
        } catch (Exception e) {
            log.error("Failed to compute HMAC", e);
            throw new IllegalStateException("Unable to generate TOTP code", e);
        }
    }

    private String base32Encode(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : data) {
            buffer <<= 8;
            buffer |= value & 0xFF;
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                bitsLeft -= 5;
                result.append(BASE32_ALPHABET.charAt(index));
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            result.append(BASE32_ALPHABET.charAt(index));
        }
        return result.toString();
    }

    private byte[] base32Decode(String value) {
        if (value == null || value.isBlank()) {
            return new byte[0];
        }
        String normalized = value.replace("=", "").toUpperCase();
        ByteBuffer buffer = ByteBuffer.allocate(normalized.length() * 5 / 8 + 1);
        int bitsLeft = 0;
        int current = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            int index = BASE32_ALPHABET.indexOf(ch);
            if (index < 0) {
                continue;
            }
            current = (current << 5) | index;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                buffer.put((byte) ((current >> (bitsLeft - 8)) & 0xFF));
                bitsLeft -= 8;
            }
        }
        buffer.flip();
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
