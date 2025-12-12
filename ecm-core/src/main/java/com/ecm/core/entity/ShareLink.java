package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Share Link Entity
 *
 * Enables secure sharing of documents via unique links.
 * Links can be password-protected and time-limited.
 *
 * Inspired by Alfresco's quick share functionality.
 */
@Entity
@Table(name = "share_links", indexes = {
    @Index(name = "idx_share_link_token", columnList = "token", unique = true),
    @Index(name = "idx_share_link_node", columnList = "node_id"),
    @Index(name = "idx_share_link_created_by", columnList = "created_by"),
    @Index(name = "idx_share_link_expiry", columnList = "expiry_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Unique token for the share link (used in URLs)
     */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    /**
     * The node being shared
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false)
    private Node node;

    /**
     * User who created the share link
     */
    @Column(name = "created_by", nullable = false)
    private String createdBy;

    /**
     * When the share link was created
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * When the share link expires (null = never expires)
     */
    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    /**
     * Optional password protection (hashed)
     */
    @Column(name = "password_hash")
    private String passwordHash;

    /**
     * Maximum number of accesses allowed (null = unlimited)
     */
    @Column(name = "max_access_count")
    private Integer maxAccessCount;

    /**
     * Current access count
     */
    @Column(name = "access_count", nullable = false)
    @Builder.Default
    private Integer accessCount = 0;

    /**
     * Whether the link is currently active
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Optional name/description for the share link
     */
    @Column(length = 255)
    private String name;

    /**
     * What permissions the link grants
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "permission_level", nullable = false)
    @Builder.Default
    private SharePermission permissionLevel = SharePermission.VIEW;

    /**
     * Last accessed timestamp
     */
    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    /**
     * IP address restrictions (comma-separated CIDR blocks, null = no restriction)
     */
    @Column(name = "allowed_ips", length = 500)
    private String allowedIps;

    /**
     * Share permission levels
     */
    public enum SharePermission {
        VIEW,           // Can view/download
        COMMENT,        // Can view and comment
        EDIT            // Can view, comment, and edit (for collaborative editing)
    }

    /**
     * Check if the share link has expired
     */
    public boolean isExpired() {
        if (expiryDate == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(expiryDate);
    }

    /**
     * Check if the share link has reached its access limit
     */
    public boolean isAccessLimitReached() {
        if (maxAccessCount == null) {
            return false;
        }
        return accessCount >= maxAccessCount;
    }

    /**
     * Check if the share link is currently valid
     */
    public boolean isValid() {
        return active && !isExpired() && !isAccessLimitReached();
    }

    /**
     * Record an access to this share link
     */
    public void recordAccess() {
        this.accessCount++;
        this.lastAccessedAt = LocalDateTime.now();
    }

    /**
     * Check if password is required
     */
    public boolean requiresPassword() {
        return passwordHash != null && !passwordHash.isEmpty();
    }
}
