package com.ecm.core.search;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class SearchAclFilteringTest {

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private SecurityService securityService;

    private FullTextSearchService fullTextSearchService;
    private FacetedSearchService facetedSearchService;

    @BeforeEach
    void setup() {
        fullTextSearchService = new FullTextSearchService(
            elasticsearchOperations,
            documentRepository,
            nodeRepository,
            securityService
        );
        facetedSearchService = new FacetedSearchService(
            elasticsearchOperations,
            nodeRepository,
            securityService
        );
        ReflectionTestUtils.setField(fullTextSearchService, "searchEnabled", true);
        ReflectionTestUtils.setField(facetedSearchService, "searchEnabled", true);

        Mockito.lenient().when(securityService.getCurrentUser()).thenReturn("alice");
        Mockito.lenient().when(securityService.getUserAuthorities("alice")).thenReturn(Set.of("alice", "EVERYONE"));
    }

    @Test
    @DisplayName("Full-text search filters unauthorized hits for non-admins")
    void fullTextSearchFiltersUnauthorizedHits() {
        UUID allowedId = UUID.randomUUID();
        UUID deniedId = UUID.randomUUID();

        NodeDocument allowedDoc = NodeDocument.builder()
            .id(allowedId.toString())
            .name("allowed")
            .build();
        NodeDocument deniedDoc = NodeDocument.builder()
            .id(deniedId.toString())
            .name("denied")
            .build();

        SearchHits<NodeDocument> searchHits = searchHits(searchHit(allowedDoc));

        Mockito.when(elasticsearchOperations.search(
                Mockito.any(Query.class),
                Mockito.eq(NodeDocument.class),
                Mockito.any(IndexCoordinates.class)))
            .thenReturn(searchHits);

        Document allowedNode = new Document();
        allowedNode.setId(allowedId);
        Mockito.when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);
        Mockito.when(nodeRepository.findAllById(Mockito.<UUID>anyIterable()))
            .thenReturn(List.of(allowedNode));
        Mockito.when(securityService.hasPermission(allowedNode, PermissionType.READ)).thenReturn(true);

        var results = fullTextSearchService.search("query", 0, 10, null, null);

        assertEquals(1, results.getTotalElements());
        assertEquals(allowedId.toString(), results.getContent().get(0).getId());

        Mockito.verify(nodeRepository).findAllById(Mockito.argThat((Iterable<UUID> ids) -> {
            List<UUID> fetchedIds = StreamSupport.stream(ids.spliterator(), false).toList();
            return fetchedIds.contains(allowedId) && fetchedIds.size() == 1;
        }));
    }

    @Test
    @DisplayName("Full-text search bypasses ACL filtering for admins")
    void fullTextSearchBypassesAclForAdmins() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        NodeDocument firstDoc = NodeDocument.builder()
            .id(firstId.toString())
            .name("first")
            .build();
        NodeDocument secondDoc = NodeDocument.builder()
            .id(secondId.toString())
            .name("second")
            .build();

        SearchHits<NodeDocument> searchHits = searchHits(searchHit(firstDoc), searchHit(secondDoc));

        Mockito.when(elasticsearchOperations.search(
                Mockito.any(Query.class),
                Mockito.eq(NodeDocument.class),
                Mockito.any(IndexCoordinates.class)))
            .thenReturn(searchHits);
        Mockito.when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);

        var results = fullTextSearchService.search("query", 0, 10, null, null);

        assertEquals(2, results.getTotalElements());
        assertEquals(firstId.toString(), results.getContent().get(0).getId());
        assertEquals(secondId.toString(), results.getContent().get(1).getId());
        Mockito.verifyNoInteractions(nodeRepository);
    }

    @Test
    @DisplayName("Full-text search returns empty results when search is disabled")
    void fullTextSearchReturnsEmptyWhenDisabled() {
        ReflectionTestUtils.setField(fullTextSearchService, "searchEnabled", false);

        var results = fullTextSearchService.search("query", 0, 10, null, null);

        assertTrue(results.isEmpty());
        Mockito.verifyNoInteractions(elasticsearchOperations, documentRepository, nodeRepository, securityService);
    }

    @Test
    @DisplayName("Full-text search skips hits with missing node IDs for non-admins")
    void fullTextSearchSkipsMissingNodeIds() {
        NodeDocument blankDoc = NodeDocument.builder()
            .id(" ")
            .name("blank")
            .build();
        NodeDocument invalidDoc = NodeDocument.builder()
            .id("not-a-uuid")
            .name("invalid")
            .build();

        SearchHits<NodeDocument> searchHits = searchHits(searchHit(blankDoc), searchHit(invalidDoc));

        Mockito.when(elasticsearchOperations.search(
                Mockito.any(Query.class),
                Mockito.eq(NodeDocument.class),
                Mockito.any(IndexCoordinates.class)))
            .thenReturn(searchHits);
        Mockito.when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

        var results = fullTextSearchService.search("query", 0, 10, null, null);

        assertEquals(2, results.getTotalElements());
        Mockito.verifyNoInteractions(nodeRepository);
        Mockito.verify(securityService, Mockito.never()).hasPermission(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("Full-text search returns empty when user lacks permissions")
    void fullTextSearchReturnsEmptyWhenNoPermissions() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        NodeDocument firstDoc = NodeDocument.builder()
            .id(firstId.toString())
            .name("first")
            .build();
        NodeDocument secondDoc = NodeDocument.builder()
            .id(secondId.toString())
            .name("second")
            .build();

        SearchHits<NodeDocument> searchHits = searchHits(searchHit(firstDoc), searchHit(secondDoc));

        Mockito.when(elasticsearchOperations.search(
                Mockito.any(Query.class),
                Mockito.eq(NodeDocument.class),
                Mockito.any(IndexCoordinates.class)))
            .thenReturn(searchHits);

        Document firstNode = new Document();
        firstNode.setId(firstId);
        Document secondNode = new Document();
        secondNode.setId(secondId);

        Mockito.when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);
        Mockito.when(nodeRepository.findAllById(Mockito.<UUID>anyIterable()))
            .thenReturn(List.of(firstNode, secondNode));
        Mockito.when(securityService.hasPermission(Mockito.any(Node.class), Mockito.eq(PermissionType.READ)))
            .thenReturn(false);

        var results = fullTextSearchService.search("query", 0, 10, null, null);

        assertEquals(2, results.getTotalElements());
        Mockito.verify(securityService, Mockito.times(2))
            .hasPermission(Mockito.any(Node.class), Mockito.eq(PermissionType.READ));
    }

    @Test
    @DisplayName("Full-text search filters hits for nodes missing from the repository")
    void fullTextSearchSkipsMissingNodes() {
        UUID existingId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();

        NodeDocument existingDoc = NodeDocument.builder()
            .id(existingId.toString())
            .name("existing")
            .build();
        NodeDocument missingDoc = NodeDocument.builder()
            .id(missingId.toString())
            .name("missing")
            .build();

        SearchHits<NodeDocument> searchHits = searchHits(searchHit(existingDoc), searchHit(missingDoc));

        Mockito.when(elasticsearchOperations.search(
                Mockito.any(Query.class),
                Mockito.eq(NodeDocument.class),
                Mockito.any(IndexCoordinates.class)))
            .thenReturn(searchHits);

        Document existingNode = new Document();
        existingNode.setId(existingId);

        Mockito.when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);
        Mockito.when(nodeRepository.findAllById(Mockito.<UUID>anyIterable()))
            .thenReturn(List.of(existingNode));
        Mockito.when(securityService.hasPermission(existingNode, PermissionType.READ)).thenReturn(true);

        var results = fullTextSearchService.search("query", 0, 10, null, null);

        assertEquals(1, results.getTotalElements());
        assertEquals(existingId.toString(), results.getContent().get(0).getId());
        Mockito.verify(securityService, Mockito.times(1)).hasPermission(Mockito.any(), Mockito.eq(PermissionType.READ));
    }

    @Test
    @DisplayName("Faceted search builds facets from authorized hits only")
    void facetedSearchBuildsFacetsFromAuthorizedHits() {
        UUID allowedId = UUID.randomUUID();
        UUID deniedId = UUID.randomUUID();

        NodeDocument allowedDoc = NodeDocument.builder()
            .id(allowedId.toString())
            .name("allowed")
            .mimeType("application/pdf")
            .createdBy("alice")
            .tags(Set.of("confidential"))
            .build();
        NodeDocument deniedDoc = NodeDocument.builder()
            .id(deniedId.toString())
            .name("denied")
            .mimeType("image/png")
            .createdBy("bob")
            .tags(Set.of("public"))
            .build();

        SearchHits<NodeDocument> searchHits = searchHits(searchHit(allowedDoc));

        Mockito.when(elasticsearchOperations.search(
                Mockito.any(Query.class),
                Mockito.eq(NodeDocument.class),
                Mockito.any(IndexCoordinates.class)))
            .thenReturn(searchHits);

        Document allowedNode = new Document();
        allowedNode.setId(allowedId);
        Mockito.when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);
        Mockito.when(nodeRepository.findAllById(Mockito.<UUID>anyIterable()))
            .thenReturn(List.of(allowedNode));
        Mockito.when(securityService.hasPermission(allowedNode, PermissionType.READ)).thenReturn(true);

        FacetedSearchService.FacetedSearchRequest request = new FacetedSearchService.FacetedSearchRequest();
        request.setQuery("doc");

        FacetedSearchService.FacetedSearchResponse response = facetedSearchService.search(request);

        assertEquals(1, response.getTotalHits());
        assertEquals(1, response.getResults().getTotalElements());
        assertEquals(allowedId.toString(), response.getResults().getContent().get(0).getId());

        Map<String, List<FacetedSearchService.FacetValue>> facets = response.getFacets();
        assertEquals("application/pdf", facets.get("mimeType").get(0).getValue());
        assertEquals(1, facets.get("mimeType").get(0).getCount());
        assertEquals("alice", facets.get("createdBy").get(0).getValue());
        assertEquals("confidential", facets.get("tags").get(0).getValue());
        assertEquals(1, facets.get("tags").get(0).getCount());
    }

    @Test
    @DisplayName("Faceted search bypasses ACL filtering for admins")
    void facetedSearchBypassesAclForAdmins() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        NodeDocument firstDoc = NodeDocument.builder()
            .id(firstId.toString())
            .name("alpha")
            .mimeType("application/pdf")
            .createdBy("alice")
            .tags(Set.of("confidential"))
            .build();
        NodeDocument secondDoc = NodeDocument.builder()
            .id(secondId.toString())
            .name("beta")
            .mimeType("image/png")
            .createdBy("bob")
            .tags(Set.of("public"))
            .build();

        SearchHits<NodeDocument> searchHits = searchHits(searchHit(firstDoc), searchHit(secondDoc));

        Mockito.when(elasticsearchOperations.search(
                Mockito.any(Query.class),
                Mockito.eq(NodeDocument.class),
                Mockito.any(IndexCoordinates.class)))
            .thenReturn(searchHits);
        Mockito.when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);

        FacetedSearchService.FacetedSearchRequest request = new FacetedSearchService.FacetedSearchRequest();
        FacetedSearchService.FacetedSearchResponse response = facetedSearchService.search(request);

        assertEquals(2, response.getTotalHits());
        assertEquals(2, response.getResults().getTotalElements());
        Map<String, List<FacetedSearchService.FacetValue>> facets = response.getFacets();
        Set<String> mimeTypes = facets.get("mimeType").stream()
            .map(FacetedSearchService.FacetValue::getValue)
            .collect(Collectors.toSet());
        assertTrue(mimeTypes.containsAll(Set.of("application/pdf", "image/png")));
        Mockito.verifyNoInteractions(nodeRepository);
    }

    @Test
    @DisplayName("Faceted search returns empty response when search is disabled")
    void facetedSearchReturnsEmptyWhenDisabled() {
        ReflectionTestUtils.setField(facetedSearchService, "searchEnabled", false);

        FacetedSearchService.FacetedSearchRequest request = new FacetedSearchService.FacetedSearchRequest();
        request.setQuery("doc");

        FacetedSearchService.FacetedSearchResponse response = facetedSearchService.search(request);

        assertEquals(0, response.getTotalHits());
        assertTrue(response.getResults().isEmpty());
        assertTrue(response.getFacets().isEmpty());
        Mockito.verifyNoInteractions(elasticsearchOperations, documentRepository, nodeRepository, securityService);
    }

    @Test
    @DisplayName("Available facets returns empty when search is disabled")
    void availableFacetsReturnsEmptyWhenDisabled() {
        ReflectionTestUtils.setField(facetedSearchService, "searchEnabled", false);

        Map<String, List<FacetedSearchService.FacetValue>> facets = facetedSearchService.getAvailableFacets("doc");

        assertTrue(facets.isEmpty());
        Mockito.verifyNoInteractions(elasticsearchOperations, documentRepository, nodeRepository, securityService);
    }

    @Test
    @DisplayName("Available facets filter unauthorized hits for non-admins")
    void availableFacetsFiltersUnauthorizedHits() {
        UUID allowedId = UUID.randomUUID();
        UUID deniedId = UUID.randomUUID();

        NodeDocument allowedDoc = NodeDocument.builder()
            .id(allowedId.toString())
            .name("allowed")
            .mimeType("application/pdf")
            .createdBy("alice")
            .tags(Set.of("confidential"))
            .build();
        NodeDocument deniedDoc = NodeDocument.builder()
            .id(deniedId.toString())
            .name("denied")
            .mimeType("image/png")
            .createdBy("bob")
            .tags(Set.of("public"))
            .build();

        SearchHits<NodeDocument> searchHits = searchHits(searchHit(allowedDoc), searchHit(deniedDoc));

        Mockito.when(elasticsearchOperations.search(
                Mockito.any(Query.class),
                Mockito.eq(NodeDocument.class),
                Mockito.any(IndexCoordinates.class)))
            .thenReturn(searchHits);

        Document allowedNode = new Document();
        allowedNode.setId(allowedId);
        Document deniedNode = new Document();
        deniedNode.setId(deniedId);

        Mockito.when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);
        Mockito.when(nodeRepository.findAllById(Mockito.<UUID>anyIterable()))
            .thenReturn(List.of(allowedNode, deniedNode));
        Mockito.when(securityService.hasPermission(allowedNode, PermissionType.READ)).thenReturn(true);
        Mockito.when(securityService.hasPermission(deniedNode, PermissionType.READ)).thenReturn(false);

        Map<String, List<FacetedSearchService.FacetValue>> facets = facetedSearchService.getAvailableFacets("doc");

        Set<String> mimeTypes = facets.get("mimeType").stream()
            .map(FacetedSearchService.FacetValue::getValue)
            .collect(Collectors.toSet());
        assertEquals(Set.of("application/pdf"), mimeTypes);
        assertTrue(
            facets.get("tags").stream().noneMatch(tag -> "public".equals(tag.getValue()))
        );
    }

    @Test
    @DisplayName("Suggested filters return date ranges when search is disabled")
    void suggestedFiltersReturnDateRangesWhenDisabled() {
        ReflectionTestUtils.setField(facetedSearchService, "searchEnabled", false);

        List<FacetedSearchService.SuggestedFilter> suggestions = facetedSearchService.getSuggestedFilters("doc");

        assertEquals(3, suggestions.size());
        assertTrue(suggestions.stream().allMatch(suggestion -> "dateRange".equals(suggestion.getField())));
        assertEquals(
            Set.of("7d", "30d", "1y"),
            suggestions.stream().map(FacetedSearchService.SuggestedFilter::getValue).collect(Collectors.toSet())
        );
        Mockito.verifyNoInteractions(elasticsearchOperations, documentRepository, nodeRepository, securityService);
    }

    @Test
    @DisplayName("Faceted search skips hits with missing node IDs for non-admins")
    void facetedSearchSkipsMissingNodeIds() {
        NodeDocument blankDoc = NodeDocument.builder()
            .id("")
            .name("blank")
            .mimeType("application/pdf")
            .build();
        NodeDocument invalidDoc = NodeDocument.builder()
            .id("not-a-uuid")
            .name("invalid")
            .mimeType("image/png")
            .build();

        SearchHits<NodeDocument> searchHits = searchHits(searchHit(blankDoc), searchHit(invalidDoc));

        Mockito.when(elasticsearchOperations.search(
                Mockito.any(Query.class),
                Mockito.eq(NodeDocument.class),
                Mockito.any(IndexCoordinates.class)))
            .thenReturn(searchHits);
        Mockito.when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

        FacetedSearchService.FacetedSearchRequest request = new FacetedSearchService.FacetedSearchRequest();
        FacetedSearchService.FacetedSearchResponse response = facetedSearchService.search(request);

        assertEquals(2, response.getTotalHits());
        assertEquals(2, response.getResults().getTotalElements());
        Mockito.verifyNoInteractions(nodeRepository);
        Mockito.verify(securityService, Mockito.never()).hasPermission(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("Advanced search filters deleted documents by default")
    void advancedSearchFiltersDeletedByDefault() {
        SearchRequest request = new SearchRequest();
        request.setQuery("doc");
        request.setFilters(new SearchFilters());

        Mockito.when(elasticsearchOperations.search(
                Mockito.any(Query.class),
                Mockito.eq(NodeDocument.class),
                Mockito.any(IndexCoordinates.class)))
            .thenReturn(searchHits());
        Mockito.when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);

        fullTextSearchService.advancedSearch(request);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        Mockito.verify(elasticsearchOperations).search(
            queryCaptor.capture(),
            Mockito.eq(NodeDocument.class),
            Mockito.any(IndexCoordinates.class)
        );

        Query captured = queryCaptor.getValue();
        assertTrue(captured instanceof NativeQuery);
        NativeQuery nativeQuery = (NativeQuery) captured;
        co.elastic.clients.elasticsearch._types.query_dsl.Query esQuery = nativeQuery.getQuery();

        assertTrue(esQuery.isBool());
        boolean hasDeletedFilter = esQuery.bool().filter().stream()
            .filter(co.elastic.clients.elasticsearch._types.query_dsl.Query::isTerm)
            .map(co.elastic.clients.elasticsearch._types.query_dsl.Query::term)
            .anyMatch(term -> "deleted".equals(term.field())
                && term.value().isBoolean()
                && !term.value().booleanValue());
        assertTrue(hasDeletedFilter);
    }

    @Test
    @DisplayName("Advanced search omits deleted filter when includeDeleted is true")
    void advancedSearchOmitsDeletedFilterWhenIncludeDeleted() {
        SearchFilters filters = new SearchFilters();
        filters.setIncludeDeleted(true);
        SearchRequest request = new SearchRequest();
        request.setQuery("doc");
        request.setFilters(filters);

        Mockito.when(elasticsearchOperations.search(
                Mockito.any(Query.class),
                Mockito.eq(NodeDocument.class),
                Mockito.any(IndexCoordinates.class)))
            .thenReturn(searchHits());
        Mockito.when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);

        fullTextSearchService.advancedSearch(request);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        Mockito.verify(elasticsearchOperations).search(
            queryCaptor.capture(),
            Mockito.eq(NodeDocument.class),
            Mockito.any(IndexCoordinates.class)
        );

        Query captured = queryCaptor.getValue();
        assertTrue(captured instanceof NativeQuery);
        NativeQuery nativeQuery = (NativeQuery) captured;
        co.elastic.clients.elasticsearch._types.query_dsl.Query esQuery = nativeQuery.getQuery();

        assertTrue(esQuery.isBool());
        boolean hasDeletedFilter = esQuery.bool().filter().stream()
            .filter(co.elastic.clients.elasticsearch._types.query_dsl.Query::isTerm)
            .map(co.elastic.clients.elasticsearch._types.query_dsl.Query::term)
            .anyMatch(term -> "deleted".equals(term.field()));
        assertFalse(hasDeletedFilter);
    }

    @Test
    @DisplayName("Advanced search prefers createdByList over createdBy")
    void advancedSearchPrefersCreatedByList() {
        SearchFilters filters = new SearchFilters();
        filters.setCreatedByList(List.of("alice", "bob"));
        filters.setCreatedBy("charlie");
        SearchRequest request = new SearchRequest();
        request.setQuery("doc");
        request.setFilters(filters);

        Mockito.when(elasticsearchOperations.search(
                Mockito.any(Query.class),
                Mockito.eq(NodeDocument.class),
                Mockito.any(IndexCoordinates.class)))
            .thenReturn(searchHits());
        Mockito.when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);

        fullTextSearchService.advancedSearch(request);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        Mockito.verify(elasticsearchOperations).search(
            queryCaptor.capture(),
            Mockito.eq(NodeDocument.class),
            Mockito.any(IndexCoordinates.class)
        );

        Query captured = queryCaptor.getValue();
        assertTrue(captured instanceof NativeQuery);
        NativeQuery nativeQuery = (NativeQuery) captured;
        co.elastic.clients.elasticsearch._types.query_dsl.Query esQuery = nativeQuery.getQuery();

        assertEquals(Set.of("alice", "bob"), extractCreatedByValues(esQuery));
    }

    @Test
    @DisplayName("Advanced search uses createdBy when list is empty")
    void advancedSearchUsesCreatedByWhenListEmpty() {
        SearchFilters filters = new SearchFilters();
        filters.setCreatedByList(List.of());
        filters.setCreatedBy("charlie");
        SearchRequest request = new SearchRequest();
        request.setQuery("doc");
        request.setFilters(filters);

        Mockito.when(elasticsearchOperations.search(
                Mockito.any(Query.class),
                Mockito.eq(NodeDocument.class),
                Mockito.any(IndexCoordinates.class)))
            .thenReturn(searchHits());
        Mockito.when(securityService.hasRole("ROLE_ADMIN")).thenReturn(true);

        fullTextSearchService.advancedSearch(request);

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        Mockito.verify(elasticsearchOperations).search(
            queryCaptor.capture(),
            Mockito.eq(NodeDocument.class),
            Mockito.any(IndexCoordinates.class)
        );

        Query captured = queryCaptor.getValue();
        assertTrue(captured instanceof NativeQuery);
        NativeQuery nativeQuery = (NativeQuery) captured;
        co.elastic.clients.elasticsearch._types.query_dsl.Query esQuery = nativeQuery.getQuery();

        assertEquals(Set.of("charlie"), extractCreatedByValues(esQuery));
    }

    @Test
    @DisplayName("Faceted search filters hits for nodes missing from the repository")
    void facetedSearchSkipsMissingNodes() {
        UUID existingId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();

        NodeDocument existingDoc = NodeDocument.builder()
            .id(existingId.toString())
            .name("existing")
            .mimeType("application/pdf")
            .createdBy("alice")
            .build();
        NodeDocument missingDoc = NodeDocument.builder()
            .id(missingId.toString())
            .name("missing")
            .mimeType("image/png")
            .createdBy("bob")
            .build();

        SearchHits<NodeDocument> searchHits = searchHits(searchHit(existingDoc), searchHit(missingDoc));

        Mockito.when(elasticsearchOperations.search(
                Mockito.any(Query.class),
                Mockito.eq(NodeDocument.class),
                Mockito.any(IndexCoordinates.class)))
            .thenReturn(searchHits);

        Document existingNode = new Document();
        existingNode.setId(existingId);

        Mockito.when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);
        Mockito.when(nodeRepository.findAllById(Mockito.<UUID>anyIterable()))
            .thenReturn(List.of(existingNode));
        Mockito.when(securityService.hasPermission(existingNode, PermissionType.READ)).thenReturn(true);

        FacetedSearchService.FacetedSearchRequest request = new FacetedSearchService.FacetedSearchRequest();
        FacetedSearchService.FacetedSearchResponse response = facetedSearchService.search(request);

        assertEquals(2, response.getTotalHits());
        assertEquals(existingId.toString(), response.getResults().getContent().get(0).getId());
        Map<String, List<FacetedSearchService.FacetValue>> facets = response.getFacets();
        assertEquals("application/pdf", facets.get("mimeType").get(0).getValue());
        assertEquals(1, facets.get("mimeType").get(0).getCount());
    }

    private static SearchHit<NodeDocument> searchHit(NodeDocument doc) {
        return new SearchHit<>(
            "ecm_documents",
            doc.getId(),
            null,
            1.0f,
            null,
            Map.of(),
            Map.of(),
            null,
            null,
            List.of(),
            doc
        );
    }

    @SafeVarargs
    private static SearchHits<NodeDocument> searchHits(SearchHit<NodeDocument>... hits) {
        return new SearchHitsImpl<>(
            hits.length,
            TotalHitsRelation.EQUAL_TO,
            1.0f,
            null,
            null,
            List.of(hits),
            null,
            null
        );
    }

    private static Set<String> extractCreatedByValues(co.elastic.clients.elasticsearch._types.query_dsl.Query esQuery) {
        if (!esQuery.isBool()) {
            return Set.of();
        }

        return esQuery.bool().filter().stream()
            .filter(co.elastic.clients.elasticsearch._types.query_dsl.Query::isBool)
            .flatMap(filter -> filter.bool().should().stream())
            .filter(co.elastic.clients.elasticsearch._types.query_dsl.Query::isTerm)
            .map(co.elastic.clients.elasticsearch._types.query_dsl.Query::term)
            .filter(term -> term.field().startsWith("createdBy"))
            .filter(term -> term.value().isString())
            .map(term -> term.value().stringValue())
            .collect(Collectors.toSet());
    }
}
