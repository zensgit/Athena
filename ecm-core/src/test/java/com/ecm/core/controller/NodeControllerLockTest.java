package com.ecm.core.controller;

import com.ecm.core.dto.LockInfoDto;
import com.ecm.core.entity.LockLifetime;
import com.ecm.core.entity.LockStatus;
import com.ecm.core.entity.LockType;
import com.ecm.core.service.DocumentRelationService;
import com.ecm.core.service.LockService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.RenditionResourceService;
import com.ecm.core.service.VersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NodeControllerLockTest {

    private MockMvc mockMvc;

    @Mock private NodeService nodeService;
    @Mock private DocumentRelationService relationService;
    @Mock private VersionService versionService;
    @Mock private RenditionResourceService renditionResourceService;
    @Mock private LockService lockService;

    @BeforeEach
    void setUp() {
        NodeController nodeController = new NodeController(nodeService, relationService, versionService, renditionResourceService, lockService);
        mockMvc = MockMvcBuilders.standaloneSetup(nodeController)
            .setControllerAdvice(new RestExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("Lock endpoint accepts lifetime parameters")
    void lockNodeAcceptsLifetimeParameters() throws Exception {
        UUID nodeId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/lock", nodeId)
                .param("lifetime", "EPHEMERAL")
                .param("durationMinutes", "15"))
            .andExpect(status().isOk());

        verify(nodeService).lockNode(nodeId, LockLifetime.EPHEMERAL, 15);
    }

    @Test
    @DisplayName("Typed lock endpoint accepts type lifetime deep and info")
    void typedLockAcceptsExtendedParameters() throws Exception {
        UUID nodeId = UUID.randomUUID();
        when(lockService.getLockInfo(nodeId)).thenReturn(new LockInfoDto(
            LockStatus.LOCK_OWNER,
            "alice",
            LocalDateTime.of(2026, 3, 29, 10, 0),
            LockLifetime.EPHEMERAL,
            LocalDateTime.of(2026, 3, 29, 10, 30),
            LockType.NODE_LOCK,
            "Bulk governance hold",
            true,
            1800L,
            60L,
            true
        ));

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/lock-typed", nodeId)
                .param("lockType", "NODE_LOCK")
                .param("lifetime", "EPHEMERAL")
                .param("durationSeconds", "1800")
                .param("deep", "true")
                .param("additionalInfo", "Bulk governance hold"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lockType").value("NODE_LOCK"))
            .andExpect(jsonPath("$.additionalInfo").value("Bulk governance hold"))
            .andExpect(jsonPath("$.lockDeep").value(true));

        verify(lockService).lock(eq(nodeId), eq(LockType.NODE_LOCK), eq(LockLifetime.EPHEMERAL), eq(1800), eq(true), eq("Bulk governance hold"));
        verify(lockService).getLockInfo(nodeId);
    }

    @Test
    @DisplayName("Deep unlock endpoint accepts recursive flag")
    void unlockDeepAcceptsRecursiveFlag() throws Exception {
        UUID nodeId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/nodes/{nodeId}/unlock-deep", nodeId)
                .param("unlockChildren", "true"))
            .andExpect(status().isOk());

        verify(lockService).unlock(nodeId, true);
    }

    @Test
    @DisplayName("Batch lock endpoint accepts list body and type")
    void batchLockAcceptsIdsAndType() throws Exception {
        UUID nodeId1 = UUID.randomUUID();
        UUID nodeId2 = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/nodes/batch-lock")
                .param("lockType", "READ_ONLY_LOCK")
                .param("durationSeconds", "900")
                .contentType("application/json")
                .content("[\"" + nodeId1 + "\",\"" + nodeId2 + "\"]"))
            .andExpect(status().isOk());

        verify(lockService).batchLock(List.of(nodeId1, nodeId2), LockType.READ_ONLY_LOCK, 900);
    }

    @Test
    @DisplayName("Batch unlock endpoint accepts list body")
    void batchUnlockAcceptsIds() throws Exception {
        UUID nodeId1 = UUID.randomUUID();
        UUID nodeId2 = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/nodes/batch-unlock")
                .contentType("application/json")
                .content("[\"" + nodeId1 + "\",\"" + nodeId2 + "\"]"))
            .andExpect(status().isOk());

        verify(lockService).batchUnlock(List.of(nodeId1, nodeId2));
    }
}
