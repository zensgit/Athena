package com.ecm.core.license;

import com.ecm.core.license.LicenseService.LicenseInfo;
import com.ecm.core.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LicenseServiceTest {

    private LicenseService serviceWithKey(String licenseKey) {
        LicenseService service = new LicenseService(mock(UserRepository.class));
        ReflectionTestUtils.setField(service, "licenseKey", licenseKey);
        ReflectionTestUtils.setField(service, "publicKeyStr", "");
        service.init();
        return service;
    }

    @Test
    void blankLicenseKeyReportsValidCommunityEdition() {
        LicenseInfo info = serviceWithKey("").getLicenseInfo();

        assertThat(info.getEdition()).isEqualTo("Community");
        assertThat(info.isValid()).isTrue();
        assertThat(info.getMaxUsers()).isEqualTo(5);
        assertThat(info.getMaxStorageGb()).isEqualTo(10L);
        assertThat(info.getExpirationDate()).isNull();
        assertThat(info.getFeatures()).containsExactly("BASIC");
    }

    @Test
    void configuredLicenseKeyDoesNotGrantEnterpriseWithoutVerifier() {
        LicenseService service = serviceWithKey("any-non-empty-key");

        LicenseInfo info = service.getLicenseInfo();
        assertThat(info.getEdition()).isEqualTo("Community");
        assertThat(info.isValid()).isFalse();
        assertThat(info.getMaxUsers()).isEqualTo(5);
        assertThat(info.getMaxStorageGb()).isEqualTo(10L);
        assertThat(info.getExpirationDate()).isNull();
        assertThat(info.getFeatures()).containsExactly("BASIC");
        assertThat(service.isFeatureEnabled("WORKFLOW")).isFalse();
        assertThat(service.isFeatureEnabled("OCR")).isFalse();
        assertThat(service.isFeatureEnabled("BASIC")).isTrue();
    }

    @Test
    void invalidLiteralAlsoFallsBackToInvalidCommunityEdition() {
        LicenseInfo info = serviceWithKey("invalid").getLicenseInfo();

        assertThat(info.getEdition()).isEqualTo("Community");
        assertThat(info.isValid()).isFalse();
        assertThat(info.getFeatures()).containsExactly("BASIC");
    }
}
