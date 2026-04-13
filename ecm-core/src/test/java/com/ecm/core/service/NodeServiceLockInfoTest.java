package com.ecm.core.service;

import com.ecm.core.dto.LockInfoDto;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.LockLifetime;
import com.ecm.core.entity.LockStatus;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeServiceLockInfoTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private CorrespondentRepository correspondentRepository;
    @Mock private SecurityService securityService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ContentReferenceService contentReferenceService;

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
            eventPublisher,
            contentReferenceService
        );
    }

    @Test
    @DisplayName("Lock info returns no lock for unlocked node")
    void getLockInfoReturnsNoLock() {
        UUID nodeId = UUID.randomUUID();
        Folder folder = folder(nodeId, "workspace");

        when(nodeRepository.findByIdAndDeletedFalse(nodeId)).thenReturn(Optional.of(folder));
        when(securityService.hasPermission(folder, com.ecm.core.entity.Permission.PermissionType.READ)).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

        LockInfoDto info = nodeService.getLockInfo(nodeId);

        assertEquals(LockStatus.NO_LOCK, info.status());
        assertNull(info.lockedBy());
        assertFalse(info.canUnlock());
    }

    @Test
    @DisplayName("Lock info returns owner status and remaining time")
    void getLockInfoReturnsOwnerStatus() {
        UUID nodeId = UUID.randomUUID();
        Folder folder = folder(nodeId, "workspace");
        folder.applyLock("alice", LocalDateTime.now().minusMinutes(5), LockLifetime.EPHEMERAL, LocalDateTime.now().plusMinutes(10));

        when(nodeRepository.findByIdAndDeletedFalse(nodeId)).thenReturn(Optional.of(folder));
        when(securityService.hasPermission(folder, com.ecm.core.entity.Permission.PermissionType.READ)).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

        LockInfoDto info = nodeService.getLockInfo(nodeId);

        assertEquals(LockStatus.LOCK_OWNER, info.status());
        assertEquals("alice", info.lockedBy());
        assertEquals(LockLifetime.EPHEMERAL, info.lockLifetime());
        assertNotNull(info.remainingSeconds());
        assertEquals(true, info.canUnlock());
    }

    @Test
    @DisplayName("Lock info returns expired status without mutating lock state")
    void getLockInfoReturnsExpiredStatus() {
        UUID nodeId = UUID.randomUUID();
        Folder folder = folder(nodeId, "workspace");
        folder.applyLock("bob", LocalDateTime.now().minusHours(1), LockLifetime.EPHEMERAL, LocalDateTime.now().minusMinutes(2));

        when(nodeRepository.findByIdAndDeletedFalse(nodeId)).thenReturn(Optional.of(folder));
        when(securityService.hasPermission(folder, com.ecm.core.entity.Permission.PermissionType.READ)).thenReturn(true);
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

        LockInfoDto info = nodeService.getLockInfo(nodeId);

        assertEquals(LockStatus.LOCK_EXPIRED, info.status());
        assertEquals("bob", info.lockedBy());
        assertEquals(0L, info.remainingSeconds());
        assertFalse(info.canUnlock());
    }

    private Folder folder(UUID id, String name) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setPath("/" + name);
        return folder;
    }
}
