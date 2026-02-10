package com.ecm.core.integration.mail.service;

import java.util.UUID;

/**
 * Raised when the OAuth refresh token has been revoked/expired (for example "invalid_grant"),
 * and the user must reconnect OAuth to continue fetching mail.
 *
 * Message is intentionally safe for UI display (no secrets).
 */
public class MailOAuthReauthRequiredException extends RuntimeException {

    private final UUID accountId;
    private final String oauthError;
    private final String oauthErrorDescription;

    public MailOAuthReauthRequiredException(UUID accountId, String oauthError, String oauthErrorDescription) {
        super(buildMessage(oauthError, oauthErrorDescription));
        this.accountId = accountId;
        this.oauthError = oauthError;
        this.oauthErrorDescription = oauthErrorDescription;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getOauthError() {
        return oauthError;
    }

    public String getOauthErrorDescription() {
        return oauthErrorDescription;
    }

    private static String buildMessage(String oauthError, String oauthErrorDescription) {
        String error = oauthError == null ? "" : oauthError.trim();
        if (error.isBlank()) {
            error = "unknown";
        }
        String description = oauthErrorDescription == null ? "" : oauthErrorDescription.trim();
        if (description.isBlank()) {
            return "OAUTH_REAUTH_REQUIRED: " + error;
        }
        return "OAUTH_REAUTH_REQUIRED: " + error + " - " + description;
    }
}

