package com.ecm.core.controller;

import com.ecm.core.entity.ReplicationJob;
import com.ecm.core.entity.TransferTarget;
import com.ecm.core.service.TransferReplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @DisplayName("GET /transfer/targets lists expanded transfer targets")
    void listTargetsReturnsTargets() throws Exception {
        when(transferReplicationService.listTargets()).thenReturn(List.of(
            targetDto(
                UUID.randomUUID(),
                "loopback",
                "Local loopback target",
                TransferTarget.TransportType.LOOPBACK,
                UUID.randomUUID(),
                "Outbound",
                null,
                "/api/v1",
                TransferTarget.AuthType.NONE,
                null,
                false,
                true,
                TransferTarget.VerificationStatus.NEVER_VERIFIED,
                null,
                null,
                LocalDateTime.now(),
                LocalDateTime.now()
            )
        ));

        mockMvc.perform(get("/api/v1/transfer/targets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("loopback"))
            .andExpect(jsonPath("$[0].transportType").value("LOOPBACK"))
            .andExpect(jsonPath("$[0].verificationStatus").value("NEVER_VERIFIED"));
    }

    @Test
    @DisplayName("POST /transfer/targets creates transfer target with remote contract")
    void createTargetReturnsCreated() throws Exception {
        UUID targetFolderId = UUID.randomUUID();
        when(transferReplicationService.createTarget(any())).thenReturn(
            targetDto(
                UUID.randomUUID(),
                "loopback",
                "Local target",
                TransferTarget.TransportType.ATHENA_HTTP,
                targetFolderId,
                "Outbound",
                "https://replica.example.com",
                "/api/v1",
                TransferTarget.AuthType.BASIC,
                "replicator",
                true,
                true,
                TransferTarget.VerificationStatus.NEVER_VERIFIED,
                "Pending verification",
                null,
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
                      "transportType": "ATHENA_HTTP",
                      "targetFolderId": "%s",
                      "endpointUrl": "https://replica.example.com",
                      "endpointPath": "/api/v1",
                      "authType": "BASIC",
                      "authUsername": "replicator",
                      "authSecret": "top-secret",
                      "enabled": true
                    }
                    """.formatted(targetFolderId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("loopback"))
            .andExpect(jsonPath("$.transportType").value("ATHENA_HTTP"))
            .andExpect(jsonPath("$.targetFolderName").value("Outbound"))
            .andExpect(jsonPath("$.endpointUrl").value("https://replica.example.com"))
            .andExpect(jsonPath("$.endpointPath").value("/api/v1"))
            .andExpect(jsonPath("$.authType").value("BASIC"))
            .andExpect(jsonPath("$.verificationStatus").value("NEVER_VERIFIED"));
    }

    @Test
    @DisplayName("POST /transfer/targets/{id}/verify verifies transfer target")
    void verifyTargetReturnsVerifiedTarget() throws Exception {
        UUID targetId = UUID.randomUUID();
        when(transferReplicationService.verifyTarget(targetId)).thenReturn(targetDto(
            targetId,
            "remote",
            "Remote Athena target",
            TransferTarget.TransportType.ATHENA_HTTP,
            UUID.randomUUID(),
            "Remote Outbound",
            "https://replica.example.com",
            "/api/v1",
            TransferTarget.AuthType.BASIC,
            "replicator",
            true,
            true,
            TransferTarget.VerificationStatus.VERIFIED,
            "Verification succeeded",
            LocalDateTime.now(),
            LocalDateTime.now(),
            LocalDateTime.now()
        ));

        mockMvc.perform(post("/api/v1/transfer/targets/{targetId}/verify", targetId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(targetId.toString()))
            .andExpect(jsonPath("$.transportType").value("ATHENA_HTTP"))
            .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
            .andExpect(jsonPath("$.verificationMessage").value("Verification succeeded"));
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
    @DisplayName("POST /replication/definitions creates replication definition with schedule and failure policy fields")
    void createDefinitionReturnsCreatedWithScheduleAndFailurePolicy() throws Exception {
        UUID sourceNodeId = UUID.randomUUID();
        UUID transferTargetId = UUID.randomUUID();
        when(transferReplicationService.createDefinition(any())).thenReturn(definitionDto(
            UUID.randomUUID(),
            "nightly",
            "Nightly replication",
            sourceNodeId,
            "Contracts",
            transferTargetId,
            "Remote Outbound",
            true,
            true,
            "0 0 2 * * *",
            "UTC",
            true,
            3,
            15,
            14
        ));

        mockMvc.perform(post("/api/v1/replication/definitions")
                .contentType("application/json")
                .content("""
                    {
                      "name": "nightly",
                      "description": "Nightly replication",
                      "sourceNodeId": "%s",
                      "transferTargetId": "%s",
                      "includeChildren": true,
                      "enabled": true,
                      "cronExpression": "0 0 2 * * *",
                      "scheduleTimezone": "UTC",
                      "autoRetryEnabled": true,
                      "maxRetryAttempts": 3,
                      "retryBackoffMinutes": 15,
                      "jobRetentionDays": 14
                    }
                    """.formatted(sourceNodeId, transferTargetId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("nightly"))
            .andExpect(jsonPath("$.cronExpression").value("0 0 2 * * *"))
            .andExpect(jsonPath("$.scheduleTimezone").value("UTC"))
            .andExpect(jsonPath("$.autoRetryEnabled").value(true))
            .andExpect(jsonPath("$.maxRetryAttempts").value(3))
            .andExpect(jsonPath("$.retryBackoffMinutes").value(15))
            .andExpect(jsonPath("$.jobRetentionDays").value(14));

        ArgumentCaptor<TransferReplicationService.ReplicationDefinitionMutationRequest> requestCaptor =
            ArgumentCaptor.forClass(TransferReplicationService.ReplicationDefinitionMutationRequest.class);
        verify(transferReplicationService).createDefinition(requestCaptor.capture());
        TransferReplicationService.ReplicationDefinitionMutationRequest request = requestCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("nightly", request.name());
        org.junit.jupiter.api.Assertions.assertEquals("Nightly replication", request.description());
        org.junit.jupiter.api.Assertions.assertEquals(sourceNodeId, request.sourceNodeId());
        org.junit.jupiter.api.Assertions.assertEquals(transferTargetId, request.transferTargetId());
        org.junit.jupiter.api.Assertions.assertEquals(true, request.includeChildren());
        org.junit.jupiter.api.Assertions.assertEquals(true, request.enabled());
        org.junit.jupiter.api.Assertions.assertEquals("0 0 2 * * *", request.cronExpression());
        org.junit.jupiter.api.Assertions.assertEquals("UTC", request.scheduleTimezone());
        org.junit.jupiter.api.Assertions.assertEquals(true, request.autoRetryEnabled());
        org.junit.jupiter.api.Assertions.assertEquals(3, request.maxRetryAttempts());
        org.junit.jupiter.api.Assertions.assertEquals(15, request.retryBackoffMinutes());
        org.junit.jupiter.api.Assertions.assertEquals(14, request.jobRetentionDays());
    }

    @Test
    @DisplayName("PUT /replication/definitions/{id} updates replication definition with schedule and failure policy fields")
    void updateDefinitionReturnsOkWithScheduleAndFailurePolicy() throws Exception {
        UUID definitionId = UUID.randomUUID();
        UUID sourceNodeId = UUID.randomUUID();
        UUID transferTargetId = UUID.randomUUID();
        when(transferReplicationService.updateDefinition(any(), any())).thenReturn(definitionDto(
            definitionId,
            "nightly",
            "Nightly replication updated",
            sourceNodeId,
            "Contracts",
            transferTargetId,
            "Remote Outbound",
            false,
            true,
            "0 30 2 * * *",
            "Asia/Shanghai",
            true,
            5,
            20,
            30
        ));

        mockMvc.perform(put("/api/v1/replication/definitions/{definitionId}", definitionId)
                .contentType("application/json")
                .content("""
                    {
                      "name": "nightly",
                      "description": "Nightly replication updated",
                      "sourceNodeId": "%s",
                      "transferTargetId": "%s",
                      "includeChildren": false,
                      "enabled": true,
                      "cronExpression": "0 30 2 * * *",
                      "scheduleTimezone": "Asia/Shanghai",
                      "autoRetryEnabled": true,
                      "maxRetryAttempts": 5,
                      "retryBackoffMinutes": 20,
                      "jobRetentionDays": 30
                    }
                    """.formatted(sourceNodeId, transferTargetId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("Nightly replication updated"))
            .andExpect(jsonPath("$.includeChildren").value(false))
            .andExpect(jsonPath("$.cronExpression").value("0 30 2 * * *"))
            .andExpect(jsonPath("$.scheduleTimezone").value("Asia/Shanghai"))
            .andExpect(jsonPath("$.autoRetryEnabled").value(true))
            .andExpect(jsonPath("$.maxRetryAttempts").value(5))
            .andExpect(jsonPath("$.retryBackoffMinutes").value(20))
            .andExpect(jsonPath("$.jobRetentionDays").value(30));

        ArgumentCaptor<TransferReplicationService.ReplicationDefinitionMutationRequest> requestCaptor =
            ArgumentCaptor.forClass(TransferReplicationService.ReplicationDefinitionMutationRequest.class);
        verify(transferReplicationService).updateDefinition(org.mockito.ArgumentMatchers.eq(definitionId), requestCaptor.capture());
        TransferReplicationService.ReplicationDefinitionMutationRequest request = requestCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("nightly", request.name());
        org.junit.jupiter.api.Assertions.assertEquals("Nightly replication updated", request.description());
        org.junit.jupiter.api.Assertions.assertEquals(sourceNodeId, request.sourceNodeId());
        org.junit.jupiter.api.Assertions.assertEquals(transferTargetId, request.transferTargetId());
        org.junit.jupiter.api.Assertions.assertEquals(false, request.includeChildren());
        org.junit.jupiter.api.Assertions.assertEquals(true, request.enabled());
        org.junit.jupiter.api.Assertions.assertEquals("0 30 2 * * *", request.cronExpression());
        org.junit.jupiter.api.Assertions.assertEquals("Asia/Shanghai", request.scheduleTimezone());
        org.junit.jupiter.api.Assertions.assertEquals(true, request.autoRetryEnabled());
        org.junit.jupiter.api.Assertions.assertEquals(5, request.maxRetryAttempts());
        org.junit.jupiter.api.Assertions.assertEquals(20, request.retryBackoffMinutes());
        org.junit.jupiter.api.Assertions.assertEquals(30, request.jobRetentionDays());
    }

    @Test
    @DisplayName("POST /replication/jobs/{id}/retry queues retry job")
    void retryJobReturnsAccepted() throws Exception {
        UUID jobId = UUID.randomUUID();
        when(transferReplicationService.retryJob(jobId)).thenReturn(jobDto(jobId, ReplicationJob.ReplicationJobStatus.PENDING));

        mockMvc.perform(post("/api/v1/replication/jobs/{jobId}/retry", jobId))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.attemptNumber").value(1));
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
            .andExpect(jsonPath("$.content[0].transportStatus").value("SUCCESS"))
            .andExpect(jsonPath("$.content[0].attemptNumber").value(1))
            .andExpect(jsonPath("$.content[0].scheduledFor").exists())
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

    private TransferReplicationService.TransferTargetDto targetDto(
        UUID id,
        String name,
        String description,
        TransferTarget.TransportType transportType,
        UUID targetFolderId,
        String targetFolderName,
        String endpointUrl,
        String endpointPath,
        TransferTarget.AuthType authType,
        String authUsername,
        boolean authSecretConfigured,
        boolean enabled,
        TransferTarget.VerificationStatus verificationStatus,
        String verificationMessage,
        LocalDateTime lastVerifiedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        return new TransferReplicationService.TransferTargetDto(
            id,
            name,
            description,
            transportType,
            targetFolderId,
            targetFolderName,
            endpointUrl,
            endpointPath,
            authType,
            authUsername,
            authSecretConfigured,
            enabled,
            verificationStatus,
            verificationMessage,
            lastVerifiedAt,
            createdAt,
            updatedAt
        );
    }

    private TransferReplicationService.ReplicationJobDto jobDto(UUID id, ReplicationJob.ReplicationJobStatus status) {
        return new TransferReplicationService.ReplicationJobDto(
            id,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            1,
            LocalDateTime.now(),
            UUID.randomUUID(),
            "alice",
            status,
            "Replication job",
            status == ReplicationJob.ReplicationJobStatus.FAILED
                ? ReplicationJob.TransportStatus.FAILED
                : status == ReplicationJob.ReplicationJobStatus.COMPLETED
                    ? ReplicationJob.TransportStatus.SUCCESS
                    : ReplicationJob.TransportStatus.NEVER_RUN,
            "Transport diagnostics",
            null,
            LocalDateTime.now(),
            LocalDateTime.now(),
            null,
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }

    private TransferReplicationService.ReplicationDefinitionDto definitionDto(
        UUID id,
        String name,
        String description,
        UUID sourceNodeId,
        String sourceNodeName,
        UUID transferTargetId,
        String transferTargetName,
        boolean includeChildren,
        boolean enabled,
        String cronExpression,
        String scheduleTimezone,
        boolean autoRetryEnabled,
        int maxRetryAttempts,
        int retryBackoffMinutes,
        int jobRetentionDays
    ) {
        LocalDateTime now = LocalDateTime.now();
        return new TransferReplicationService.ReplicationDefinitionDto(
            id,
            name,
            description,
            sourceNodeId,
            sourceNodeName,
            transferTargetId,
            transferTargetName,
            includeChildren,
            enabled,
            cronExpression,
            scheduleTimezone,
            now,
            autoRetryEnabled,
            maxRetryAttempts,
            retryBackoffMinutes,
            jobRetentionDays,
            now,
            now,
            now
        );
    }
}
