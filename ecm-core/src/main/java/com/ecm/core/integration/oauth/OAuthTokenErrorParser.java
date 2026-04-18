package com.ecm.core.integration.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;

public final class OAuthTokenErrorParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private OAuthTokenErrorParser() {
    }

    public record OAuthTokenError(String error, String errorDescription) {
    }

    public static Optional<OAuthTokenError> parse(String body) {
        if (body == null) {
            return Optional.empty();
        }
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = OBJECT_MAPPER.readValue(trimmed, Map.class);
                Object error = payload.get("error");
                if (error == null) {
                    return Optional.empty();
                }
                Object description = payload.get("error_description");
                return Optional.of(new OAuthTokenError(
                    String.valueOf(error),
                    description != null ? String.valueOf(description) : null
                ));
            } catch (Exception ignored) {
                // Fall through to heuristic parsing.
            }
        }

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
