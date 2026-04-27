package com.ecm.core.controller;

import com.ecm.core.integration.email.EmailIngestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EmailIntegrationController.class)
@ContextConfiguration(classes = {
    EmailIntegrationController.class,
    RestExceptionHandler.class,
    EmailIntegrationControllerSecurityTest.TestSecurityConfig.class
})
class EmailIntegrationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmailIngestionService emailIngestionService;

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

    private MockMultipartFile sampleEmail() {
        return new MockMultipartFile(
            "file",
            "msg.eml",
            "message/rfc822",
            "From: alice@example.com\r\nSubject: hi\r\n\r\nbody".getBytes()
        );
    }

    @Test
    @DisplayName("unauthenticated POST /integration/email/ingest returns 401")
    void unauthenticatedIngestReturns401() throws Exception {
        mockMvc.perform(multipart("/api/v1/integration/email/ingest")
                .file(sampleEmail())
                .contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("authenticated user can ingest email (isAuthenticated() gate)")
    void authenticatedUserCanIngestEmail() throws Exception {
        when(emailIngestionService.ingestEmail(any(), any())).thenReturn(null);

        mockMvc.perform(multipart("/api/v1/integration/email/ingest")
                .file(sampleEmail())
                .contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().isOk());
    }
}
