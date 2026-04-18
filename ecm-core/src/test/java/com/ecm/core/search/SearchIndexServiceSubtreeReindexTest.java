package com.ecm.core.search;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchIndexServiceSubtreeReindexTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private SecurityService securityService;
    @Mock private ElasticsearchOperations elasticsearchOperations;

    private SearchIndexService searchIndexService;

    @BeforeEach
    void setUp() {
        searchIndexService = new SearchIndexService(
            documentRepository,
            nodeRepository,
            securityService,
            elasticsearchOperations
        );
    }

    @Test
    @DisplayName("reindexNodeSubtree reloads descendants from database using refreshed paths")
    void reindexNodeSubtreeReloadsDescendantsFromDatabase() {
        UUID parentId = UUID.randomUUID();
        UUID childFolderId = UUID.randomUUID();
        UUID childDocumentId = UUID.randomUUID();

        Folder parent = folder(parentId, "Projects", "/Sites/Target/Projects");
        Folder childFolder = folder(childFolderId, "Q1", "/Sites/Target/Projects/Q1");
        childFolder.setParent(parent);
        Document childDocument = document(childDocumentId, "report.pdf", "/Sites/Target/Projects/Q1/report.pdf");
        childDocument.setParent(childFolder);

        when(nodeRepository.findByPathPrefix("/Sites/Target/Projects/"))
            .thenReturn(List.of(childFolder, childDocument));
        when(nodeRepository.findById(childFolderId)).thenReturn(java.util.Optional.of(childFolder));
        when(nodeRepository.findById(childDocumentId)).thenReturn(java.util.Optional.of(childDocument));
        when(securityService.resolveReadAuthorities(childFolder)).thenReturn(Set.of("EVERYONE"));
        when(securityService.resolveReadAuthorities(childDocument)).thenReturn(Set.of("EVERYONE", "alice"));

        searchIndexService.reindexNodeSubtree(parent);

        ArgumentCaptor<NodeDocument> documentCaptor = ArgumentCaptor.forClass(NodeDocument.class);
        verify(elasticsearchOperations, times(2)).save(documentCaptor.capture(), any(IndexCoordinates.class));

        List<NodeDocument> saved = documentCaptor.getAllValues();
        assertEquals("/Sites/Target/Projects/Q1", saved.get(0).getPath());
        assertEquals("/Sites/Target/Projects/Q1/report.pdf", saved.get(1).getPath());
        assertEquals(Set.of("EVERYONE"), saved.get(0).getPermissions());
        assertEquals(Set.of("EVERYONE", "alice"), saved.get(1).getPermissions());
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
