package com.ecm.core.controller;

import com.ecm.core.entity.ShareLink;
import com.ecm.core.entity.ShareLink.SharePermission;
import com.ecm.core.service.ShareLinkService;
import com.ecm.core.service.ShareLinkService.CreateShareLinkRequest;
import com.ecm.core.service.ShareLinkService.ShareLinkAccessResult;
import com.ecm.core.service.ShareLinkService.UpdateShareLinkRequest;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Share Link operations
 */
@Slf4j
@RestController
@RequestMapping({"/api/share", "/api/v1/share"})
@RequiredArgsConstructor
public class ShareLinkController {

    private final ShareLinkService shareLinkService;

    /**
     * Create a share link for a document
     */
    @PostMapping("/nodes/{nodeId}")
    public ResponseEntity<ShareLinkResponse> createShareLink(
            @PathVariable UUID nodeId,
            @RequestBody CreateShareLinkRequestDto request) {

        CreateShareLinkRequest serviceRequest = new CreateShareLinkRequest(
            request.name(),
            request.expiryDate(),
            request.maxAccessCount(),
            request.permissionLevel(),
            request.password(),
            request.allowedIps()
        );

        ShareLink shareLink = shareLinkService.createShareLink(nodeId, serviceRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ShareLinkResponse.from(shareLink));
    }

    /**
     * Access a share link (public endpoint)
     */
    @GetMapping("/access/{token}")
    public ResponseEntity<?> accessShareLink(
            @PathVariable String token,
            @RequestParam(required = false) String password,
            HttpServletRequest request) {

        String clientIp = getClientIp(request);
        ShareLinkAccessResult result = shareLinkService.accessShareLink(token, password, clientIp);

        if (result.success()) {
            return ResponseEntity.ok(ShareLinkAccessResponse.from(result.shareLink()));
        } else if (result.passwordRequired()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                    "error", "Password required",
                    "passwordRequired", true
                ));
        } else {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", result.error()));
        }
    }

    /**
     * Get share link info (without recording access)
     */
    @GetMapping("/{token}")
    public ResponseEntity<ShareLinkResponse> getShareLink(@PathVariable String token) {
        ShareLink shareLink = shareLinkService.getByToken(token);
        return ResponseEntity.ok(ShareLinkResponse.from(shareLink));
    }

    /**
     * Get all share links for a node
     */
    @GetMapping("/nodes/{nodeId}")
    public ResponseEntity<List<ShareLinkResponse>> getShareLinksForNode(@PathVariable UUID nodeId) {
        List<ShareLink> shareLinks = shareLinkService.getShareLinksForNode(nodeId);
        return ResponseEntity.ok(shareLinks.stream()
            .map(ShareLinkResponse::from)
            .toList());
    }

    /**
     * Get all share links created by current user
     */
    @GetMapping("/my")
    public ResponseEntity<List<ShareLinkResponse>> getMyShareLinks() {
        List<ShareLink> shareLinks = shareLinkService.getMyShareLinks();
        return ResponseEntity.ok(shareLinks.stream()
            .map(ShareLinkResponse::from)
            .toList());
    }

    /**
     * Update a share link
     */
    @PutMapping("/{token}")
    public ResponseEntity<ShareLinkResponse> updateShareLink(
            @PathVariable String token,
            @RequestBody UpdateShareLinkRequestDto request) {

        UpdateShareLinkRequest serviceRequest = new UpdateShareLinkRequest(
            request.name(),
            request.expiryDate(),
            request.maxAccessCount(),
            request.permissionLevel(),
            request.password(),
            request.allowedIps(),
            request.active()
        );

        ShareLink shareLink = shareLinkService.updateShareLink(token, serviceRequest);
        return ResponseEntity.ok(ShareLinkResponse.from(shareLink));
    }

    /**
     * Deactivate a share link
     */
    @PostMapping("/{token}/deactivate")
    public ResponseEntity<Void> deactivateShareLink(@PathVariable String token) {
        shareLinkService.deactivateShareLink(token);
        return ResponseEntity.noContent().build();
    }

    /**
     * Delete a share link
     */
    @DeleteMapping("/{token}")
    public ResponseEntity<Void> deleteShareLink(@PathVariable String token) {
        shareLinkService.deleteShareLink(token);
        return ResponseEntity.noContent().build();
    }

    // Helper method to get client IP
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    // DTOs

    public record CreateShareLinkRequestDto(
        String name,
        LocalDateTime expiryDate,
        Integer maxAccessCount,
        SharePermission permissionLevel,
        String password,
        String allowedIps
    ) {}

    public record UpdateShareLinkRequestDto(
        String name,
        LocalDateTime expiryDate,
        Integer maxAccessCount,
        SharePermission permissionLevel,
        String password,
        String allowedIps,
        Boolean active
    ) {}

    public record ShareLinkResponse(
        UUID id,
        String token,
        UUID nodeId,
        String nodeName,
        String createdBy,
        LocalDateTime createdAt,
        LocalDateTime expiryDate,
        Integer maxAccessCount,
        Integer accessCount,
        boolean active,
        String name,
        SharePermission permissionLevel,
        LocalDateTime lastAccessedAt,
        boolean passwordProtected,
        boolean hasIpRestrictions,
        boolean isValid
    ) {
        public static ShareLinkResponse from(ShareLink shareLink) {
            return new ShareLinkResponse(
                shareLink.getId(),
                shareLink.getToken(),
                shareLink.getNode().getId(),
                shareLink.getNode().getName(),
                shareLink.getCreatedBy(),
                shareLink.getCreatedAt(),
                shareLink.getExpiryDate(),
                shareLink.getMaxAccessCount(),
                shareLink.getAccessCount(),
                shareLink.isActive(),
                shareLink.getName(),
                shareLink.getPermissionLevel(),
                shareLink.getLastAccessedAt(),
                shareLink.requiresPassword(),
                shareLink.getAllowedIps() != null && !shareLink.getAllowedIps().isEmpty(),
                shareLink.isValid()
            );
        }
    }

    public record ShareLinkAccessResponse(
        UUID nodeId,
        String nodeName,
        String nodePath,
        SharePermission permissionLevel
    ) {
        public static ShareLinkAccessResponse from(ShareLink shareLink) {
            return new ShareLinkAccessResponse(
                shareLink.getNode().getId(),
                shareLink.getNode().getName(),
                shareLink.getNode().getPath(),
                shareLink.getPermissionLevel()
            );
        }
    }
}
