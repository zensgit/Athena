package com.ecm.core.integration.mail.service;

import com.ecm.core.integration.email.EmailIngestionService;
import com.ecm.core.integration.mail.model.MailAccount;
import com.ecm.core.integration.mail.repository.MailAccountRepository;
import com.ecm.core.integration.mail.repository.MailRuleRepository;
import com.ecm.core.integration.mail.repository.ProcessedMailRepository;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.service.DocumentUploadService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.TagService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailFetcherServiceOAuthEnvTest {

    @Mock
    private MailAccountRepository accountRepository;

    @Mock
    private MailRuleRepository ruleRepository;

    @Mock
    private ProcessedMailRepository processedMailRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private DocumentUploadService uploadService;

    @Mock
    private NodeService nodeService;

    @Mock
    private TagService tagService;

    @Mock
    private EmailIngestionService emailIngestionService;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private MailOAuthService mailOAuthService;

    private MailFetcherService service;

    @BeforeEach
    void setUp() {
        service = new MailFetcherService(
            accountRepository,
            ruleRepository,
            processedMailRepository,
            documentRepository,
            nodeRepository,
            uploadService,
            nodeService,
            tagService,
            emailIngestionService,
            meterRegistry,
            mailOAuthService
        );
    }

    @Test
    @DisplayName("checkOAuthEnv delegates to MailOAuthService for non-OAuth accounts")
    void nonOauthAccountIsConfigured() {
        MailAccount account = new MailAccount();
        account.setSecurity(MailAccount.SecurityType.SSL);
        when(mailOAuthService.checkOAuthEnv(account))
            .thenReturn(new MailFetcherService.OAuthEnvCheckResult(false, true, null, List.of()));

        var result = service.checkOAuthEnv(account);

        assertFalse(result.oauthAccount());
        assertTrue(result.configured());
        assertEquals(List.of(), result.missingEnvKeys());
    }

    @Test
    @DisplayName("checkOAuthEnv delegates missing-key results from MailOAuthService")
    void oauthAccountMissingKeys() {
        MailAccount account = new MailAccount();
        account.setSecurity(MailAccount.SecurityType.OAUTH2);
        account.setOauthCredentialKey("gmail-joshua");
        when(mailOAuthService.checkOAuthEnv(account))
            .thenReturn(new MailFetcherService.OAuthEnvCheckResult(
                true,
                false,
                "GMAIL_JOSHUA",
                List.of(
                    "ECM_MAIL_OAUTH_GMAIL_JOSHUA_CLIENT_ID",
                    "ECM_MAIL_OAUTH_GMAIL_JOSHUA_REFRESH_TOKEN"
                )
            ));

        var result = service.checkOAuthEnv(account);

        assertTrue(result.oauthAccount());
        assertFalse(result.configured());
        assertEquals("GMAIL_JOSHUA", result.credentialKey());
        assertEquals(
            List.of(
                "ECM_MAIL_OAUTH_GMAIL_JOSHUA_CLIENT_ID",
                "ECM_MAIL_OAUTH_GMAIL_JOSHUA_REFRESH_TOKEN"
            ),
            result.missingEnvKeys()
        );
    }

    @Test
    @DisplayName("checkOAuthEnv delegates configured results from MailOAuthService")
    void oauthAccountConfiguredWhenEnvPresent() {
        MailAccount account = new MailAccount();
        account.setSecurity(MailAccount.SecurityType.OAUTH2);
        account.setOauthCredentialKey("gmail-joshua");
        when(mailOAuthService.checkOAuthEnv(account))
            .thenReturn(new MailFetcherService.OAuthEnvCheckResult(true, true, "GMAIL_JOSHUA", List.of()));

        var result = service.checkOAuthEnv(account);

        assertTrue(result.oauthAccount());
        assertTrue(result.configured());
        assertEquals(List.of(), result.missingEnvKeys());
    }
}
