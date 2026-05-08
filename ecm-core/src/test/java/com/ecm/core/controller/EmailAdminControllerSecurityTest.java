package com.ecm.core.controller;

import com.ecm.core.integration.email.notify.EmailAdminTestService;
import com.ecm.core.integration.email.notify.EmailAdminTestService.EmailTestSmtpResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer security and shape tests for {@link EmailAdminController}.
 *
 * <p>Mirrors the structure of {@code OAuthCredentialAdminControllerSecurityTest}:
 * {@code @WebMvcTest} + an inner {@code TestSecurityConfig} that mirrors
 * production rules ({@code /api/** authenticated}, http-basic). The service
 * itself is mocked — the EmailAdminTestService logic is covered by
 * {@code EmailAdminTestServiceTest}.</p>
 */
@WebMvcTest(EmailAdminController.class)
@ContextConfiguration(classes = {
    EmailAdminController.class,
    RestExceptionHandler.class,
    EmailAdminControllerSecurityTest.TestSecurityConfig.class
})
class EmailAdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmailAdminTestService emailAdminTestService;

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
    @DisplayName("anonymous test-smtp request returns 401 and never invokes the service")
    void anonymousTestSmtpReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/admin/email/test-smtp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"to\":\"operator@example.com\"}"))
            .andExpect(status().isUnauthorized());

        verify(emailAdminTestService, never()).sendTestMessage(any());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("non-admin role gets 403 — proves @PreAuthorize fires")
    void nonAdminRoleReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/email/test-smtp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"to\":\"operator@example.com\"}"))
            .andExpect(status().isForbidden());

        verify(emailAdminTestService, never()).sendTestMessage(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admin success returns 200 with ok=true; response carries no token/secret field")
    void adminSuccessReturnsOk() throws Exception {
        when(emailAdminTestService.sendTestMessage("operator@example.com")).thenReturn(
            new EmailTestSmtpResult(
                true,
                "Test message dispatched",
                "smtp.example.com",
                465,
                "athena@example.com",
                null
            )
        );

        String body = mockMvc.perform(post("/api/v1/admin/email/test-smtp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"to\":\"operator@example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok", is(true)))
            .andExpect(jsonPath("$.message", is("Test message dispatched")))
            .andExpect(jsonPath("$.smtpHost", is("smtp.example.com")))
            .andExpect(jsonPath("$.smtpPort", is(465)))
            .andExpect(jsonPath("$.fromAddress", is("athena@example.com")))
            // Diagnostic must be null (not absent) on success — Package B
            // checks for null to gate the "show diagnostic" UI.
            .andExpect(jsonPath("$.diagnostic").doesNotExist())
            .andReturn().getResponse().getContentAsString();

        // Belt-and-braces guard against accidental token disclosure: the
        // response record doesn't carry these fields, but the test exists
        // to catch a future regression that adds them.
        org.assertj.core.api.Assertions.assertThat(body)
            .doesNotContain("\"password\"")
            .doesNotContain("\"accessToken\"")
            .doesNotContain("\"refreshToken\"")
            .doesNotContain("\"oauthClientSecret\"")
            .doesNotContain("\"oauthClientId\"");

        verify(emailAdminTestService).sendTestMessage("operator@example.com");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admin failure surfaces ok=false with diagnostic but still returns 200")
    void adminFailureSurfacesDiagnostic() throws Exception {
        when(emailAdminTestService.sendTestMessage("operator@example.com")).thenReturn(
            new EmailTestSmtpResult(
                false,
                "SMTP send failed",
                "smtp.example.com",
                465,
                "athena@example.com",
                "Connection refused: connect"
            )
        );

        // 200 (not 5xx) is intentional — the operator endpoint always
        // succeeds at returning a structured diagnostic; only the SMTP
        // dispatch attempt failed.
        mockMvc.perform(post("/api/v1/admin/email/test-smtp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"to\":\"operator@example.com\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok", is(false)))
            .andExpect(jsonPath("$.message", is("SMTP send failed")))
            .andExpect(jsonPath("$.smtpHost", is("smtp.example.com")))
            .andExpect(jsonPath("$.smtpPort", is(465)))
            .andExpect(jsonPath("$.fromAddress", is("athena@example.com")))
            .andExpect(jsonPath("$.diagnostic", is("Connection refused: connect")));

        verify(emailAdminTestService).sendTestMessage("operator@example.com");
    }
}
