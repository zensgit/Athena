package com.ecm.core.service;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.LockLifetime;
import com.ecm.core.repository.CorrespondentRepository;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.PermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeServiceLockTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private CorrespondentRepository correspondentRepository;
    @Mock private SecurityService securityService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private NodeService nodeService;

    @BeforeEach
    void setUp() {
        nodeService = new NodeService(
            nodeRepository,
            folderRepository,
            documentRepository,
            permissionRepository,
            correspondentRepository,
            securityService,
            eventPublisher
        );
    }

    @Test
    @DisplayName("Ephemeral lock persists lifetime and expiry")
    void lockNodePersistsEphemeralLifetime() {
        UUID nodeId = UUID.randomUUID();
        Folder folder = folder(nodeId, "workspace");

        when(nodeRepository.findByIdAndDeletedFalse(nodeId)).thenReturn(Optional.of(folder));
        when(nodeRepository.save(any(Folder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(securityService.hasPermission(folder, com.ecm.core.entity.Permission.PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(folder, com.ecm.core.entity.Permission.PermissionType.WRITE)).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("alice");

        nodeService.lockNode(nodeId, LockLifetime.EPHEMERAL, 15);

        assertEquals(LockLifetime.EPHEMERAL, folder.getLockLifetime());
        assertNotNull(folder.getLockExpiresAt());
        verify(nodeRepository).save(folder);
    }

    @Test
    @DisplayName("Expired lock is cleared when node is accessed")
    void getNodeClearsExpiredLock() {
        UUID nodeId = UUID.randomUUID();
        Folder folder = folder(nodeId, "workspace");
        folder.applyLock("alice", LocalDateTime.now().minusHours(2), LockLifetime.EPHEMERAL, LocalDateTime.now().minusMinutes(5));

        when(nodeRepository.findByIdAndDeletedFalse(nodeId)).thenReturn(Optional.of(folder));
        when(nodeRepository.save(any(Folder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(securityService.hasPermission(folder, com.ecm.core.entity.Permission.PermissionType.READ)).thenReturn(true);

        Folder resolved = (Folder) nodeService.getNode(nodeId);

        assertFalse(resolved.isLocked());
        assertNull(resolved.getLockedBy());
        assertNull(resolved.getLockLifetime());
        assertNull(resolved.getLockExpiresAt());
        verify(nodeRepository).save(folder);
    }

    private Folder folder(UUID id, String name) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setPath("/" + name);
        return folder;
    }
}
