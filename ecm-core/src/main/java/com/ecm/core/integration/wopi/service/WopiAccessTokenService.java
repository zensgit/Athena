package com.ecm.core.integration.wopi.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived WOPI access token store.
 *
 * WOPI requires passing an access_token to the editor via URL query string.
 * Never embed the user's Keycloak JWT in that URL; instead issue an opaque,
 * time-limited token scoped to a single document.
 *
 * For production/multi-instance deployments, back this store with Redis.
 */
@Service
public class WopiAccessTokenService {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, TokenInfo> tokens = new ConcurrentHashMap<>();

    public String issue(
        UUID documentId,
        String userId,
        String userFriendlyName,
        boolean canWrite,
        Collection<? extends GrantedAuthority> authorities,
        Duration ttl
    ) {
        cleanupExpired();

        Instant expiresAt = Instant.now().plus(ttl != null ? ttl : DEFAULT_TTL);
        List<String> authorityNames = authorities != null
            ? authorities.stream().map(GrantedAuthority::getAuthority).distinct().toList()
            : List.of();

        String token = generateToken();
        tokens.put(token, new TokenInfo(documentId, userId, userFriendlyName, canWrite, authorityNames, expiresAt));
        return token;
    }

    public TokenInfo validate(UUID documentId, String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing WOPI access_token");
        }

        TokenInfo info = tokens.get(token);
        if (info == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid WOPI access_token");
        }

        if (info.isExpired()) {
            tokens.remove(token, info);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Expired WOPI access_token");
        }

        if (documentId != null && !documentId.equals(info.documentId())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "WOPI access_token does not match document");
        }

        return info;
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        for (Map.Entry<String, TokenInfo> entry : tokens.entrySet()) {
            TokenInfo info = entry.getValue();
            if (info == null || info.expiresAt().isBefore(now)) {
                tokens.remove(entry.getKey(), info);
            }
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record TokenInfo(
        UUID documentId,
        String userId,
        String userFriendlyName,
        boolean canWrite,
        List<String> authorities,
        Instant expiresAt
    ) {
        public boolean isExpired() {
            return expiresAt == null || expiresAt.isBefore(Instant.now());
        }
    }
}

