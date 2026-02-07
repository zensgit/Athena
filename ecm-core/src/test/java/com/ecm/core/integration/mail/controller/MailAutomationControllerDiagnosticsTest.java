package com.ecm.core.integration.mail.controller;

import com.ecm.core.integration.mail.model.ProcessedMail;
import com.ecm.core.integration.mail.repository.MailAccountRepository;
import com.ecm.core.integration.mail.repository.MailRuleRepository;
import com.ecm.core.integration.mail.repository.ProcessedMailRepository;
import com.ecm.core.integration.mail.service.MailFetcherService;
import com.ecm.core.integration.mail.service.MailOAuthService;
import com.ecm.core.integration.mail.service.MailProcessedRetentionService;
import com.ecm.core.integration.mail.service.MailReportingService;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.SecurityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MailAutomationController.class)
@ContextConfiguration(classes = {
    MailAutomationController.class,
    MailAutomationControllerDiagnosticsTest.TestSecurityConfig.class
})
class MailAutomationControllerDiagnosticsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MailAccountRepository accountRepository;

    @MockBean
    private MailRuleRepository ruleRepository;

    @MockBean
    private MailFetcherService fetcherService;

    @MockBean
    private MailOAuthService oauthService;

    @MockBean
    private MailProcessedRetentionService retentionService;

    @MockBean
    private MailReportingService reportingService;

    @MockBean
    private ProcessedMailRepository processedMailRepository;

    @MockBean
    private AuditService auditService;

    @MockBean
    private SecurityService securityService;

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll()
                )
                .httpBasic(basic -> {});
            return http.build();
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Mail diagnostics returns processed and document samples")
    void diagnosticsReturnsSamples() throws Exception {
        UUID accountId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 1, 28, 10, 30);

        var processed = new MailFetcherService.ProcessedMailDiagnosticItem(
            UUID.randomUUID(),
            now,
            ProcessedMail.Status.PROCESSED,
            accountId,
            "gmail-imap",
            ruleId,
            "gmail-attachments",
            "INBOX",
            "12345",
            "subject",
            null
        );

        var document = new MailFetcherService.MailDocumentDiagnosticItem(
            docId,
            "mail-attachment.pdf",
            "/Root/Documents/mail-attachment.pdf",
            now,
            "admin",
            "application/pdf",
            1024L,
            accountId,
            "gmail-imap",
            ruleId,
            "gmail-attachments",
            "INBOX",
            "12345"
        );

        Mockito.when(fetcherService.getDiagnostics(2, null, null, null, null, null, null, null, null, null))
            .thenReturn(new MailFetcherService.MailDiagnosticsResult(2, List.of(processed), List.of(document)));

        mockMvc.perform(get("/api/v1/integration/mail/diagnostics").param("limit", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limit").value(2))
            .andExpect(jsonPath("$.recentProcessed[0].status").value("PROCESSED"))
            .andExpect(jsonPath("$.recentProcessed[0].accountName").value("gmail-imap"))
            .andExpect(jsonPath("$.recentProcessed[0].ruleName").value("gmail-attachments"))
            .andExpect(jsonPath("$.recentDocuments[0].name").value("mail-attachment.pdf"))
            .andExpect(jsonPath("$.recentDocuments[0].mimeType").value("application/pdf"))
            .andExpect(jsonPath("$.recentDocuments[0].accountName").value("gmail-imap"))
            .andExpect(jsonPath("$.recentDocuments[0].ruleName").value("gmail-attachments"));

        Mockito.verify(fetcherService).getDiagnostics(2, null, null, null, null, null, null, null, null, null);
    }
}
