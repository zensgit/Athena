package com.ecm.core.controller;

import com.ecm.core.entity.DispositionActionExecution;
import com.ecm.core.entity.Node;
import com.ecm.core.service.DispositionScheduleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DispositionScheduleControllerTest {

    @Mock
    private DispositionScheduleService dispositionScheduleService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        mockMvc = MockMvcBuilders.standaloneSetup(new DispositionScheduleController(dispositionScheduleService))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("PUT /folders/{folderId}/disposition-schedule returns schedule payload")
    void upsertScheduleReturnsPayload() throws Exception {
        UUID folderId = UUID.randomUUID();
        when(dispositionScheduleService.upsertSchedule(eq(folderId), any()))
            .thenReturn(new DispositionScheduleService.DispositionScheduleDto(
                UUID.randomUUID(),
                folderId,
                "Finance",
                "/Finance",
                true,
                true,
                90,
                30,
                30,
                Node.ArchiveStoreTier.COLD,
                100,
                null,
                null,
                null
            ));

        mockMvc.perform(put("/api/v1/folders/{folderId}/disposition-schedule", folderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "enabled": true,
                      "includeSubfolders": true,
                      "cutoffAfterDays": 90,
                      "archiveAfterCutoffDays": 30,
                      "destroyAfterArchiveDays": 30,
                      "archiveStorageTier": "COLD",
                      "maxCandidatesPerAction": 100
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.folderId").value(folderId.toString()))
            .andExpect(jsonPath("$.destroyAfterArchiveDays").value(30));
    }

    @Test
    @DisplayName("POST /folders/{folderId}/disposition-schedule/dry-run returns grouped candidates")
    void dryRunReturnsCandidates() throws Exception {
        UUID folderId = UUID.randomUUID();
        when(dispositionScheduleService.dryRunSchedule(eq(folderId), any()))
            .thenReturn(new DispositionScheduleService.DispositionDryRunDto(
                folderId,
                "Finance",
                true,
                Node.ArchiveStoreTier.COLD,
                100,
                1,
                1,
                1,
                List.of(new DispositionScheduleService.DispositionCandidateDto(
                    UUID.randomUUID(),
                    "legacy.pdf",
                    "DOCUMENT",
                    "/Finance/legacy.pdf",
                    "DESTROY",
                    LocalDateTime.of(2026, 4, 14, 12, 0),
                    "Matter A"
                ))
            ));

        mockMvc.perform(post("/api/v1/folders/{folderId}/disposition-schedule/dry-run", folderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "enabled": true,
                      "includeSubfolders": true,
                      "cutoffAfterDays": 90,
                      "archiveAfterCutoffDays": 30,
                      "destroyAfterArchiveDays": 30,
                      "archiveStorageTier": "COLD",
                      "maxCandidatesPerAction": 100
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.destroyCount").value(1))
            .andExpect(jsonPath("$.candidates[0].actionType").value("DESTROY"))
            .andExpect(jsonPath("$.candidates[0].blockedByHoldNames").value("Matter A"));
    }

    @Test
    @DisplayName("GET /folders/{folderId}/disposition-schedule/executions returns execution history page")
    void listExecutionsReturnsPage() throws Exception {
        UUID folderId = UUID.randomUUID();
        when(dispositionScheduleService.listExecutions(eq(folderId), eq(PageRequest.of(0, 20))))
            .thenReturn(new PageImpl<>(
                List.of(new DispositionScheduleService.DispositionActionExecutionDto(
                    UUID.randomUUID(),
                    DispositionActionExecution.ActionType.DESTROY,
                    DispositionActionExecution.ExecutionStatus.BLOCKED,
                    UUID.randomUUID(),
                    "legacy.pdf",
                    "DOCUMENT",
                    "/Finance/legacy.pdf",
                    0,
                    "held",
                    "system:disposition-schedule",
                    LocalDateTime.of(2026, 4, 14, 12, 0)
                )),
                PageRequest.of(0, 20),
                1
            ));

        mockMvc.perform(get("/api/v1/folders/{folderId}/disposition-schedule/executions", folderId)
                .param("page", "0")
                .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].actionType").value("DESTROY"))
            .andExpect(jsonPath("$.content[0].status").value("BLOCKED"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }
}
