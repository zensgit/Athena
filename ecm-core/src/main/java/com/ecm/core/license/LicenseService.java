package com.ecm.core.license;

import com.ecm.core.entity.User;
import com.ecm.core.repository.UserRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Date;

/**
 * License Service
 *
 * <p>Current status: read-only license reporting placeholder. Until signed license verification is
 * implemented, configured keys must not grant commercial editions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LicenseService {

    private final UserRepository userRepository;

    @Value("${ecm.license.key:}")
    private String licenseKey;

    @Value("${ecm.license.public-key:}")
    private String publicKeyStr;

    private LicenseInfo currentLicense;

    @PostConstruct
    public void init() {
        if (licenseKey == null || licenseKey.isBlank()) {
            log.warn("No license key found. Running in Community Edition mode.");
            currentLicense = LicenseInfo.communityEdition();
        } else {
            validateLicense();
        }
    }

    public void validateLicense() {
        if (licenseKey == null || licenseKey.isBlank()) {
            currentLicense = LicenseInfo.communityEdition();
            return;
        }

        currentLicense = LicenseInfo.communityEdition(false);
        log.warn(
            "Commercial license verification is not implemented; ignoring configured license key{} "
                + "and running in Community Edition mode.",
            (publicKeyStr == null || publicKeyStr.isBlank()) ? "" : " and public key");
    }

    /**
     * Check if a specific feature is enabled by the license.
     */
    public boolean isFeatureEnabled(String feature) {
        if (currentLicense == null || feature == null || currentLicense.getFeatures() == null) {
            return false;
        }
        for (String f : currentLicense.getFeatures()) {
            if (f.equalsIgnoreCase(feature)) return true;
        }
        return false;
    }

    /**
     * Check if creating a new user is allowed.
     */
    public void checkUserLimit() {
        if (currentLicense.getMaxUsers() <= 0) return; // Unlimited
        
        long count = userRepository.count();
        if (count >= currentLicense.getMaxUsers()) {
            throw new IllegalStateException("License user limit reached (" + currentLicense.getMaxUsers() + ")");
        }
    }

    public LicenseInfo getLicenseInfo() {
        return currentLicense;
    }

    @Data
    @Builder
    public static class LicenseInfo {
        private String edition;
        private int maxUsers;
        private long maxStorageGb;
        private Date expirationDate;
        private String[] features;
        private boolean valid;

        public static LicenseInfo communityEdition() {
            return communityEdition(true);
        }

        public static LicenseInfo communityEdition(boolean valid) {
            return LicenseInfo.builder()
                .edition("Community")
                .maxUsers(5) // Limit for community
                .maxStorageGb(10)
                .expirationDate(null) // Never expires
                .features(new String[]{"BASIC"})
                .valid(valid)
                .build();
        }
    }
}
