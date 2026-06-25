package com.ecm.core.integration.oauth;

public record OAuthProviderRevokeCapability(
    boolean supported,
    String unsupportedReason,
    OAuthRevokeCapabilityMode mode
) {
    public OAuthProviderRevokeCapability(boolean supported, String unsupportedReason) {
        this(supported, unsupportedReason, supported
            ? OAuthRevokeCapabilityMode.PROVIDER_REVOKE
            : OAuthRevokeCapabilityMode.UNSUPPORTED);
    }
}
