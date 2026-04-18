package com.ecm.core.integration.oauth;

import java.time.LocalDateTime;
import java.util.UUID;

public interface OAuthCredentialOwnerAdapter {

    String ownerType();

    OAuthCredentialOwner loadOwner(UUID ownerId);

    OAuthCredentialOwner saveTokens(UUID ownerId, String accessToken, String refreshToken, LocalDateTime expiresAt);

    OAuthCredentialOwner clearTokens(UUID ownerId);

    String buildCredentialEnvKey(String normalizedCredentialKey, String suffix);

    String buildProviderEnvKey(OAuthProviderType provider, String suffix);
}
