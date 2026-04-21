package com.ecm.core.service;

import com.ecm.core.entity.RmReportPreset;
import com.ecm.core.entity.RmReportPresetExecution;
import com.ecm.core.pipeline.PipelineResult;
import com.ecm.core.repository.RmReportPresetExecutionRepository;
import com.ecm.core.repository.RmReportPresetRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
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

        RmReportPresetDeliveryService service = new RmReportPresetDeliveryService(
            presetService,
            presetRepository,
            executionRepository,
            recordsManagementService,
            uploadService,
            auditService,
            securityService
        );

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
    @DisplayName("updateSchedule rejects summary-only preset kinds")
    void updateScheduleRejectsSummaryOnlyPresetKinds() {
        RmReportPreset preset = preset(RmReportPreset.Kind.ACTIVITY_FAMILY_HIGHLIGHTS, Map.of("windowDays", 7));
        when(presetService.getOwned(preset.getId())).thenReturn(preset);

        RmReportPresetDeliveryService service = new RmReportPresetDeliveryService(
            presetService,
            presetRepository,
            executionRepository,
            recordsManagementService,
            uploadService,
            auditService,
            securityService
        );

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> service.updateSchedule(
                preset.getId(),
                new RmReportPresetDeliveryService.UpdateScheduleRequest(
                    true,
                    "0 */10 * * * *",
                    "UTC",
                    UUID.randomUUID()
                )
            )
        );

        assertTrue(ex.getMessage().contains("Scheduled delivery is not supported"));
        verifyNoInteractions(presetRepository, uploadService);
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

        RmReportPresetDeliveryService service = new RmReportPresetDeliveryService(
            presetService,
            presetRepository,
            executionRepository,
            recordsManagementService,
            uploadService,
            auditService,
            securityService
        );

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
    @DisplayName("runScheduledDeliveries executes due presets and advances nextRunAt")
    void runScheduledDeliveriesExecutesDuePresets() throws Exception {
        RmReportPreset preset = preset(
            RmReportPreset.Kind.ACTIVITY_CONTRIBUTOR_REPORT,
            Map.of(
                "from", "2026-04-01T00:00:00",
                "to", "2026-04-15T23:59:59",
                "limit", 5,
                "eventTypeLimit", 3
            )
        );
        preset.setScheduleEnabled(true);
        preset.setCronExpression("0 */10 * * * *");
        preset.setScheduleTimezone("UTC");
        preset.setDeliveryFolderId(UUID.randomUUID());
        preset.setNextRunAt(LocalDateTime.now().minusMinutes(1));

        when(presetRepository.findByScheduleEnabledTrueAndDeletedFalseAndNextRunAtLessThanEqualOrderByNextRunAtAsc(any(LocalDateTime.class)))
            .thenReturn(List.of(preset));
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
        when(uploadService.uploadDocument(any(MultipartFile.class), eq(preset.getDeliveryFolderId()), isNull()))
            .thenReturn(PipelineResult.builder().success(true).documentId(UUID.randomUUID()).build());
        when(presetRepository.save(any(RmReportPreset.class))).thenAnswer(inv -> inv.getArgument(0));
        when(executionRepository.save(any(RmReportPresetExecution.class))).thenAnswer(inv -> inv.getArgument(0));

        RmReportPresetDeliveryService service = new RmReportPresetDeliveryService(
            presetService,
            presetRepository,
            executionRepository,
            recordsManagementService,
            uploadService,
            auditService,
            securityService
        );

        service.runScheduledDeliveries();

        assertNotNull(preset.getLastRunAt());
        assertNotNull(preset.getNextRunAt());
        assertTrue(preset.getNextRunAt().isAfter(LocalDateTime.now().minusMinutes(1)));
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

        RmReportPresetDeliveryService service = new RmReportPresetDeliveryService(
            presetService,
            presetRepository,
            executionRepository,
            recordsManagementService,
            uploadService,
            auditService,
            securityService
        );

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

        RmReportPresetDeliveryService service = new RmReportPresetDeliveryService(
            presetService,
            presetRepository,
            executionRepository,
            recordsManagementService,
            uploadService,
            auditService,
            securityService
        );

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
}
