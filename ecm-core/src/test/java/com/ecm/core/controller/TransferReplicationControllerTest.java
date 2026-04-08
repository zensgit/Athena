package com.ecm.core.controller;

import com.ecm.core.entity.ReplicationJob;
import com.ecm.core.service.TransferReplicationService;
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
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TransferReplicationControllerTest {

    @Mock
    private TransferReplicationService transferReplicationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TransferReplicationController controller = new TransferReplicationController(transferReplicationService);
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
    @DisplayName("GET /transfer/targets lists transfer targets")
    void listTargetsReturnsTargets() throws Exception {
        when(transferReplicationService.listTargets()).thenReturn(List.of(targetDto(UUID.randomUUID(), "loopback")));

        mockMvc.perform(get("/api/v1/transfer/targets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("loopback"));
    }

    @Test
    @DisplayName("POST /transfer/targets creates transfer target")
    void createTargetReturnsCreated() throws Exception {
        UUID targetFolderId = UUID.randomUUID();
        when(transferReplicationService.createTarget(any())).thenReturn(
            new TransferReplicationService.TransferTargetDto(
                UUID.randomUUID(),
                "loopback",
                "Local target",
                targetFolderId,
                "Outbound",
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
            )
        );

        mockMvc.perform(post("/api/v1/transfer/targets")
                .contentType("application/json")
                .content("""
                    {
                      "name": "loopback",
                      "description": "Local target",
                      "targetFolderId": "%s",
                      "enabled": true
                    }
                    """.formatted(targetFolderId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("loopback"))
            .andExpect(jsonPath("$.targetFolderName").value("Outbound"));
    }

    @Test
    @DisplayName("POST /replication/definitions/{id}/run queues replication job")
    void runDefinitionReturnsAccepted() throws Exception {
        UUID definitionId = UUID.randomUUID();
        when(transferReplicationService.runDefinition(definitionId)).thenReturn(jobDto(UUID.randomUUID(), ReplicationJob.ReplicationJobStatus.PENDING));

        mockMvc.perform(post("/api/v1/replication/definitions/{definitionId}/run", definitionId))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /replication/jobs lists jobs")
    void listJobsReturnsPage() throws Exception {
        when(transferReplicationService.listJobs(any())).thenReturn(new PageImpl<>(
            List.of(jobDto(UUID.randomUUID(), ReplicationJob.ReplicationJobStatus.COMPLETED)),
            PageRequest.of(0, 20),
            1
        ));

        mockMvc.perform(get("/api/v1/replication/jobs").param("page", "0").param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /replication/jobs/{id} returns job details")
    void getJobReturnsJob() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(transferReplicationService.getJob(jobId)).thenReturn(jobDto(jobId, ReplicationJob.ReplicationJobStatus.RUNNING));

        mockMvc.perform(get("/api/v1/replication/jobs/{jobId}", jobId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(jobId.toString()))
            .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    private TransferReplicationService.TransferTargetDto targetDto(UUID id, String name) {
        return new TransferReplicationService.TransferTargetDto(
            id,
            name,
            "Local loopback target",
            UUID.randomUUID(),
            "Outbound",
            true,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }

    private TransferReplicationService.ReplicationJobDto jobDto(UUID id, ReplicationJob.ReplicationJobStatus status) {
        return new TransferReplicationService.ReplicationJobDto(
            id,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "alice",
            status,
            "Replication job",
            null,
            LocalDateTime.now(),
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }
}
