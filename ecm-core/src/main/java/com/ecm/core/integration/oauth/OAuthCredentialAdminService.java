package com.ecm.core.integration.oauth;

import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.integration.oauth.model.OAuthCredential;
import com.ecm.core.integration.oauth.repository.OAuthCredentialRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuthCredentialAdminService {

    private final OAuthCredentialRepository oauthCredentialRepository;
    private final OAuthCredentialService oauthCredentialService;

    public List<OAuthCredentialInventoryItem> listCredentials(String ownerType, OAuthProviderType provider) {
        return oauthCredentialRepository.findInventoryItems(normalize(ownerType), provider).stream()
            .map(this::withResolvedCapability)
            .toList();
    }

    @Transactional
    public OAuthCredentialInventoryItem requireReauth(UUID credentialId) {
        OAuthCredentialOwnerReference owner = oauthCredentialRepository.findOwnerReferenceById(credentialId)
            .orElseThrow(() -> new ResourceNotFoundException("OAuth credential not found: " + credentialId));
        oauthCredentialService.clearTokens(owner.ownerType(), owner.ownerId());
        return withResolvedCapability(oauthCredentialRepository.findInventoryItemById(credentialId)
            .orElseThrow(() -> new ResourceNotFoundException("OAuth credential not found after reauth reset: " + credentialId)));
    }

    @Transactional
    public OAuthCredentialInventoryItem refreshNow(UUID credentialId) {
        OAuthCredentialOwnerReference owner = oauthCredentialRepository.findOwnerReferenceById(credentialId)
            .orElseThrow(() -> new ResourceNotFoundException("OAuth credential not found: " + credentialId));
        oauthCredentialService.refreshAccessTokenNow(owner.ownerType(), owner.ownerId());
        return withResolvedCapability(oauthCredentialRepository.findInventoryItemById(credentialId)
            .orElseThrow(() -> new ResourceNotFoundException("OAuth credential not found after token refresh: " + credentialId)));
    }

    @Transactional
    public OAuthCredentialInventoryItem revokeProvider(UUID credentialId) {
        OAuthCredentialOwnerReference owner = oauthCredentialRepository.findOwnerReferenceById(credentialId)
            .orElseThrow(() -> new ResourceNotFoundException("OAuth credential not found: " + credentialId));
        oauthCredentialService.revokeProviderTokens(owner.ownerType(), owner.ownerId());
        return withResolvedCapability(oauthCredentialRepository.findInventoryItemById(credentialId)
            .orElseThrow(() -> new ResourceNotFoundException("OAuth credential not found after provider revoke: " + credentialId)));
    }

    @Transactional
    public OAuthCredentialInventoryItem updateRevokeEndpoint(UUID credentialId, String revokeEndpoint) {
        OAuthCredential credential = oauthCredentialRepository.findById(credentialId)
            .orElseThrow(() -> new ResourceNotFoundException("OAuth credential not found: " + credentialId));
        if (credential.getProvider() != OAuthProviderType.CUSTOM) {
            throw new IllegalArgumentException("Revoke endpoint can only be configured for CUSTOM OAuth credentials");
        }
        credential.setRevokeEndpoint(normalizeRevokeEndpoint(revokeEndpoint));
        oauthCredentialRepository.save(credential);
        return withResolvedCapability(oauthCredentialRepository.findInventoryItemById(credentialId)
            .orElseThrow(() -> new ResourceNotFoundException("OAuth credential not found after revoke endpoint update: " + credentialId)));
    }

    /**
     * Enrich an inventory item with provider-revoke capability metadata.
     *
     * <p>The capability decision tree mirrors {@code OAuthCredentialService#revokeProviderTokens}
     * exactly so that the UI metadata and the {@code POST /revoke} error path agree on:
     * <ol>
     *   <li>which providers support revoke (GOOGLE plus configured CUSTOM),</li>
     *   <li>which credentials lack a locally stored token to revoke,</li>
     *   <li>and which env-managed-credential-key rows are out of scope.</li>
     * </ol>
     *
     * <p>Inputs are the redacted projection booleans only — never raw token contents.
     */
    private OAuthCredentialInventoryItem withResolvedCapability(OAuthCredentialInventoryItem item) {
        if (item.provider() == OAuthProviderType.CUSTOM) {
            OAuthProviderRevokeCapability capability =
                oauthCredentialService.providerRevokeCapability(item.ownerType(), item.ownerId());
            return withCapability(item, capability);
        }
        return withCapability(item);
    }

    static OAuthCredentialInventoryItem withCapability(OAuthCredentialInventoryItem item) {
        OAuthProviderRevokeCapability capability = staticCapability(item);
        return withCapability(item, capability);
    }

    private static OAuthProviderRevokeCapability staticCapability(OAuthCredentialInventoryItem item) {
        boolean supported;
        String reason;
        if (item.provider() == null) {
            supported = false;
            reason = "Provider-side revoke is only supported for GOOGLE or CUSTOM; this credential is null";
        } else if (item.provider() == OAuthProviderType.MICROSOFT) {
            supported = false;
            reason = "Provider-side revoke is not yet supported for MICROSOFT";
        } else if (item.accessTokenStored() || item.refreshTokenStored()) {
            supported = item.provider() == OAuthProviderType.GOOGLE;
            reason = supported ? null : "Provider-side revoke endpoint is not configured for this CUSTOM credential";
        } else if (item.credentialKeyConfigured()) {
            supported = false;
            reason = "Provider-side revoke requires a locally stored OAuth token; "
                + "this credential row only references env-managed secrets";
        } else {
            supported = false;
            reason = "No locally stored OAuth token to revoke";
        }
        return new OAuthProviderRevokeCapability(supported, reason);
    }

    private static OAuthCredentialInventoryItem withCapability(
        OAuthCredentialInventoryItem item,
        OAuthProviderRevokeCapability capability
    ) {
        return new OAuthCredentialInventoryItem(
            item.id(),
            item.ownerType(),
            item.ownerId(),
            item.provider(),
            item.tokenEndpointConfigured(),
            item.revokeEndpointConfigured(),
            item.tenantIdConfigured(),
            item.scopeConfigured(),
            item.credentialKeyConfigured(),
            item.accessTokenStored(),
            item.refreshTokenStored(),
            item.connected(),
            item.tokenExpiresAt(),
            item.createdAt(),
            item.updatedAt(),
            capability.supported(),
            capability.unsupportedReason()
        );
    }

    private String normalizeRevokeEndpoint(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > 512) {
            throw new IllegalArgumentException("Revoke endpoint must be 512 characters or fewer");
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Revoke endpoint must be a valid absolute HTTPS URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("Revoke endpoint must be a valid absolute HTTPS URL");
        }
        return trimmed;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
