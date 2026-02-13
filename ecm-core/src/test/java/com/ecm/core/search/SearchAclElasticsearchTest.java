package com.ecm.core.search;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node.NodeType;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.service.SecurityService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.test.autoconfigure.data.elasticsearch.AutoConfigureDataElasticsearch;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = SearchAclElasticsearchTest.ElasticsearchTestConfig.class)
@AutoConfigureDataElasticsearch
@Import({FullTextSearchService.class, FacetedSearchService.class})
class SearchAclElasticsearchTest {

    @SpringBootConfiguration
    @AutoConfigurationPackage
    static class ElasticsearchTestConfig {
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        String elasticsearchUrl = System.getenv("ECM_ELASTICSEARCH_URL");
        if (elasticsearchUrl == null || elasticsearchUrl.isBlank()) {
            elasticsearchUrl = "http://localhost:9200";
        }
        String resolvedUrl = elasticsearchUrl;
        registry.add("spring.elasticsearch.uris", () -> resolvedUrl);
    }

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private FullTextSearchService fullTextSearchService;

    @Autowired
    private FacetedSearchService facetedSearchService;

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private NodeRepository nodeRepository;

    @MockBean
    private SecurityService securityService;

    @BeforeEach
    void setupIndex() {
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(NodeDocument.class);
            if (indexOps.exists()) {
                indexOps.delete();
            }
            indexOps.create();
            indexOps.putMapping();
            indexOps.refresh();

            Mockito.lenient().when(securityService.getCurrentUser()).thenReturn("alice");
            Mockito.lenient().when(securityService.getUserAuthorities("alice"))
                .thenReturn(Set.of("alice", "EVERYONE"));
        } catch (Exception ex) {
            Assumptions.assumeTrue(false, "Elasticsearch not available: " + ex.getMessage());
        }
    }

    @Test
    @DisplayName("Full-text search filters unauthorized hits using Elasticsearch")
    void fullTextSearchFiltersUnauthorizedHits() {
        UUID allowedId = UUID.randomUUID();
        UUID deniedId = UUID.randomUUID();

        NodeDocument allowedDoc = NodeDocument.builder()
            .id(allowedId.toString())
            .name("allowed doc")
            .deleted(false)
            .permissions(Set.of("alice"))
            .build();
        NodeDocument deniedDoc = NodeDocument.builder()
            .id(deniedId.toString())
            .name("denied doc")
            .deleted(false)
            .permissions(Set.of("bob"))
            .build();

        indexDocuments(allowedDoc, deniedDoc);

        Document allowedNode = new Document();
        allowedNode.setId(allowedId);
        Document deniedNode = new Document();
        deniedNode.setId(deniedId);

        Mockito.when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);
        Mockito.when(nodeRepository.findAllById(Mockito.<UUID>anyIterable()))
            .thenReturn(List.of(allowedNode, deniedNode));
        Mockito.when(securityService.hasPermission(allowedNode, PermissionType.READ)).thenReturn(true);
        Mockito.when(securityService.hasPermission(deniedNode, PermissionType.READ)).thenReturn(false);

        var results = fullTextSearchService.search("", 0, 10, null, null);

        assertEquals(1, results.getTotalElements());
        assertEquals(allowedId.toString(), results.getContent().get(0).getId());
    }

    @Test
    @DisplayName("Faceted search builds facets from authorized hits only")
    void facetedSearchBuildsFacetsFromAuthorizedHits() {
        UUID allowedId = UUID.randomUUID();
        UUID deniedId = UUID.randomUUID();

        NodeDocument allowedDoc = NodeDocument.builder()
            .id(allowedId.toString())
            .name("allowed doc")
            .mimeType("application/pdf")
            .createdBy("alice")
            .tags(Set.of("confidential"))
            .deleted(false)
            .permissions(Set.of("alice"))
            .build();
        NodeDocument deniedDoc = NodeDocument.builder()
            .id(deniedId.toString())
            .name("denied doc")
            .mimeType("image/png")
            .createdBy("bob")
            .tags(Set.of("public"))
            .deleted(false)
            .permissions(Set.of("bob"))
            .build();

        indexDocuments(allowedDoc, deniedDoc);

        Document allowedNode = new Document();
        allowedNode.setId(allowedId);
        Document deniedNode = new Document();
        deniedNode.setId(deniedId);

        Mockito.when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);
        Mockito.when(nodeRepository.findAllById(Mockito.<UUID>anyIterable()))
            .thenReturn(List.of(allowedNode, deniedNode));
        Mockito.when(securityService.hasPermission(allowedNode, PermissionType.READ)).thenReturn(true);
        Mockito.when(securityService.hasPermission(deniedNode, PermissionType.READ)).thenReturn(false);

        FacetedSearchService.FacetedSearchRequest request = new FacetedSearchService.FacetedSearchRequest();
        FacetedSearchService.FacetedSearchResponse response = facetedSearchService.search(request);

        assertEquals(1, response.getTotalHits());
        assertEquals(1, response.getResults().getTotalElements());
        assertEquals(allowedId.toString(), response.getResults().getContent().get(0).getId());

        Map<String, List<FacetedSearchService.FacetValue>> facets = response.getFacets();
        assertNotNull(facets.get("mimeType"));
        assertEquals("application/pdf", facets.get("mimeType").get(0).getValue());
        assertEquals(1, facets.get("mimeType").get(0).getCount());
        assertEquals("alice", facets.get("createdBy").get(0).getValue());
        assertEquals("confidential", facets.get("tags").get(0).getValue());
    }

    @Test
    @DisplayName("Full-text search paginates sorted results and excludes deleted documents")
    void fullTextSearchPaginatesAndExcludesDeletedDocuments() {
        Mockito.when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);

        List<NodeDocument> docs = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            String name = String.format("doc-%03d", i);
            docs.add(NodeDocument.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .nameSort(name)
                .deleted(false)
                .build());
        }
        docs.add(NodeDocument.builder()
            .id(UUID.randomUUID().toString())
            .name("zzz-deleted-001")
            .nameSort("zzz-deleted-001")
            .deleted(true)
            .build());
        docs.add(NodeDocument.builder()
            .id(UUID.randomUUID().toString())
            .name("zzz-deleted-002")
            .nameSort("zzz-deleted-002")
            .deleted(true)
            .build());

        indexDocuments(docs.toArray(new NodeDocument[0]));

        var results = fullTextSearchService.search("", 1, 10, "name", "asc");

        assertEquals(10, results.getContent().size());
        assertEquals("doc-011", results.getContent().get(0).getName());
        assertEquals("doc-020", results.getContent().get(9).getName());
        boolean hasDeleted = results.getContent().stream()
            .anyMatch(result -> result.getName().startsWith("zzz-deleted"));
        assertEquals(false, hasDeleted);
    }

    @Test
    @DisplayName("Full-text search can filter by preview status (including UNSUPPORTED and synthetic PENDING)")
    void fullTextSearchFiltersByPreviewStatus() {
        Mockito.when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);

        UUID pendingId = UUID.randomUUID();
        UUID failedId = UUID.randomUUID();
        UUID failedOctetId = UUID.randomUUID();
        UUID unsupportedId = UUID.randomUUID();

        NodeDocument pendingDoc = NodeDocument.builder()
            .id(pendingId.toString())
            .name("doc-pending")
            .nameSort("doc-pending")
            .nodeType(NodeType.DOCUMENT)
            .mimeType("text/plain")
            .deleted(false)
            .build();

        NodeDocument failedDoc = NodeDocument.builder()
            .id(failedId.toString())
            .name("doc-failed")
            .nameSort("doc-failed")
            .nodeType(NodeType.DOCUMENT)
            .mimeType("application/pdf")
            .previewStatus("FAILED")
            .previewFailureReason("timeout")
            .deleted(false)
            .build();

        // Simulate a stale/legacy unsupported failure that is still stored as FAILED in the index.
        NodeDocument failedOctetDoc = NodeDocument.builder()
            .id(failedOctetId.toString())
            .name("doc-failed-octet")
            .nameSort("doc-failed-octet")
            .nodeType(NodeType.DOCUMENT)
            .mimeType("application/octet-stream")
            .previewStatus("FAILED")
            .previewFailureReason("Preview not supported for mime type: application/octet-stream")
            .deleted(false)
            .build();

        NodeDocument unsupportedDoc = NodeDocument.builder()
            .id(unsupportedId.toString())
            .name("doc-unsupported")
            .nameSort("doc-unsupported")
            .nodeType(NodeType.DOCUMENT)
            .mimeType("application/octet-stream")
            .previewStatus("UNSUPPORTED")
            .previewFailureReason("Preview not supported for mime type: application/octet-stream")
            .deleted(false)
            .build();

        indexDocuments(pendingDoc, failedDoc, failedOctetDoc, unsupportedDoc);

        var pendingResults = fullTextSearchService.search(
            "",
            0,
            10,
            null,
            null,
            null,
            true,
            List.of("PENDING")
        );
        assertEquals(1, pendingResults.getTotalElements());
        assertEquals(pendingId.toString(), pendingResults.getContent().get(0).getId());

        var failedResults = fullTextSearchService.search(
            "",
            0,
            10,
            null,
            null,
            null,
            true,
            List.of("FAILED")
        );
        assertEquals(1, failedResults.getTotalElements());
        assertEquals(failedId.toString(), failedResults.getContent().get(0).getId());

        var unsupportedResults = fullTextSearchService.search(
            "",
            0,
            10,
            null,
            null,
            null,
            true,
            List.of("UNSUPPORTED")
        );
        assertEquals(2, unsupportedResults.getTotalElements());
    }

    @Test
    @DisplayName("Faceted search supports preview status filtering")
    void facetedSearchFiltersByPreviewStatus() {
        Mockito.when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);

        UUID readyId = UUID.randomUUID();
        UUID unsupportedId = UUID.randomUUID();

        NodeDocument readyDoc = NodeDocument.builder()
            .id(readyId.toString())
            .name("doc-ready")
            .nameSort("doc-ready")
            .nodeType(NodeType.DOCUMENT)
            .mimeType("application/pdf")
            .previewStatus("READY")
            .deleted(false)
            .build();

        NodeDocument unsupportedDoc = NodeDocument.builder()
            .id(unsupportedId.toString())
            .name("doc-unsupported")
            .nameSort("doc-unsupported")
            .nodeType(NodeType.DOCUMENT)
            .mimeType("application/octet-stream")
            .previewStatus("UNSUPPORTED")
            .previewFailureReason("Preview not supported for mime type: application/octet-stream")
            .deleted(false)
            .build();

        indexDocuments(readyDoc, unsupportedDoc);

        SearchFilters filters = new SearchFilters();
        filters.setPreviewStatuses(List.of("UNSUPPORTED"));

        FacetedSearchService.FacetedSearchRequest request = new FacetedSearchService.FacetedSearchRequest();
        request.setFilters(filters);

        FacetedSearchService.FacetedSearchResponse response = facetedSearchService.search(request);

        assertEquals(1, response.getTotalHits());
        assertEquals(1, response.getResults().getTotalElements());
        assertEquals(unsupportedId.toString(), response.getResults().getContent().get(0).getId());
    }

    private void indexDocuments(NodeDocument... docs) {
        for (NodeDocument doc : docs) {
            if (doc.getPermissions() == null || doc.getPermissions().isEmpty()) {
                doc.setPermissions(Set.of("alice"));
            }
            elasticsearchOperations.save(doc);
        }
        elasticsearchOperations.indexOps(NodeDocument.class).refresh();
    }
}
