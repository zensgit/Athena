package com.ecm.core.service;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.exception.IllegalOperationException;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderServiceLegalHoldTest {

    @Mock private FolderRepository folderRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private com.ecm.core.repository.RenditionResourceRepository renditionResourceRepository;
    @Mock private SecurityService securityService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private com.ecm.core.search.FacetedSearchService searchService;
    @Mock private LegalHoldService legalHoldService;

    private FolderService folderService;

    @BeforeEach
    void setUp() {
        folderService = new FolderService(
            folderRepository,
            nodeRepository,
            permissionRepository,
            renditionResourceRepository,
            securityService,
            eventPublisher,
            searchService,
            new ObjectMapper()
        );
        ReflectionTestUtils.setField(folderService, "legalHoldService", legalHoldService);
    }

    @Test
    @DisplayName("deleteFolder rejects held folder before recursion")
    void deleteFolderRejectsHeldFolder() {
        UUID folderId = UUID.randomUUID();
        Folder folder = folder(folderId, "/Sites/Finance");

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(securityService.hasPermission(folder, PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(folder, PermissionType.DELETE)).thenReturn(true);
        doThrow(new IllegalOperationException("held")).when(legalHoldService).assertOperationAllowed(folder, "delete");

        assertThrows(IllegalOperationException.class, () -> folderService.deleteFolder(folderId, false, true));

        verify(nodeRepository, never()).softDeleteByPathPrefix(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    private Folder folder(UUID id, String path) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName("Finance");
        folder.setPath(path);
        folder.setArchiveStatus(Node.ArchiveStatus.LIVE);
        folder.setStatus(Node.NodeStatus.ACTIVE);
        return folder;
    }
}
