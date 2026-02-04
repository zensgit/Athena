package com.ecm.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_mfa_settings")
public class UserMfaSettings extends BaseEntity {

    @Column(name = "username", nullable = false, unique = true, length = 190)
    private String username;

    @Column(name = "totp_secret", length = 128)
    private String totpSecret;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "recovery_codes_hash", length = 2048)
    private String recoveryCodesHash;

    @Column(name = "recovery_codes_generated_at")
    private LocalDateTime recoveryCodesGeneratedAt;

    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;
}
