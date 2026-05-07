package com.ecm.core.integration.mail.controller;

import com.ecm.core.integration.mail.repository.MailAccountRepository;
import com.ecm.core.integration.mail.repository.MailRuleRepository;
import com.ecm.core.integration.mail.repository.ProcessedMailRepository;
import com.ecm.core.integration.mail.service.MailFetcherService;
import com.ecm.core.integration.mail.service.MailOAuthCredentialOwnerAdapter;
import com.ecm.core.integration.mail.service.MailOAuthService;
import com.ecm.core.integration.mail.service.MailProcessedRetentionService;
import com.ecm.core.integration.mail.service.MailReportScheduledExportService;
import com.ecm.core.integration.mail.service.MailReportingService;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.SecurityService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
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

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MailAutomationController.class)
@ContextConfiguration(classes = {
    MailAutomationController.class,
    MailAutomationControllerSecurityTest.TestSecurityConfig.class
})
class MailAutomationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MailAccountRepository accountRepository;

    @MockBean
    private MailRuleRepository ruleRepository;

    @MockBean
    private MailFetcherService fetcherService;

    @MockBean
    private MailOAuthCredentialOwnerAdapter oauthOwnerAdapter;

    @MockBean
    private MailOAuthService oauthService;

    @MockBean
    private MailProcessedRetentionService retentionService;

    @MockBean
    private MailReportingService reportingService;

    @MockBean
    private MailReportScheduledExportService scheduledExportService;

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
    @DisplayName("Mail diagnostics requires authentication")
    void diagnosticsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/integration/mail/diagnostics"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Mail diagnostics requires admin role")
    void diagnosticsRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/integration/mail/diagnostics"))
            .andExpect(status().isForbidden());

        Mockito.verifyNoInteractions(fetcherService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Mail diagnostics export requires admin role")
    void diagnosticsExportRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/integration/mail/diagnostics/export"))
            .andExpect(status().isForbidden());

        Mockito.verifyNoInteractions(fetcherService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Processed retention requires admin role")
    void processedRetentionRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/integration/mail/processed/retention"))
            .andExpect(status().isForbidden());

        Mockito.verifyNoInteractions(retentionService);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Processed cleanup requires admin role")
    void processedCleanupRequiresAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/integration/mail/processed/cleanup"))
            .andExpect(status().isForbidden());

        Mockito.verifyNoInteractions(retentionService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can access mail diagnostics")
    void diagnosticsAllowsAdmin() throws Exception {
        Mockito.when(fetcherService.getDiagnostics(5, null, null, null, null, null, null, null, null, null))
            .thenReturn(new MailFetcherService.MailDiagnosticsResult(5, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/integration/mail/diagnostics").param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limit").value(5))
            .andExpect(jsonPath("$.recentProcessed").isArray())
            .andExpect(jsonPath("$.recentDocuments").isArray());

        Mockito.verify(fetcherService).getDiagnostics(5, null, null, null, null, null, null, null, null, null);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can access processed retention info")
    void processedRetentionAllowsAdmin() throws Exception {
        Mockito.when(retentionService.getRetentionDays()).thenReturn(90);
        Mockito.when(retentionService.isRetentionEnabled()).thenReturn(true);
        Mockito.when(retentionService.getExpiredCount()).thenReturn(4L);

        mockMvc.perform(get("/api/v1/integration/mail/processed/retention"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.retentionDays").value(90))
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.expiredCount").value(4));

        Mockito.verify(retentionService).getRetentionDays();
        Mockito.verify(retentionService).isRetentionEnabled();
        Mockito.verify(retentionService).getExpiredCount();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can trigger processed cleanup")
    void processedCleanupAllowsAdmin() throws Exception {
        Mockito.when(retentionService.manualCleanupExpiredProcessedMail()).thenReturn(3L);

        mockMvc.perform(post("/api/v1/integration/mail/processed/cleanup"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(3));

        Mockito.verify(retentionService).manualCleanupExpiredProcessedMail();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Mail diagnostics uses default limit when not provided")
    void diagnosticsUsesDefaultLimit() throws Exception {
        Mockito.when(fetcherService.getDiagnostics(null, null, null, null, null, null, null, null, null, null))
            .thenReturn(new MailFetcherService.MailDiagnosticsResult(25, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/integration/mail/diagnostics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limit").value(25))
            .andExpect(jsonPath("$.recentProcessed").isArray())
            .andExpect(jsonPath("$.recentDocuments").isArray());

        Mockito.verify(fetcherService).getDiagnostics(null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("Provider presets endpoint requires authentication")
    void providerPresetsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/integration/mail/provider-presets"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Provider presets endpoint requires admin role")
    void providerPresetsRequireAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/integration/mail/provider-presets"))
            .andExpect(status().isForbidden());

        Mockito.verifyNoInteractions(fetcherService, accountRepository);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin gets the 5 IMAP presets in fixed order, with no credential fields exposed")
    void providerPresetsAllowAdminAndExposeNoSecrets() throws Exception {
        // The response shape is the contract Package B consumes. Ordering and
        // host/port/security values are load-bearing — DO NOT relax these
        // assertions without coordinating with Package B.
        String body = mockMvc.perform(get("/api/v1/integration/mail/provider-presets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(5))
            // Order: ALIYUN_QIYE, TENCENT_EXMAIL, TENCENT_EXMAIL_OVERSEAS, MAIL_263, MAIL_263_OVERSEAS.
            .andExpect(jsonPath("$[0].id").value("ALIYUN_QIYE"))
            .andExpect(jsonPath("$[0].imapHost").value("imap.qiye.aliyun.com"))
            .andExpect(jsonPath("$[0].imapPort").value(993))
            .andExpect(jsonPath("$[0].imapSecurity").value("SSL"))
            .andExpect(jsonPath("$[1].id").value("TENCENT_EXMAIL"))
            .andExpect(jsonPath("$[1].imapHost").value("imap.exmail.qq.com"))
            .andExpect(jsonPath("$[1].imapPort").value(993))
            .andExpect(jsonPath("$[1].imapSecurity").value("SSL"))
            .andExpect(jsonPath("$[2].id").value("TENCENT_EXMAIL_OVERSEAS"))
            .andExpect(jsonPath("$[2].imapHost").value("hwimap.exmail.qq.com"))
            .andExpect(jsonPath("$[2].imapPort").value(993))
            .andExpect(jsonPath("$[2].imapSecurity").value("SSL"))
            .andExpect(jsonPath("$[3].id").value("MAIL_263"))
            .andExpect(jsonPath("$[3].imapHost").value("imap.263.net"))
            .andExpect(jsonPath("$[3].imapPort").value(993))
            .andExpect(jsonPath("$[3].imapSecurity").value("SSL"))
            .andExpect(jsonPath("$[4].id").value("MAIL_263_OVERSEAS"))
            .andExpect(jsonPath("$[4].imapHost").value("imapw.263.net"))
            .andExpect(jsonPath("$[4].imapPort").value(993))
            .andExpect(jsonPath("$[4].imapSecurity").value("SSL"))
            // Each preset has a Chinese label; presence is verified, exact wording
            // is not — we only need the field to be non-blank.
            .andExpect(jsonPath("$[0].label").isNotEmpty())
            .andExpect(jsonPath("$[1].label").isNotEmpty())
            .andExpect(jsonPath("$[2].label").isNotEmpty())
            .andExpect(jsonPath("$[3].label").isNotEmpty())
            .andExpect(jsonPath("$[4].label").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        // Hard guarantee: the static metadata response must never carry any
        // credential field. These names match the property paths used by
        // MailAccount/MailAccountResponse, so a regression that accidentally
        // wires in account credentials would surface here.
        org.assertj.core.api.Assertions.assertThat(body)
            .doesNotContain("\"password\"")
            .doesNotContain("\"oauthAccessToken\"")
            .doesNotContain("\"oauthRefreshToken\"")
            .doesNotContain("\"oauthClientSecret\"")
            .doesNotContain("\"oauthClientId\"");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can export mail diagnostics")
    void diagnosticsExportAllowsAdmin() throws Exception {
        Mockito.when(securityService.getCurrentUser()).thenReturn("admin");
        Mockito.when(fetcherService.exportDiagnosticsCsv(
            Mockito.eq(5),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.eq("run-abc"),
            Mockito.anyString(),
            Mockito.eq("admin"),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull()
        ))
            .thenReturn("Mail Diagnostics Export\n");

        mockMvc.perform(get("/api/v1/integration/mail/diagnostics/export").param("limit", "5").param("runId", "run-abc"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", Matchers.containsString("text/csv")))
            .andExpect(content().string(Matchers.containsString("Mail Diagnostics Export")));

        Mockito.verify(fetcherService).exportDiagnosticsCsv(
            Mockito.eq(5),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.eq("run-abc"),
            Mockito.anyString(),
            Mockito.eq("admin"),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull(),
            Mockito.isNull()
        );
        Mockito.verify(auditService).logEvent(
            Mockito.eq("MAIL_DIAGNOSTICS_EXPORTED"),
            Mockito.isNull(),
            Mockito.eq("MAIL_DIAGNOSTICS"),
            Mockito.eq("admin"),
            ArgumentMatchers.argThat(details -> details.contains("limit=5") && details.contains("runId=run-abc"))
        );
    }
}
