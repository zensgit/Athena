package com.ecm.core.controller;

import com.ecm.core.service.RmReportPresetDeliveryService;
import com.ecm.core.service.RmReportPresetService;
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
            .andExpect(jsonPath("$[0].triggerType").value("SCHEDULED"))
            .andExpect(jsonPath("$[0].status").value("SUCCESS"));
    }
}
