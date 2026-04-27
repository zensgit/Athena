package com.ecm.core.controller;

import com.ecm.core.integration.webhook.WebhookSubscriptionService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WebhookController.class)
@ContextConfiguration(classes = {
    WebhookController.class,
    RestExceptionHandler.class,
    WebhookControllerSecurityTest.TestSecurityConfig.class
})
class WebhookControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookSubscriptionService subscriptionService;

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
    @DisplayName("unauthenticated GET /webhooks returns 401")
    void unauthenticatedListReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated GET /webhooks/event-types returns 401")
    void unauthenticatedEventTypesReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks/event-types"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated POST /webhooks returns 401")
    void unauthenticatedCreateReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("unauthenticated DELETE /webhooks/{id} returns 401")
    void unauthenticatedDeleteReturns401() throws Exception {
        mockMvc.perform(delete("/api/v1/webhooks/{id}", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("non-admin authenticated user cannot list webhooks (hasRole ADMIN)")
    void nonAdminCannotListWebhooks() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("non-admin authenticated user cannot create webhook")
    void nonAdminCannotCreateWebhook() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\",\"url\":\"https://example.com\",\"enabled\":true}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("non-admin authenticated user cannot delete webhook")
    void nonAdminCannotDeleteWebhook() throws Exception {
        mockMvc.perform(delete("/api/v1/webhooks/{id}", UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admin can list webhooks")
    void adminCanListWebhooks() throws Exception {
        when(subscriptionService.list()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/webhooks"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("admin can read webhook event types")
    void adminCanReadEventTypes() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks/event-types"))
            .andExpect(status().isOk());
    }
}
