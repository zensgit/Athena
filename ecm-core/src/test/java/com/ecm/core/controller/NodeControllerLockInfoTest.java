package com.ecm.core.controller;

import com.ecm.core.dto.LockInfoDto;
import com.ecm.core.entity.LockLifetime;
import com.ecm.core.entity.LockStatus;
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
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NodeControllerLockInfoTest {

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
    @DisplayName("Lock info endpoint returns caller-relative status")
    void getLockInfoReturnsPayload() throws Exception {
        UUID nodeId = UUID.randomUUID();
        Mockito.when(nodeService.getLockInfo(nodeId)).thenReturn(new LockInfoDto(
            LockStatus.LOCK_OWNER,
            "alice",
            LocalDateTime.of(2026, 3, 26, 18, 0),
            LockLifetime.EPHEMERAL,
            LocalDateTime.of(2026, 3, 26, 18, 30),
            null,
            null,
            false,
            900L,
            300L,
            true
        ));

        mockMvc.perform(get("/api/v1/nodes/{nodeId}/lock-info", nodeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("LOCK_OWNER"))
            .andExpect(jsonPath("$.lockedBy").value("alice"))
            .andExpect(jsonPath("$.lockLifetime").value("EPHEMERAL"))
            .andExpect(jsonPath("$.remainingSeconds").value(900))
            .andExpect(jsonPath("$.canUnlock").value(true));
    }
}
