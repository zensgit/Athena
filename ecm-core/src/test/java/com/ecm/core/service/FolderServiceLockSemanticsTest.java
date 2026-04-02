package com.ecm.core.service;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.LockLifetime;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.PermissionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderServiceLockSemanticsTest {

    @Mock private FolderRepository folderRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private SecurityService securityService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private com.ecm.core.search.FacetedSearchService searchService;
    @Mock private ObjectMapper objectMapper;

    private FolderService folderService;

    @BeforeEach
    void setUp() {
        folderService = new FolderService(
            folderRepository,
            nodeRepository,
            permissionRepository,
            securityService,
            eventPublisher,
            searchService,
            objectMapper
        );
    }

    @Test
    @DisplayName("Update folder clears expired lock before applying changes")
    void updateFolderClearsExpiredLock() {
        UUID folderId = UUID.randomUUID();
        Folder folder = new Folder();
        folder.setId(folderId);
        folder.setName("workspace");
        folder.setPath("/workspace");
        folder.applyLock("alice", LocalDateTime.now().minusHours(1), LockLifetime.EPHEMERAL, LocalDateTime.now().minusMinutes(5));

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(securityService.hasPermission(folder, com.ecm.core.entity.Permission.PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(folder, com.ecm.core.entity.Permission.PermissionType.WRITE)).thenReturn(true);

        folderService.updateFolder(folderId, new FolderService.UpdateFolderRequest("workspace-2", null, null, null, null, null, null));

        assertFalse(folder.isLocked());
        assertNull(folder.getLockedBy());
        verify(folderRepository, atLeastOnce()).save(folder);
    }
}
