package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "transfer_receivers", indexes = {
    @Index(name = "idx_transfer_receiver_name", columnList = "name", unique = true),
    @Index(name = "idx_transfer_receiver_root_folder", columnList = "root_folder_id"),
    @Index(name = "idx_transfer_receiver_enabled", columnList = "enabled")
})
public class TransferReceiverRegistration {

    public enum AccessStatus {
        NEVER_USED,
        SUCCESS,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200, unique = true)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "root_folder_id", nullable = false)
    private UUID rootFolderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 20)
    private TransferTarget.AuthType authType = TransferTarget.AuthType.NONE;

    @Column(name = "auth_username", length = 255)
    private String authUsername;

    @Column(name = "auth_secret", columnDefinition = "TEXT")
    private String authSecret;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    private TransferTarget.VerificationStatus verificationStatus = TransferTarget.VerificationStatus.NEVER_VERIFIED;

    @Column(name = "verification_message", columnDefinition = "TEXT")
    private String verificationMessage;

    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_access_status", nullable = false, length = 20)
    private AccessStatus lastAccessStatus = AccessStatus.NEVER_USED;

    @Column(name = "last_access_message", columnDefinition = "TEXT")
    private String lastAccessMessage;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
