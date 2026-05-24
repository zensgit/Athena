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

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecm.core.exception.ResourceNotFoundException;

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
                LocalDateTime.of(2026, 4, 26, 9, 0),
                null, null, null, 0, null
            )
        );

        mockMvc.perform(post("/api/v1/invitations/accept")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "token": "abc123token" }
                    """))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("unauthenticated POST resend returns 401")
    void unauthenticatedResendReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/sites/finance/invitations/{id}/resend",
                UUID.fromString("11111111-2222-3333-4444-555555555555")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("resend non-PENDING invitation maps IllegalArgumentException to 400")
    void resendNonPendingMapsTo400() throws Exception {
        UUID invitationId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        when(invitationService.resend("finance", invitationId)).thenThrow(
            new IllegalArgumentException("Only PENDING invitations can be resent; current status: ACCEPTED")
        );

        mockMvc.perform(post("/api/v1/sites/finance/invitations/{id}/resend", invitationId))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("resend without manager/admin permission maps SecurityException to 403")
    void resendWithoutPermissionMapsTo403() throws Exception {
        UUID invitationId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        when(invitationService.resend("finance", invitationId)).thenThrow(
            new SecurityException("User is not a site manager or admin")
        );

        mockMvc.perform(post("/api/v1/sites/finance/invitations/{id}/resend", invitationId))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("resend unknown invitation maps ResourceNotFoundException to 404")
    void resendUnknownInvitationMapsTo404() throws Exception {
        UUID invitationId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        when(invitationService.resend("finance", invitationId)).thenThrow(
            new ResourceNotFoundException("Invitation not found: " + invitationId)
        );

        mockMvc.perform(post("/api/v1/sites/finance/invitations/{id}/resend", invitationId))
            .andExpect(status().isNotFound());
    }

    // ========================================================================
    // Bulk invite endpoint
    // ========================================================================

    @Test
    @DisplayName("unauthenticated POST bulk invite returns 401")
    void unauthenticatedBulkInviteReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/sites/finance/invitations/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "inviteeEmails": ["alice@example.com"], "invitedRole": "CONSUMER" }
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("authenticated user reaches the bulk endpoint (service enforces manager/admin)")
    void authenticatedUserCanReachBulkEndpoint() throws Exception {
        UUID inv1 = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        when(invitationService.inviteBulk(
            org.mockito.ArgumentMatchers.eq("finance"),
            org.mockito.ArgumentMatchers.any(com.ecm.core.service.SiteInvitationService.BulkInviteRequest.class)
        )).thenReturn(new com.ecm.core.service.SiteInvitationService.BulkInviteResponse(java.util.List.of(
            com.ecm.core.service.SiteInvitationService.BulkInviteResult.success(
                "alice@example.com",
                new SiteInvitationService.SiteInvitationDto(
                    inv1, siteId, "Finance", "alice@example.com",
                    null, "CONSUMER", "PENDING", null, "admin",
                    LocalDateTime.of(2026, 6, 1, 9, 0), null,
                    LocalDateTime.of(2026, 5, 24, 10, 0),
                    LocalDateTime.of(2026, 5, 24, 10, 0), "SENT", null, 1,
                    LocalDateTime.of(2026, 5, 24, 10, 0)
                )
            )
        )));

        mockMvc.perform(post("/api/v1/sites/finance/invitations/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "inviteeEmails": ["alice@example.com"], "invitedRole": "CONSUMER" }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results[0].status").value("SUCCESS"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("bulk invite without manager/admin permission maps SecurityException to 403")
    void bulkInviteWithoutPermissionMapsTo403() throws Exception {
        when(invitationService.inviteBulk(
            org.mockito.ArgumentMatchers.eq("finance"),
            org.mockito.ArgumentMatchers.any(com.ecm.core.service.SiteInvitationService.BulkInviteRequest.class)
        )).thenThrow(new SecurityException("User is not a site manager or admin"));

        mockMvc.perform(post("/api/v1/sites/finance/invitations/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "inviteeEmails": ["alice@example.com"], "invitedRole": "CONSUMER" }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("bulk invite with empty inviteeEmails maps IllegalArgumentException to 400")
    void bulkInviteEmptyMapsTo400() throws Exception {
        when(invitationService.inviteBulk(
            org.mockito.ArgumentMatchers.eq("finance"),
            org.mockito.ArgumentMatchers.any(com.ecm.core.service.SiteInvitationService.BulkInviteRequest.class)
        )).thenThrow(new IllegalArgumentException("inviteeEmails must contain at least one entry"));

        mockMvc.perform(post("/api/v1/sites/finance/invitations/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "inviteeEmails": [], "invitedRole": "CONSUMER" }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("admin/manager resend returns 200 with send-tracking fields populated")
    void adminResendReturns200WithSendTracking() throws Exception {
        UUID invitationId = UUID.fromString("11111111-2222-3333-4444-555555555555");
        UUID siteUuid = UUID.randomUUID();
        when(invitationService.resend("finance", invitationId)).thenReturn(
            new SiteInvitationService.SiteInvitationDto(
                invitationId,
                siteUuid,
                "Finance Team",
                "alice@example.com",
                null,
                "CONSUMER",
                "PENDING",
                null,
                "admin",
                LocalDateTime.of(2026, 5, 14, 9, 0),
                null,
                LocalDateTime.of(2026, 5, 7, 9, 0),
                LocalDateTime.of(2026, 5, 7, 10, 0),
                "SENT",
                null,
                2,
                LocalDateTime.of(2026, 5, 7, 10, 0)
            )
        );

        mockMvc.perform(post("/api/v1/sites/finance/invitations/{id}/resend", invitationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(invitationId.toString())))
            .andExpect(jsonPath("$.lastSendStatus", is("SENT")))
            .andExpect(jsonPath("$.lastSendError").doesNotExist())
            .andExpect(jsonPath("$.sendAttemptCount", is(2)))
            .andExpect(jsonPath("$.lastSentAt", is("2026-05-07T10:00:00")))
            .andExpect(jsonPath("$.lastSendAttemptAt", is("2026-05-07T10:00:00")));
    }
}
