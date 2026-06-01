package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.RenditionResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class TrashServiceTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private RenditionResourceRepository renditionResourceRepository;
    @Mock private ShareLinkNodeCleanupService shareLinkNodeCleanupService;
    @Mock private SecurityService securityService;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;

    private TrashService service;

    @BeforeEach
    void setUp() {
        service = new TrashService(nodeRepository, renditionResourceRepository, securityService, tenantWorkspaceScopeService);
        ReflectionTestUtils.setField(service, "shareLinkNodeCleanupService", shareLinkNodeCleanupService);
        ReflectionTestUtils.setField(service, "retentionDays", 30);
        ReflectionTestUtils.setField(service, "autoPurgeEnabled", true);
    }

    @Test
    @DisplayName("moveToTrash hides foreign-tenant node")
    void moveToTrashHidesForeignTenantNode() {
        UUID nodeId = UUID.randomUUID();
        Document hidden = document(nodeId, "/foreign/report.pdf");

        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(hidden));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
        when(tenantWorkspaceScopeService.isPathVisible(hidden.getPath())).thenReturn(false);

        assertThrows(NoSuchElementException.class, () -> service.moveToTrash(nodeId));
    }

    @Test
    @DisplayName("getTrashItems filters hidden tenant nodes")
    void getTrashItemsFiltersHiddenTenantNodes() {
        Document visible = document(UUID.randomUUID(), "/tenant-a/report.pdf");
        visible.setDeleted(true);
        visible.setDeletedBy("alice");
        Document hidden = document(UUID.randomUUID(), "/tenant-b/report.pdf");
        hidden.setDeleted(true);
        hidden.setDeletedBy("alice");

        when(securityService.getCurrentUser()).thenReturn("alice");
        when(securityService.isAdmin("alice")).thenReturn(false);
        when(nodeRepository.findDeletedByUser("alice")).thenReturn(List.of(visible, hidden));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
        when(tenantWorkspaceScopeService.isPathVisible(visible.getPath())).thenReturn(true);
        when(tenantWorkspaceScopeService.isPathVisible(hidden.getPath())).thenReturn(false);

        List<Node> trashItems = service.getTrashItems();

        assertEquals(1, trashItems.size());
        assertEquals(visible.getId(), trashItems.get(0).getId());
    }

    @Test
    @DisplayName("permanentDelete removes rendition resources before deleting document")
    void permanentDeleteRemovesRenditionResourcesBeforeDeletingDocument() {
        UUID nodeId = UUID.randomUUID();
        Document document = document(nodeId, "/Root/uploads/report.pdf");
        document.setDeleted(true);
        document.setCreatedBy("alice");

        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(document));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(securityService.isAdmin("alice")).thenReturn(false);

        service.permanentDelete(nodeId);

        var order = inOrder(shareLinkNodeCleanupService, renditionResourceRepository, nodeRepository);
        order.verify(shareLinkNodeCleanupService).deleteByNodeId(nodeId);
        order.verify(renditionResourceRepository).deleteByDocumentId(nodeId);
        order.verify(nodeRepository).delete(document);
    }

    @Test
    @DisplayName("permanentDelete recursively removes child document renditions before child delete")
    void permanentDeleteRecursivelyRemovesChildDocumentRenditionsBeforeChildDelete() {
        UUID folderId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Folder folder = folder(folderId, "/Root/uploads/batch");
        folder.setDeleted(true);
        folder.setCreatedBy("admin");
        Document document = document(documentId, "/Root/uploads/batch/report.pdf");
        document.setDeleted(true);
        document.setCreatedBy("admin");

        when(nodeRepository.findById(folderId)).thenReturn(Optional.of(folder));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(securityService.isAdmin("admin")).thenReturn(true);
        when(nodeRepository.findByParentId(folderId)).thenReturn(List.of(document));

        service.permanentDelete(folderId);

        var order = inOrder(shareLinkNodeCleanupService, renditionResourceRepository, nodeRepository);
        order.verify(shareLinkNodeCleanupService).deleteByNodeId(documentId);
        order.verify(renditionResourceRepository).deleteByDocumentId(documentId);
        order.verify(nodeRepository).delete(document);
        order.verify(nodeRepository).delete(folder);
    }
    
    private Document document(UUID id, String path) {
        Document document = new Document();
        document.setId(id);
        document.setName("doc");
        document.setPath(path);
        document.setArchiveStatus(Node.ArchiveStatus.LIVE);
        return document;
    }

    private Folder folder(UUID id, String path) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName("folder");
        folder.setPath(path);
        folder.setArchiveStatus(Node.ArchiveStatus.LIVE);
        return folder;
    }
}
