package com.ecm.core.integration.oauth;

import java.util.UUID;

public class OAuthReauthRequiredException extends RuntimeException {

    private final String ownerType;
    private final UUID ownerId;
    private final String error;
    private final String errorDescription;

    public OAuthReauthRequiredException(String ownerType, UUID ownerId, String error, String errorDescription) {
        super(buildMessage(error, errorDescription));
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.error = error;
        this.errorDescription = errorDescription;
    }

    public String getOwnerType() {
        return ownerType;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getError() {
        return error;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    private static String buildMessage(String error, String errorDescription) {
        if (errorDescription == null || errorDescription.isBlank()) {
            return "OAUTH_REAUTH_REQUIRED: " + error;
        }
        return "OAUTH_REAUTH_REQUIRED: " + error + " - " + errorDescription.trim();
    }
}
