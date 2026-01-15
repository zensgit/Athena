package com.ecm.core.integration.mail.model;

import com.ecm.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "mail_accounts")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = { "password", "oauthClientSecret", "oauthAccessToken", "oauthRefreshToken" })
public class MailAccount extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private Integer port;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String password; // Should be encrypted in prod

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SecurityType security = SecurityType.SSL;

    @Enumerated(EnumType.STRING)
    @Column(name = "oauth_provider")
    private OAuthProvider oauthProvider;

    @Column(name = "oauth_token_endpoint")
    private String oauthTokenEndpoint;

    @Column(name = "oauth_client_id")
    private String oauthClientId;

    @Column(name = "oauth_client_secret", columnDefinition = "TEXT")
    private String oauthClientSecret;

    @Column(name = "oauth_tenant_id")
    private String oauthTenantId;

    @Column(name = "oauth_scope", columnDefinition = "TEXT")
    private String oauthScope;

    @Column(name = "oauth_access_token", columnDefinition = "TEXT")
    private String oauthAccessToken;

    @Column(name = "oauth_refresh_token", columnDefinition = "TEXT")
    private String oauthRefreshToken;

    @Column(name = "oauth_token_expires_at")
    private LocalDateTime oauthTokenExpiresAt;

    @Column(name = "is_enabled")
    private boolean enabled = true;

    @Column(name = "poll_interval_minutes")
    private Integer pollIntervalMinutes = 10;

    public enum SecurityType {
        NONE, SSL, STARTTLS, OAUTH2
    }

    public enum OAuthProvider {
        GOOGLE, MICROSOFT, CUSTOM
    }
}
