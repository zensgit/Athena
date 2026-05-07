package com.ecm.core.integration.oauth;

import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.integration.oauth.repository.OAuthCredentialRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthCredentialAdminServiceTest {

    @Mock
    private OAuthCredentialRepository oauthCredentialRepository;

    @Mock
    private OAuthCredentialService oauthCredentialService;

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

    @Test
    @DisplayName("requireReauth clears owner tokens and returns redacted inventory")
    void requireReauthClearsOwnerTokensAndReturnsRedactedInventory() {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID ownerId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        OAuthCredentialInventoryItem item = new OAuthCredentialInventoryItem(
            credentialId,
            "MAIL_ACCOUNT",
            ownerId,
            OAuthProviderType.GOOGLE,
            true,
            false,
            true,
            true,
            false,
            false,
            false,
            null,
            LocalDateTime.parse("2026-05-01T09:00:00"),
            LocalDateTime.parse("2026-05-06T10:00:00")
        );
        when(oauthCredentialRepository.findOwnerReferenceById(credentialId))
            .thenReturn(Optional.of(new OAuthCredentialOwnerReference(credentialId, "MAIL_ACCOUNT", ownerId)));
        when(oauthCredentialRepository.findInventoryItemById(credentialId)).thenReturn(Optional.of(item));

        OAuthCredentialInventoryItem result = oauthCredentialAdminService.requireReauth(credentialId);

        assertEquals(item, result);
        verify(oauthCredentialService).clearTokens("MAIL_ACCOUNT", ownerId);
    }

    @Test
    @DisplayName("requireReauth rejects unknown credential id")
    void requireReauthRejectsUnknownCredentialId() {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        when(oauthCredentialRepository.findOwnerReferenceById(credentialId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> oauthCredentialAdminService.requireReauth(credentialId));
    }

    @Test
    @DisplayName("refreshNow refreshes owner token and returns redacted inventory")
    void refreshNowRefreshesOwnerTokenAndReturnsRedactedInventory() {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID ownerId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        OAuthCredentialInventoryItem item = new OAuthCredentialInventoryItem(
            credentialId,
            "MAIL_ACCOUNT",
            ownerId,
            OAuthProviderType.GOOGLE,
            true,
            false,
            true,
            true,
            true,
            true,
            true,
            LocalDateTime.parse("2026-05-06T12:00:00"),
            LocalDateTime.parse("2026-05-01T09:00:00"),
            LocalDateTime.parse("2026-05-06T11:00:00")
        );
        when(oauthCredentialRepository.findOwnerReferenceById(credentialId))
            .thenReturn(Optional.of(new OAuthCredentialOwnerReference(credentialId, "MAIL_ACCOUNT", ownerId)));
        when(oauthCredentialRepository.findInventoryItemById(credentialId)).thenReturn(Optional.of(item));

        OAuthCredentialInventoryItem result = oauthCredentialAdminService.refreshNow(credentialId);

        assertEquals(item, result);
        verify(oauthCredentialService).refreshAccessTokenNow("MAIL_ACCOUNT", ownerId);
    }

    @Test
    @DisplayName("refreshNow rejects unknown credential id")
    void refreshNowRejectsUnknownCredentialId() {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        when(oauthCredentialRepository.findOwnerReferenceById(credentialId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> oauthCredentialAdminService.refreshNow(credentialId));
    }

    @Test
    @DisplayName("revokeProvider revokes owner tokens at provider and returns redacted inventory")
    void revokeProviderRevokesOwnerTokensAndReturnsRedactedInventory() {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID ownerId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        OAuthCredentialInventoryItem item = new OAuthCredentialInventoryItem(
            credentialId,
            "MAIL_ACCOUNT",
            ownerId,
            OAuthProviderType.GOOGLE,
            true,
            false,
            true,
            true,
            false,
            false,
            false,
            null,
            LocalDateTime.parse("2026-05-01T09:00:00"),
            LocalDateTime.parse("2026-05-07T10:00:00")
        );
        when(oauthCredentialRepository.findOwnerReferenceById(credentialId))
            .thenReturn(Optional.of(new OAuthCredentialOwnerReference(credentialId, "MAIL_ACCOUNT", ownerId)));
        when(oauthCredentialRepository.findInventoryItemById(credentialId)).thenReturn(Optional.of(item));

        OAuthCredentialInventoryItem result = oauthCredentialAdminService.revokeProvider(credentialId);

        assertEquals(item, result);
        verify(oauthCredentialService).revokeProviderTokens("MAIL_ACCOUNT", ownerId);
    }

    @Test
    @DisplayName("revokeProvider rejects unknown credential id")
    void revokeProviderRejectsUnknownCredentialId() {
        UUID credentialId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        when(oauthCredentialRepository.findOwnerReferenceById(credentialId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> oauthCredentialAdminService.revokeProvider(credentialId));
    }
}
