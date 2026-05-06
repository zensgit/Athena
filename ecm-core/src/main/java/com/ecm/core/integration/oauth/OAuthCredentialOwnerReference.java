package com.ecm.core.integration.oauth;

import java.util.UUID;

public record OAuthCredentialOwnerReference(
    UUID id,
    String ownerType,
    UUID ownerId
) {
}
