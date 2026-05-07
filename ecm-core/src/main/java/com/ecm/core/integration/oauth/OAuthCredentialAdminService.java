package com.ecm.core.integration.oauth;

import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.integration.oauth.repository.OAuthCredentialRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OAuthCredentialAdminService {

    private final OAuthCredentialRepository oauthCredentialRepository;
    private final OAuthCredentialService oauthCredentialService;

    public List<OAuthCredentialInventoryItem> listCredentials(String ownerType, OAuthProviderType provider) {
        return oauthCredentialRepository.findInventoryItems(normalize(ownerType), provider);
    }

    @Transactional
    public OAuthCredentialInventoryItem requireReauth(UUID credentialId) {
        OAuthCredentialOwnerReference owner = oauthCredentialRepository.findOwnerReferenceById(credentialId)
            .orElseThrow(() -> new ResourceNotFoundException("OAuth credential not found: " + credentialId));
        oauthCredentialService.clearTokens(owner.ownerType(), owner.ownerId());
        return oauthCredentialRepository.findInventoryItemById(credentialId)
            .orElseThrow(() -> new ResourceNotFoundException("OAuth credential not found after reauth reset: " + credentialId));
    }

    @Transactional
    public OAuthCredentialInventoryItem refreshNow(UUID credentialId) {
        OAuthCredentialOwnerReference owner = oauthCredentialRepository.findOwnerReferenceById(credentialId)
            .orElseThrow(() -> new ResourceNotFoundException("OAuth credential not found: " + credentialId));
        oauthCredentialService.refreshAccessTokenNow(owner.ownerType(), owner.ownerId());
        return oauthCredentialRepository.findInventoryItemById(credentialId)
            .orElseThrow(() -> new ResourceNotFoundException("OAuth credential not found after token refresh: " + credentialId));
    }

    @Transactional
    public OAuthCredentialInventoryItem revokeProvider(UUID credentialId) {
        OAuthCredentialOwnerReference owner = oauthCredentialRepository.findOwnerReferenceById(credentialId)
            .orElseThrow(() -> new ResourceNotFoundException("OAuth credential not found: " + credentialId));
        oauthCredentialService.revokeProviderTokens(owner.ownerType(), owner.ownerId());
        return oauthCredentialRepository.findInventoryItemById(credentialId)
            .orElseThrow(() -> new ResourceNotFoundException("OAuth credential not found after provider revoke: " + credentialId));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
