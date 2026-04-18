package com.ecm.core.integration.mail.service;

import com.ecm.core.integration.mail.model.MailAccount;
import com.ecm.core.integration.oauth.OAuthCredentialService;
import com.ecm.core.integration.oauth.OAuthEnvironmentStatus;
import com.ecm.core.integration.oauth.OAuthReauthRequiredException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailOAuthServiceTest {

    @Mock
    private OAuthCredentialService oauthCredentialService;

    @Test
    @DisplayName("Facade converts generic environment status to mail env check result")
    void convertsEnvironmentStatus() {
        MailOAuthService service = new MailOAuthService(oauthCredentialService);
        UUID accountId = UUID.randomUUID();
        when(oauthCredentialService.checkEnvironment(MailOAuthCredentialOwnerAdapter.OWNER_TYPE, accountId))
            .thenReturn(new OAuthEnvironmentStatus(true, false, "GMAIL", List.of("ECM_MAIL_OAUTH_GMAIL_CLIENT_ID")));

        MailFetcherService.OAuthEnvCheckResult result = service.checkOAuthEnv(accountId);

        assertEquals(true, result.oauthAccount());
        assertEquals(false, result.configured());
        assertEquals("GMAIL", result.credentialKey());
        assertEquals(List.of("ECM_MAIL_OAUTH_GMAIL_CLIENT_ID"), result.missingEnvKeys());
    }

    @Test
    @DisplayName("Facade wraps generic reauth exception with mail-specific exception")
    void wrapsGenericReauthException() {
        MailOAuthService service = new MailOAuthService(oauthCredentialService);
        UUID accountId = UUID.randomUUID();
        MailAccount account = new MailAccount();
        account.setId(accountId);

        when(oauthCredentialService.resolveAccessToken(MailOAuthCredentialOwnerAdapter.OWNER_TYPE, accountId))
            .thenThrow(new OAuthReauthRequiredException(
                MailOAuthCredentialOwnerAdapter.OWNER_TYPE,
                accountId,
                "invalid_grant",
                "Token expired"
            ));

        MailOAuthReauthRequiredException exception = assertThrows(
            MailOAuthReauthRequiredException.class,
            () -> service.resolveAccessToken(account)
        );

        assertEquals(accountId, exception.getAccountId());
        assertEquals("invalid_grant", exception.getOauthError());
        assertEquals("Token expired", exception.getOauthErrorDescription());
    }
}
