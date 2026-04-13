package com.ecm.core.service;

import com.ecm.core.entity.ContentReference.OwnerType;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.entity.Version;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeServiceContentReferenceTest {

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
    @DisplayName("createNode attaches document content ownership when content exists")
    void createNodeAttachesDocumentOwnership() {
        UUID parentId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        Folder parent = folder(parentId, "workspace");
        Document document = document(documentId, "contract.pdf");
        document.setContentId("content-1");

        when(folderRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(securityService.hasPermission(parent, PermissionType.CREATE_CHILDREN)).thenReturn(true);
        when(nodeRepository.findByParentIdAndName(parentId, "contract.pdf")).thenReturn(Optional.empty());
        when(nodeRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document saved = invocation.getArgument(0);
            saved.setId(documentId);
            return saved;
        });
        when(securityService.getCurrentUser()).thenReturn("alice");

        nodeService.createNode(document, parentId);

        verify(contentReferenceService).attach("content-1", OwnerType.DOCUMENT, documentId);
    }

    @Test
    @DisplayName("deleteNode detaches document and version ownership on permanent delete")
    void deleteNodeDetachesOwnershipOnPermanentDelete() {
        UUID documentId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        Document document = document(documentId, "contract.pdf");
        document.setContentId("doc-content");

        Version version = new Version();
        version.setId(versionId);
        version.setContentId("version-content");
        version.setDocument(document);
        document.getVersions().add(version);

        when(nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(documentId, Node.ArchiveStatus.LIVE))
            .thenReturn(Optional.of(document));
        when(securityService.hasPermission(document, PermissionType.READ)).thenReturn(true);
        when(securityService.hasPermission(document, PermissionType.DELETE)).thenReturn(true);
        when(nodeRepository.findByParentIdAndDeletedFalse(documentId)).thenReturn(List.of());

        nodeService.deleteNode(documentId, true);

        verify(contentReferenceService).detach("doc-content", OwnerType.DOCUMENT, documentId);
        verify(contentReferenceService).detach("version-content", OwnerType.VERSION, versionId);
        verify(nodeRepository).delete(document);
    }

    private Folder folder(UUID id, String name) {
        Folder folder = new Folder();
        folder.setId(id);
        folder.setName(name);
        folder.setPath("/" + name);
        return folder;
    }

    private Document document(UUID id, String name) {
        Document document = new Document();
        document.setId(id);
        document.setName(name);
        document.setPath("/" + name);
        document.setMimeType("application/pdf");
        return document;
    }
}
