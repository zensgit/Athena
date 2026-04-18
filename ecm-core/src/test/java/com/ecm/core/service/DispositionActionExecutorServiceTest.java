package com.ecm.core.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispositionActionExecutorServiceTest {

    @Mock
    private NodeService nodeService;

    @Test
    @DisplayName("destroyNodeByDisposition delegates to governance delete path")
    void destroyNodeByDispositionDelegatesToGovernanceDelete() {
        UUID nodeId = UUID.randomUUID();
        when(nodeService.deleteNodeByGovernance(nodeId, "system:disposition"))
            .thenReturn(new NodeService.GovernanceDeleteResult(nodeId, "legacy.pdf", 3));

        DispositionActionExecutorService service = new DispositionActionExecutorService(nodeService);
        DispositionActionExecutorService.DestroyMutationDto result = service.destroyNodeByDisposition(nodeId, "system:disposition");

        assertEquals(nodeId, result.nodeId());
        assertEquals("legacy.pdf", result.name());
        assertEquals(3, result.affectedNodeCount());
    }
}
