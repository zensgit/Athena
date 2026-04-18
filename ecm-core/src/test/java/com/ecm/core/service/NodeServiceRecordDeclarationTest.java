package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.exception.IllegalOperationException;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeServiceRecordDeclarationTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private CorrespondentRepository correspondentRepository;
    @Mock private SecurityService securityService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ContentReferenceService contentReferenceService;
    @Mock private RecordsManagementService recordsManagementService;

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
        ReflectionTestUtils.setField(nodeService, "recordsManagementService", recordsManagementService);
    }

    @Test
    @DisplayName("updateNode rejects declared document")
    void updateNodeRejectsDeclaredDocument() {
        UUID nodeId = UUID.randomUUID();
        Document document = document(nodeId, "/Sites/Finance/report.pdf");

        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nodeId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(document));
        when(securityService.hasPermission(document, PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(document, PermissionType.WRITE)).thenReturn(true);
        doThrow(new IllegalOperationException("record")).when(recordsManagementService)
            .assertDirectMutationAllowed(document, "update");

        assertThrows(IllegalOperationException.class, () -> nodeService.updateNode(nodeId, Map.of("description", "x")));

        verify(nodeRepository, never()).save(document);
    }

    @Test
    @DisplayName("moveNode rejects folder containing declared record")
    void moveNodeRejectsFolderContainingDeclaredRecord() {
        UUID nodeId = UUID.randomUUID();
        UUID targetParentId = UUID.randomUUID();

        Folder folder = folder(nodeId, "/Sites/Finance");
        Folder targetParent = folder(targetParentId, "/Sites/Archive");

        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(nodeId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(folder));
        when(folderRepository.findById(targetParentId)).thenReturn(Optional.of(targetParent));
        when(securityService.hasPermission(folder, PermissionType.READ)).thenReturn(true);
        doThrow(new IllegalOperationException("record")).when(recordsManagementService)
            .assertHierarchyMutationAllowed(folder, "move");

        assertThrows(IllegalOperationException.class, () -> nodeService.moveNode(nodeId, targetParentId));

        verify(nodeRepository, never()).save(folder);
    }

    private Document document(UUID id, String path) {
        Document document = new Document();
        document.setId(id);
        document.setName("report.pdf");
        document.setPath(path);
        document.setArchiveStatus(Node.ArchiveStatus.LIVE);
        document.setStatus(Node.NodeStatus.ACTIVE);
        return document;
    }

    private Folder folder(UUID id, String path) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(path.substring(path.lastIndexOf('/') + 1));
        folder.setPath(path);
        folder.setArchiveStatus(Node.ArchiveStatus.LIVE);
        folder.setStatus(Node.NodeStatus.ACTIVE);
        return folder;
    }
}
