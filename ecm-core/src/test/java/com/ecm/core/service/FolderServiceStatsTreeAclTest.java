package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.PermissionRepository;
import com.ecm.core.search.FacetedSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderServiceStatsTreeAclTest {

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private SecurityService securityService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private FacetedSearchService searchService;

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
            new ObjectMapper()
        );
    }

    @Test
    @DisplayName("Filters folder stats counts for non-admin users")
    void filtersFolderStatsCountsForNonAdmin() {
        UUID rootId = UUID.randomUUID();
        Folder root = folder(rootId, "Root", 10);

        Folder allowedFolder = folder(UUID.randomUUID(), "AllowedFolder", null);
        Folder deniedFolder = folder(UUID.randomUUID(), "DeniedFolder", null);
        Document allowedDoc = document("AllowedDoc", 10);
        Document deniedDoc = document("DeniedDoc", 20);

        Folder allowedSubFolder = folder(UUID.randomUUID(), "AllowedSubFolder", null);
        Folder deniedSubFolder = folder(UUID.randomUUID(), "DeniedSubFolder", null);
        Document allowedSubDoc = document("AllowedSubDoc", 30);
        Document allowedSubDoc2 = document("AllowedSubDoc2", 40);

        when(folderRepository.findById(rootId)).thenReturn(Optional.of(root));
        when(securityService.hasPermission(root, PermissionType.READ)).thenReturn(true);
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

        when(nodeRepository.findByParentIdAndDeletedFalse(rootId))
            .thenReturn(List.of(allowedFolder, deniedFolder, allowedDoc, deniedDoc));
        when(nodeRepository.findByParentIdAndDeletedFalse(allowedFolder.getId()))
            .thenReturn(List.of(allowedSubFolder, deniedSubFolder, allowedSubDoc));
        when(nodeRepository.findByParentIdAndDeletedFalse(allowedSubFolder.getId()))
            .thenReturn(List.of(allowedSubDoc2));

        when(securityService.hasPermission(allowedFolder, PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(deniedFolder, PermissionType.READ)).thenReturn(false);
        when(securityService.hasPermission(allowedDoc, PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(deniedDoc, PermissionType.READ)).thenReturn(false);
        when(securityService.hasPermission(allowedSubFolder, PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(deniedSubFolder, PermissionType.READ)).thenReturn(false);
        when(securityService.hasPermission(allowedSubDoc, PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(allowedSubDoc2, PermissionType.READ)).thenReturn(true);

        FolderService.FolderStats stats = folderService.getFolderStats(rootId);

        assertEquals(1, stats.directFolders());
        assertEquals(1, stats.directDocuments());
        assertEquals(10L, stats.directSize());
        assertEquals(2, stats.totalFolders());
        assertEquals(3, stats.totalDocuments());
        assertEquals(80L, stats.totalSize());
        assertEquals(8, stats.remainingCapacity());
    }

    @Test
    @DisplayName("Filters folder tree child counts for non-admin users")
    void filtersFolderTreeChildCountsForNonAdmin() {
        UUID rootId = UUID.randomUUID();
        Folder root = folder(rootId, "Root", null);

        Folder allowedFolder = folder(UUID.randomUUID(), "AllowedFolder", null);
        Folder deniedFolder = folder(UUID.randomUUID(), "DeniedFolder", null);
        Document allowedDoc = document("AllowedDoc", 10);

        Document allowedSubDoc = document("AllowedSubDoc", 5);

        when(folderRepository.findById(rootId)).thenReturn(Optional.of(root));
        when(securityService.hasPermission(root, PermissionType.READ)).thenReturn(true);
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

        when(nodeRepository.findByParentIdAndDeletedFalse(rootId))
            .thenReturn(List.of(allowedFolder, deniedFolder, allowedDoc));
        when(nodeRepository.findByParentIdAndDeletedFalse(allowedFolder.getId()))
            .thenReturn(List.of(allowedSubDoc));

        when(securityService.hasPermission(allowedFolder, PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(deniedFolder, PermissionType.READ)).thenReturn(false);
        when(securityService.hasPermission(allowedDoc, PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(allowedSubDoc, PermissionType.READ)).thenReturn(true);

        List<FolderService.FolderTreeNode> tree = folderService.getFolderTree(rootId, 1);

        assertEquals(1, tree.size());

        FolderService.FolderTreeNode rootNode = tree.get(0);
        assertEquals(2, rootNode.childCount());
        assertTrue(rootNode.hasChildren());
        assertEquals(1, rootNode.children().size());

        FolderService.FolderTreeNode childNode = rootNode.children().get(0);
        assertEquals(1, childNode.childCount());
        assertFalse(childNode.hasChildren());
        assertEquals(0, childNode.children().size());
    }

    @Test
    @DisplayName("Includes denied children in folder stats for admin users")
    void includesDeniedChildrenForAdminStats() {
        UUID rootId = UUID.randomUUID();
        Folder root = folder(rootId, "Root", 10);

        Folder allowedFolder = folder(UUID.randomUUID(), "AllowedFolder", null);
        Folder deniedFolder = folder(UUID.randomUUID(), "DeniedFolder", null);
        Document allowedDoc = document("AllowedDoc", 10);
        Document deniedDoc = document("DeniedDoc", 20);
        Document nestedDoc = document("NestedDoc", 5);

        when(folderRepository.findById(rootId)).thenReturn(Optional.of(root));
        when(securityService.hasPermission(root, PermissionType.READ)).thenReturn(true);
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);

        when(nodeRepository.findByParentIdAndDeletedFalse(rootId))
            .thenReturn(List.of(allowedFolder, deniedFolder, allowedDoc, deniedDoc));
        when(nodeRepository.findByParentIdAndDeletedFalse(allowedFolder.getId()))
            .thenReturn(List.of());
        when(nodeRepository.findByParentIdAndDeletedFalse(deniedFolder.getId()))
            .thenReturn(List.of(nestedDoc));

        FolderService.FolderStats stats = folderService.getFolderStats(rootId);

        assertEquals(2, stats.directFolders());
        assertEquals(2, stats.directDocuments());
        assertEquals(30L, stats.directSize());
        assertEquals(2, stats.totalFolders());
        assertEquals(3, stats.totalDocuments());
        assertEquals(35L, stats.totalSize());
        assertEquals(6, stats.remainingCapacity());
    }

    @Test
    @DisplayName("Uses repository child count at max depth for admin tree")
    void adminTreeUsesRepositoryCountAtMaxDepth() {
        UUID rootId = UUID.randomUUID();
        Folder root = folder(rootId, "Root", null);

        when(folderRepository.findById(rootId)).thenReturn(Optional.of(root));
        when(securityService.hasPermission(root, PermissionType.READ)).thenReturn(true);
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(folderRepository.countChildren(rootId)).thenReturn(4L);

        List<FolderService.FolderTreeNode> tree = folderService.getFolderTree(rootId, 0);

        assertEquals(1, tree.size());
        FolderService.FolderTreeNode rootNode = tree.get(0);
        assertEquals(4L, rootNode.childCount());
        assertFalse(rootNode.hasChildren());
        assertEquals(0, rootNode.children().size());

        verify(nodeRepository, never()).findByParentIdAndDeletedFalse(rootId);
    }

    private static Folder folder(UUID id, String name, Integer maxItems) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setDeleted(false);
        folder.setMaxItems(maxItems);
        return folder;
    }

    private static Document document(String name, long size) {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName(name);
        document.setFileSize(size);
        document.setDeleted(false);
        return document;
    }
}
