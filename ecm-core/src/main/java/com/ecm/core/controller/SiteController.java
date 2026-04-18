package com.ecm.core.controller;

import com.ecm.core.entity.Site.SiteStatus;
import com.ecm.core.entity.Site.SiteVisibility;
import com.ecm.core.service.SiteMembershipService;
import com.ecm.core.entity.SiteMember.SiteMemberRole;
import com.ecm.core.service.SiteMembershipService.MembershipRequestDto;
import com.ecm.core.service.SiteMembershipService.SiteMemberDto;
import com.ecm.core.service.SiteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sites")
@RequiredArgsConstructor
@Tag(name = "Sites", description = "Manage collaboration sites")
public class SiteController {

    private final SiteService siteService;
    private final SiteMembershipService membershipService;

    @GetMapping
    @Operation(summary = "List sites", description = "Get collaboration sites")
    public ResponseEntity<List<SiteService.SiteDto>> getSites(
        @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        return ResponseEntity.ok(siteService.listSites(includeArchived));
    }

    @GetMapping("/{siteId}")
    @Operation(summary = "Get site", description = "Get a collaboration site by site ID")
    public ResponseEntity<SiteService.SiteDto> getSite(@PathVariable String siteId) {
        return ResponseEntity.ok(siteService.getSite(siteId));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create site", description = "Create a collaboration site")
    public ResponseEntity<SiteService.SiteDto> createSite(@RequestBody SiteService.CreateSiteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(siteService.createSite(request));
    }

    @PutMapping("/{siteId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update site", description = "Update a collaboration site")
    public ResponseEntity<SiteService.SiteDto> updateSite(
        @PathVariable String siteId,
        @RequestBody SiteService.UpdateSiteRequest request
    ) {
        return ResponseEntity.ok(siteService.updateSite(siteId, request));
    }

    @DeleteMapping("/{siteId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete site", description = "Archive a collaboration site")
    public ResponseEntity<Void> deleteSite(@PathVariable String siteId) {
        siteService.deleteSite(siteId);
        return ResponseEntity.noContent().build();
    }

    // ---- membership requests ------------------------------------------------

    @GetMapping("/{siteId}/membership-requests")
    @Operation(summary = "List membership requests for a site")
    public ResponseEntity<List<MembershipRequestDto>> getMembershipRequests(@PathVariable String siteId) {
        return ResponseEntity.ok(membershipService.getRequestsForSite(siteId));
    }

    @PostMapping("/{siteId}/membership-requests")
    @Operation(summary = "Request membership to a site")
    public ResponseEntity<MembershipRequestDto> createMembershipRequest(
            @PathVariable String siteId,
            @RequestBody SiteMembershipService.CreateMembershipRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(membershipService.createRequest(siteId, request));
    }

    @PostMapping("/{siteId}/membership-requests/{username}/approve")
    @Operation(summary = "Approve membership request")
    public ResponseEntity<MembershipRequestDto> approveMembershipRequest(
            @PathVariable String siteId,
            @PathVariable String username,
            @RequestBody(required = false) SiteMembershipService.ModerationRequest request) {
        return ResponseEntity.ok(membershipService.approve(siteId, username, request != null ? request.comment() : null));
    }

    @PostMapping("/{siteId}/membership-requests/{username}/reject")
    @Operation(summary = "Reject membership request")
    public ResponseEntity<MembershipRequestDto> rejectMembershipRequest(
            @PathVariable String siteId,
            @PathVariable String username,
            @RequestBody(required = false) SiteMembershipService.ModerationRequest request) {
        return ResponseEntity.ok(membershipService.reject(siteId, username, request != null ? request.comment() : null));
    }

    @DeleteMapping("/{siteId}/membership-requests")
    @Operation(summary = "Withdraw own membership request")
    public ResponseEntity<Void> withdrawMembershipRequest(@PathVariable String siteId) {
        membershipService.withdraw(siteId);
        return ResponseEntity.noContent().build();
    }

    // ---- members (roster) ---------------------------------------------------

    @GetMapping("/{siteId}/members")
    @Operation(summary = "List site members", description = "Get all members of a site with their roles")
    public ResponseEntity<List<SiteMemberDto>> getMembers(@PathVariable String siteId) {
        return ResponseEntity.ok(membershipService.getMembers(siteId));
    }

    @PostMapping("/{siteId}/members")
    @Operation(summary = "Add site member")
    public ResponseEntity<SiteMemberDto> addMember(
            @PathVariable String siteId,
            @RequestBody SiteMembershipService.AddMemberRequest request) {
        SiteMemberRole role = request.role() != null ? SiteMemberRole.valueOf(request.role()) : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(membershipService.addMember(siteId, request.username(), role));
    }

    @PutMapping("/{siteId}/members/{username}")
    @Operation(summary = "Update member role")
    public ResponseEntity<SiteMemberDto> updateMemberRole(
            @PathVariable String siteId,
            @PathVariable String username,
            @RequestBody SiteMembershipService.UpdateRoleRequest request) {
        return ResponseEntity.ok(membershipService.updateMemberRole(siteId, username, SiteMemberRole.valueOf(request.role())));
    }

    @DeleteMapping("/{siteId}/members/{username}")
    @Operation(summary = "Remove site member")
    public ResponseEntity<Void> removeMember(
            @PathVariable String siteId,
            @PathVariable String username) {
        membershipService.removeMember(siteId, username);
        return ResponseEntity.noContent().build();
    }
}
