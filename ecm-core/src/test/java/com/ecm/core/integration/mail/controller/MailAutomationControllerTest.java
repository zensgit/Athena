package com.ecm.core.integration.mail.controller;

import com.ecm.core.integration.mail.model.MailAccount;
import com.ecm.core.integration.mail.repository.MailAccountRepository;
import com.ecm.core.integration.mail.repository.MailRuleRepository;
import com.ecm.core.integration.mail.repository.ProcessedMailRepository;
import com.ecm.core.integration.mail.service.MailFetcherService;
import com.ecm.core.integration.mail.service.MailOAuthService;
import com.ecm.core.integration.mail.service.MailProcessedRetentionService;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailAutomationControllerTest {

    @Mock
    private MailAccountRepository accountRepository;

    @Mock
    private MailRuleRepository ruleRepository;

    @Mock
    private MailFetcherService fetcherService;

    @Mock
    private MailOAuthService oauthService;

    @Mock
    private MailProcessedRetentionService retentionService;

    @Mock
    private ProcessedMailRepository processedMailRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private SecurityService securityService;

    private MailAutomationController controller;
    private MailAccount lastSavedAccount;

    @BeforeEach
    void setUp() {
        controller = new MailAutomationController(
            accountRepository,
            ruleRepository,
            fetcherService,
            oauthService,
            retentionService,
            processedMailRepository,
            auditService,
            securityService
        );
        lastSavedAccount = null;
        lenient().when(accountRepository.save(any(MailAccount.class))).thenAnswer(invocation -> {
            lastSavedAccount = invocation.getArgument(0);
            return lastSavedAccount;
        });
        lenient().when(fetcherService.checkOAuthEnv(any(MailAccount.class)))
            .thenReturn(new MailFetcherService.OAuthEnvCheckResult(true, true, "TEST", java.util.List.of()));
    }

    @Test
    @DisplayName("Custom OAuth accounts require oauthCredentialKey")
    void createOauthAccountRequiresCredentialKey() {
        var request = new MailAutomationController.MailAccountRequest(
            "gmail",
            "imap.gmail.com",
            993,
            "user@gmail.com",
            "secret",
            MailAccount.SecurityType.OAUTH2,
            true,
            10,
            MailAccount.OAuthProvider.CUSTOM,
            null,
            null,
            "https://mail.google.com/",
            null
        );

        assertThrows(IllegalArgumentException.class, () -> controller.createAccount(request));
    }

    @Test
    @DisplayName("OAuth2 account save clears password and stored secrets")
    void createOauthAccountClearsPasswordAndSecrets() {
        when(fetcherService.checkOAuthEnv(any(MailAccount.class)))
            .thenReturn(
                new MailFetcherService.OAuthEnvCheckResult(
                    true,
                    false,
                    "GMAIL_JOSHUA",
                    List.of("ECM_MAIL_OAUTH_GMAIL_JOSHUA_CLIENT_ID")
                )
            );

        var request = new MailAutomationController.MailAccountRequest(
            "gmail",
            "imap.gmail.com",
            993,
            "user@gmail.com",
            "secret",
            MailAccount.SecurityType.OAUTH2,
            true,
            10,
            MailAccount.OAuthProvider.GOOGLE,
            null,
            null,
            "https://mail.google.com/",
            "gmail_joshua"
        );

        var response = controller.createAccount(request).getBody();

        assertNull(lastSavedAccount.getPassword());
        assertEquals("gmail_joshua", lastSavedAccount.getOauthCredentialKey());
        assertNull(lastSavedAccount.getOauthClientId());
        assertNull(lastSavedAccount.getOauthClientSecret());
        assertNull(lastSavedAccount.getOauthAccessToken());
        assertNull(lastSavedAccount.getOauthRefreshToken());
        assertNull(lastSavedAccount.getOauthTokenExpiresAt());
        assertFalse(response.oauthEnvConfigured());
        assertEquals(List.of("ECM_MAIL_OAUTH_GMAIL_JOSHUA_CLIENT_ID"), response.oauthMissingEnvKeys());
    }

    @Test
    @DisplayName("Switching to non-OAuth clears OAuth configuration")
    void updateNonOauthAccountClearsOauthFields() {
        UUID id = UUID.randomUUID();
        MailAccount existing = new MailAccount();
        existing.setId(id);
        existing.setName("gmail");
        existing.setHost("imap.gmail.com");
        existing.setPort(993);
        existing.setUsername("user@gmail.com");
        existing.setPassword(null);
        existing.setSecurity(MailAccount.SecurityType.OAUTH2);
        existing.setOauthProvider(MailAccount.OAuthProvider.GOOGLE);
        existing.setOauthTokenEndpoint("https://oauth2.googleapis.com/token");
        existing.setOauthTenantId("tenant");
        existing.setOauthScope("https://mail.google.com/");
        existing.setOauthCredentialKey("GMAIL_JOSHUA");
        existing.setOauthClientId("client");
        existing.setOauthClientSecret("secret");
        existing.setOauthAccessToken("access");
        existing.setOauthRefreshToken("refresh");
        existing.setOauthTokenExpiresAt(LocalDateTime.now().plusHours(1));

        when(accountRepository.findById(id)).thenReturn(Optional.of(existing));

        var request = new MailAutomationController.MailAccountRequest(
            null,
            null,
            null,
            null,
            null,
            MailAccount.SecurityType.SSL,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );

        controller.updateAccount(id, request);

        assertEquals(MailAccount.SecurityType.SSL, existing.getSecurity());
        assertNull(existing.getOauthProvider());
        assertNull(existing.getOauthTokenEndpoint());
        assertNull(existing.getOauthTenantId());
        assertNull(existing.getOauthScope());
        assertNull(existing.getOauthCredentialKey());
        assertNull(existing.getOauthClientId());
        assertNull(existing.getOauthClientSecret());
        assertNull(existing.getOauthAccessToken());
        assertNull(existing.getOauthRefreshToken());
        assertNull(existing.getOauthTokenExpiresAt());
    }
}
