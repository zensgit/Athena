package com.ecm.core.integration.oauth.model;

import com.ecm.core.integration.oauth.OAuthProviderType;
import com.ecm.core.security.secret.EncryptedSecretConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "oauth_credentials", uniqueConstraints = {
    @UniqueConstraint(name = "uq_oauth_credential_owner", columnNames = {"owner_type", "owner_id"})
}, indexes = {
    @Index(name = "idx_oauth_credential_owner_type", columnList = "owner_type"),
    @Index(name = "idx_oauth_credential_credential_key", columnList = "credential_key")
})
@ToString(exclude = {"accessToken", "refreshToken"})
public class OAuthCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_type", nullable = false, length = 64)
    private String ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 32)
    private OAuthProviderType provider;

    @Column(name = "token_endpoint", length = 512)
    private String tokenEndpoint;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "scope", columnDefinition = "TEXT")
    private String scope;

    @Column(name = "credential_key")
    private String credentialKey;

    @Column(name = "access_token", columnDefinition = "TEXT")
    @Convert(converter = EncryptedSecretConverter.class)
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    @Convert(converter = EncryptedSecretConverter.class)
    private String refreshToken;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
