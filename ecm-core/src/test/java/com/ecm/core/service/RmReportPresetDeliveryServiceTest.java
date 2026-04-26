package com.ecm.core.service;

import com.ecm.core.entity.Activity;
import com.ecm.core.entity.RmReportPreset;
import com.ecm.core.entity.RmReportPresetExecution;
import com.ecm.core.entity.User;
import com.ecm.core.integration.email.notify.NotificationDispatcher;
import com.ecm.core.integration.email.notify.NotificationPayload;
import com.ecm.core.pipeline.PipelineResult;
import com.ecm.core.repository.RmReportPresetExecutionRepository;
import com.ecm.core.repository.RmReportPresetRepository;
import com.ecm.core.repository.UserRepository;
import org.springframework.data.domain.PageImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RmReportPresetDeliveryServiceTest {

    @Mock
    private RmReportPresetService presetService;

    @Mock
    private RmReportPresetRepository presetRepository;

    @Mock
    private RmReportPresetExecutionRepository executionRepository;

    @Mock
    private RecordsManagementService recordsManagementService;

    @Mock
    private DocumentUploadService uploadService;

    @Mock
    private AuditService auditService;

    @Mock
    private SecurityService securityService;

    @Mock
    private ActivityService activityService;

    @Mock
    private PreferenceService preferenceService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @Test
    @DisplayName("updateSchedule enables a schedulable preset and computes next run")
    void updateScheduleEnablesSchedulablePreset() {
        RmReportPreset preset = preset(
            RmReportPreset.Kind.ACTIVITY_FAMILY_REPORT,
            Map.of("from", "2026-04-01T00:00:00", "to", "2026-04-15T23:59:59")
        );
        UUID folderId = UUID.randomUUID();
        when(presetService.getOwned(preset.getId())).thenReturn(preset);
        when(presetRepository.save(any(RmReportPreset.class))).thenAnswer(inv -> inv.getArgument(0));
        when(executionRepository.findFirstByPresetIdOrderByStartedAtDesc(preset.getId())).thenReturn(Optional.empty());

        RmReportPresetDeliveryService service = service();

        RmReportPresetDeliveryService.ScheduleStatusDto result = service.updateSchedule(
            preset.getId(),
            new RmReportPresetDeliveryService.UpdateScheduleRequest(
                true,
                "0 */10 * * * *",
                "UTC",
                folderId
            )
        );

        assertTrue(result.enabled());
        assertEquals("0 */10 * * * *", result.cronExpression());
        assertEquals("UTC", result.timezone());
        assertEquals(folderId, result.deliveryFolderId());
        assertNotNull(result.nextRunAt());
    }

    @Test
    @DisplayName("updateSchedule enables summary-only preset kinds that now support CSV delivery")
    void updateScheduleEnablesSummaryOnlyPresetKinds() {
        RmReportPreset preset = preset(RmReportPreset.Kind.ACTIVITY_FAMILY_HIGHLIGHTS, Map.of("windowDays", 7));
        UUID folderId = UUID.randomUUID();
        when(presetService.getOwned(preset.getId())).thenReturn(preset);
        when(presetRepository.save(any(RmReportPreset.class))).thenAnswer(inv -> inv.getArgument(0));
        when(executionRepository.findFirstByPresetIdOrderByStartedAtDesc(preset.getId())).thenReturn(Optional.empty());

        RmReportPresetDeliveryService service = service();

        RmReportPresetDeliveryService.ScheduleStatusDto result = service.updateSchedule(
            preset.getId(),
            new RmReportPresetDeliveryService.UpdateScheduleRequest(
                true,
                "0 */10 * * * *",
                "UTC",
                folderId
            )
        );

        assertTrue(result.enabled());
        assertEquals(folderId, result.deliveryFolderId());
        assertNotNull(result.nextRunAt());
    }

    @Test
    @DisplayName("deliverNow uploads CSV document and records execution")
    void deliverNowUploadsCsvAndRecordsExecution() throws Exception {
        RmReportPreset preset = preset(
            RmReportPreset.Kind.ACTIVITY_EVENT_TYPE_REPORT,
            Map.of(
                "from", "2026-04-01T00:00:00",
                "to", "2026-04-15T23:59:59",
                "limit", 5
            )
        );
        UUID folderId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        preset.setDeliveryFolderId(folderId);
        when(presetService.getOwned(preset.getId())).thenReturn(preset);
        when(recordsManagementService.getActivityEventTypeReport(
                LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDateTime.of(2026, 4, 15, 23, 59, 59),
                5))
            .thenReturn(
                new RecordsManagementService.ActivityEventTypeReportDto(
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-01T00:00", "2026-04-15T23:59:59"),
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-16T00:00", "2026-03-31T23:59:59"),
                    5,
                    8,
                    5,
                    List.of(
                        new RecordsManagementService.ActivityEventTypeReportEntryDto(
                            "RM_RECORD_DECLARED",
                            "DECLARED",
                            5,
                            2,
                            3,
                            LocalDateTime.of(2026, 4, 15, 10, 0)
                        )
                    )
                )
            );
        when(uploadService.uploadDocument(any(MultipartFile.class), eq(folderId), isNull()))
            .thenReturn(PipelineResult.builder().success(true).documentId(documentId).build());
        when(presetRepository.save(any(RmReportPreset.class))).thenAnswer(inv -> inv.getArgument(0));
        when(executionRepository.save(any(RmReportPresetExecution.class))).thenAnswer(inv -> {
            RmReportPresetExecution execution = inv.getArgument(0);
            execution.setId(UUID.randomUUID());
            return execution;
        });

        RmReportPresetDeliveryService service = service();

        RmReportPresetDeliveryService.PresetExecutionDto result = service.deliverNow(preset.getId());

        assertEquals(RmReportPresetExecution.ExecutionStatus.SUCCESS, result.status());
        assertEquals(documentId, result.documentId());
        assertEquals(folderId, result.targetFolderId());
        assertTrue(result.filename().endsWith(".csv"));

        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(uploadService).uploadDocument(fileCaptor.capture(), eq(folderId), isNull());
        MultipartFile uploaded = fileCaptor.getValue();
        assertEquals("text/csv", uploaded.getContentType());
        assertTrue(new String(uploaded.getBytes(), StandardCharsets.UTF_8).contains("eventType,family,currentCount"));
    }

    @Test
    @DisplayName("deliverNow supports summary-only presets by rendering the current rolling family report CSV")
    void deliverNowSupportsSummaryOnlyPresets() throws Exception {
        RmReportPreset preset = preset(
            RmReportPreset.Kind.ACTIVITY_FAMILY_HIGHLIGHTS,
            Map.of("windowDays", 7)
        );
        UUID folderId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        preset.setDeliveryFolderId(folderId);
        when(presetService.getOwned(preset.getId())).thenReturn(preset);
        when(recordsManagementService.getActivityFamilyReport(
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                isNull(),
                isNull()))
            .thenReturn(
                new RecordsManagementService.ActivityFamilyReportDto(
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-09T00:00:00", "2026-04-15T23:59:59"),
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-02T00:00:00", "2026-04-08T23:59:59"),
                    5,
                    5,
                    7,
                    4,
                    List.of(
                        new RecordsManagementService.ActivityFamilyReportEntryDto(
                            "DECLARED",
                            5,
                            2,
                            3,
                            LocalDateTime.of(2026, 4, 15, 10, 0),
                            List.of(),
                            List.of()
                        )
                    )
                )
            );
        when(uploadService.uploadDocument(any(MultipartFile.class), eq(folderId), isNull()))
            .thenReturn(PipelineResult.builder().success(true).documentId(documentId).build());
        when(presetRepository.save(any(RmReportPreset.class))).thenAnswer(inv -> inv.getArgument(0));
        when(executionRepository.save(any(RmReportPresetExecution.class))).thenAnswer(inv -> {
            RmReportPresetExecution execution = inv.getArgument(0);
            execution.setId(UUID.randomUUID());
            return execution;
        });

        RmReportPresetDeliveryService service = service();

        RmReportPresetDeliveryService.PresetExecutionDto result = service.deliverNow(preset.getId());

        assertEquals(RmReportPresetExecution.ExecutionStatus.SUCCESS, result.status());
        assertEquals(documentId, result.documentId());
        ArgumentCaptor<MultipartFile> fileCaptor = ArgumentCaptor.forClass(MultipartFile.class);
        verify(uploadService).uploadDocument(fileCaptor.capture(), eq(folderId), isNull());
        assertTrue(new String(fileCaptor.getValue().getBytes(), StandardCharsets.UTF_8)
            .contains("family,currentCount,previousCount,delta"));
    }

    @Test
    @DisplayName("runScheduledDeliveries executes due presets and advances nextRunAt")
    void runScheduledDeliveriesExecutesDuePresets() throws Exception {
        RmReportPreset duePreset = preset(
            RmReportPreset.Kind.ACTIVITY_CONTRIBUTOR_REPORT,
            Map.of(
                "from", "2026-04-01T00:00:00",
                "to", "2026-04-15T23:59:59",
                "limit", 5,
                "eventTypeLimit", 3
            )
        );
        duePreset.setScheduleEnabled(true);
        duePreset.setCronExpression("0 */10 * * * *");
        duePreset.setScheduleTimezone("UTC");
        duePreset.setDeliveryFolderId(UUID.randomUUID());
        duePreset.setNextRunAt(LocalDateTime.now().minusMinutes(1));
        LocalDateTime claimedNextRunAt = ScheduledRuleValidation.computeNextRunAt(
            duePreset.getCronExpression(),
            duePreset.getScheduleTimezone(),
            duePreset.getNextRunAt()
        );
        RmReportPreset claimedPreset = preset(
            RmReportPreset.Kind.ACTIVITY_CONTRIBUTOR_REPORT,
            duePreset.getParams()
        );
        claimedPreset.setId(duePreset.getId());
        claimedPreset.setOwner(duePreset.getOwner());
        claimedPreset.setScheduleEnabled(true);
        claimedPreset.setCronExpression(duePreset.getCronExpression());
        claimedPreset.setScheduleTimezone(duePreset.getScheduleTimezone());
        claimedPreset.setDeliveryFolderId(duePreset.getDeliveryFolderId());
        claimedPreset.setNextRunAt(claimedNextRunAt);

        when(presetRepository.findByScheduleEnabledTrueAndDeletedFalseAndNextRunAtLessThanEqualOrderByNextRunAtAsc(any(LocalDateTime.class)))
            .thenReturn(List.of(duePreset));
        when(presetRepository.claimScheduledRun(duePreset.getId(), duePreset.getNextRunAt(), claimedNextRunAt))
            .thenReturn(1);
        when(presetRepository.findByIdAndDeletedFalse(duePreset.getId()))
            .thenReturn(Optional.of(claimedPreset));
        when(recordsManagementService.getActivityContributorReport(
                LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDateTime.of(2026, 4, 15, 23, 59, 59),
                5,
                3))
            .thenReturn(
                new RecordsManagementService.ActivityContributorReportDto(
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-01T00:00", "2026-04-15T23:59:59"),
                    new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-16T00:00", "2026-03-31T23:59:59"),
                    5,
                    3,
                    12,
                    6,
                    List.of(
                        new RecordsManagementService.ActivityContributorReportEntryDto(
                            "admin",
                            "admin",
                            8,
                            4,
                            4,
                            LocalDateTime.of(2026, 4, 15, 10, 0),
                            List.of()
                        )
                    )
                )
            );
        when(uploadService.uploadDocument(any(MultipartFile.class), eq(claimedPreset.getDeliveryFolderId()), isNull()))
            .thenReturn(PipelineResult.builder().success(true).documentId(UUID.randomUUID()).build());
        when(presetRepository.save(any(RmReportPreset.class))).thenAnswer(inv -> inv.getArgument(0));
        when(executionRepository.save(any(RmReportPresetExecution.class))).thenAnswer(inv -> inv.getArgument(0));

        RmReportPresetDeliveryService service = service();

        service.runScheduledDeliveries();

        assertNotNull(claimedPreset.getLastRunAt());
        assertEquals(claimedNextRunAt, claimedPreset.getNextRunAt());
        verify(presetRepository).claimScheduledRun(duePreset.getId(), duePreset.getNextRunAt(), claimedNextRunAt);
    }

    @Test
    @DisplayName("runScheduledDeliveries dispatches notification via dispatcher when scheduled delivery succeeds")
    void runScheduledDeliveriesPostsDirectNotificationOnSuccess() throws Exception {
        RmReportPreset duePreset = preset(
            RmReportPreset.Kind.ACTIVITY_CONTRIBUTOR_REPORT,
            Map.of(
                "from", "2026-04-01T00:00:00",
                "to", "2026-04-15T23:59:59",
                "limit", 5,
                "eventTypeLimit", 3
            )
        );
        duePreset.setScheduleEnabled(true);
        duePreset.setCronExpression("0 */10 * * * *");
        duePreset.setScheduleTimezone("UTC");
        UUID folderId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        duePreset.setDeliveryFolderId(folderId);
        duePreset.setNextRunAt(LocalDateTime.now().minusMinutes(1));
        LocalDateTime claimedNextRunAt = ScheduledRuleValidation.computeNextRunAt(
            duePreset.getCronExpression(),
            duePreset.getScheduleTimezone(),
            duePreset.getNextRunAt()
        );
        RmReportPreset claimedPreset = preset(RmReportPreset.Kind.ACTIVITY_CONTRIBUTOR_REPORT, duePreset.getParams());
        claimedPreset.setId(duePreset.getId());
        claimedPreset.setOwner(duePreset.getOwner());
        claimedPreset.setScheduleEnabled(true);
        claimedPreset.setCronExpression(duePreset.getCronExpression());
        claimedPreset.setScheduleTimezone(duePreset.getScheduleTimezone());
        claimedPreset.setDeliveryFolderId(folderId);
        claimedPreset.setNextRunAt(claimedNextRunAt);

        Activity savedActivity = new Activity();
        when(activityService.createNotificationActivity(
            eq("rm.report_preset.delivery.succeeded"), any(), any(), any(), any(), any()
        )).thenReturn(savedActivity);
        when(presetRepository.findByScheduleEnabledTrueAndDeletedFalseAndNextRunAtLessThanEqualOrderByNextRunAtAsc(any(LocalDateTime.class)))
            .thenReturn(List.of(duePreset));
        when(presetRepository.claimScheduledRun(duePreset.getId(), duePreset.getNextRunAt(), claimedNextRunAt))
            .thenReturn(1);
        when(presetRepository.findByIdAndDeletedFalse(duePreset.getId()))
            .thenReturn(Optional.of(claimedPreset));
        when(recordsManagementService.getActivityContributorReport(
            LocalDateTime.of(2026, 4, 1, 0, 0),
            LocalDateTime.of(2026, 4, 15, 23, 59, 59),
            5,
            3
        )).thenReturn(
            new RecordsManagementService.ActivityContributorReportDto(
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-01T00:00", "2026-04-15T23:59:59"),
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-16T00:00", "2026-03-31T23:59:59"),
                5,
                3,
                12,
                6,
                List.of()
            )
        );
        when(uploadService.uploadDocument(any(MultipartFile.class), eq(folderId), isNull()))
            .thenReturn(PipelineResult.builder().success(true).documentId(documentId).build());
        when(presetRepository.save(any(RmReportPreset.class))).thenAnswer(inv -> inv.getArgument(0));
        when(executionRepository.save(any(RmReportPresetExecution.class))).thenAnswer(inv -> {
            RmReportPresetExecution execution = inv.getArgument(0);
            execution.setId(UUID.randomUUID());
            return execution;
        });

        RmReportPresetDeliveryService service = service();

        service.runScheduledDeliveries();

        ArgumentCaptor<Map<String, Object>> summaryCaptor = ArgumentCaptor.forClass(Map.class);
        verify(activityService).createNotificationActivity(
            eq("rm.report_preset.delivery.succeeded"),
            eq("system"),
            isNull(),
            eq(documentId),
            org.mockito.ArgumentMatchers.endsWith(".csv"),
            summaryCaptor.capture()
        );
        assertEquals("Preset", summaryCaptor.getValue().get("presetName"));
        assertEquals("SUCCESS", summaryCaptor.getValue().get("status"));
        assertEquals(documentId.toString(), summaryCaptor.getValue().get("documentId"));
        assertTrue(summaryCaptor.getValue().containsKey("durationMs"));

        ArgumentCaptor<NotificationPayload> payloadCaptor = ArgumentCaptor.forClass(NotificationPayload.class);
        verify(notificationDispatcher).dispatch(payloadCaptor.capture(), anyCollection());
        assertEquals("rm.report_preset.delivery.succeeded", payloadCaptor.getValue().getType());
        assertEquals("admin", payloadCaptor.getValue().getRecipientUserId());
        assertTrue(payloadCaptor.getValue().getTemplateVars().containsKey("durationMs"));
    }

    @Test
    @DisplayName("runScheduledDeliveries keeps successful execution when success notification publish fails")
    void runScheduledDeliveriesKeepsSuccessWhenSuccessNotificationPublishFails() throws Exception {
        RmReportPreset duePreset = preset(
            RmReportPreset.Kind.ACTIVITY_CONTRIBUTOR_REPORT,
            Map.of(
                "from", "2026-04-01T00:00:00",
                "to", "2026-04-15T23:59:59",
                "limit", 5,
                "eventTypeLimit", 3
            )
        );
        duePreset.setScheduleEnabled(true);
        duePreset.setCronExpression("0 */10 * * * *");
        duePreset.setScheduleTimezone("UTC");
        UUID folderId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        duePreset.setDeliveryFolderId(folderId);
        duePreset.setNextRunAt(LocalDateTime.now().minusMinutes(1));
        LocalDateTime claimedNextRunAt = ScheduledRuleValidation.computeNextRunAt(
            duePreset.getCronExpression(),
            duePreset.getScheduleTimezone(),
            duePreset.getNextRunAt()
        );
        RmReportPreset claimedPreset = preset(RmReportPreset.Kind.ACTIVITY_CONTRIBUTOR_REPORT, duePreset.getParams());
        claimedPreset.setId(duePreset.getId());
        claimedPreset.setOwner(duePreset.getOwner());
        claimedPreset.setScheduleEnabled(true);
        claimedPreset.setCronExpression(duePreset.getCronExpression());
        claimedPreset.setScheduleTimezone(duePreset.getScheduleTimezone());
        claimedPreset.setDeliveryFolderId(folderId);
        claimedPreset.setNextRunAt(claimedNextRunAt);

        when(presetRepository.findByScheduleEnabledTrueAndDeletedFalseAndNextRunAtLessThanEqualOrderByNextRunAtAsc(any(LocalDateTime.class)))
            .thenReturn(List.of(duePreset));
        when(presetRepository.claimScheduledRun(duePreset.getId(), duePreset.getNextRunAt(), claimedNextRunAt))
            .thenReturn(1);
        when(presetRepository.findByIdAndDeletedFalse(duePreset.getId()))
            .thenReturn(Optional.of(claimedPreset));
        when(recordsManagementService.getActivityContributorReport(
            LocalDateTime.of(2026, 4, 1, 0, 0),
            LocalDateTime.of(2026, 4, 15, 23, 59, 59),
            5,
            3
        )).thenReturn(
            new RecordsManagementService.ActivityContributorReportDto(
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-01T00:00", "2026-04-15T23:59:59"),
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-16T00:00", "2026-03-31T23:59:59"),
                5,
                3,
                12,
                6,
                List.of()
            )
        );
        when(uploadService.uploadDocument(any(MultipartFile.class), eq(folderId), isNull()))
            .thenReturn(PipelineResult.builder().success(true).documentId(documentId).build());
        when(presetRepository.save(any(RmReportPreset.class))).thenAnswer(inv -> inv.getArgument(0));
        when(executionRepository.save(any(RmReportPresetExecution.class))).thenAnswer(inv -> {
            RmReportPresetExecution execution = inv.getArgument(0);
            execution.setId(UUID.randomUUID());
            return execution;
        });
        doThrow(new RuntimeException("notification down"))
            .when(activityService)
            .createNotificationActivity(
                eq("rm.report_preset.delivery.succeeded"),
                any(), any(), any(), any(), any()
            );

        RmReportPresetDeliveryService service = service();

        service.runScheduledDeliveries();

        ArgumentCaptor<RmReportPresetExecution> executionCaptor =
            ArgumentCaptor.forClass(RmReportPresetExecution.class);
        verify(executionRepository).save(executionCaptor.capture());
        assertEquals(RmReportPresetExecution.ExecutionStatus.SUCCESS, executionCaptor.getValue().getStatus());
        assertEquals(documentId, executionCaptor.getValue().getDocumentId());
    }

    @Test
    @DisplayName("runScheduledDeliveries skips direct success notification when owner disables it")
    void runScheduledDeliveriesSkipsDirectSuccessNotificationWhenDisabledByPreference() throws Exception {
        RmReportPreset duePreset = preset(
            RmReportPreset.Kind.ACTIVITY_CONTRIBUTOR_REPORT,
            Map.of(
                "from", "2026-04-01T00:00:00",
                "to", "2026-04-15T23:59:59",
                "limit", 5,
                "eventTypeLimit", 3
            )
        );
        duePreset.setScheduleEnabled(true);
        duePreset.setCronExpression("0 */10 * * * *");
        duePreset.setScheduleTimezone("UTC");
        UUID folderId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        duePreset.setDeliveryFolderId(folderId);
        duePreset.setNextRunAt(LocalDateTime.now().minusMinutes(1));
        LocalDateTime claimedNextRunAt = ScheduledRuleValidation.computeNextRunAt(
            duePreset.getCronExpression(),
            duePreset.getScheduleTimezone(),
            duePreset.getNextRunAt()
        );
        RmReportPreset claimedPreset = preset(RmReportPreset.Kind.ACTIVITY_CONTRIBUTOR_REPORT, duePreset.getParams());
        claimedPreset.setId(duePreset.getId());
        claimedPreset.setOwner(duePreset.getOwner());
        claimedPreset.setScheduleEnabled(true);
        claimedPreset.setCronExpression(duePreset.getCronExpression());
        claimedPreset.setScheduleTimezone(duePreset.getScheduleTimezone());
        claimedPreset.setDeliveryFolderId(folderId);
        claimedPreset.setNextRunAt(claimedNextRunAt);

        when(presetRepository.findByScheduleEnabledTrueAndDeletedFalseAndNextRunAtLessThanEqualOrderByNextRunAtAsc(any(LocalDateTime.class)))
            .thenReturn(List.of(duePreset));
        when(presetRepository.claimScheduledRun(duePreset.getId(), duePreset.getNextRunAt(), claimedNextRunAt))
            .thenReturn(1);
        when(presetRepository.findByIdAndDeletedFalse(duePreset.getId()))
            .thenReturn(Optional.of(claimedPreset));
        when(recordsManagementService.getActivityContributorReport(
            LocalDateTime.of(2026, 4, 1, 0, 0),
            LocalDateTime.of(2026, 4, 15, 23, 59, 59),
            5,
            3
        )).thenReturn(
            new RecordsManagementService.ActivityContributorReportDto(
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-01T00:00", "2026-04-15T23:59:59"),
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-16T00:00", "2026-03-31T23:59:59"),
                5,
                3,
                12,
                6,
                List.of()
            )
        );
        when(uploadService.uploadDocument(any(MultipartFile.class), eq(folderId), isNull()))
            .thenReturn(PipelineResult.builder().success(true).documentId(documentId).build());
        when(presetRepository.save(any(RmReportPreset.class))).thenAnswer(inv -> inv.getArgument(0));
        when(executionRepository.save(any(RmReportPresetExecution.class))).thenAnswer(inv -> {
            RmReportPresetExecution execution = inv.getArgument(0);
            execution.setId(UUID.randomUUID());
            return execution;
        });
        when(preferenceService.getPreference("admin", RmReportPresetDeliveryService.PREF_NOTIFY_ON_SUCCESS))
            .thenReturn(false);

        RmReportPresetDeliveryService service = service();

        service.runScheduledDeliveries();

        verify(activityService, never()).createNotificationActivity(any(), any(), any(), any(), any(), any());
        verify(notificationDispatcher, never()).dispatch(any(), any());
    }

    @Test
    @DisplayName("runScheduledDeliveries dispatches notification via dispatcher when scheduled delivery fails")
    void runScheduledDeliveriesPostsDirectNotificationOnFailure() throws Exception {
        RmReportPreset duePreset = preset(
            RmReportPreset.Kind.ACTIVITY_CONTRIBUTOR_REPORT,
            Map.of(
                "from", "2026-04-01T00:00:00",
                "to", "2026-04-15T23:59:59",
                "limit", 5,
                "eventTypeLimit", 3
            )
        );
        duePreset.setScheduleEnabled(true);
        duePreset.setCronExpression("0 */10 * * * *");
        duePreset.setScheduleTimezone("UTC");
        UUID folderId = UUID.randomUUID();
        duePreset.setDeliveryFolderId(folderId);
        duePreset.setNextRunAt(LocalDateTime.now().minusMinutes(1));
        LocalDateTime claimedNextRunAt = ScheduledRuleValidation.computeNextRunAt(
            duePreset.getCronExpression(),
            duePreset.getScheduleTimezone(),
            duePreset.getNextRunAt()
        );
        RmReportPreset claimedPreset = preset(RmReportPreset.Kind.ACTIVITY_CONTRIBUTOR_REPORT, duePreset.getParams());
        claimedPreset.setId(duePreset.getId());
        claimedPreset.setOwner(duePreset.getOwner());
        claimedPreset.setScheduleEnabled(true);
        claimedPreset.setCronExpression(duePreset.getCronExpression());
        claimedPreset.setScheduleTimezone(duePreset.getScheduleTimezone());
        claimedPreset.setDeliveryFolderId(folderId);
        claimedPreset.setNextRunAt(claimedNextRunAt);

        Activity savedActivity = new Activity();
        when(activityService.createNotificationActivity(
            eq("rm.report_preset.delivery.failed"), any(), any(), any(), any(), any()
        )).thenReturn(savedActivity);
        when(presetRepository.findByScheduleEnabledTrueAndDeletedFalseAndNextRunAtLessThanEqualOrderByNextRunAtAsc(any(LocalDateTime.class)))
            .thenReturn(List.of(duePreset));
        when(presetRepository.claimScheduledRun(duePreset.getId(), duePreset.getNextRunAt(), claimedNextRunAt))
            .thenReturn(1);
        when(presetRepository.findByIdAndDeletedFalse(duePreset.getId()))
            .thenReturn(Optional.of(claimedPreset));
        when(recordsManagementService.getActivityContributorReport(
            LocalDateTime.of(2026, 4, 1, 0, 0),
            LocalDateTime.of(2026, 4, 15, 23, 59, 59),
            5,
            3
        )).thenReturn(
            new RecordsManagementService.ActivityContributorReportDto(
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-01T00:00", "2026-04-15T23:59:59"),
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-16T00:00", "2026-03-31T23:59:59"),
                5,
                3,
                12,
                6,
                List.of()
            )
        );
        when(uploadService.uploadDocument(any(MultipartFile.class), eq(folderId), isNull()))
            .thenReturn(PipelineResult.builder().success(false).errors(Map.of("upload", "Folder not found")).build());
        when(presetRepository.save(any(RmReportPreset.class))).thenAnswer(inv -> inv.getArgument(0));
        when(executionRepository.save(any(RmReportPresetExecution.class))).thenAnswer(inv -> {
            RmReportPresetExecution execution = inv.getArgument(0);
            execution.setId(UUID.randomUUID());
            return execution;
        });

        RmReportPresetDeliveryService service = service();

        service.runScheduledDeliveries();

        ArgumentCaptor<Map<String, Object>> summaryCaptor = ArgumentCaptor.forClass(Map.class);
        verify(activityService).createNotificationActivity(
            eq("rm.report_preset.delivery.failed"),
            eq("system"),
            isNull(),
            eq(folderId),
            isNull(),
            summaryCaptor.capture()
        );
        assertEquals("Preset", summaryCaptor.getValue().get("presetName"));
        assertEquals("FAILED", summaryCaptor.getValue().get("status"));
        assertEquals("Folder not found", summaryCaptor.getValue().get("message"));
        assertTrue(summaryCaptor.getValue().containsKey("durationMs"));

        ArgumentCaptor<NotificationPayload> payloadCaptor = ArgumentCaptor.forClass(NotificationPayload.class);
        verify(notificationDispatcher).dispatch(payloadCaptor.capture(), anyCollection());
        assertEquals("rm.report_preset.delivery.failed", payloadCaptor.getValue().getType());
        assertEquals("admin", payloadCaptor.getValue().getRecipientUserId());
        assertTrue(payloadCaptor.getValue().getTemplateVars().containsKey("durationMs"));
    }

    @Test
    @DisplayName("runScheduledDeliveries keeps failed execution when failure notification publish fails")
    void runScheduledDeliveriesKeepsFailureWhenFailureNotificationPublishFails() throws Exception {
        RmReportPreset duePreset = preset(
            RmReportPreset.Kind.ACTIVITY_CONTRIBUTOR_REPORT,
            Map.of(
                "from", "2026-04-01T00:00:00",
                "to", "2026-04-15T23:59:59",
                "limit", 5,
                "eventTypeLimit", 3
            )
        );
        duePreset.setScheduleEnabled(true);
        duePreset.setCronExpression("0 */10 * * * *");
        duePreset.setScheduleTimezone("UTC");
        UUID folderId = UUID.randomUUID();
        duePreset.setDeliveryFolderId(folderId);
        duePreset.setNextRunAt(LocalDateTime.now().minusMinutes(1));
        LocalDateTime claimedNextRunAt = ScheduledRuleValidation.computeNextRunAt(
            duePreset.getCronExpression(),
            duePreset.getScheduleTimezone(),
            duePreset.getNextRunAt()
        );
        RmReportPreset claimedPreset = preset(RmReportPreset.Kind.ACTIVITY_CONTRIBUTOR_REPORT, duePreset.getParams());
        claimedPreset.setId(duePreset.getId());
        claimedPreset.setOwner(duePreset.getOwner());
        claimedPreset.setScheduleEnabled(true);
        claimedPreset.setCronExpression(duePreset.getCronExpression());
        claimedPreset.setScheduleTimezone(duePreset.getScheduleTimezone());
        claimedPreset.setDeliveryFolderId(folderId);
        claimedPreset.setNextRunAt(claimedNextRunAt);

        when(presetRepository.findByScheduleEnabledTrueAndDeletedFalseAndNextRunAtLessThanEqualOrderByNextRunAtAsc(any(LocalDateTime.class)))
            .thenReturn(List.of(duePreset));
        when(presetRepository.claimScheduledRun(duePreset.getId(), duePreset.getNextRunAt(), claimedNextRunAt))
            .thenReturn(1);
        when(presetRepository.findByIdAndDeletedFalse(duePreset.getId()))
            .thenReturn(Optional.of(claimedPreset));
        when(recordsManagementService.getActivityContributorReport(
            LocalDateTime.of(2026, 4, 1, 0, 0),
            LocalDateTime.of(2026, 4, 15, 23, 59, 59),
            5,
            3
        )).thenReturn(
            new RecordsManagementService.ActivityContributorReportDto(
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-01T00:00", "2026-04-15T23:59:59"),
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-16T00:00", "2026-03-31T23:59:59"),
                5,
                3,
                12,
                6,
                List.of()
            )
        );
        when(uploadService.uploadDocument(any(MultipartFile.class), eq(folderId), isNull()))
            .thenReturn(PipelineResult.builder().success(false).errors(Map.of("upload", "Folder not found")).build());
        when(presetRepository.save(any(RmReportPreset.class))).thenAnswer(inv -> inv.getArgument(0));
        when(executionRepository.save(any(RmReportPresetExecution.class))).thenAnswer(inv -> {
            RmReportPresetExecution execution = inv.getArgument(0);
            execution.setId(UUID.randomUUID());
            return execution;
        });
        doThrow(new RuntimeException("notification down"))
            .when(activityService)
            .createNotificationActivity(
                eq("rm.report_preset.delivery.failed"),
                any(), any(), any(), any(), any()
            );

        RmReportPresetDeliveryService service = service();

        service.runScheduledDeliveries();

        ArgumentCaptor<RmReportPresetExecution> executionCaptor =
            ArgumentCaptor.forClass(RmReportPresetExecution.class);
        verify(executionRepository).save(executionCaptor.capture());
        assertEquals(RmReportPresetExecution.ExecutionStatus.FAILED, executionCaptor.getValue().getStatus());
        assertEquals("Folder not found", executionCaptor.getValue().getMessage());
    }

    @Test
    @DisplayName("runScheduledDeliveries skips direct failure notification when owner disables it")
    void runScheduledDeliveriesSkipsDirectFailureNotificationWhenDisabledByPreference() throws Exception {
        RmReportPreset duePreset = preset(
            RmReportPreset.Kind.ACTIVITY_CONTRIBUTOR_REPORT,
            Map.of(
                "from", "2026-04-01T00:00:00",
                "to", "2026-04-15T23:59:59",
                "limit", 5,
                "eventTypeLimit", 3
            )
        );
        duePreset.setScheduleEnabled(true);
        duePreset.setCronExpression("0 */10 * * * *");
        duePreset.setScheduleTimezone("UTC");
        UUID folderId = UUID.randomUUID();
        duePreset.setDeliveryFolderId(folderId);
        duePreset.setNextRunAt(LocalDateTime.now().minusMinutes(1));
        LocalDateTime claimedNextRunAt = ScheduledRuleValidation.computeNextRunAt(
            duePreset.getCronExpression(),
            duePreset.getScheduleTimezone(),
            duePreset.getNextRunAt()
        );
        RmReportPreset claimedPreset = preset(RmReportPreset.Kind.ACTIVITY_CONTRIBUTOR_REPORT, duePreset.getParams());
        claimedPreset.setId(duePreset.getId());
        claimedPreset.setOwner(duePreset.getOwner());
        claimedPreset.setScheduleEnabled(true);
        claimedPreset.setCronExpression(duePreset.getCronExpression());
        claimedPreset.setScheduleTimezone(duePreset.getScheduleTimezone());
        claimedPreset.setDeliveryFolderId(folderId);
        claimedPreset.setNextRunAt(claimedNextRunAt);

        when(presetRepository.findByScheduleEnabledTrueAndDeletedFalseAndNextRunAtLessThanEqualOrderByNextRunAtAsc(any(LocalDateTime.class)))
            .thenReturn(List.of(duePreset));
        when(presetRepository.claimScheduledRun(duePreset.getId(), duePreset.getNextRunAt(), claimedNextRunAt))
            .thenReturn(1);
        when(presetRepository.findByIdAndDeletedFalse(duePreset.getId()))
            .thenReturn(Optional.of(claimedPreset));
        when(recordsManagementService.getActivityContributorReport(
            LocalDateTime.of(2026, 4, 1, 0, 0),
            LocalDateTime.of(2026, 4, 15, 23, 59, 59),
            5,
            3
        )).thenReturn(
            new RecordsManagementService.ActivityContributorReportDto(
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-01T00:00", "2026-04-15T23:59:59"),
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-16T00:00", "2026-03-31T23:59:59"),
                5,
                3,
                12,
                6,
                List.of()
            )
        );
        when(uploadService.uploadDocument(any(MultipartFile.class), eq(folderId), isNull()))
            .thenReturn(PipelineResult.builder().success(false).errors(Map.of("upload", "Folder not found")).build());
        when(presetRepository.save(any(RmReportPreset.class))).thenAnswer(inv -> inv.getArgument(0));
        when(executionRepository.save(any(RmReportPresetExecution.class))).thenAnswer(inv -> {
            RmReportPresetExecution execution = inv.getArgument(0);
            execution.setId(UUID.randomUUID());
            return execution;
        });
        when(preferenceService.getPreference("admin", RmReportPresetDeliveryService.PREF_NOTIFY_ON_FAILURE))
            .thenReturn(false);

        RmReportPresetDeliveryService service = service();

        service.runScheduledDeliveries();

        verify(activityService, never()).createNotificationActivity(any(), any(), any(), any(), any(), any());
        verify(notificationDispatcher, never()).dispatch(any(), any());
    }

    @Test
    @DisplayName("runScheduledDeliveries skips presets that were already claimed by another runner")
    void runScheduledDeliveriesSkipsUnclaimedPreset() {
        RmReportPreset duePreset = preset(
            RmReportPreset.Kind.ACTIVITY_CONTRIBUTOR_REPORT,
            Map.of(
                "from", "2026-04-01T00:00:00",
                "to", "2026-04-15T23:59:59",
                "limit", 5,
                "eventTypeLimit", 3
            )
        );
        duePreset.setScheduleEnabled(true);
        duePreset.setCronExpression("0 */10 * * * *");
        duePreset.setScheduleTimezone("UTC");
        duePreset.setDeliveryFolderId(UUID.randomUUID());
        duePreset.setNextRunAt(LocalDateTime.now().minusMinutes(1));
        LocalDateTime claimedNextRunAt = ScheduledRuleValidation.computeNextRunAt(
            duePreset.getCronExpression(),
            duePreset.getScheduleTimezone(),
            duePreset.getNextRunAt()
        );

        when(presetRepository.findByScheduleEnabledTrueAndDeletedFalseAndNextRunAtLessThanEqualOrderByNextRunAtAsc(any(LocalDateTime.class)))
            .thenReturn(List.of(duePreset));
        when(presetRepository.claimScheduledRun(duePreset.getId(), duePreset.getNextRunAt(), claimedNextRunAt))
            .thenReturn(0);

        RmReportPresetDeliveryService service = service();

        service.runScheduledDeliveries();

        verify(presetRepository).claimScheduledRun(duePreset.getId(), duePreset.getNextRunAt(), claimedNextRunAt);
        verifyNoInteractions(uploadService, executionRepository, auditService);
    }

    @Test
    @DisplayName("runScheduledDeliveriesNow audits admin ops trigger")
    void runScheduledDeliveriesNowAuditsAdminOpsTrigger() {
        when(presetRepository.findByScheduleEnabledTrueAndDeletedFalseAndNextRunAtLessThanEqualOrderByNextRunAtAsc(any(LocalDateTime.class)))
            .thenReturn(List.of());
        when(securityService.getCurrentUser()).thenReturn("admin");

        RmReportPresetDeliveryService service = service();

        RmReportPresetDeliveryService.ScheduledRunResultDto result =
            service.runScheduledDeliveriesNow();

        assertEquals(0, result.processedCount());
        assertNotNull(result.generatedAt());
        verify(auditService).logEvent(
            eq("RM_REPORT_PRESET_SCHEDULED_DELIVERIES_TRIGGERED"),
            isNull(),
            eq("rm-report-preset-scheduled-deliveries"),
            eq("admin"),
            org.mockito.ArgumentMatchers.contains("processedCount=0")
        );
    }

    @Test
    @DisplayName("listExecutionLedger filters by owner and optional preset/status/trigger/time window")
    void listExecutionLedgerFiltersByOwnerAndCriteria() {
        UUID presetId = UUID.randomUUID();
        RmReportPreset ownedPreset = preset(RmReportPreset.Kind.ACTIVITY_FAMILY_REPORT, Map.of());
        ownedPreset.setId(presetId);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(presetService.getOwned(presetId)).thenReturn(ownedPreset);
        when(executionRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(
                execution(
                    presetId,
                    "Weekly Family Report",
                    RmReportPreset.Kind.ACTIVITY_FAMILY_REPORT,
                    RmReportPresetExecution.TriggerType.SCHEDULED,
                    RmReportPresetExecution.ExecutionStatus.SUCCESS
                )
            )));

        RmReportPresetDeliveryService service = service();

        org.springframework.data.domain.Page<RmReportPresetDeliveryService.PresetExecutionDto> page = service.listExecutionLedger(
            presetId,
            RmReportPresetExecution.ExecutionStatus.SUCCESS,
            RmReportPresetExecution.TriggerType.SCHEDULED,
            LocalDateTime.of(2026, 4, 21, 0, 0),
            LocalDateTime.of(2026, 4, 21, 23, 59, 59),
            0,
            10
        );

        assertEquals(1, page.getTotalElements());
        assertEquals("Weekly Family Report", page.getContent().get(0).presetName());
        assertEquals(RmReportPreset.Kind.ACTIVITY_FAMILY_REPORT, page.getContent().get(0).presetKind());
    }

    @Test
    @DisplayName("exportExecutionLedgerCsv writes preset metadata and execution fields")
    void exportExecutionLedgerCsvWritesPresetMetadataAndExecutionFields() {
        UUID presetId = UUID.randomUUID();
        RmReportPreset ownedPreset = preset(RmReportPreset.Kind.ACTIVITY_FAMILY_REPORT, Map.of());
        ownedPreset.setId(presetId);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(presetService.getOwned(presetId)).thenReturn(ownedPreset);
        when(executionRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(
                execution(
                    presetId,
                    "Weekly Family Report",
                    RmReportPreset.Kind.ACTIVITY_FAMILY_REPORT,
                    RmReportPresetExecution.TriggerType.MANUAL,
                    RmReportPresetExecution.ExecutionStatus.FAILED
                )
            )));

        RmReportPresetDeliveryService service = service();

        String csv = service.exportExecutionLedgerCsv(
            presetId,
            RmReportPresetExecution.ExecutionStatus.FAILED,
            RmReportPresetExecution.TriggerType.MANUAL,
            null,
            null,
            50
        );

        assertTrue(csv.contains("executionId,presetId,presetName,presetKind,triggerType,status"));
        assertTrue(csv.contains("Weekly Family Report"));
        assertTrue(csv.contains("ACTIVITY_FAMILY_REPORT"));
        assertTrue(csv.contains("FAILED"));
    }

    @Test
    @DisplayName("getScheduledDeliveryTelemetry aggregates owner-scoped counts")
    void getScheduledDeliveryTelemetryAggregatesOwnerScopedCounts() {
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(presetRepository.countByOwnerAndScheduleEnabledTrueAndDeletedFalse("admin")).thenReturn(5L);
        when(presetRepository.countByOwnerAndScheduleEnabledTrueAndDeletedFalseAndNextRunAtLessThanEqual(
            eq("admin"), any(LocalDateTime.class))).thenReturn(2L);
        when(executionRepository.countByOwnerAndStatusAndStartedAtGreaterThanEqual(
            eq("admin"), eq(RmReportPresetExecution.ExecutionStatus.SUCCESS), any(LocalDateTime.class)))
            .thenReturn(7L);
        when(executionRepository.countByOwnerAndStatusAndStartedAtGreaterThanEqual(
            eq("admin"), eq(RmReportPresetExecution.ExecutionStatus.FAILED), any(LocalDateTime.class)))
            .thenReturn(1L);
        RmReportPresetExecution latest = execution(
            UUID.randomUUID(),
            "Weekly Family Report",
            RmReportPreset.Kind.ACTIVITY_FAMILY_REPORT,
            RmReportPresetExecution.TriggerType.SCHEDULED,
            RmReportPresetExecution.ExecutionStatus.SUCCESS
        );
        latest.setStartedAt(LocalDateTime.of(2026, 4, 21, 9, 0));
        when(executionRepository.findFirstByOwnerOrderByStartedAtDesc("admin"))
            .thenReturn(Optional.of(latest));

        RmReportPresetDeliveryService service = service();

        RmReportPresetDeliveryService.ScheduledDeliveryTelemetryDto telemetry =
            service.getScheduledDeliveryTelemetry();

        assertEquals(5L, telemetry.scheduleEnabledCount());
        assertEquals(2L, telemetry.duePresetCount());
        assertEquals(7L, telemetry.last24hSuccessCount());
        assertEquals(1L, telemetry.last24hFailedCount());
        assertEquals(LocalDateTime.of(2026, 4, 21, 9, 0), telemetry.lastExecutionAt());
        assertNotNull(telemetry.generatedAt());
    }

    @Test
    @DisplayName("getScheduledDeliveryTelemetry rejects anonymous callers")
    void getScheduledDeliveryTelemetryRejectsAnonymousCallers() {
        when(securityService.getCurrentUser()).thenReturn(null);

        RmReportPresetDeliveryService service = service();

        assertThrows(IllegalStateException.class, service::getScheduledDeliveryTelemetry);
        verifyNoInteractions(presetRepository, executionRepository);
    }

    private static RmReportPreset preset(RmReportPreset.Kind kind, Map<String, Object> params) {
        RmReportPreset preset = RmReportPreset.builder()
            .owner("admin")
            .name("Preset")
            .kind(kind)
            .params(params)
            .build();
        preset.setId(UUID.randomUUID());
        return preset;
    }

    private static RmReportPresetExecution execution(
        UUID presetId,
        String presetName,
        RmReportPreset.Kind presetKind,
        RmReportPresetExecution.TriggerType triggerType,
        RmReportPresetExecution.ExecutionStatus status
    ) {
        RmReportPreset preset = RmReportPreset.builder()
            .owner("admin")
            .name(presetName)
            .kind(presetKind)
            .params(Map.of())
            .build();
        preset.setId(presetId);

        RmReportPresetExecution execution = new RmReportPresetExecution();
        execution.setId(UUID.randomUUID());
        execution.setPreset(preset);
        execution.setOwner("admin");
        execution.setTriggerType(triggerType);
        execution.setStatus(status);
        execution.setFilename("weekly-family-report.csv");
        execution.setTargetFolderId(UUID.randomUUID());
        execution.setDocumentId(UUID.randomUUID());
        execution.setMessage(status == RmReportPresetExecution.ExecutionStatus.SUCCESS ? "Delivered successfully" : "Delivery failed");
        execution.setStartedAt(LocalDateTime.of(2026, 4, 21, 16, 0));
        execution.setFinishedAt(LocalDateTime.of(2026, 4, 21, 16, 1));
        execution.setDurationMs(1000L);
        return execution;
    }

    @Test
    @DisplayName("runScheduledDeliveriesNow propagates underlying failures unwrapped — controller catches them")
    void runScheduledDeliveriesNowPropagatesUnderlyingFailure() {
        RuntimeException underlying = new RuntimeException("scan failed: boom");
        when(presetRepository.findByScheduleEnabledTrueAndDeletedFalseAndNextRunAtLessThanEqualOrderByNextRunAtAsc(any(LocalDateTime.class)))
            .thenThrow(underlying);

        RmReportPresetDeliveryService service = service();

        // Service no longer wraps. The controller catches at its boundary so it
        // also catches transaction-commit exceptions thrown by the @Transactional
        // proxy (e.g. UnexpectedRollbackException), which a service-body try
        // could not intercept.
        RuntimeException ex = assertThrows(
            RuntimeException.class,
            service::runScheduledDeliveriesNow
        );
        assertEquals(underlying, ex);
    }

    @Test
    @DisplayName("runScheduledDeliveriesNow isolates per-preset failures — outer call still returns processedCount")
    void runScheduledDeliveriesNowIsolatesPerPresetFailures() throws Exception {
        // Two due presets. The first one fails when processOneScheduledDelivery
        // throws (simulating any RuntimeException — upload error, downstream
        // service hiccup, etc). The second succeeds. Outer call must still
        // return 1 instead of bubbling the per-preset exception.
        RmReportPreset failing = preset(RmReportPreset.Kind.ACTIVITY_FAMILY_REPORT,
            Map.of("from", "2026-04-01T00:00:00", "to", "2026-04-15T23:59:59"));
        failing.setScheduleEnabled(true);
        failing.setCronExpression("0 0 * * * *");
        failing.setScheduleTimezone("UTC");
        failing.setNextRunAt(LocalDateTime.now().minusMinutes(5));

        RmReportPreset succeeding = preset(RmReportPreset.Kind.ACTIVITY_FAMILY_REPORT,
            Map.of("from", "2026-04-01T00:00:00", "to", "2026-04-15T23:59:59"));
        succeeding.setScheduleEnabled(true);
        succeeding.setCronExpression("0 0 * * * *");
        succeeding.setScheduleTimezone("UTC");
        succeeding.setNextRunAt(LocalDateTime.now().minusMinutes(5));

        when(presetRepository.findByScheduleEnabledTrueAndDeletedFalseAndNextRunAtLessThanEqualOrderByNextRunAtAsc(any(LocalDateTime.class)))
            .thenReturn(List.of(failing, succeeding));
        // Claim succeeds for both
        when(presetRepository.claimScheduledRun(any(UUID.class), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(1);
        // First findByIdAndDeletedFalse returns failing's claimed state (will throw downstream),
        // second returns succeeding's claimed state (will succeed)
        when(presetRepository.findByIdAndDeletedFalse(failing.getId()))
            .thenThrow(new RuntimeException("simulated per-preset failure"));
        when(presetRepository.findByIdAndDeletedFalse(succeeding.getId()))
            .thenReturn(Optional.of(succeeding));
        // For the succeeding preset to deliver, set folder + stub minimal upload path
        UUID folderId = UUID.randomUUID();
        succeeding.setDeliveryFolderId(folderId);
        when(recordsManagementService.getActivityFamilyReport(any(), any(), any(), any()))
            .thenReturn(new RecordsManagementService.ActivityFamilyReportDto(
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-04-01T00:00", "2026-04-15T23:59:59"),
                new RecordsManagementService.ActivityDateTimeRangeDto("2026-03-17T00:00", "2026-03-31T23:59:59"),
                5,
                5,
                0,
                0,
                List.of()
            ));
        when(uploadService.uploadDocument(any(), any(UUID.class), any()))
            .thenReturn(PipelineResult.builder().success(true).documentId(UUID.randomUUID()).build());
        when(presetRepository.save(any(RmReportPreset.class))).thenAnswer(inv -> inv.getArgument(0));
        when(executionRepository.save(any(RmReportPresetExecution.class))).thenAnswer(inv -> {
            RmReportPresetExecution exec = inv.getArgument(0);
            exec.setId(UUID.randomUUID());
            return exec;
        });
        when(securityService.getCurrentUser()).thenReturn("admin");

        RmReportPresetDeliveryService service = service();

        RmReportPresetDeliveryService.ScheduledRunResultDto result =
            service.runScheduledDeliveriesNow();

        // Outer call returns successfully — the per-preset exception did not
        // propagate. processedCount reflects only the succeeded preset.
        assertEquals(1, result.processedCount());
        assertNotNull(result.generatedAt());
    }

    private RmReportPresetDeliveryService service() {
        RmReportPresetDeliveryService svc = new RmReportPresetDeliveryService(
            presetService,
            presetRepository,
            executionRepository,
            recordsManagementService,
            uploadService,
            auditService,
            securityService,
            activityService,
            preferenceService,
            userRepository,
            notificationDispatcher
        );
        // In production Spring injects a proxy here so per-preset
        // processOneScheduledDelivery calls go through @Transactional(REQUIRES_NEW).
        // Unit tests don't honour @Transactional anyway, so wiring self → self
        // exercises the same call shape without the propagation semantics.
        org.springframework.test.util.ReflectionTestUtils.setField(svc, "self", svc);
        return svc;
    }
}
