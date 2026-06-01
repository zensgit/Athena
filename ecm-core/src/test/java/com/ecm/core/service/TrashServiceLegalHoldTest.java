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
class TrashServiceLegalHoldTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private RenditionResourceRepository renditionResourceRepository;
    @Mock private SecurityService securityService;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;
    @Mock private LegalHoldService legalHoldService;

    private TrashService service;

    @BeforeEach
    void setUp() {
        service = new TrashService(nodeRepository, renditionResourceRepository, securityService, tenantWorkspaceScopeService);
        ReflectionTestUtils.setField(service, "retentionDays", 30);
        ReflectionTestUtils.setField(service, "autoPurgeEnabled", true);
        ReflectionTestUtils.setField(service, "legalHoldService", legalHoldService);
    }

    @Test
    @DisplayName("moveToTrash rejects held node")
    void moveToTrashRejectsHeldNode() {
        UUID nodeId = UUID.randomUUID();
        Document document = document(nodeId, "/Sites/Finance/report.pdf");

        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(document));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        when(securityService.hasPermission(document, PermissionType.DELETE)).thenReturn(true);
        doThrow(new IllegalOperationException("held")).when(legalHoldService).assertOperationAllowed(document, "move to trash");

        assertThrows(IllegalOperationException.class, () -> service.moveToTrash(nodeId));

        verify(nodeRepository, never()).save(document);
    }

    @Test
    @DisplayName("emptyTrash aborts before deleting held root item")
    void emptyTrashAbortsBeforeDeletingHeldRootItem() {
        Document document = document(UUID.randomUUID(), "/Sites/Finance/report.pdf");
        document.setDeleted(true);
        document.setDeletedBy("alice");

        when(securityService.getCurrentUser()).thenReturn("alice");
        when(securityService.isAdmin("alice")).thenReturn(false);
        when(nodeRepository.findDeletedByUser("alice")).thenReturn(List.of(document));
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(false);
        doThrow(new IllegalOperationException("held")).when(legalHoldService).assertOperationAllowed(document, "empty trash");

        assertThrows(IllegalOperationException.class, () -> service.emptyTrash());

        verify(nodeRepository, never()).delete(document);
    }

    @Test
    @DisplayName("purgeOldTrashItems skips held nodes")
    void purgeOldTrashItemsSkipsHeldNodes() {
        Document document = document(UUID.randomUUID(), "/Sites/Finance/report.pdf");
        document.setDeleted(true);
        document.setDeletedAt(LocalDateTime.now().minusDays(40));

        when(nodeRepository.findDeletedBefore(org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
            .thenReturn(List.of(document));
        doThrow(new IllegalOperationException("held")).when(legalHoldService).assertOperationAllowed(document, "auto-purge");

        service.purgeOldTrashItems();

        verify(nodeRepository, never()).delete(document);
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
