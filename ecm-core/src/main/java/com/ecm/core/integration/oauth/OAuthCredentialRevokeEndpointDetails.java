package com.ecm.core.integration.oauth;

import java.util.UUID;

public record OAuthCredentialRevokeEndpointDetails(
    UUID id,
    String ownerType,
    UUID ownerId,
    OAuthProviderType provider,
    boolean revokeEndpointConfigured,
    String revokeEndpoint
) {
}
