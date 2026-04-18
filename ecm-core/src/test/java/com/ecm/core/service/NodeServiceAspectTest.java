package com.ecm.core.service;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Permission;
import com.ecm.core.repository.CorrespondentRepository;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.PermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeServiceAspectTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private CorrespondentRepository correspondentRepository;
    @Mock private SecurityService securityService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ContentReferenceService contentReferenceService;

    private NodeService nodeService;

    @BeforeEach
    void setUp() {
        nodeService = new NodeService(
            nodeRepository, folderRepository, documentRepository,
            permissionRepository, correspondentRepository, securityService, eventPublisher, contentReferenceService
        );
    }

    @Test
    @DisplayName("addAspect persists aspect to node")
    void addAspectPersistsAspect() {
        Folder folder = folder("test");
        stubWritable(folder);

        nodeService.addAspect(folder.getId(), "cm:titled");

        assertTrue(folder.hasAspect("cm:titled"));
    }

    @Test
    @DisplayName("addAspect rejects without WRITE permission")
    void addAspectRejectsWithoutPermission() {
        Folder folder = folder("test");
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folder.getId(), com.ecm.core.entity.Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(folder));
        when(securityService.hasPermission(folder, Permission.PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(folder, Permission.PermissionType.WRITE)).thenReturn(false);

        assertThrows(SecurityException.class, () -> nodeService.addAspect(folder.getId(), "cm:titled"));
    }

    @Test
    @DisplayName("removeAspect removes aspect and cleans prefixed properties")
    void removeAspectCleansProperties() {
        Folder folder = folder("test");
        folder.addAspect("cm:titled");
        folder.setProperties(new HashMap<>());
        folder.getProperties().put("cm:title", "Hello");
        folder.getProperties().put("cm:description", "World");
        folder.getProperties().put("app:icon", "folder.png");
        stubWritable(folder);

        nodeService.removeAspect(folder.getId(), "cm:titled");

        assertFalse(folder.hasAspect("cm:titled"));
        assertFalse(folder.getProperties().containsKey("cm:title"));
        assertFalse(folder.getProperties().containsKey("cm:description"));
        assertTrue(folder.getProperties().containsKey("app:icon"));
    }

    @Test
    @DisplayName("getAspects returns set of aspect names")
    void getAspectsReturnsSet() {
        Folder folder = folder("test");
        folder.addAspect("cm:titled");
        folder.addAspect("cm:auditable");
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folder.getId(), com.ecm.core.entity.Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(folder));
        when(securityService.hasPermission(folder, Permission.PermissionType.READ)).thenReturn(true);

        Set<String> aspects = nodeService.getAspects(folder.getId());

        assertEquals(Set.of("cm:titled", "cm:auditable"), aspects);
    }

    @Test
    @DisplayName("hasAspect returns true for existing aspect")
    void hasAspectTrue() {
        Folder folder = folder("test");
        folder.addAspect("cm:titled");
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folder.getId(), com.ecm.core.entity.Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(folder));
        when(securityService.hasPermission(folder, Permission.PermissionType.READ)).thenReturn(true);

        assertTrue(nodeService.hasAspect(folder.getId(), "cm:titled"));
    }

    @Test
    @DisplayName("hasAspect returns false for missing aspect")
    void hasAspectFalse() {
        Folder folder = folder("test");
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folder.getId(), com.ecm.core.entity.Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(folder));
        when(securityService.hasPermission(folder, Permission.PermissionType.READ)).thenReturn(true);

        assertFalse(nodeService.hasAspect(folder.getId(), "cm:titled"));
    }

    private Folder folder(String name) {
        Folder f = new Folder();
        f.setId(UUID.randomUUID());
        f.setName(name);
        f.setPath("/" + name);
        f.setArchiveStatus(com.ecm.core.entity.Node.ArchiveStatus.LIVE);
        return f;
    }

    private void stubWritable(Folder folder) {
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(folder.getId(), com.ecm.core.entity.Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(folder));
        when(securityService.hasPermission(folder, Permission.PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(folder, Permission.PermissionType.WRITE)).thenReturn(true);
        when(nodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }
}
