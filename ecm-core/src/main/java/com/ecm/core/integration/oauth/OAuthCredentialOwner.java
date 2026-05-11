package com.ecm.core.integration.oauth;

import java.time.LocalDateTime;
import java.util.UUID;

public record OAuthCredentialOwner(
    String ownerType,
    UUID ownerId,
    String ownerName,
    OAuthProviderType provider,
    String tokenEndpoint,
    String revokeEndpoint,
    String tenantId,
    String scope,
    String credentialKey,
    String accessToken,
    String refreshToken,
    LocalDateTime tokenExpiresAt
) {
    public OAuthCredentialOwner(
        String ownerType,
        UUID ownerId,
        String ownerName,
        OAuthProviderType provider,
        String tokenEndpoint,
        String tenantId,
        String scope,
        String credentialKey,
        String accessToken,
        String refreshToken,
        LocalDateTime tokenExpiresAt
    ) {
        this(
            ownerType,
            ownerId,
            ownerName,
            provider,
            tokenEndpoint,
            null,
            tenantId,
            scope,
            credentialKey,
            accessToken,
            refreshToken,
            tokenExpiresAt
        );
    }
}
