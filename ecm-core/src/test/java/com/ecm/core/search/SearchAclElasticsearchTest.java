package com.ecm.core.search;

import com.ecm.core.entity.Document;
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
            .build();
        NodeDocument deniedDoc = NodeDocument.builder()
            .id(deniedId.toString())
            .name("denied doc")
            .deleted(false)
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
            .build();
        NodeDocument deniedDoc = NodeDocument.builder()
            .id(deniedId.toString())
            .name("denied doc")
            .mimeType("image/png")
            .createdBy("bob")
            .tags(Set.of("public"))
            .deleted(false)
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

    private void indexDocuments(NodeDocument... docs) {
        for (NodeDocument doc : docs) {
            elasticsearchOperations.save(doc);
        }
        elasticsearchOperations.indexOps(NodeDocument.class).refresh();
    }
}
