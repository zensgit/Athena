package com.ecm.core.integration.mail.controller;

import com.ecm.core.integration.mail.repository.MailAccountRepository;
import com.ecm.core.integration.mail.repository.MailRuleRepository;
import com.ecm.core.integration.mail.repository.ProcessedMailRepository;
import com.ecm.core.integration.mail.service.MailFetcherService;
import com.ecm.core.integration.mail.service.MailOAuthService;
import com.ecm.core.integration.mail.service.MailProcessedRetentionService;
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
    private MailOAuthService oauthService;

    @MockBean
    private MailProcessedRetentionService retentionService;

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
        Mockito.when(fetcherService.getDiagnostics(5, null, null, null, null, null, null))
            .thenReturn(new MailFetcherService.MailDiagnosticsResult(5, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/integration/mail/diagnostics").param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limit").value(5))
            .andExpect(jsonPath("$.recentProcessed").isArray())
            .andExpect(jsonPath("$.recentDocuments").isArray());

        Mockito.verify(fetcherService).getDiagnostics(5, null, null, null, null, null, null);
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
        Mockito.when(fetcherService.getDiagnostics(null, null, null, null, null, null, null))
            .thenReturn(new MailFetcherService.MailDiagnosticsResult(25, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/integration/mail/diagnostics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limit").value(25))
            .andExpect(jsonPath("$.recentProcessed").isArray())
            .andExpect(jsonPath("$.recentDocuments").isArray());

        Mockito.verify(fetcherService).getDiagnostics(null, null, null, null, null, null, null);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can export mail diagnostics")
    void diagnosticsExportAllowsAdmin() throws Exception {
        Mockito.when(securityService.getCurrentUser()).thenReturn("admin");
        Mockito.when(fetcherService.exportDiagnosticsCsv(
            5,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        ))
            .thenReturn("Mail Diagnostics Export\n");

        mockMvc.perform(get("/api/v1/integration/mail/diagnostics/export").param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", Matchers.containsString("text/csv")))
            .andExpect(content().string(Matchers.containsString("Mail Diagnostics Export")));

        Mockito.verify(fetcherService).exportDiagnosticsCsv(
            5,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
        Mockito.verify(auditService).logEvent(
            Mockito.eq("MAIL_DIAGNOSTICS_EXPORTED"),
            Mockito.isNull(),
            Mockito.eq("MAIL_DIAGNOSTICS"),
            Mockito.eq("admin"),
            ArgumentMatchers.contains("limit=5")
        );
    }
}
