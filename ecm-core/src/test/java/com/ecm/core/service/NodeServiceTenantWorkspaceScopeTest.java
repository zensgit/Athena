package com.ecm.core.service;

import com.ecm.core.config.TenantContext;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.CorrespondentRepository;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.PermissionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeServiceTenantWorkspaceScopeTest {

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
            nodeRepository,
            folderRepository,
            documentRepository,
            permissionRepository,
            correspondentRepository,
            securityService,
            eventPublisher,
            contentReferenceService
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("createDocument without parent uses current tenant root workspace")
    void createDocumentWithoutParentUsesTenantRootWorkspace() {
        UUID rootId = UUID.randomUUID();
        TenantContext.setCurrentTenantRootNodeId(rootId);
        Folder root = folder(rootId, "Acme Workspace", "/Acme Workspace");

        when(folderRepository.findById(rootId)).thenReturn(Optional.of(root));
        when(securityService.hasPermission(root, PermissionType.CREATE_CHILDREN)).thenReturn(true);
        when(nodeRepository.findByParentIdAndName(rootId, "doc.txt")).thenReturn(Optional.empty());
        when(permissionRepository.findByNodeId(rootId)).thenReturn(List.of());
        when(nodeRepository.save(any(Node.class))).thenAnswer(invocation -> {
            Node saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
        when(securityService.getCurrentUser()).thenReturn("admin");

        Document created = nodeService.createDocument("doc.txt", "text/plain", 16L, null);

        assertEquals(rootId, created.getParent().getId());
        assertEquals("/Acme Workspace/doc.txt", created.getPath());
    }

    @Test
    @DisplayName("getNode rejects nodes outside current tenant workspace")
    void getNodeRejectsForeignWorkspaceNode() {
        UUID rootId = UUID.randomUUID();
        UUID foreignNodeId = UUID.randomUUID();
        TenantContext.setCurrentTenantRootNodeId(rootId);

        Folder root = folder(rootId, "Acme Workspace", "/Acme Workspace");
        Document foreign = document(foreignNodeId, "outside.txt", "/Other Workspace/outside.txt");

        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(foreignNodeId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(foreign));
        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(rootId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(root));

        assertThrows(NoSuchElementException.class, () -> nodeService.getNode(foreignNodeId));
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

    private static Document document(UUID id, String name, String path) {
        Document document = new Document();
        document.setId(id);
        document.setName(name);
        document.setPath(path);
        document.setDeleted(false);
        document.setArchiveStatus(Node.ArchiveStatus.LIVE);
        return document;
    }
}
