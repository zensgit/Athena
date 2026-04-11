package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "transfer_targets", indexes = {
    @Index(name = "idx_transfer_target_name", columnList = "name", unique = true),
    @Index(name = "idx_transfer_target_folder_id", columnList = "target_folder_id")
})
public class TransferTarget {

    public enum TransportType {
        LOOPBACK,
        ATHENA_HTTP
    }

    public enum AuthType {
        NONE,
        BASIC,
        BEARER
    }

    public enum VerificationStatus {
        NEVER_VERIFIED,
        VERIFIED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200, unique = true)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_type", nullable = false, length = 30)
    private TransportType transportType = TransportType.LOOPBACK;

    @Column(name = "target_folder_id", nullable = false)
    private UUID targetFolderId;

    @Column(name = "endpoint_url", columnDefinition = "TEXT")
    private String endpointUrl;

    @Column(name = "endpoint_path", length = 255)
    private String endpointPath = "/api/v1";

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 20)
    private AuthType authType = AuthType.NONE;

    @Column(name = "auth_username", length = 255)
    private String authUsername;

    @Column(name = "auth_secret", columnDefinition = "TEXT")
    private String authSecret;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    private VerificationStatus verificationStatus = VerificationStatus.NEVER_VERIFIED;

    @Column(name = "verification_message", columnDefinition = "TEXT")
    private String verificationMessage;

    @Column(name = "remote_repository_id", length = 255)
    private String remoteRepositoryId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
