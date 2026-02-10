package com.ecm.core.integration.mail.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;

/**
 * Parses OAuth token endpoint error payloads (best-effort).
 *
 * We keep this logic isolated so we can:
 * - produce safe, user-facing error messages without leaking secrets
 * - classify non-retryable errors like "invalid_grant" (reauth required)
 */
final class MailOAuthTokenErrorParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MailOAuthTokenErrorParser() {
        // utility
    }

    record OAuthTokenError(String error, String errorDescription) {
    }

    static Optional<OAuthTokenError> parse(String body) {
        if (body == null) {
            return Optional.empty();
        }
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        // Common providers return JSON: {"error":"invalid_grant","error_description":"..."}
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = OBJECT_MAPPER.readValue(trimmed, Map.class);
                Object error = payload.get("error");
                if (error == null) {
                    return Optional.empty();
                }
                Object desc = payload.get("error_description");
                return Optional.of(new OAuthTokenError(
                    String.valueOf(error),
                    desc != null ? String.valueOf(desc) : null
                ));
            } catch (Exception ignored) {
                // Fall through to heuristics.
            }
        }

        // Fallback heuristics for non-JSON responses (or unexpected formats).
        String lower = trimmed.toLowerCase();
        if (lower.contains("invalid_grant")) {
            return Optional.of(new OAuthTokenError("invalid_grant", null));
        }
        if (lower.contains("invalid_client")) {
            return Optional.of(new OAuthTokenError("invalid_client", null));
        }
        if (lower.contains("unauthorized_client")) {
            return Optional.of(new OAuthTokenError("unauthorized_client", null));
        }

        return Optional.empty();
    }
}

