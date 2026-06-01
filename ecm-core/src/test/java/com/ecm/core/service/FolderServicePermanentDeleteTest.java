package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.PermissionRepository;
import com.ecm.core.repository.RenditionResourceRepository;
import com.ecm.core.search.FacetedSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderServicePermanentDeleteTest {

    @Mock private FolderRepository folderRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private RenditionResourceRepository renditionResourceRepository;
    @Mock private ShareLinkNodeCleanupService shareLinkNodeCleanupService;
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
        ReflectionTestUtils.setField(folderService, "shareLinkNodeCleanupService", shareLinkNodeCleanupService);
    }

    @Test
    @DisplayName("Recursive permanent folder delete clears document rendition resources before document delete")
    void recursivePermanentDeleteClearsDocumentRenditionsBeforeDocumentDelete() {
        UUID folderId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Folder folder = folder(folderId);
        Document document = document(documentId);

        when(folderRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(securityService.hasPermission(folder, PermissionType.READ)).thenReturn(true);
        when(securityService.resolveReadAuthorities(folder)).thenReturn(Set.of("ROLE_ADMIN"));
        when(securityService.hasPermission(folder, PermissionType.DELETE)).thenReturn(true);
        when(folderRepository.countChildren(folderId)).thenReturn(1L);
        when(nodeRepository.findByParentIdAndDeletedFalse(folderId)).thenReturn(List.of(document));
        when(securityService.getCurrentUser()).thenReturn("staging-smoke-admin");

        folderService.deleteFolder(folderId, true, true);

        InOrder inOrder = inOrder(shareLinkNodeCleanupService, renditionResourceRepository, permissionRepository, nodeRepository, folderRepository);
        inOrder.verify(permissionRepository).deleteByNodeId(documentId);
        inOrder.verify(shareLinkNodeCleanupService).deleteByNodeId(documentId);
        inOrder.verify(renditionResourceRepository).deleteByDocumentId(documentId);
        inOrder.verify(nodeRepository).delete(document);
        inOrder.verify(permissionRepository).deleteByNodeId(folderId);
        inOrder.verify(folderRepository).delete(folder);
    }

    private static Folder folder(UUID id) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName("smoke-folder");
        folder.setPath("/smoke-folder");
        folder.setDeleted(false);
        folder.setArchiveStatus(Node.ArchiveStatus.LIVE);
        folder.setStatus(Node.NodeStatus.ACTIVE);
        return folder;
    }

    private static Document document(UUID id) {
        Document document = new Document();
        document.setId(id);
        document.setName("smoke.pdf");
        document.setDeleted(false);
        document.setArchiveStatus(Node.ArchiveStatus.LIVE);
        document.setStatus(Node.NodeStatus.ACTIVE);
        return document;
    }
}
