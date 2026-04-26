package com.ecm.core.controller;

import com.ecm.core.service.SiteInvitationService;
import com.ecm.core.service.SiteInvitationService.InviteRequest;
import com.ecm.core.service.SiteInvitationService.SiteInvitationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Site Invitations", description = "Invite users to collaboration sites via token-based email invitations")
public class SiteInvitationController {

    private final SiteInvitationService invitationService;

    // ---- site-scoped endpoints (manager/admin) ---------------------------------

    @GetMapping("/api/v1/sites/{siteId}/invitations")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List invitations for a site", description = "Returns all invitations for the site (manager or admin only)")
    public ResponseEntity<List<SiteInvitationDto>> listInvitations(@PathVariable String siteId) {
        return ResponseEntity.ok(invitationService.listForSite(siteId));
    }

    @PostMapping("/api/v1/sites/{siteId}/invitations")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a site invitation", description = "Invite an email address to a site (manager or admin only)")
    public ResponseEntity<SiteInvitationDto> invite(
        @PathVariable String siteId,
        @RequestBody InviteRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invitationService.invite(siteId, request));
    }

    @DeleteMapping("/api/v1/sites/{siteId}/invitations/{invitationId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cancel a site invitation", description = "Cancel a pending invitation (manager or admin only)")
    public ResponseEntity<Void> cancel(
        @PathVariable String siteId,
        @PathVariable UUID invitationId
    ) {
        invitationService.cancel(siteId, invitationId);
        return ResponseEntity.noContent().build();
    }

    // ---- token-based endpoints (any authenticated user) ----------------------

    @PostMapping("/api/v1/invitations/accept")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Accept a site invitation", description = "Accept an invitation using the token from the invitation email")
    public ResponseEntity<SiteInvitationDto> accept(@RequestBody TokenRequest request) {
        return ResponseEntity.ok(invitationService.accept(request.token()));
    }

    @PostMapping("/api/v1/invitations/reject")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Reject a site invitation", description = "Reject an invitation using the token from the invitation email")
    public ResponseEntity<SiteInvitationDto> reject(@RequestBody TokenRequest request) {
        return ResponseEntity.ok(invitationService.reject(request.token()));
    }

    // ---- inner record ---------------------------------------------------------

    public record TokenRequest(String token) {}
}
