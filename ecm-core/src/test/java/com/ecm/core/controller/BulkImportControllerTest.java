package com.ecm.core.controller;

import com.ecm.core.entity.ImportJob.ConflictPolicy;
import com.ecm.core.entity.ImportJob.ImportJobStatus;
import com.ecm.core.service.BulkImportService;
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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BulkImportControllerTest {

    @Mock private BulkImportService bulkImportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        BulkImportController controller = new BulkImportController(bulkImportService);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(converter)
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("POST /bulk-import starts import job")
    void startImportStartsJob() throws Exception {
        UUID targetFolderId = UUID.randomUUID();
        when(bulkImportService.startImport(any(), anyList(), eq(targetFolderId), eq(ConflictPolicy.RENAME)))
            .thenReturn(dto(UUID.randomUUID(), ImportJobStatus.PENDING, targetFolderId, ConflictPolicy.RENAME));

        mockMvc.perform(multipart("/api/v1/bulk-import")
                .file(new MockMultipartFile("files", "budget.xlsx", "application/vnd.ms-excel", "sheet".getBytes()))
                .param("relativePaths", "finance/q1/budget.xlsx")
                .param("targetFolderId", targetFolderId.toString())
                .param("conflictPolicy", "RENAME"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.conflictPolicy").value("RENAME"))
            .andExpect(jsonPath("$.targetFolderId").value(targetFolderId.toString()));
    }

    @Test
    @DisplayName("GET /bulk-import/{jobId} returns job details")
    void getJobReturnsJobDetails() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(bulkImportService.getJob(jobId)).thenReturn(dto(jobId, ImportJobStatus.RUNNING, UUID.randomUUID(), ConflictPolicy.SKIP));

        mockMvc.perform(get("/api/v1/bulk-import/{jobId}", jobId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(jobId.toString()))
            .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    @DisplayName("GET /bulk-import lists jobs")
    void listJobsReturnsPage() throws Exception {
        when(bulkImportService.listJobs(any())).thenReturn(new PageImpl<>(
            List.of(dto(UUID.randomUUID(), ImportJobStatus.COMPLETED, UUID.randomUUID(), ConflictPolicy.SKIP)),
            PageRequest.of(0, 20),
            1
        ));

        mockMvc.perform(get("/api/v1/bulk-import").param("page", "0").param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("DELETE /bulk-import/{jobId} cancels job")
    void cancelJobCancelsImport() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(bulkImportService.cancelImport(jobId)).thenReturn(dto(jobId, ImportJobStatus.CANCELED, UUID.randomUUID(), ConflictPolicy.SKIP));

        mockMvc.perform(delete("/api/v1/bulk-import/{jobId}", jobId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    private BulkImportService.ImportJobDto dto(
        UUID id,
        ImportJobStatus status,
        UUID targetFolderId,
        ConflictPolicy conflictPolicy
    ) {
        return new BulkImportService.ImportJobDto(
            id,
            "alice",
            status,
            targetFolderId,
            conflictPolicy,
            3,
            1,
            1,
            0,
            0,
            "finance/q1/budget.xlsx",
            "Importing finance/q1/budget.xlsx",
            null,
            LocalDateTime.now(),
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }
}
