package com.ecm.core.integration.mail.service;

import com.ecm.core.integration.email.EmailIngestionService;
import com.ecm.core.integration.mail.model.MailAccount;
import com.ecm.core.integration.mail.repository.MailAccountRepository;
import com.ecm.core.integration.mail.repository.MailRuleRepository;
import com.ecm.core.integration.mail.repository.ProcessedMailRepository;
import com.ecm.core.repository.DocumentRepository;
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
import org.springframework.core.env.Environment;

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
    private Environment environment;

    private MailFetcherService service;

    @BeforeEach
    void setUp() {
        service = new MailFetcherService(
            accountRepository,
            ruleRepository,
            processedMailRepository,
            documentRepository,
            uploadService,
            nodeService,
            tagService,
            emailIngestionService,
            meterRegistry,
            environment
        );
    }

    @Test
    @DisplayName("Non-OAuth accounts are always configured")
    void nonOauthAccountIsConfigured() {
        MailAccount account = new MailAccount();
        account.setSecurity(MailAccount.SecurityType.SSL);

        var result = service.checkOAuthEnv(account);

        assertFalse(result.oauthAccount());
        assertTrue(result.configured());
        assertEquals(List.of(), result.missingEnvKeys());
    }

    @Test
    @DisplayName("OAuth accounts report missing required env keys")
    void oauthAccountMissingKeys() {
        MailAccount account = new MailAccount();
        account.setSecurity(MailAccount.SecurityType.OAUTH2);
        account.setOauthCredentialKey("gmail-joshua");

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
    @DisplayName("OAuth accounts are configured when required env keys exist")
    void oauthAccountConfiguredWhenEnvPresent() {
        MailAccount account = new MailAccount();
        account.setSecurity(MailAccount.SecurityType.OAUTH2);
        account.setOauthCredentialKey("gmail-joshua");

        when(environment.getProperty("ECM_MAIL_OAUTH_GMAIL_JOSHUA_CLIENT_ID")).thenReturn("client");
        when(environment.getProperty("ECM_MAIL_OAUTH_GMAIL_JOSHUA_REFRESH_TOKEN")).thenReturn("refresh");

        var result = service.checkOAuthEnv(account);

        assertTrue(result.oauthAccount());
        assertTrue(result.configured());
        assertEquals(List.of(), result.missingEnvKeys());
    }
}
