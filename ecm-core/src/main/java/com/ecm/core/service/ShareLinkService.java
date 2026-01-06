package com.ecm.core.service;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.entity.ShareLink;
import com.ecm.core.entity.ShareLink.SharePermission;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.ShareLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Share Link Service
 *
 * Manages document sharing via secure links.
 * Supports password protection, expiry dates, and access limits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShareLinkService {

    private final ShareLinkRepository shareLinkRepository;
    private final NodeRepository nodeRepository;
    private final SecurityService securityService;
    private final PasswordEncoder passwordEncoder;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_LENGTH = 32;

    /**
     * Create a share link for a node
     */
    @Transactional
    public ShareLink createShareLink(UUID nodeId, CreateShareLinkRequest request) {
        Node node = nodeRepository.findById(nodeId)
            .orElseThrow(() -> new NoSuchElementException("Node not found: " + nodeId));

        // Check if user has permission to share
        if (!securityService.hasPermission(node, PermissionType.READ)) {
            throw new SecurityException("No permission to share this document");
        }

        String normalizedAllowedIps = normalizeAllowedIps(request.allowedIps());
        validateAllowedIps(normalizedAllowedIps);

        String token = generateUniqueToken();
        String currentUser = securityService.getCurrentUser();

        ShareLink shareLink = ShareLink.builder()
            .token(token)
            .node(node)
            .createdBy(currentUser)
            .name(request.name())
            .expiryDate(request.expiryDate())
            .maxAccessCount(request.maxAccessCount())
            .permissionLevel(request.permissionLevel() != null ? request.permissionLevel() : SharePermission.VIEW)
            .allowedIps(normalizedAllowedIps)
            .build();

        // Set password if provided
        if (request.password() != null && !request.password().isEmpty()) {
            shareLink.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        shareLink = shareLinkRepository.save(shareLink);
        log.info("Created share link {} for node {} by user {}", token, nodeId, currentUser);

        return shareLink;
    }

    /**
     * Access a share link
     */
    @Transactional
    public ShareLinkAccessResult accessShareLink(String token, String password, String clientIp) {
        ShareLink shareLink = shareLinkRepository.findByToken(token)
            .orElseThrow(() -> new NoSuchElementException("Share link not found"));

        // Check if link is valid
        if (!shareLink.isValid()) {
            String reason = determineInvalidReason(shareLink);
            log.warn("Invalid share link access attempt: {} - {}", token, reason);
            return ShareLinkAccessResult.invalid(reason);
        }

        // Check password if required
        if (shareLink.requiresPassword()) {
            if (password == null || !passwordEncoder.matches(password, shareLink.getPasswordHash())) {
                log.warn("Incorrect password for share link: {}", token);
                return ShareLinkAccessResult.requirePassword();
            }
        }

        // Check IP restrictions
        if (shareLink.getAllowedIps() != null && !isIpAllowed(clientIp, shareLink.getAllowedIps())) {
            log.warn("IP {} not allowed for share link: {}", clientIp, token);
            return ShareLinkAccessResult.ipRestricted();
        }

        // Record access
        shareLink.recordAccess();
        shareLinkRepository.save(shareLink);

        log.info("Share link {} accessed successfully", token);
        return ShareLinkAccessResult.success(shareLink);
    }

    /**
     * Get share link by token (without recording access)
     */
    public ShareLink getByToken(String token) {
        return shareLinkRepository.findByToken(token)
            .orElseThrow(() -> new NoSuchElementException("Share link not found: " + token));
    }

    /**
     * Get all share links for a node
     */
    public List<ShareLink> getShareLinksForNode(UUID nodeId) {
        Node node = nodeRepository.findById(nodeId)
            .orElseThrow(() -> new NoSuchElementException("Node not found: " + nodeId));

        // Check if user has permission to view share links
        if (!securityService.hasPermission(node, PermissionType.READ)) {
            throw new SecurityException("No permission to view share links");
        }

        return shareLinkRepository.findByNodeId(nodeId);
    }

    /**
     * Get all share links created by current user
     */
    public List<ShareLink> getMyShareLinks() {
        String currentUser = securityService.getCurrentUser();
        return shareLinkRepository.findByCreatedBy(currentUser);
    }

    /**
     * Deactivate a share link
     */
    @Transactional
    public void deactivateShareLink(String token) {
        ShareLink shareLink = shareLinkRepository.findByToken(token)
            .orElseThrow(() -> new NoSuchElementException("Share link not found: " + token));

        // Check if user has permission (must be creator or have CHANGE_PERMISSIONS)
        String currentUser = securityService.getCurrentUser();
        boolean isCreator = currentUser.equals(shareLink.getCreatedBy());
        boolean hasPermission = securityService.hasPermission(shareLink.getNode(), PermissionType.CHANGE_PERMISSIONS);

        if (!isCreator && !hasPermission) {
            throw new SecurityException("No permission to deactivate this share link");
        }

        shareLink.setActive(false);
        shareLinkRepository.save(shareLink);
        log.info("Share link {} deactivated by {}", token, currentUser);
    }

    /**
     * Delete a share link
     */
    @Transactional
    public void deleteShareLink(String token) {
        ShareLink shareLink = shareLinkRepository.findByToken(token)
            .orElseThrow(() -> new NoSuchElementException("Share link not found: " + token));

        // Check if user has permission (must be creator or have CHANGE_PERMISSIONS)
        String currentUser = securityService.getCurrentUser();
        boolean isCreator = currentUser.equals(shareLink.getCreatedBy());
        boolean hasPermission = securityService.hasPermission(shareLink.getNode(), PermissionType.CHANGE_PERMISSIONS);

        if (!isCreator && !hasPermission) {
            throw new SecurityException("No permission to delete this share link");
        }

        shareLinkRepository.delete(shareLink);
        log.info("Share link {} deleted by {}", token, currentUser);
    }

    /**
     * Update share link settings
     */
    @Transactional
    public ShareLink updateShareLink(String token, UpdateShareLinkRequest request) {
        ShareLink shareLink = shareLinkRepository.findByToken(token)
            .orElseThrow(() -> new NoSuchElementException("Share link not found: " + token));

        // Check if user has permission
        String currentUser = securityService.getCurrentUser();
        boolean isCreator = currentUser.equals(shareLink.getCreatedBy());
        boolean hasPermission = securityService.hasPermission(shareLink.getNode(), PermissionType.CHANGE_PERMISSIONS);

        if (!isCreator && !hasPermission) {
            throw new SecurityException("No permission to update this share link");
        }

        if (request.name() != null) {
            shareLink.setName(request.name());
        }
        if (request.expiryDate() != null) {
            shareLink.setExpiryDate(request.expiryDate());
        }
        if (request.maxAccessCount() != null) {
            shareLink.setMaxAccessCount(request.maxAccessCount());
        }
        if (request.permissionLevel() != null) {
            shareLink.setPermissionLevel(request.permissionLevel());
        }
        if (request.allowedIps() != null) {
            String normalizedAllowedIps = normalizeAllowedIps(request.allowedIps());
            validateAllowedIps(normalizedAllowedIps);
            shareLink.setAllowedIps(normalizedAllowedIps);
        }
        if (request.password() != null) {
            if (request.password().isEmpty()) {
                shareLink.setPasswordHash(null);
            } else {
                shareLink.setPasswordHash(passwordEncoder.encode(request.password()));
            }
        }
        if (request.active() != null) {
            shareLink.setActive(request.active());
        }

        shareLink = shareLinkRepository.save(shareLink);
        log.info("Share link {} updated by {}", token, currentUser);

        return shareLink;
    }

    /**
     * Cleanup expired and limit-reached share links
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    @Transactional
    public void cleanupShareLinks() {
        LocalDateTime now = LocalDateTime.now();

        int expiredCount = shareLinkRepository.deactivateExpiredLinks(now);
        int limitReachedCount = shareLinkRepository.deactivateAccessLimitReachedLinks();

        if (expiredCount > 0 || limitReachedCount > 0) {
            log.info("Share link cleanup: {} expired, {} limit-reached deactivated",
                expiredCount, limitReachedCount);
        }
    }

    /**
     * Generate a unique token
     */
    private String generateUniqueToken() {
        String token;
        do {
            byte[] bytes = new byte[TOKEN_LENGTH];
            SECURE_RANDOM.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (shareLinkRepository.existsByToken(token));
        return token;
    }

    /**
     * Determine why a share link is invalid
     */
    private String determineInvalidReason(ShareLink shareLink) {
        if (!shareLink.isActive()) {
            return "Link has been deactivated";
        }
        if (shareLink.isExpired()) {
            return "Link has expired";
        }
        if (shareLink.isAccessLimitReached()) {
            return "Access limit reached";
        }
        return "Unknown";
    }

    /**
     * Check if client IP is allowed
     */
    private boolean isIpAllowed(String clientIp, String allowedIps) {
        if (clientIp == null || allowedIps == null || allowedIps.isEmpty()) {
            return true;
        }

        String[] allowed = allowedIps.split(",");
        for (String cidr : allowed) {
            String trimmed = cidr.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (isIpInCidr(clientIp, trimmed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if IP is in CIDR range or matches a single IP entry.
     */
    private boolean isIpInCidr(String ip, String cidr) {
        if (ip == null || cidr == null || cidr.isBlank()) {
            return false;
        }

        try {
            String trimmed = cidr.trim();
            InetAddress ipAddress = InetAddress.getByName(ip);

            int slashIndex = trimmed.lastIndexOf('/');
            if (slashIndex < 0) {
                return ipAddress.equals(InetAddress.getByName(trimmed));
            }

            String network = trimmed.substring(0, slashIndex);
            String prefix = trimmed.substring(slashIndex + 1);
            if (network.isEmpty() || prefix.isEmpty()) {
                return false;
            }

            int prefixLength = Integer.parseInt(prefix);
            InetAddress networkAddress = InetAddress.getByName(network);

            byte[] ipBytes = ipAddress.getAddress();
            byte[] networkBytes = networkAddress.getAddress();
            if (ipBytes.length != networkBytes.length) {
                return false;
            }

            int maxBits = ipBytes.length * 8;
            if (prefixLength < 0 || prefixLength > maxBits) {
                return false;
            }

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (ipBytes[i] != networkBytes[i]) {
                    return false;
                }
            }

            if (remainingBits == 0) {
                return true;
            }

            int mask = 0xFF << (8 - remainingBits);
            int ipByte = ipBytes[fullBytes] & 0xFF;
            int networkByte = networkBytes[fullBytes] & 0xFF;
            return (ipByte & mask) == (networkByte & mask);
        } catch (UnknownHostException | NumberFormatException ex) {
            return false;
        }
    }

    private void validateAllowedIps(String allowedIps) {
        if (allowedIps == null || allowedIps.isBlank()) {
            return;
        }

        String[] entries = allowedIps.split(",");
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!isValidAllowedIpEntry(trimmed)) {
                throw new IllegalArgumentException("Invalid allowedIps entry: " + trimmed);
            }
        }
    }

    private boolean isValidAllowedIpEntry(String entry) {
        try {
            int slashIndex = entry.lastIndexOf('/');
            if (slashIndex < 0) {
                InetAddress.getByName(entry);
                return true;
            }

            String network = entry.substring(0, slashIndex);
            String prefix = entry.substring(slashIndex + 1);
            if (network.isEmpty() || prefix.isEmpty()) {
                return false;
            }

            InetAddress networkAddress = InetAddress.getByName(network);
            int prefixLength = Integer.parseInt(prefix);
            int maxBits = networkAddress.getAddress().length * 8;
            return prefixLength >= 0 && prefixLength <= maxBits;
        } catch (UnknownHostException | NumberFormatException ex) {
            return false;
        }
    }

    private String normalizeAllowedIps(String allowedIps) {
        if (allowedIps == null) {
            return null;
        }
        String trimmed = allowedIps.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String[] entries = allowedIps.split(",");
        List<String> normalized = new ArrayList<>();
        for (String entry : entries) {
            String value = entry.trim();
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }

        if (normalized.isEmpty()) {
            return null;
        }
        return String.join(",", normalized);
    }

    // Request/Response records

    public record CreateShareLinkRequest(
        String name,
        LocalDateTime expiryDate,
        Integer maxAccessCount,
        SharePermission permissionLevel,
        String password,
        String allowedIps
    ) {}

    public record UpdateShareLinkRequest(
        String name,
        LocalDateTime expiryDate,
        Integer maxAccessCount,
        SharePermission permissionLevel,
        String password,
        String allowedIps,
        Boolean active
    ) {}

    public record ShareLinkAccessResult(
        boolean success,
        String error,
        boolean passwordRequired,
        ShareLink shareLink
    ) {
        public static ShareLinkAccessResult success(ShareLink shareLink) {
            return new ShareLinkAccessResult(true, null, false, shareLink);
        }

        public static ShareLinkAccessResult invalid(String reason) {
            return new ShareLinkAccessResult(false, reason, false, null);
        }

        public static ShareLinkAccessResult requirePassword() {
            return new ShareLinkAccessResult(false, "Password required", true, null);
        }

        public static ShareLinkAccessResult ipRestricted() {
            return new ShareLinkAccessResult(false, "Access from this IP is not allowed", false, null);
        }
    }
}
