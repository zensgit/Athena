package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Node.ArchiveStatus;
import com.ecm.core.entity.Node.ArchiveStoreTier;
import com.ecm.core.repository.NodeRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentArchiveServiceTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;
    @Mock private ActivityEventListener activityEventListener;

    private ContentArchiveService service;

    @BeforeEach
    void setUp() {
        service = new ContentArchiveService(nodeRepository, securityService, activityEventListener);
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
        verify(activityEventListener).postNodeActivity(eq("node.restored"), eq("alice"), eq(root), anyMap());
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
