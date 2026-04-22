package com.ecm.core.controller;

import com.ecm.core.entity.RmReportPreset;
import com.ecm.core.service.RmReportPresetDeliveryService;
import com.ecm.core.service.RmReportPresetService;
import org.springframework.data.domain.PageImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RmReportPresetControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RmReportPresetService presetService;

    @Mock
    private RmReportPresetDeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RmReportPresetController(presetService, deliveryService))
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("listMine returns additive schedule metadata")
    void listMineReturnsScheduleMetadata() throws Exception {
        UUID presetId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        RmReportPreset preset = RmReportPreset.builder()
            .owner("admin")
            .name("Weekly Family Report")
            .kind(RmReportPreset.Kind.ACTIVITY_FAMILY_REPORT)
            .params(java.util.Map.of("from", "2026-04-01T00:00:00", "to", "2026-04-21T23:59:59"))
            .scheduleEnabled(true)
            .deliveryFolderId(folderId)
            .nextRunAt(LocalDateTime.of(2026, 4, 22, 9, 0))
            .lastRunAt(LocalDateTime.of(2026, 4, 21, 9, 0))
            .build();
        preset.setId(presetId);
        Mockito.when(presetService.listForCurrentUser()).thenReturn(List.of(preset));

        mockMvc.perform(get("/api/v1/records/report-presets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(presetId.toString()))
            .andExpect(jsonPath("$[0].scheduleEnabled").value(true))
            .andExpect(jsonPath("$[0].deliveryFolderId").value(folderId.toString()))
            .andExpect(jsonPath("$[0].nextRunAt").exists())
            .andExpect(jsonPath("$[0].lastRunAt").exists());
    }

    @Test
    @DisplayName("updateSchedule returns schedule payload")
    void updateScheduleReturnsPayload() throws Exception {
        UUID presetId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        Mockito.when(deliveryService.updateSchedule(Mockito.eq(presetId), Mockito.any()))
            .thenReturn(new RmReportPresetDeliveryService.ScheduleStatusDto(
                presetId,
                true,
                "0 */10 * * * *",
                "UTC",
                folderId,
                LocalDateTime.of(2026, 4, 21, 16, 0),
                null,
                null
            ));

        mockMvc.perform(put("/api/v1/records/report-presets/{id}/schedule", presetId)
                .contentType("application/json")
                .content("""
                    {
                      "enabled": true,
                      "cronExpression": "0 */10 * * * *",
                      "timezone": "UTC",
                      "deliveryFolderId": "%s"
                    }
                    """.formatted(folderId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.cronExpression").value("0 */10 * * * *"))
            .andExpect(jsonPath("$.deliveryFolderId").value(folderId.toString()));
    }

    @Test
    @DisplayName("deliverNow returns delivery execution payload")
    void deliverNowReturnsExecutionPayload() throws Exception {
        UUID presetId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Mockito.when(deliveryService.deliverNow(presetId))
            .thenReturn(new RmReportPresetDeliveryService.PresetExecutionDto(
                executionId,
                presetId,
                "Preset",
                com.ecm.core.entity.RmReportPreset.Kind.ACTIVITY_FAMILY_REPORT,
                com.ecm.core.entity.RmReportPresetExecution.TriggerType.MANUAL,
                com.ecm.core.entity.RmReportPresetExecution.ExecutionStatus.SUCCESS,
                "preset-20260421.csv",
                folderId,
                documentId,
                "Delivered successfully",
                LocalDateTime.of(2026, 4, 21, 16, 0),
                LocalDateTime.of(2026, 4, 21, 16, 1),
                1000L
            ));

        mockMvc.perform(post("/api/v1/records/report-presets/{id}/deliver", presetId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(executionId.toString()))
            .andExpect(jsonPath("$.presetName").value("Preset"))
            .andExpect(jsonPath("$.presetKind").value("ACTIVITY_FAMILY_REPORT"))
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.documentId").value(documentId.toString()));
    }

    @Test
    @DisplayName("listExecutions returns recent execution payload")
    void listExecutionsReturnsPayload() throws Exception {
        UUID presetId = UUID.randomUUID();
        Mockito.when(deliveryService.listExecutions(presetId, 5))
            .thenReturn(List.of(
                new RmReportPresetDeliveryService.PresetExecutionDto(
                    UUID.randomUUID(),
                    presetId,
                    "Preset",
                    com.ecm.core.entity.RmReportPreset.Kind.ACTIVITY_FAMILY_REPORT,
                    com.ecm.core.entity.RmReportPresetExecution.TriggerType.SCHEDULED,
                    com.ecm.core.entity.RmReportPresetExecution.ExecutionStatus.SUCCESS,
                    "preset-20260421.csv",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Delivered successfully",
                    LocalDateTime.of(2026, 4, 21, 16, 0),
                    LocalDateTime.of(2026, 4, 21, 16, 1),
                    1000L
                )
            ));

        mockMvc.perform(get("/api/v1/records/report-presets/{id}/executions", presetId)
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].presetId").value(presetId.toString()))
            .andExpect(jsonPath("$[0].presetName").value("Preset"))
            .andExpect(jsonPath("$[0].triggerType").value("SCHEDULED"))
            .andExpect(jsonPath("$[0].status").value("SUCCESS"));
    }

    @Test
    @DisplayName("listExecutionLedger returns filtered page payload")
    void listExecutionLedgerReturnsPagePayload() throws Exception {
        UUID presetId = UUID.randomUUID();
        Mockito.when(deliveryService.listExecutionLedger(
                Mockito.eq(presetId),
                Mockito.eq(com.ecm.core.entity.RmReportPresetExecution.ExecutionStatus.SUCCESS),
                Mockito.eq(com.ecm.core.entity.RmReportPresetExecution.TriggerType.MANUAL),
                Mockito.any(),
                Mockito.any(),
                Mockito.eq(0),
                Mockito.eq(10)
            ))
            .thenReturn(new PageImpl<>(List.of(
                new RmReportPresetDeliveryService.PresetExecutionDto(
                    UUID.randomUUID(),
                    presetId,
                    "Weekly Family Report",
                    com.ecm.core.entity.RmReportPreset.Kind.ACTIVITY_FAMILY_REPORT,
                    com.ecm.core.entity.RmReportPresetExecution.TriggerType.MANUAL,
                    com.ecm.core.entity.RmReportPresetExecution.ExecutionStatus.SUCCESS,
                    "weekly-family-report.csv",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Delivered successfully",
                    LocalDateTime.of(2026, 4, 21, 16, 0),
                    LocalDateTime.of(2026, 4, 21, 16, 1),
                    1000L
                )
            )));

        mockMvc.perform(get("/api/v1/records/report-presets/executions")
                .param("presetId", presetId.toString())
                .param("status", "SUCCESS")
                .param("triggerType", "MANUAL")
                .param("from", "2026-04-21T00:00:00")
                .param("to", "2026-04-21T23:59:59")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].presetId").value(presetId.toString()))
            .andExpect(jsonPath("$.content[0].presetName").value("Weekly Family Report"))
            .andExpect(jsonPath("$.content[0].presetKind").value("ACTIVITY_FAMILY_REPORT"));
    }

    @Test
    @DisplayName("exportExecutionLedger returns csv attachment")
    void exportExecutionLedgerReturnsCsvAttachment() throws Exception {
        UUID presetId = UUID.randomUUID();
        Mockito.when(deliveryService.exportExecutionLedgerCsv(
                Mockito.eq(presetId),
                Mockito.eq(com.ecm.core.entity.RmReportPresetExecution.ExecutionStatus.FAILED),
                Mockito.eq(com.ecm.core.entity.RmReportPresetExecution.TriggerType.SCHEDULED),
                Mockito.any(),
                Mockito.any(),
                Mockito.eq(50)
            ))
            .thenReturn("""
                executionId,presetId,presetName,presetKind,triggerType,status,filename,targetFolderId,documentId,message,startedAt,finishedAt,durationMs
                exec-1,%s,Weekly Family Report,ACTIVITY_FAMILY_REPORT,SCHEDULED,FAILED,weekly-family-report.csv,,,Delivery failed,2026-04-21T16:00,2026-04-21T16:01,1000
                """.formatted(presetId));

        mockMvc.perform(get("/api/v1/records/report-presets/executions/export")
                .param("presetId", presetId.toString())
                .param("status", "FAILED")
                .param("triggerType", "SCHEDULED")
                .param("from", "2026-04-21T00:00:00")
                .param("to", "2026-04-21T23:59:59")
                .param("limit", "50"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment;")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("executionId,presetId,presetName,presetKind")));
    }

    @Test
    @DisplayName("getTelemetry returns scheduled delivery health summary")
    void getTelemetryReturnsScheduledDeliveryHealthSummary() throws Exception {
        Mockito.when(deliveryService.getScheduledDeliveryTelemetry())
            .thenReturn(new RmReportPresetDeliveryService.ScheduledDeliveryTelemetryDto(
                5L,
                2L,
                7L,
                1L,
                LocalDateTime.of(2026, 4, 21, 9, 0),
                LocalDateTime.of(2026, 4, 21, 16, 0)
            ));

        mockMvc.perform(get("/api/v1/records/report-presets/telemetry"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scheduleEnabledCount").value(5))
            .andExpect(jsonPath("$.duePresetCount").value(2))
            .andExpect(jsonPath("$.last24hSuccessCount").value(7))
            .andExpect(jsonPath("$.last24hFailedCount").value(1))
            .andExpect(jsonPath("$.lastExecutionAt").exists())
            .andExpect(jsonPath("$.generatedAt").exists());
    }
}
