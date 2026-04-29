package com.ecm.core.controller;

import com.ecm.core.entity.ReplicationJob;
import com.ecm.core.entity.TransferTarget;
import com.ecm.core.service.TransferReplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security test for {@link TransferReplicationController}.
 *
 * Sender-side transfer replication is admin-facing and must NOT inherit the
 * receiver-side {@code /api/v1/transfer/receiver/** permitAll()} exception.
 */
@WebMvcTest(controllers = TransferReplicationController.class)
@ContextConfiguration(classes = {
    TransferReplicationController.class,
    RestExceptionHandler.class,
    TransferReplicationControllerSecurityTest.TestSecurityConfig.class
})
class TransferReplicationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransferReplicationService transferReplicationService;

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll()
                )
                .httpBasic(basic -> {});
            return http.build();
        }
    }

    @Test
    @DisplayName("unauthenticated GET /transfer/targets returns 401")
    void unauthenticatedListTargetsReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/transfer/targets"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER GET /transfer/targets returns 403 because controller requires ADMIN")
    void userListTargetsReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/transfer/targets"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ROLE_ADMIN GET /transfer/targets reaches service and returns 200")
    void adminListTargetsReturnsOk() throws Exception {
        when(transferReplicationService.listTargets()).thenReturn(List.of(targetDto()));

        mockMvc.perform(get("/api/v1/transfer/targets"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER POST /transfer/targets/{id}/verify returns 403")
    void userVerifyTargetReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/transfer/targets/{targetId}/verify", UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ROLE_ADMIN POST /transfer/targets/{id}/verify reaches service and returns 200")
    void adminVerifyTargetReturnsOk() throws Exception {
        UUID targetId = UUID.randomUUID();
        when(transferReplicationService.verifyTarget(targetId)).thenReturn(targetDto());

        mockMvc.perform(post("/api/v1/transfer/targets/{targetId}/verify", targetId))
            .andExpect(status().isOk());

        verify(transferReplicationService).verifyTarget(targetId);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER POST /replication/definitions/{id}/run returns 403")
    void userRunDefinitionReturns403() throws Exception {
        mockMvc.perform(post("/api/v1/replication/definitions/{definitionId}/run", UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ROLE_ADMIN POST /replication/definitions/{id}/run reaches service and returns 202")
    void adminRunDefinitionReturnsAccepted() throws Exception {
        UUID definitionId = UUID.randomUUID();
        when(transferReplicationService.runDefinition(definitionId)).thenReturn(jobDto());

        mockMvc.perform(post("/api/v1/replication/definitions/{definitionId}/run", definitionId))
            .andExpect(status().isAccepted());

        verify(transferReplicationService).runDefinition(definitionId);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ROLE_USER GET /replication/jobs returns 403")
    void userListJobsReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/replication/jobs"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ROLE_ADMIN GET /replication/jobs reaches service and returns 200")
    void adminListJobsReturnsOk() throws Exception {
        when(transferReplicationService.listJobs(any())).thenReturn(new PageImpl<>(
            List.of(jobDto()),
            PageRequest.of(0, 20),
            1
        ));

        mockMvc.perform(get("/api/v1/replication/jobs").param("page", "0").param("size", "20"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("service SecurityException maps to 403 for admin caller")
    void serviceSecurityExceptionMapsToForbidden() throws Exception {
        UUID targetId = UUID.randomUUID();
        when(transferReplicationService.getTarget(eq(targetId)))
            .thenThrow(new SecurityException("Transfer target denied"));

        mockMvc.perform(get("/api/v1/transfer/targets/{targetId}", targetId))
            .andExpect(status().isForbidden());
    }

    private TransferReplicationService.TransferTargetDto targetDto() {
        LocalDateTime now = LocalDateTime.now();
        return new TransferReplicationService.TransferTargetDto(
            UUID.randomUUID(),
            "remote",
            "Remote target",
            TransferTarget.TransportType.ATHENA_HTTP,
            UUID.randomUUID(),
            "Outbound",
            "https://replica.example.com",
            "/api/v1",
            TransferTarget.AuthType.BASIC,
            "replicator",
            true,
            true,
            TransferTarget.VerificationStatus.VERIFIED,
            "ok",
            null,
            now,
            now,
            now
        );
    }

    private TransferReplicationService.ReplicationJobDto jobDto() {
        LocalDateTime now = LocalDateTime.now();
        return new TransferReplicationService.ReplicationJobDto(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            1,
            now,
            UUID.randomUUID(),
            "alice",
            ReplicationJob.ReplicationJobStatus.PENDING,
            "Queued replication",
            ReplicationJob.TransportStatus.NEVER_RUN,
            null,
            null,
            null,
            false,
            now,
            now,
            null,
            now,
            now
        );
    }

}
