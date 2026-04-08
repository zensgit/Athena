package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.repository.NodeRepository;
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

@ExtendWith(MockitoExtension.class)
class TrashServiceTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;

    private TrashService service;

    @BeforeEach
    void setUp() {
        service = new TrashService(nodeRepository, securityService, tenantWorkspaceScopeService);
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
    
    private Document document(UUID id, String path) {
        Document document = new Document();
        document.setId(id);
        document.setName("doc");
        document.setPath(path);
        document.setArchiveStatus(Node.ArchiveStatus.LIVE);
        return document;
    }
}
