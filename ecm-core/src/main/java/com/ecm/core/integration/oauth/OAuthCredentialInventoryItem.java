package com.ecm.core.integration.oauth;

import java.time.LocalDateTime;
import java.util.UUID;

public record OAuthCredentialInventoryItem(
    UUID id,
    String ownerType,
    UUID ownerId,
    OAuthProviderType provider,
    boolean tokenEndpointConfigured,
    boolean revokeEndpointConfigured,
    boolean tenantIdConfigured,
    boolean scopeConfigured,
    boolean credentialKeyConfigured,
    boolean accessTokenStored,
    boolean refreshTokenStored,
    boolean connected,
    LocalDateTime tokenExpiresAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    boolean providerRevokeSupported,
    String providerRevokeUnsupportedReason
) {
    public OAuthCredentialInventoryItem(
        UUID id,
        String ownerType,
        UUID ownerId,
        OAuthProviderType provider,
        boolean tokenEndpointConfigured,
        boolean tenantIdConfigured,
        boolean scopeConfigured,
        boolean credentialKeyConfigured,
        boolean accessTokenStored,
        boolean refreshTokenStored,
        boolean connected,
        LocalDateTime tokenExpiresAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean providerRevokeSupported,
        String providerRevokeUnsupportedReason
    ) {
        this(
            id,
            ownerType,
            ownerId,
            provider,
            tokenEndpointConfigured,
            false,
            tenantIdConfigured,
            scopeConfigured,
            credentialKeyConfigured,
            accessTokenStored,
            refreshTokenStored,
            connected,
            tokenExpiresAt,
            createdAt,
            updatedAt,
            providerRevokeSupported,
            providerRevokeUnsupportedReason
        );
    }
}
