package com.ecm.core.security.mfa;

import com.ecm.core.entity.UserMfaSettings;
import com.ecm.core.repository.UserMfaSettingsRepository;
import com.ecm.core.service.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MfaService {

    private static final int RECOVERY_CODE_COUNT = 8;
    private static final int RECOVERY_CODE_LENGTH = 10;

    private final UserMfaSettingsRepository repository;
    private final TotpService totpService;
    private final SecurityService securityService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${ecm.mfa.issuer:Athena ECM}")
    private String issuer;

    @Transactional(readOnly = true)
    public MfaStatus getStatus() {
        String username = securityService.getCurrentUser();
        Optional<UserMfaSettings> settingsOpt = repository.findByUsername(username);
        if (settingsOpt.isEmpty()) {
            return new MfaStatus(username, false, false, null, null);
        }
        UserMfaSettings settings = settingsOpt.get();
        boolean configured = settings.getTotpSecret() != null && !settings.getTotpSecret().isBlank();
        return new MfaStatus(username, configured, settings.isEnabled(), settings.getLastVerifiedAt(),
            settings.getRecoveryCodesGeneratedAt());
    }

    @Transactional
    public MfaEnrollment enroll() {
        String username = securityService.getCurrentUser();
        UserMfaSettings settings = repository.findByUsername(username)
            .orElseGet(() -> UserMfaSettings.builder().username(username).build());

        String secret = totpService.generateSecret();
        List<String> recoveryCodes = generateRecoveryCodes();
        settings.setTotpSecret(secret);
        settings.setEnabled(false);
        settings.setRecoveryCodesHash(hashRecoveryCodes(recoveryCodes));
        settings.setRecoveryCodesGeneratedAt(LocalDateTime.now());
        settings.setLastVerifiedAt(null);
        repository.save(settings);

        String otpauthUri = totpService.buildOtpAuthUri(issuer, username, secret);
        return new MfaEnrollment(username, secret, otpauthUri, recoveryCodes);
    }

    @Transactional
    public MfaVerification verify(String code) {
        String username = securityService.getCurrentUser();
        UserMfaSettings settings = repository.findByUsername(username)
            .orElseThrow(() -> new IllegalStateException("MFA enrollment not found"));
        if (settings.getTotpSecret() == null || settings.getTotpSecret().isBlank()) {
            throw new IllegalStateException("MFA secret missing - enroll first");
        }
        boolean valid = totpService.verifyCode(settings.getTotpSecret(), code);
        if (!valid) {
            return new MfaVerification(false, settings.isEnabled(), settings.getLastVerifiedAt());
        }
        settings.setEnabled(true);
        settings.setLastVerifiedAt(LocalDateTime.now());
        repository.save(settings);
        return new MfaVerification(true, true, settings.getLastVerifiedAt());
    }

    @Transactional
    public MfaDisableResult disable(String codeOrRecovery) {
        String username = securityService.getCurrentUser();
        UserMfaSettings settings = repository.findByUsername(username)
            .orElseThrow(() -> new IllegalStateException("MFA enrollment not found"));
        boolean totpValid = settings.getTotpSecret() != null
            && totpService.verifyCode(settings.getTotpSecret(), codeOrRecovery);
        RecoveryCodeCheck recovery = verifyRecoveryCode(settings, codeOrRecovery);

        if (!totpValid && !recovery.matched()) {
            return new MfaDisableResult(false, settings.isEnabled(), recovery.remainingCount());
        }

        if (recovery.matched()) {
            settings.setRecoveryCodesHash(recovery.updatedHash());
        }

        settings.setEnabled(false);
        settings.setTotpSecret(null);
        settings.setLastVerifiedAt(null);
        repository.save(settings);
        return new MfaDisableResult(true, false, recovery.remainingCount());
    }

    @Transactional
    public RecoveryCodes regenerateRecoveryCodes(String totpCode) {
        String username = securityService.getCurrentUser();
        UserMfaSettings settings = repository.findByUsername(username)
            .orElseThrow(() -> new IllegalStateException("MFA enrollment not found"));
        if (!totpService.verifyCode(settings.getTotpSecret(), totpCode)) {
            throw new IllegalArgumentException("Invalid verification code");
        }
        List<String> recoveryCodes = generateRecoveryCodes();
        settings.setRecoveryCodesHash(hashRecoveryCodes(recoveryCodes));
        settings.setRecoveryCodesGeneratedAt(LocalDateTime.now());
        repository.save(settings);
        return new RecoveryCodes(recoveryCodes);
    }

    private List<String> generateRecoveryCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            codes.add(randomCode());
        }
        return codes;
    }

    private String randomCode() {
        String raw = UUID.randomUUID().toString().replace("-", "");
        return raw.substring(0, RECOVERY_CODE_LENGTH).toUpperCase();
    }

    private String hashRecoveryCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return null;
        }
        List<String> hashes = new ArrayList<>();
        for (String code : codes) {
            hashes.add(passwordEncoder.encode(code));
        }
        return String.join(",", hashes);
    }

    private RecoveryCodeCheck verifyRecoveryCode(UserMfaSettings settings, String code) {
        if (settings.getRecoveryCodesHash() == null || settings.getRecoveryCodesHash().isBlank() || code == null) {
            return new RecoveryCodeCheck(false, settings.getRecoveryCodesHash(), 0);
        }
        String[] hashes = settings.getRecoveryCodesHash().split(",");
        List<String> remaining = new ArrayList<>();
        boolean matched = false;
        for (String hash : hashes) {
            if (!matched && passwordEncoder.matches(code.trim(), hash)) {
                matched = true;
                continue;
            }
            remaining.add(hash);
        }
        String updated = remaining.isEmpty() ? null : String.join(",", remaining);
        return new RecoveryCodeCheck(matched, updated, remaining.size());
    }

    public record MfaStatus(
        String username,
        boolean configured,
        boolean enabled,
        LocalDateTime lastVerifiedAt,
        LocalDateTime recoveryCodesGeneratedAt
    ) {}

    public record MfaEnrollment(
        String username,
        String secret,
        String otpauthUri,
        List<String> recoveryCodes
    ) {}

    public record MfaVerification(
        boolean verified,
        boolean enabled,
        LocalDateTime lastVerifiedAt
    ) {}

    public record MfaDisableResult(
        boolean disabled,
        boolean enabled,
        int remainingRecoveryCodes
    ) {}

    public record RecoveryCodes(List<String> recoveryCodes) {
        public RecoveryCodes {
            recoveryCodes = recoveryCodes == null ? Collections.emptyList() : recoveryCodes;
        }
    }

    private record RecoveryCodeCheck(boolean matched, String updatedHash, int remainingCount) {}
}
