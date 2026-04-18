package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Node.ArchiveStatus;
import com.ecm.core.entity.Node.ArchiveStoreTier;
import com.ecm.core.exception.IllegalOperationException;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.search.SearchIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentArchiveServiceTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;
    @Mock private ActivityEventListener activityEventListener;
    @Mock private SearchIndexService searchIndexService;
    @Mock private TenantWorkspaceScopeService tenantWorkspaceScopeService;
    @Mock private LegalHoldService legalHoldService;
    @Mock private RecordsManagementService recordsManagementService;

    private ContentArchiveService service;

    @BeforeEach
    void setUp() {
        service = new ContentArchiveService(
            nodeRepository,
            securityService,
            activityEventListener,
            searchIndexService,
            tenantWorkspaceScopeService,
            legalHoldService,
            recordsManagementService
        );
        lenient().when(tenantWorkspaceScopeService.isPathVisible(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("archiveNode archives folder descendants and posts node.archived activity")
    void archiveNodeArchivesFolderScope() {
        Folder root = folder("finance", "alice");
        Document child = document("spec.docx", "/finance/spec.docx", "alice");
        when(nodeRepository.findByIdAndDeletedFalse(root.getId())).thenReturn(Optional.of(root));
        when(nodeRepository.findByPathPrefix("/finance/")).thenReturn(List.of(child));
        when(nodeRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(securityService.getCurrentUser()).thenReturn("alice");

        ContentArchiveService.ArchiveMutationDto result = service.archiveNode(root.getId(), ArchiveStoreTier.COLD);

        assertEquals(ArchiveStatus.ARCHIVED, root.getArchiveStatus());
        assertEquals(ArchiveStoreTier.COLD, root.getArchiveStoreTier());
        assertEquals(ArchiveStatus.ARCHIVED, child.getArchiveStatus());
        assertEquals(ArchiveStoreTier.COLD, child.getArchiveStoreTier());
        assertEquals(2, result.affectedNodeCount());
        verify(searchIndexService).updateNode(root);
        verify(searchIndexService).updateDocument(child);
        verify(activityEventListener).postNodeActivity(eq("node.archived"), eq("alice"), eq(root), anyMap());
    }

    @Test
    @DisplayName("archiveNode rejects already archived node")
    void archiveNodeRejectsAlreadyArchivedNode() {
        Folder root = folder("finance", "alice");
        root.setArchiveStatus(ArchiveStatus.ARCHIVED);
        when(nodeRepository.findByIdAndDeletedFalse(root.getId())).thenReturn(Optional.of(root));

        assertThrows(IllegalStateException.class, () -> service.archiveNode(root.getId(), ArchiveStoreTier.GLACIER));
    }

    @Test
    @DisplayName("restoreNode restores folder descendants and posts node.restored activity")
    void restoreNodeRestoresFolderScope() {
        Folder root = folder("finance", "alice");
        root.setArchiveStatus(ArchiveStatus.ARCHIVED);
        root.setArchiveStoreTier(ArchiveStoreTier.GLACIER);
        root.setArchivedBy("alice");
        root.setArchivedDate(LocalDateTime.now().minusDays(1));
        Document child = document("spec.docx", "/finance/spec.docx", "alice");
        child.setArchiveStatus(ArchiveStatus.ARCHIVED);
        child.setArchiveStoreTier(ArchiveStoreTier.GLACIER);
        child.setArchivedBy("alice");
        child.setArchivedDate(LocalDateTime.now().minusDays(1));

        when(nodeRepository.findByIdAndDeletedFalse(root.getId())).thenReturn(Optional.of(root));
        when(nodeRepository.findByPathPrefix("/finance/")).thenReturn(List.of(child));
        when(nodeRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(securityService.getCurrentUser()).thenReturn("alice");

        ContentArchiveService.ArchiveMutationDto result = service.restoreNode(root.getId());

        assertEquals(ArchiveStatus.LIVE, root.getArchiveStatus());
        assertEquals(ArchiveStoreTier.HOT, root.getArchiveStoreTier());
        assertNull(root.getArchivedDate());
        assertNull(root.getArchivedBy());
        assertEquals(ArchiveStatus.LIVE, child.getArchiveStatus());
        assertEquals(ArchiveStoreTier.HOT, child.getArchiveStoreTier());
        assertEquals(2, result.affectedNodeCount());
        verify(recordsManagementService).assertRestoreScopeAllowed(eq(root), anyList(), eq("restore"));
        verify(searchIndexService).updateNode(root);
        verify(searchIndexService).updateDocument(child);
        verify(activityEventListener).postNodeActivity(eq("node.restored"), eq("alice"), eq(root), anyMap());
    }

    @Test
    @DisplayName("restoreNode rejects archived folder scope containing non-archived descendants")
    void restoreNodeRejectsScopeContainingNonArchivedDescendant() {
        Folder root = folder("finance", "alice");
        root.setArchiveStatus(ArchiveStatus.ARCHIVED);
        root.setArchiveStoreTier(ArchiveStoreTier.GLACIER);
        root.setArchivedBy("alice");
        Document child = document("spec.docx", "/finance/spec.docx", "alice");
        child.setArchiveStatus(ArchiveStatus.LIVE);

        when(nodeRepository.findByIdAndDeletedFalse(root.getId())).thenReturn(Optional.of(root));
        when(nodeRepository.findByPathPrefix("/finance/")).thenReturn(List.of(child));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.restoreNode(root.getId()));

        assertEquals("Cannot restore because archived scope contains non-archived node 'spec.docx'", ex.getMessage());
        verify(recordsManagementService, never()).assertRestoreScopeAllowed(eq(root), anyList(), eq("restore"));
    }

    @Test
    @DisplayName("restoreNode rejects archived folder scope containing working copies")
    void restoreNodeRejectsScopeContainingWorkingCopy() {
        Folder root = folder("finance", "alice");
        root.setArchiveStatus(ArchiveStatus.ARCHIVED);
        root.setArchiveStoreTier(ArchiveStoreTier.GLACIER);
        root.setArchivedBy("alice");
        Document child = document("spec.docx", "/finance/spec.docx", "alice");
        child.setArchiveStatus(ArchiveStatus.ARCHIVED);
        child.setWorkingCopy(true);

        when(nodeRepository.findByIdAndDeletedFalse(root.getId())).thenReturn(Optional.of(root));
        when(nodeRepository.findByPathPrefix("/finance/")).thenReturn(List.of(child));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.restoreNode(root.getId()));

        assertEquals("Cannot restore because archived scope contains working copy 'spec.docx'", ex.getMessage());
        verify(recordsManagementService, never()).assertRestoreScopeAllowed(eq(root), anyList(), eq("restore"));
    }

    @Test
    @DisplayName("restoreNode rejects RM-governed descendants before mutating archived scope")
    void restoreNodeRejectsRestoreScopeBlockedByRecordsManagement() {
        Folder root = folder("finance", "alice");
        root.setArchiveStatus(ArchiveStatus.ARCHIVED);
        root.setArchiveStoreTier(ArchiveStoreTier.GLACIER);
        root.setArchivedBy("alice");
        Document child = document("spec.docx", "/finance/spec.docx", "alice");
        child.setArchiveStatus(ArchiveStatus.ARCHIVED);
        child.setArchiveStoreTier(ArchiveStoreTier.GLACIER);
        child.setArchivedBy("alice");

        when(nodeRepository.findByIdAndDeletedFalse(root.getId())).thenReturn(Optional.of(root));
        when(nodeRepository.findByPathPrefix("/finance/")).thenReturn(List.of(child));
        doThrow(new IllegalOperationException("Cannot restore because node 'finance' contains declared record(s): spec.docx"))
            .when(recordsManagementService).assertRestoreScopeAllowed(eq(root), anyList(), eq("restore"));

        IllegalOperationException ex = assertThrows(IllegalOperationException.class, () -> service.restoreNode(root.getId()));

        assertEquals("Cannot restore because node 'finance' contains declared record(s): spec.docx", ex.getMessage());
        assertEquals(ArchiveStatus.ARCHIVED, root.getArchiveStatus());
        assertEquals(ArchiveStatus.ARCHIVED, child.getArchiveStatus());
    }

    @Test
    @DisplayName("restoreNode rejects non-owner non-archiver non-admin")
    void restoreNodeRejectsUnauthorizedUser() {
        Folder root = folder("finance", "alice");
        root.setArchiveStatus(ArchiveStatus.ARCHIVED);
        root.setArchivedBy("alice");
        when(nodeRepository.findByIdAndDeletedFalse(root.getId())).thenReturn(Optional.of(root));
        when(securityService.getCurrentUser()).thenReturn("bob");
        when(securityService.isAdmin("bob")).thenReturn(false);

        assertThrows(SecurityException.class, () -> service.restoreNode(root.getId()));
    }

    @Test
    @DisplayName("listArchivedNodes returns archived page for admin")
    void listArchivedNodesReturnsArchivedPage() {
        Folder root = folder("finance", "alice");
        root.setArchiveStatus(ArchiveStatus.ARCHIVED);
        root.setArchiveStoreTier(ArchiveStoreTier.WARM);
        root.setArchivedBy("admin");
        root.setArchivedDate(LocalDateTime.now());
        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(nodeRepository.findByArchiveStatusAndDeletedFalseOrderByArchivedDateDesc(eq(ArchiveStatus.ARCHIVED), eq(PageRequest.of(0, 20))))
            .thenReturn(new PageImpl<>(List.of(root), PageRequest.of(0, 20), 1));

        var page = service.listArchivedNodes(PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals("finance", page.getContent().get(0).name());
        assertEquals(ArchiveStoreTier.WARM, page.getContent().get(0).archiveStoreTier());
    }

    @Test
    @DisplayName("archiveNode hides nodes outside current tenant workspace")
    void archiveNodeHidesForeignTenantNode() {
        Folder root = folder("finance", "alice");
        when(nodeRepository.findByIdAndDeletedFalse(root.getId())).thenReturn(Optional.of(root));
        when(tenantWorkspaceScopeService.isPathVisible(root.getPath())).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.archiveNode(root.getId(), ArchiveStoreTier.COLD));
    }

    @Test
    @DisplayName("archiveNode rejects declared records managed by RM")
    void archiveNodeRejectsDeclaredRecords() {
        Folder root = folder("finance", "alice");
        when(nodeRepository.findByIdAndDeletedFalse(root.getId())).thenReturn(Optional.of(root));
        doThrow(new IllegalOperationException("Cannot archive because node 'finance' is declared as a record"))
            .when(recordsManagementService).assertArchiveMutationAllowed(root, "archive");

        assertThrows(IllegalOperationException.class, () -> service.archiveNode(root.getId(), ArchiveStoreTier.COLD));
    }

    @Test
    @DisplayName("archiveNodeByPolicy rejects held nodes")
    void archiveNodeByPolicyRejectsHeldNodes() {
        Folder root = folder("finance", "alice");
        when(nodeRepository.findByIdAndDeletedFalse(root.getId())).thenReturn(Optional.of(root));
        doThrow(new IllegalOperationException("Cannot archive because node 'finance' is under active legal hold(s): Hold A"))
            .when(legalHoldService).assertOperationAllowed(root, "archive");

        assertThrows(IllegalOperationException.class, () -> service.archiveNodeByPolicy(root.getId(), ArchiveStoreTier.COLD, "system"));
    }

    @Test
    @DisplayName("listArchivedNodes filters nodes outside current tenant workspace")
    void listArchivedNodesFiltersForeignTenantNodes() {
        Folder visible = folder("finance", "alice");
        visible.setArchiveStatus(ArchiveStatus.ARCHIVED);
        visible.setArchiveStoreTier(ArchiveStoreTier.WARM);
        visible.setArchivedBy("admin");
        visible.setArchivedDate(LocalDateTime.now());

        Folder hidden = folder("legal", "alice");
        hidden.setArchiveStatus(ArchiveStatus.ARCHIVED);
        hidden.setArchiveStoreTier(ArchiveStoreTier.COLD);
        hidden.setArchivedBy("admin");
        hidden.setArchivedDate(LocalDateTime.now().minusDays(1));

        when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);
        when(tenantWorkspaceScopeService.hasScopedTenantWorkspace()).thenReturn(true);
        when(tenantWorkspaceScopeService.resolveCurrentTenantRootPath()).thenReturn("/finance");
        when(nodeRepository.findByArchiveStatusAndDeletedFalseOrderByArchivedDateDesc(eq(ArchiveStatus.ARCHIVED), eq(org.springframework.data.domain.Pageable.unpaged())))
            .thenReturn(new PageImpl<>(List.of(visible, hidden)));
        when(tenantWorkspaceScopeService.isPathVisible("/finance", "/finance")).thenReturn(true);
        when(tenantWorkspaceScopeService.isPathVisible("/legal", "/finance")).thenReturn(false);

        var page = service.listArchivedNodes(PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals("finance", page.getContent().get(0).name());
    }

    private Folder folder(String name, String createdBy) {
        Folder folder = new Folder();
        folder.setId(UUID.randomUUID());
        folder.setName(name);
        folder.setPath("/" + name);
        folder.setCreatedBy(createdBy);
        return folder;
    }

    private Document document(String name, String path, String createdBy) {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName(name);
        document.setPath(path);
        document.setCreatedBy(createdBy);
        return document;
    }
}
