package com.ecm.core.integration.mail.service;

import com.ecm.core.integration.mail.model.MailAccount;
import com.ecm.core.integration.mail.repository.MailAccountRepository;
import com.ecm.core.integration.oauth.OAuthCredentialOwner;
import com.ecm.core.integration.oauth.OAuthProviderType;
import com.ecm.core.integration.oauth.model.OAuthCredential;
import com.ecm.core.integration.oauth.repository.OAuthCredentialRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailOAuthCredentialOwnerAdapterTest {

    @Mock
    private MailAccountRepository accountRepository;

    @Mock
    private OAuthCredentialRepository oauthCredentialRepository;

    @Test
    @DisplayName("loadOwner prefers generic OAuth credential row when present")
    void loadOwnerPrefersGenericCredential() {
        UUID accountId = UUID.randomUUID();
        MailAccount account = oauthAccount(accountId);
        account.setOauthRefreshToken("legacy-refresh");
        OAuthCredential credential = new OAuthCredential();
        credential.setOwnerType(MailOAuthCredentialOwnerAdapter.OWNER_TYPE);
        credential.setOwnerId(accountId);
        credential.setProvider(OAuthProviderType.GOOGLE);
        credential.setCredentialKey("GMAIL_GENERIC");
        credential.setRefreshToken("generic-refresh");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(oauthCredentialRepository.findByOwnerTypeAndOwnerId(MailOAuthCredentialOwnerAdapter.OWNER_TYPE, accountId))
            .thenReturn(Optional.of(credential));

        MailOAuthCredentialOwnerAdapter adapter =
            new MailOAuthCredentialOwnerAdapter(accountRepository, oauthCredentialRepository);

        OAuthCredentialOwner owner = adapter.loadOwner(accountId);

        assertEquals("GMAIL_GENERIC", owner.credentialKey());
        assertEquals("generic-refresh", owner.refreshToken());
    }

    @Test
    @DisplayName("saveTokens mirrors refreshed token state to generic credential row and mail account")
    void saveTokensMirrorsToCredentialAndMailAccount() {
        UUID accountId = UUID.randomUUID();
        MailAccount account = oauthAccount(accountId);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(1);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(MailAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(oauthCredentialRepository.findByOwnerTypeAndOwnerId(MailOAuthCredentialOwnerAdapter.OWNER_TYPE, accountId))
            .thenReturn(Optional.empty());
        when(oauthCredentialRepository.save(any(OAuthCredential.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MailOAuthCredentialOwnerAdapter adapter =
            new MailOAuthCredentialOwnerAdapter(accountRepository, oauthCredentialRepository);

        OAuthCredentialOwner owner = adapter.saveTokens(accountId, "new-access", "new-refresh", expiresAt);

        assertEquals("new-access", account.getOauthAccessToken());
        assertEquals("new-refresh", account.getOauthRefreshToken());
        assertEquals(expiresAt, account.getOauthTokenExpiresAt());
        assertEquals("new-refresh", owner.refreshToken());
        verify(oauthCredentialRepository).save(any(OAuthCredential.class));
    }

    @Test
    @DisplayName("syncAccount removes generic credential when account stops using OAuth")
    void syncAccountDeletesCredentialWhenOauthDisabled() {
        UUID accountId = UUID.randomUUID();
        MailAccount account = new MailAccount();
        account.setId(accountId);
        account.setSecurity(MailAccount.SecurityType.SSL);

        MailOAuthCredentialOwnerAdapter adapter =
            new MailOAuthCredentialOwnerAdapter(accountRepository, oauthCredentialRepository);

        adapter.syncAccount(account);

        verify(oauthCredentialRepository).deleteByOwnerTypeAndOwnerId(MailOAuthCredentialOwnerAdapter.OWNER_TYPE, accountId);
    }

    @Test
    @DisplayName("clearTokens clears generic and mirrored mail account state")
    void clearTokensClearsBothStores() {
        UUID accountId = UUID.randomUUID();
        MailAccount account = oauthAccount(accountId);
        account.setOauthAccessToken("access");
        account.setOauthRefreshToken("refresh");
        account.setOauthTokenExpiresAt(LocalDateTime.now().plusHours(2));
        OAuthCredential credential = new OAuthCredential();
        credential.setOwnerType(MailOAuthCredentialOwnerAdapter.OWNER_TYPE);
        credential.setOwnerId(accountId);
        credential.setRefreshToken("refresh");
        credential.setAccessToken("access");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(MailAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(oauthCredentialRepository.findByOwnerTypeAndOwnerId(MailOAuthCredentialOwnerAdapter.OWNER_TYPE, accountId))
            .thenReturn(Optional.of(credential));
        when(oauthCredentialRepository.save(any(OAuthCredential.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MailOAuthCredentialOwnerAdapter adapter =
            new MailOAuthCredentialOwnerAdapter(accountRepository, oauthCredentialRepository);

        OAuthCredentialOwner owner = adapter.clearTokens(accountId);

        assertNull(account.getOauthAccessToken());
        assertNull(account.getOauthRefreshToken());
        assertNull(account.getOauthTokenExpiresAt());
        assertNull(owner.accessToken());
        assertNull(owner.refreshToken());
    }

    private MailAccount oauthAccount(UUID accountId) {
        MailAccount account = new MailAccount();
        account.setId(accountId);
        account.setName("gmail");
        account.setSecurity(MailAccount.SecurityType.OAUTH2);
        account.setOauthProvider(MailAccount.OAuthProvider.GOOGLE);
        account.setOauthTokenEndpoint("https://oauth2.googleapis.com/token");
        account.setOauthTenantId("common");
        account.setOauthScope("https://mail.google.com/");
        account.setOauthCredentialKey("GMAIL");
        return account;
    }
}
