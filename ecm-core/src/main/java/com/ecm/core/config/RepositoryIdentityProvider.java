package com.ecm.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for repository identity across CMIS and Transfer subsystems.
 * <p>
 * Configuration keys:
 * <ul>
 *   <li>{@code ecm.cmis.repository-id} — CMIS repository ID, default {@code "athena"}</li>
 *   <li>{@code ecm.transfer.repository-id} — Transfer repository ID, defaults to CMIS value</li>
 * </ul>
 */
@Component
public class RepositoryIdentityProvider {

    public static final String DEFAULT_CMIS_REPOSITORY_ID = "athena";

    private final String cmisRepositoryId;
    private final String transferRepositoryId;

    public RepositoryIdentityProvider(
        @Value("${ecm.cmis.repository-id:" + DEFAULT_CMIS_REPOSITORY_ID + "}") String cmisRepositoryId,
        @Value("${ecm.transfer.repository-id:${ecm.cmis.repository-id:" + DEFAULT_CMIS_REPOSITORY_ID + "}}") String transferRepositoryId
    ) {
        this.cmisRepositoryId = normalize(cmisRepositoryId, DEFAULT_CMIS_REPOSITORY_ID);
        this.transferRepositoryId = normalize(transferRepositoryId, this.cmisRepositoryId);
    }

    public String getCmisRepositoryId() {
        return cmisRepositoryId;
    }

    public String getTransferRepositoryId() {
        return transferRepositoryId;
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
