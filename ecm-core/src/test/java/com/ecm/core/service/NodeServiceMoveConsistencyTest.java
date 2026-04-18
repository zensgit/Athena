package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.event.NodeMovedEvent;
import com.ecm.core.repository.CorrespondentRepository;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.FolderRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.repository.PermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeServiceMoveConsistencyTest {

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
    @DisplayName("moveNode refreshes descendant paths before publishing move event")
    void moveNodeRefreshesDescendantPaths() {
        UUID currentParentId = UUID.randomUUID();
        UUID targetParentId = UUID.randomUUID();
        UUID movedFolderId = UUID.randomUUID();
        UUID childFolderId = UUID.randomUUID();
        UUID childDocumentId = UUID.randomUUID();

        Folder currentParent = folder(currentParentId, "Source", "/Sites/Source");
        Folder targetParent = folder(targetParentId, "Target", "/Sites/Target");
        Folder movedFolder = folder(movedFolderId, "Projects", "/Sites/Source/Projects");
        movedFolder.setParent(currentParent);

        Folder childFolder = folder(childFolderId, "Q1", "/Sites/Source/Projects/Q1");
        childFolder.setParent(movedFolder);

        Document childDocument = document(childDocumentId, "report.pdf", "/Sites/Source/Projects/Q1/report.pdf");
        childDocument.setParent(childFolder);

        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(movedFolderId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(movedFolder));
        when(folderRepository.findById(targetParentId)).thenReturn(Optional.of(targetParent));
        when(securityService.hasPermission(movedFolder, PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(movedFolder, PermissionType.DELETE)).thenReturn(true);
        when(securityService.hasPermission(targetParent, PermissionType.CREATE_CHILDREN)).thenReturn(true);
        when(nodeRepository.findByParentIdAndName(targetParentId, "Projects")).thenReturn(Optional.empty());
        when(nodeRepository.findByParentIdAndDeletedFalse(movedFolderId)).thenReturn(List.of(childFolder));
        when(nodeRepository.findByParentIdAndDeletedFalse(childFolderId)).thenReturn(List.of(childDocument));
        when(nodeRepository.save(any(Node.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(securityService.getCurrentUser()).thenReturn("alice");

        Node moved = nodeService.moveNode(movedFolderId, targetParentId);

        assertEquals("/Sites/Target/Projects", moved.getPath());
        assertEquals("/Sites/Target/Projects/Q1", childFolder.getPath());
        assertEquals("/Sites/Target/Projects/Q1/report.pdf", childDocument.getPath());

        ArgumentCaptor<NodeMovedEvent> eventCaptor = ArgumentCaptor.forClass(NodeMovedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals("/Sites/Target/Projects", eventCaptor.getValue().getNode().getPath());
    }

    private Folder folder(UUID id, String name, String path) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setPath(path);
        folder.setArchiveStatus(Node.ArchiveStatus.LIVE);
        folder.setStatus(Node.NodeStatus.ACTIVE);
        return folder;
    }

    private Document document(UUID id, String name, String path) {
        Document document = new Document();
        document.setId(id);
        document.setName(name);
        document.setPath(path);
        document.setMimeType("application/pdf");
        document.setArchiveStatus(Node.ArchiveStatus.LIVE);
        document.setStatus(Node.NodeStatus.ACTIVE);
        return document;
    }
}
