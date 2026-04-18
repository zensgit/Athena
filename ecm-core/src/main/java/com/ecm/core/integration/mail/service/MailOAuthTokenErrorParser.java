package com.ecm.core.integration.mail.service;

import com.ecm.core.integration.oauth.OAuthTokenErrorParser;

import java.util.Optional;

/**
 * Parses OAuth token endpoint error payloads (best-effort).
 *
 * We keep this logic isolated so we can:
 * - produce safe, user-facing error messages without leaking secrets
 * - classify non-retryable errors like "invalid_grant" (reauth required)
 */
final class MailOAuthTokenErrorParser {

    private MailOAuthTokenErrorParser() {
        // utility
    }

    record OAuthTokenError(String error, String errorDescription) {
    }

    static Optional<OAuthTokenError> parse(String body) {
        return OAuthTokenErrorParser.parse(body)
            .map(parsed -> new OAuthTokenError(parsed.error(), parsed.errorDescription()));
    }
}
