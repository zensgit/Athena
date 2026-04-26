package com.ecm.core.controller;

import com.ecm.core.service.SiteInvitationService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SiteInvitationController.class)
@ContextConfiguration(classes = {
    SiteInvitationController.class,
    RestExceptionHandler.class,
    SiteInvitationControllerSecurityTest.TestSecurityConfig.class
})
class SiteInvitationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SiteInvitationService invitationService;

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
    @DisplayName("unauthenticated GET site invitations returns 401")
    void unauthenticatedGetInvitationsReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/sites/finance/invitations"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("authenticated user can list site invitations (isAuthenticated() gate)")
    void authenticatedUserCanListInvitations() throws Exception {
        when(invitationService.listForSite("finance")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/sites/finance/invitations"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("authenticated user can accept invitation via token endpoint")
    void authenticatedUserCanAcceptInvitation() throws Exception {
        UUID invId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        when(invitationService.accept("abc123token")).thenReturn(
            new SiteInvitationService.SiteInvitationDto(
                invId,
                siteId,
                "Finance Team",
                "alice@example.com",
                "alice",
                "CONSUMER",
                "ACCEPTED",
                null,
                "admin",
                LocalDateTime.of(2026, 5, 3, 9, 0),
                LocalDateTime.of(2026, 4, 26, 10, 0),
                LocalDateTime.of(2026, 4, 26, 9, 0)
            )
        );

        mockMvc.perform(post("/api/v1/invitations/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "token": "abc123token" }
                    """))
            .andExpect(status().isOk());
    }
}
