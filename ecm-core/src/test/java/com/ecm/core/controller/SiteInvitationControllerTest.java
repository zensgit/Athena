package com.ecm.core.controller;

import com.ecm.core.service.SiteInvitationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SiteInvitationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SiteInvitationService invitationService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SiteInvitationController(invitationService))
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("listInvitations returns invitation list for site")
    void listInvitationsReturnsInvitationList() throws Exception {
        UUID invId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        Mockito.when(invitationService.listForSite("finance")).thenReturn(List.of(
            new SiteInvitationService.SiteInvitationDto(
                invId,
                siteId,
                "Finance Team",
                "alice@example.com",
                null,
                "CONSUMER",
                "PENDING",
                null,
                "admin",
                LocalDateTime.of(2026, 5, 3, 9, 0),
                null,
                LocalDateTime.of(2026, 4, 26, 9, 0),
                null, null, null, 0, null
            )
        ));

        mockMvc.perform(get("/api/v1/sites/finance/invitations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(invId.toString()))
            .andExpect(jsonPath("$[0].inviteeEmail").value("alice@example.com"))
            .andExpect(jsonPath("$[0].status").value("PENDING"))
            .andExpect(jsonPath("$[0].invitedRole").value("CONSUMER"));
    }

    @Test
    @DisplayName("invite returns 201 with created invitation dto")
    void inviteReturnsCreatedInvitationDto() throws Exception {
        UUID invId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        Mockito.when(invitationService.invite(
            Mockito.eq("finance"),
            Mockito.any(SiteInvitationService.InviteRequest.class)
        )).thenReturn(
            new SiteInvitationService.SiteInvitationDto(
                invId,
                siteId,
                "Finance Team",
                "alice@example.com",
                null,
                "CONSUMER",
                "PENDING",
                null,
                "admin",
                LocalDateTime.of(2026, 5, 3, 9, 0),
                null,
                LocalDateTime.of(2026, 4, 26, 9, 0),
                LocalDateTime.of(2026, 4, 26, 9, 0),
                "SENT",
                null,
                1,
                LocalDateTime.of(2026, 4, 26, 9, 0)
            )
        );

        mockMvc.perform(post("/api/v1/sites/finance/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "inviteeEmail": "alice@example.com", "invitedRole": "CONSUMER" }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(invId.toString()))
            .andExpect(jsonPath("$.inviteeEmail").value("alice@example.com"))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("cancel invitation returns 204 no content")
    void cancelInvitationReturnsNoContent() throws Exception {
        UUID invId = UUID.randomUUID();
        Mockito.doNothing().when(invitationService).cancel(Mockito.eq("finance"), Mockito.eq(invId));

        mockMvc.perform(delete("/api/v1/sites/finance/invitations/" + invId))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("accept invitation returns 200 with accepted dto")
    void acceptInvitationReturnsAcceptedDto() throws Exception {
        UUID invId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        Mockito.when(invitationService.accept("abc123token")).thenReturn(
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
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(invId.toString()))
            .andExpect(jsonPath("$.status").value("ACCEPTED"))
            .andExpect(jsonPath("$.inviteeUsername").value("alice"));
    }

    @Test
    @DisplayName("reject invitation returns 200 with rejected dto")
    void rejectInvitationReturnsRejectedDto() throws Exception {
        UUID invId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        Mockito.when(invitationService.reject("abc123token")).thenReturn(
            new SiteInvitationService.SiteInvitationDto(
                invId,
                siteId,
                "Finance Team",
                "alice@example.com",
                null,
                "CONSUMER",
                "REJECTED",
                null,
                "admin",
                LocalDateTime.of(2026, 5, 3, 9, 0),
                null,
                LocalDateTime.of(2026, 4, 26, 9, 0),
                null, null, null, 0, null
            )
        );

        mockMvc.perform(post("/api/v1/invitations/reject")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "token": "abc123token" }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(invId.toString()))
            .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    // ========================================================================
    // Bulk invite endpoint
    // ========================================================================

    @Test
    @DisplayName("bulkInvite returns 200 with per-row results (all SUCCESS)")
    void bulkInviteReturns200WithAllSuccessResults() throws Exception {
        UUID inv1 = UUID.randomUUID();
        UUID inv2 = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        Mockito.when(invitationService.inviteBulk(
            Mockito.eq("finance"),
            Mockito.any(SiteInvitationService.BulkInviteRequest.class)
        )).thenReturn(new SiteInvitationService.BulkInviteResponse(java.util.List.of(
            SiteInvitationService.BulkInviteResult.success(
                "alice@example.com",
                new SiteInvitationService.SiteInvitationDto(
                    inv1, siteId, "Finance", "alice@example.com",
                    null, "CONSUMER", "PENDING", null, "admin",
                    LocalDateTime.of(2026, 6, 1, 9, 0), null,
                    LocalDateTime.of(2026, 5, 24, 10, 0),
                    LocalDateTime.of(2026, 5, 24, 10, 0), "SENT", null, 1,
                    LocalDateTime.of(2026, 5, 24, 10, 0)
                )
            ),
            SiteInvitationService.BulkInviteResult.success(
                "bob@example.com",
                new SiteInvitationService.SiteInvitationDto(
                    inv2, siteId, "Finance", "bob@example.com",
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
                    {
                      "inviteeEmails": ["alice@example.com", "bob@example.com"],
                      "invitedRole": "CONSUMER",
                      "message": null
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results[0].inviteeEmail").value("alice@example.com"))
            .andExpect(jsonPath("$.results[0].status").value("SUCCESS"))
            .andExpect(jsonPath("$.results[0].invitation.id").value(inv1.toString()))
            .andExpect(jsonPath("$.results[1].inviteeEmail").value("bob@example.com"))
            .andExpect(jsonPath("$.results[1].status").value("SUCCESS"));
    }

    @Test
    @DisplayName("bulkInvite returns 200 with partial failure — HTTP status NOT 400 on per-row failures")
    void bulkInviteReturns200OnPartialFailure() throws Exception {
        UUID inv1 = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        Mockito.when(invitationService.inviteBulk(
            Mockito.eq("finance"),
            Mockito.any(SiteInvitationService.BulkInviteRequest.class)
        )).thenReturn(new SiteInvitationService.BulkInviteResponse(java.util.List.of(
            SiteInvitationService.BulkInviteResult.success(
                "alice@example.com",
                new SiteInvitationService.SiteInvitationDto(
                    inv1, siteId, "Finance", "alice@example.com",
                    null, "CONSUMER", "PENDING", null, "admin",
                    LocalDateTime.of(2026, 6, 1, 9, 0), null,
                    LocalDateTime.of(2026, 5, 24, 10, 0),
                    LocalDateTime.of(2026, 5, 24, 10, 0), "SENT", null, 1,
                    LocalDateTime.of(2026, 5, 24, 10, 0)
                )
            ),
            SiteInvitationService.BulkInviteResult.failed(
                "dup@example.com",
                SiteInvitationService.BulkInviteErrorCategory.DUPLICATE_PENDING,
                "A pending invitation already exists for this email in this site."
            )
        )));

        mockMvc.perform(post("/api/v1/sites/finance/invitations/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "inviteeEmails": ["alice@example.com", "dup@example.com"],
                      "invitedRole": "CONSUMER"
                    }
                    """))
            .andExpect(status().isOk())   // 200, NOT 400 — per-row failures are part of the body, not an HTTP error
            .andExpect(jsonPath("$.results[0].status").value("SUCCESS"))
            .andExpect(jsonPath("$.results[1].status").value("FAILED"))
            .andExpect(jsonPath("$.results[1].errorCategory").value("DUPLICATE_PENDING"))
            .andExpect(jsonPath("$.results[1].invitation").doesNotExist());
    }

    @Test
    @DisplayName("bulkInvite empty inviteeEmails maps IllegalArgumentException to 400")
    void bulkInviteEmptyArrayMapsTo400() throws Exception {
        Mockito.when(invitationService.inviteBulk(
            Mockito.eq("finance"),
            Mockito.any(SiteInvitationService.BulkInviteRequest.class)
        )).thenThrow(new IllegalArgumentException("inviteeEmails must contain at least one entry"));

        mockMvc.perform(post("/api/v1/sites/finance/invitations/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "inviteeEmails": [], "invitedRole": "CONSUMER" }
                    """))
            .andExpect(status().isBadRequest());
    }
}
