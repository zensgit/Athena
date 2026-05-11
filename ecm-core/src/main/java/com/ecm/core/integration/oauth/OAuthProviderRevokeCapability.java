package com.ecm.core.integration.oauth;

public record OAuthProviderRevokeCapability(
    boolean supported,
    String unsupportedReason
) {
}
