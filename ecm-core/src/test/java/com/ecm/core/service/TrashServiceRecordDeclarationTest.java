package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.exception.IllegalOperationException;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.RenditionResourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrashServiceRecordDeclarationTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private RenditionResourceRepository renditionResourceRepository;
    @Mock private SecurityService securityService;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;
    @Mock private RecordsManagementService recordsManagementService;

    private TrashService service;

    @BeforeEach
    void setUp() {
        service = new TrashService(nodeRepository, renditionResourceRepository, securityService, tenantWorkspaceScopeService);
        ReflectionTestUtils.setField(service, "retentionDays", 30);
        ReflectionTestUtils.setField(service, "autoPurgeEnabled", true);
        ReflectionTestUtils.setField(service, "recordsManagementService", recordsManagementService);
    }

    @Test
    @DisplayName("moveToTrash rejects declared node")
    void moveToTrashRejectsDeclaredNode() {
        UUID nodeId = UUID.randomUUID();
        Document document = document(nodeId, "/Sites/Finance/report.pdf");

        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(document));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        when(securityService.hasPermission(document, PermissionType.DELETE)).thenReturn(true);
        doThrow(new IllegalOperationException("record")).when(recordsManagementService)
            .assertHierarchyMutationAllowed(document, "move to trash");

        assertThrows(IllegalOperationException.class, () -> service.moveToTrash(nodeId));

        verify(nodeRepository, never()).save(document);
    }

    @Test
    @DisplayName("purgeOldTrashItems skips declared records")
    void purgeOldTrashItemsSkipsDeclaredRecords() {
        Document document = document(UUID.randomUUID(), "/Sites/Finance/report.pdf");
        document.setDeleted(true);
        document.setDeletedAt(LocalDateTime.now().minusDays(40));

        when(nodeRepository.findDeletedBefore(org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
            .thenReturn(List.of(document));
        doThrow(new IllegalOperationException("record")).when(recordsManagementService)
            .assertHierarchyMutationAllowed(document, "auto-purge");

        service.purgeOldTrashItems();

        verify(nodeRepository, never()).delete(document);
    }

    @Test
    @DisplayName("restore rejects RM-governed node")
    void restoreRejectsRmGovernedNode() {
        UUID nodeId = UUID.randomUUID();
        Document document = document(nodeId, "/Corporate File Plan/report.pdf");
        document.setDeleted(true);
        document.setDeletedBy("admin");

        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(document));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        when(securityService.getCurrentUser()).thenReturn("admin");
        when(securityService.isAdmin("admin")).thenReturn(true);
        doThrow(new IllegalOperationException("record")).when(recordsManagementService)
            .assertRestoreAllowed(document, "restore from trash");

        assertThrows(IllegalOperationException.class, () -> service.restore(nodeId));

        verify(nodeRepository, never()).save(document);
    }

    private Document document(UUID id, String path) {
        Document document = new Document();
        document.setId(id);
        document.setName("report.pdf");
        document.setPath(path);
        document.setArchiveStatus(Node.ArchiveStatus.LIVE);
        return document;
    }
}
