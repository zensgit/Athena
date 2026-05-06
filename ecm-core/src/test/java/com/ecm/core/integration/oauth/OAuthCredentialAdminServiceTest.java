package com.ecm.core.integration.oauth;

import com.ecm.core.integration.oauth.repository.OAuthCredentialRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthCredentialAdminServiceTest {

    @Mock
    private OAuthCredentialRepository oauthCredentialRepository;

    @InjectMocks
    private OAuthCredentialAdminService oauthCredentialAdminService;

    @Test
    @DisplayName("listCredentials normalizes blank owner type filter")
    void listCredentialsNormalizesBlankOwnerTypeFilter() {
        when(oauthCredentialRepository.findInventoryItems(null, OAuthProviderType.GOOGLE)).thenReturn(List.of());

        List<OAuthCredentialInventoryItem> result = oauthCredentialAdminService.listCredentials("  ", OAuthProviderType.GOOGLE);

        assertTrue(result.isEmpty());
        verify(oauthCredentialRepository).findInventoryItems(null, OAuthProviderType.GOOGLE);
    }

    @Test
    @DisplayName("listCredentials trims owner type filter")
    void listCredentialsTrimsOwnerTypeFilter() {
        when(oauthCredentialRepository.findInventoryItems("MAIL_ACCOUNT", null)).thenReturn(List.of());

        oauthCredentialAdminService.listCredentials(" MAIL_ACCOUNT ", null);

        verify(oauthCredentialRepository).findInventoryItems("MAIL_ACCOUNT", null);
    }
}
