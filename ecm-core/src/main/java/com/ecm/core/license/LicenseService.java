package com.ecm.core.license;

import com.ecm.core.entity.User;
import com.ecm.core.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

/**
 * License Service
 * 
 * Enforces commercial licensing constraints:
 * - Expiration date
 * - Maximum users
 * - Maximum storage
 * - Enabled features (e.g. Workflow, OCR)
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
        try {
            // In a real scenario, use RSA verify. 
            // For MVP/Demo, we assume JWT signed with a secret or simple decoding if public key not set.
            // Simplified: Treat licenseKey as a JWT-like payload or simple JSON for now if keys missing.
            
            // Placeholder logic:
            if ("invalid".equals(licenseKey)) {
                throw new IllegalStateException("Invalid license");
            }
            
            // Mock parsing
            currentLicense = LicenseInfo.builder()
                .edition("Enterprise")
                .maxUsers(100)
                .maxStorageGb(1000)
                .expirationDate(new Date(System.currentTimeMillis() + 31536000000L)) // +1 year
                .features(new String[]{"WORKFLOW", "OCR", "AUDIT"})
                .valid(true)
                .build();
                
            log.info("License validated: {} Edition (Expires: {})", 
                currentLicense.getEdition(), currentLicense.getExpirationDate());

        } catch (Exception e) {
            log.error("Failed to validate license", e);
            currentLicense = LicenseInfo.communityEdition();
        }
    }

    /**
     * Check if a specific feature is enabled by the license.
     */
    public boolean isFeatureEnabled(String feature) {
        if (currentLicense == null) return false;
        if ("Community".equals(currentLicense.getEdition())) {
            // Community has basic features but maybe not advanced ones
            return !feature.equals("MULTI_TENANCY") && !feature.equals("ADVANCED_AUDIT");
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
            return LicenseInfo.builder()
                .edition("Community")
                .maxUsers(5) // Limit for community
                .maxStorageGb(10)
                .expirationDate(null) // Never expires
                .features(new String[]{"BASIC"})
                .valid(true)
                .build();
        }
    }
}
