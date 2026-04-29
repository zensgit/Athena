package com.ecm.core.integration.wopi.controller;

import com.ecm.core.controller.RestExceptionHandler;
import com.ecm.core.integration.wopi.model.WopiHealthResponse;
import com.ecm.core.integration.wopi.service.WopiEditorService;
import com.ecm.core.integration.wopi.service.WopiEditorService.WopiUrlResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security test for {@link WopiIntegrationController}.
 *
 * This is the authenticated application-facing WOPI companion, not the
 * externally-called {@code /wopi/**} host endpoint that is permitAll.
 */
@WebMvcTest(controllers = WopiIntegrationController.class)
@ContextConfiguration(classes = {
    WopiIntegrationController.class,
    RestExceptionHandler.class,
    WopiIntegrationControllerSecurityTest.TestSecurityConfig.class
})
class WopiIntegrationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WopiEditorService wopiEditorService;

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
    @DisplayName("unauthenticated GET /integration/wopi/health returns 401")
    void unauthenticatedHealthReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/integration/wopi/health"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /integration/wopi/url/{documentId} returns 401")
    void unauthenticatedEditorUrlReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/integration/wopi/url/{documentId}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("authenticated GET /integration/wopi/health returns 200")
    void authenticatedHealthReturnsOk() throws Exception {
        when(wopiEditorService.getHealth()).thenReturn(WopiHealthResponse.builder()
            .enabled(true)
            .wopiHostUrl("http://ecm-core:8080")
            .build());

        mockMvc.perform(get("/api/v1/integration/wopi/health"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("authenticated GET /integration/wopi/url/{documentId} returns 200")
    void authenticatedEditorUrlReturnsOk() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(wopiEditorService.generateEditorUrl(eq(documentId), eq("write")))
            .thenReturn(new WopiUrlResponse("https://office.example.com/editor", 1_800_000L));

        mockMvc.perform(get("/api/v1/integration/wopi/url/{documentId}", documentId)
                .param("permission", "write"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("service SecurityException maps to 403 for authenticated editor URL request")
    void serviceSecurityExceptionMapsToForbidden() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(wopiEditorService.generateEditorUrl(eq(documentId), eq("write")))
            .thenThrow(new SecurityException("WOPI editor access denied"));

        mockMvc.perform(get("/api/v1/integration/wopi/url/{documentId}", documentId)
                .param("permission", "write"))
            .andExpect(status().isForbidden());
    }
}
