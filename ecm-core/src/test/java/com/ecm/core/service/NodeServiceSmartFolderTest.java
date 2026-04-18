package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
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

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeServiceSmartFolderTest {

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

    @Test
    @DisplayName("Document creation rejects smart folder parents")
    void createNodeRejectsSmartParent() {
        UUID parentId = UUID.randomUUID();
        Folder smartParent = folder(parentId, "smart-parent");
        smartParent.setSmart(true);

        Document document = new Document();
        document.setName("contract.pdf");

        when(folderRepository.findById(parentId)).thenReturn(Optional.of(smartParent));
        when(securityService.hasPermission(smartParent, PermissionType.CREATE_CHILDREN)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> nodeService.createNode(document, parentId)
        );

        assertEquals("Smart folders cannot contain physical child nodes: smart-parent", ex.getMessage());
    }

    @Test
    @DisplayName("Move rejects smart folder targets")
    void moveNodeRejectsSmartTarget() {
        UUID nodeId = UUID.randomUUID();
        UUID targetParentId = UUID.randomUUID();
        Document document = document(nodeId, "invoice.pdf");
        Folder smartParent = folder(targetParentId, "smart-parent");
        smartParent.setSmart(true);

        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nodeId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(document));
        when(folderRepository.findById(targetParentId)).thenReturn(Optional.of(smartParent));
        when(securityService.hasPermission(document, PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(document, PermissionType.DELETE)).thenReturn(true);
        when(securityService.hasPermission(smartParent, PermissionType.CREATE_CHILDREN)).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> nodeService.moveNode(nodeId, targetParentId)
        );

        assertEquals("Smart folders cannot contain physical child nodes: smart-parent", ex.getMessage());
    }

    private Folder folder(UUID id, String name) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setPath("/" + name);
        folder.setDeleted(false);
        folder.setArchiveStatus(Node.ArchiveStatus.LIVE);
        return folder;
    }

    private Document document(UUID id, String name) {
        Document document = new Document();
        document.setId(id);
        document.setName(name);
        document.setPath("/docs/" + name);
        document.setDeleted(false);
        document.setArchiveStatus(Node.ArchiveStatus.LIVE);
        return document;
    }
}
