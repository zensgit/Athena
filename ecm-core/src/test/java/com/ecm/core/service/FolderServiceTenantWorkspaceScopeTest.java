package com.ecm.core.service;

import com.ecm.core.config.TenantContext;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.PermissionRepository;
import com.ecm.core.search.FacetedSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderServiceTenantWorkspaceScopeTest {

    @Mock private FolderRepository folderRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private com.ecm.core.repository.RenditionResourceRepository renditionResourceRepository;
    @Mock private SecurityService securityService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private FacetedSearchService searchService;

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
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("getRootFolders returns only the current tenant root workspace")
    void getRootFoldersReturnsOnlyTenantRootWorkspace() {
        UUID rootId = UUID.randomUUID();
        TenantContext.setCurrentTenantRootNodeId(rootId);
        Folder root = folder(rootId, "Acme Workspace", "/Acme Workspace");

        when(folderRepository.findById(rootId)).thenReturn(Optional.of(root));
        when(securityService.hasPermission(root, PermissionType.READ)).thenReturn(true);

        List<Folder> roots = folderService.getRootFolders();

        assertEquals(1, roots.size());
        assertEquals(rootId, roots.get(0).getId());
    }

    @Test
    @DisplayName("createFolder without parent provisions under current tenant root workspace")
    void createFolderWithoutParentUsesTenantRootWorkspace() {
        UUID rootId = UUID.randomUUID();
        TenantContext.setCurrentTenantRootNodeId(rootId);
        Folder root = folder(rootId, "Acme Workspace", "/Acme Workspace");

        when(folderRepository.findById(rootId)).thenReturn(Optional.of(root));
        when(securityService.hasPermission(root, PermissionType.CREATE_CHILDREN)).thenReturn(true);
        when(nodeRepository.findByParentIdAndName(rootId, "Shared")).thenReturn(Optional.empty());
        when(permissionRepository.findByNodeId(rootId)).thenReturn(List.of());
        when(folderRepository.save(any(Folder.class))).thenAnswer(invocation -> {
            Folder saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(securityService.getCurrentUser()).thenReturn("admin");

        Folder created = folderService.createFolder(new FolderService.CreateFolderRequest(
            "Shared",
            "Scoped child",
            null,
            Folder.FolderType.GENERAL,
            null,
            null,
            null,
            null,
            true,
            false,
            null
        ));

        assertEquals(rootId, created.getParent().getId());
    }

    private static Folder folder(UUID id, String name, String path) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setPath(path);
        folder.setDeleted(false);
        folder.setArchiveStatus(Node.ArchiveStatus.LIVE);
        return folder;
    }
}
