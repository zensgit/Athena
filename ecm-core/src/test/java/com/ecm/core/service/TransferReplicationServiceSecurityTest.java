package com.ecm.core.service;

import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.ReplicationDefinitionRepository;
import com.ecm.core.repository.ReplicationJobRepository;
import com.ecm.core.repository.TransferTargetRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferReplicationServiceSecurityTest {

    @Mock private TransferTargetRepository transferTargetRepository;
    @Mock private ReplicationDefinitionRepository replicationDefinitionRepository;
    @Mock private ReplicationJobRepository replicationJobRepository;
    @Mock private FolderService folderService;
    @Mock private NodeService nodeService;
    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;

    @Test
    @DisplayName("non-admin caller is rejected before transfer target repository access")
    void nonAdminListTargetsIsRejectedBeforeRepositoryAccess() {
        TransferReplicationService service = service();
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

        SecurityException ex = assertThrows(SecurityException.class, service::listTargets);

        assertEquals("Only administrators can manage transfer and replication", ex.getMessage());
        verify(transferTargetRepository, never()).findAll();
    }

    @Test
    @DisplayName("admin caller can list transfer targets through service guard")
    void adminListTargetsPassesServiceGuard() {
        TransferReplicationService service = service();
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(transferTargetRepository.findAll()).thenReturn(List.of());

        assertEquals(List.of(), service.listTargets());
        verify(transferTargetRepository).findAll();
    }

    private TransferReplicationService service() {
        return new TransferReplicationService(
            transferTargetRepository,
            replicationDefinitionRepository,
            replicationJobRepository,
            folderService,
            nodeService,
            nodeRepository,
            securityService,
            List.of(),
            Runnable::run
        );
    }
}
