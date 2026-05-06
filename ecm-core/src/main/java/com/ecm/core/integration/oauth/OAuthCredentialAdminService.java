package com.ecm.core.integration.oauth;

import com.ecm.core.integration.oauth.repository.OAuthCredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OAuthCredentialAdminService {

    private final OAuthCredentialRepository oauthCredentialRepository;

    public List<OAuthCredentialInventoryItem> listCredentials(String ownerType, OAuthProviderType provider) {
        return oauthCredentialRepository.findInventoryItems(normalize(ownerType), provider);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
