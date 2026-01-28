package com.ecm.core.integration.mail.controller;

import com.ecm.core.integration.mail.repository.MailAccountRepository;
import com.ecm.core.integration.mail.repository.MailRuleRepository;
import com.ecm.core.integration.mail.service.MailFetcherService;
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

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can access mail diagnostics")
    void diagnosticsAllowsAdmin() throws Exception {
        Mockito.when(fetcherService.getDiagnostics(5))
            .thenReturn(new MailFetcherService.MailDiagnosticsResult(5, List.of(), List.of()));

        mockMvc.perform(get("/api/v1/integration/mail/diagnostics").param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limit").value(5));

        Mockito.verify(fetcherService).getDiagnostics(5);
    }
}
