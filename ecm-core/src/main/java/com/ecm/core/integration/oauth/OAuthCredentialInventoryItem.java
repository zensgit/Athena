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
    String providerRevokeUnsupportedReason,
    OAuthRevokeCapabilityMode providerRevokeMode
) {
    public OAuthCredentialInventoryItem(
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
        this(
            id,
            ownerType,
            ownerId,
            provider,
            tokenEndpointConfigured,
            revokeEndpointConfigured,
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
            providerRevokeUnsupportedReason,
            defaultMode(providerRevokeSupported)
        );
    }

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
            providerRevokeUnsupportedReason,
            defaultMode(providerRevokeSupported)
        );
    }

    private static OAuthRevokeCapabilityMode defaultMode(boolean providerRevokeSupported) {
        return providerRevokeSupported
            ? OAuthRevokeCapabilityMode.PROVIDER_REVOKE
            : OAuthRevokeCapabilityMode.UNSUPPORTED;
    }
}
