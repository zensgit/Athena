package com.ecm.core.service;

import com.ecm.core.integration.email.notify.NotificationChannel;
import com.ecm.core.repository.RmReportPresetExecutionRepository;
import com.ecm.core.repository.RmReportPresetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.NoSuchElementException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RmReportPresetDeliveryServiceChannelResolutionTest {

    @Mock private RmReportPresetService presetService;
    @Mock private RmReportPresetRepository presetRepository;
    @Mock private RmReportPresetExecutionRepository executionRepository;
    @Mock private RecordsManagementService recordsManagementService;
    @Mock private DocumentUploadService uploadService;
    @Mock private AuditService auditService;
    @Mock private SecurityService securityService;
    @Mock private ActivityService activityService;
    @Mock private PreferenceService preferenceService;

    private RmReportPresetDeliveryService service() {
        RmReportPresetDeliveryService svc = new RmReportPresetDeliveryService(
            presetService, presetRepository, executionRepository,
            recordsManagementService, uploadService, auditService,
            securityService, activityService, preferenceService
        );
        ReflectionTestUtils.setField(svc, "self", svc);
        return svc;
    }

    @Test
    @DisplayName("resolveDeliveryChannels: inbox only when email pref is absent (opt-in default OFF)")
    void success_inboxOnly_whenEmailPrefAbsent() {
        when(preferenceService.getPreference(eq("alice"), eq(RmReportPresetDeliveryService.PREF_NOTIFY_ON_SUCCESS)))
            .thenThrow(new NoSuchElementException());
        when(preferenceService.getPreference(eq("alice"), eq(RmReportPresetDeliveryService.PREF_NOTIFY_BY_EMAIL_ON_SUCCESS)))
            .thenThrow(new NoSuchElementException());

        Set<String> channels = service().resolveDeliveryChannels("alice", true);

        assertEquals(Set.of(NotificationChannel.INBOX), channels);
    }

    @Test
    @DisplayName("resolveDeliveryChannels: inbox + email when email success pref is true (Boolean)")
    void success_inboxAndEmail_whenEmailPrefTrueBoolean() {
        when(preferenceService.getPreference(eq("alice"), eq(RmReportPresetDeliveryService.PREF_NOTIFY_ON_SUCCESS)))
            .thenThrow(new NoSuchElementException());
        when(preferenceService.getPreference(eq("alice"), eq(RmReportPresetDeliveryService.PREF_NOTIFY_BY_EMAIL_ON_SUCCESS)))
            .thenReturn(Boolean.TRUE);

        Set<String> channels = service().resolveDeliveryChannels("alice", true);

        assertTrue(channels.contains(NotificationChannel.INBOX));
        assertTrue(channels.contains(NotificationChannel.EMAIL));
        assertEquals(2, channels.size());
    }

    @Test
    @DisplayName("resolveDeliveryChannels: inbox + email when email failure pref is string \"true\"")
    void failure_inboxAndEmail_whenEmailPrefStringTrue() {
        when(preferenceService.getPreference(eq("alice"), eq(RmReportPresetDeliveryService.PREF_NOTIFY_ON_FAILURE)))
            .thenThrow(new NoSuchElementException());
        when(preferenceService.getPreference(eq("alice"), eq(RmReportPresetDeliveryService.PREF_NOTIFY_BY_EMAIL_ON_FAILURE)))
            .thenReturn("true");

        Set<String> channels = service().resolveDeliveryChannels("alice", false);

        assertTrue(channels.contains(NotificationChannel.INBOX));
        assertTrue(channels.contains(NotificationChannel.EMAIL));
    }

    @Test
    @DisplayName("resolveDeliveryChannels: empty set when inbox pref is explicitly false")
    void success_emptySet_whenInboxPrefExplicitlyFalse() {
        when(preferenceService.getPreference(eq("alice"), eq(RmReportPresetDeliveryService.PREF_NOTIFY_ON_SUCCESS)))
            .thenReturn(Boolean.FALSE);
        when(preferenceService.getPreference(eq("alice"), eq(RmReportPresetDeliveryService.PREF_NOTIFY_BY_EMAIL_ON_SUCCESS)))
            .thenReturn(Boolean.FALSE);

        Set<String> channels = service().resolveDeliveryChannels("alice", true);

        assertTrue(channels.isEmpty());
    }

    @Test
    @DisplayName("resolveDeliveryChannels: email only when inbox pref is false but email pref is true")
    void success_emailOnly_whenInboxFalseEmailTrue() {
        when(preferenceService.getPreference(eq("alice"), eq(RmReportPresetDeliveryService.PREF_NOTIFY_ON_SUCCESS)))
            .thenReturn(Boolean.FALSE);
        when(preferenceService.getPreference(eq("alice"), eq(RmReportPresetDeliveryService.PREF_NOTIFY_BY_EMAIL_ON_SUCCESS)))
            .thenReturn(Boolean.TRUE);

        Set<String> channels = service().resolveDeliveryChannels("alice", true);

        assertEquals(Set.of(NotificationChannel.EMAIL), channels);
        assertFalse(channels.contains(NotificationChannel.INBOX));
    }

    @Test
    @DisplayName("resolveDeliveryChannels: email pref exception falls back to false (email excluded)")
    void success_emailExcluded_whenEmailPrefThrowsUnexpectedException() {
        when(preferenceService.getPreference(eq("alice"), eq(RmReportPresetDeliveryService.PREF_NOTIFY_ON_SUCCESS)))
            .thenThrow(new NoSuchElementException());
        when(preferenceService.getPreference(eq("alice"), eq(RmReportPresetDeliveryService.PREF_NOTIFY_BY_EMAIL_ON_SUCCESS)))
            .thenThrow(new RuntimeException("pref service down"));

        Set<String> channels = service().resolveDeliveryChannels("alice", true);

        assertEquals(Set.of(NotificationChannel.INBOX), channels);
    }
}
