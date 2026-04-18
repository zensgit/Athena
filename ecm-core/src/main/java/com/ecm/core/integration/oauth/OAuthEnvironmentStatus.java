package com.ecm.core.integration.oauth;

import java.util.List;

public record OAuthEnvironmentStatus(
    boolean oauthAccount,
    boolean configured,
    String credentialKey,
    List<String> missingEnvKeys
) {
}
