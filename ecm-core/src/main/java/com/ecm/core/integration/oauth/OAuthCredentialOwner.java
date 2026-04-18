package com.ecm.core.integration.oauth;

import java.time.LocalDateTime;
import java.util.UUID;

public record OAuthCredentialOwner(
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
}
