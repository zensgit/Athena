package com.ecm.core.search;

import com.ecm.core.entity.Document;
import com.ecm.core.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Faceted Search Service
 *
 * Provides advanced search capabilities with:
 * - Faceted navigation (filtering by categories)
 * - Aggregations for facet counts
 * - Multi-field search with boosting
 * - Date range filtering
 * - Size range filtering
 * - Tag and category filtering
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FacetedSearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final DocumentRepository documentRepository;

    @Value("${ecm.search.enabled:true}")
    private boolean searchEnabled;

    private static final String INDEX_NAME = "ecm_documents";

    /**
     * Perform faceted search with aggregations
     */
    public FacetedSearchResponse search(FacetedSearchRequest request) {
        if (!searchEnabled) {
            log.warn("Search is disabled");
            return FacetedSearchResponse.empty();
        }

        try {
            // Build the search query
            Query query = buildFacetedQuery(request);

            // Execute search
            SearchHits<NodeDocument> searchHits = elasticsearchOperations.search(
                query, NodeDocument.class, IndexCoordinates.of(INDEX_NAME));

            // Convert to results
            List<SearchResult> results = searchHits.stream()
                .map(this::toSearchResult)
                .collect(Collectors.toList());

            // Build facets from results (simplified aggregation)
            Map<String, List<FacetValue>> facets = buildFacets(searchHits);

            Pageable pageable = request.getPageable() != null
                ? request.getPageable().toPageable()
                : PageRequest.of(0, 20);

            Page<SearchResult> resultPage = new PageImpl<>(results, pageable, searchHits.getTotalHits());

            return FacetedSearchResponse.builder()
                .results(resultPage)
                .facets(facets)
                .totalHits(searchHits.getTotalHits())
                .queryTime(System.currentTimeMillis())
                .build();

        } catch (LinkageError e) {
            log.error("Faceted search failed due to missing/invalid Elasticsearch client dependency", e);
            return FacetedSearchResponse.empty();
        } catch (Exception e) {
            log.error("Faceted search failed", e);
            return FacetedSearchResponse.empty();
        }
    }

    /**
     * Get available facets for a query (without executing full search)
     */
    public Map<String, List<FacetValue>> getAvailableFacets(String query) {
        if (!searchEnabled) {
            return Collections.emptyMap();
        }

        try {
            FacetedSearchRequest request = new FacetedSearchRequest();
            request.setQuery(query);
            SimplePageRequest pageable = new SimplePageRequest();
            pageable.setPage(0);
            pageable.setSize(1000); // Fetch more for accurate facet counts
            request.setPageable(pageable);

            Query esQuery = buildFacetedQuery(request);
            SearchHits<NodeDocument> searchHits = elasticsearchOperations.search(
                esQuery, NodeDocument.class, IndexCoordinates.of(INDEX_NAME));

            return buildFacets(searchHits);

        } catch (LinkageError e) {
            log.error("Failed to get facets due to missing/invalid Elasticsearch client dependency", e);
            return Collections.emptyMap();
        } catch (Exception e) {
            log.error("Failed to get facets", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Get suggested filters based on current results
     */
    public List<SuggestedFilter> getSuggestedFilters(String query) {
        Map<String, List<FacetValue>> facets = getAvailableFacets(query);
        List<SuggestedFilter> suggestions = new ArrayList<>();

        // Suggest top mime types
        if (facets.containsKey("mimeType")) {
            facets.get("mimeType").stream()
                .limit(5)
                .forEach(fv -> suggestions.add(new SuggestedFilter(
                    "mimeType",
                    getMimeTypeLabel(fv.getValue()),
                    fv.getValue(),
                    fv.getCount()
                )));
        }

        // Suggest top authors
        if (facets.containsKey("createdBy")) {
            facets.get("createdBy").stream()
                .limit(5)
                .forEach(fv -> suggestions.add(new SuggestedFilter(
                    "createdBy",
                    "By " + fv.getValue(),
                    fv.getValue(),
                    fv.getCount()
                )));
        }

        // Suggest date ranges
        suggestions.add(new SuggestedFilter("dateRange", "Last 7 days", "7d", null));
        suggestions.add(new SuggestedFilter("dateRange", "Last 30 days", "30d", null));
        suggestions.add(new SuggestedFilter("dateRange", "Last year", "1y", null));

        return suggestions;
    }

    /**
     * Search with automatic query enhancement
     */
    public FacetedSearchResponse smartSearch(String query) {
        FacetedSearchRequest request = new FacetedSearchRequest();
        request.setQuery(query);
        request.setBoostFields(Arrays.asList("name^3", "title^2", "content^1"));
        request.setHighlightEnabled(true);
        request.setFacetFields(Arrays.asList("mimeType", "createdBy", "tags", "categories"));

        return search(request);
    }

    /**
     * Search within a specific folder and its subfolders
     */
    public FacetedSearchResponse searchInFolder(String query, String folderPath) {
        FacetedSearchRequest request = new FacetedSearchRequest();
        request.setQuery(query);
        request.setPathPrefix(folderPath);

        return search(request);
    }

    /**
     * Find similar documents based on content
     */
    public List<SearchResult> findSimilar(String documentId, int maxResults) {
        if (!searchEnabled) {
            return Collections.emptyList();
        }

        try {
            // First, get the source document
            CriteriaQuery query = new CriteriaQuery(new Criteria("id").is(documentId));
            SearchHits<NodeDocument> hits = elasticsearchOperations.search(
                query, NodeDocument.class, IndexCoordinates.of(INDEX_NAME));

            if (hits.isEmpty()) {
                return Collections.emptyList();
            }

            NodeDocument sourceDoc = hits.getSearchHit(0).getContent();

            // Build a "more like this" query based on document content
            List<String> searchTerms = new ArrayList<>();
            if (sourceDoc.getName() != null) {
                searchTerms.addAll(Arrays.asList(sourceDoc.getName().split("\\s+")));
            }
            if (sourceDoc.getTags() != null) {
                searchTerms.addAll(sourceDoc.getTags());
            }

            if (searchTerms.isEmpty()) {
                return Collections.emptyList();
            }

            // Search for similar documents
            Criteria criteria = new Criteria();
            for (String term : searchTerms.stream().limit(5).collect(Collectors.toList())) {
                criteria = criteria.or("name").contains(term)
                    .or("content").contains(term)
                    .or("tags").contains(term);
            }
            criteria = criteria.and("id").not().is(documentId);
            criteria = criteria.and("deleted").is(false);

            CriteriaQuery similarQuery = new CriteriaQuery(criteria);
            similarQuery.setPageable(PageRequest.of(0, maxResults));

            SearchHits<NodeDocument> similarHits = elasticsearchOperations.search(
                similarQuery, NodeDocument.class, IndexCoordinates.of(INDEX_NAME));

            return similarHits.stream()
                .map(this::toSearchResult)
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Find similar failed for document: {}", documentId, e);
            return Collections.emptyList();
        }
    }

    /**
     * Get search suggestions (autocomplete)
     */
    public List<String> getSuggestions(String prefix, int maxSuggestions) {
        if (!searchEnabled || prefix == null || prefix.length() < 2) {
            return Collections.emptyList();
        }

        try {
            Criteria criteria = new Criteria("name").startsWith(prefix.toLowerCase())
                .and("deleted").is(false);

            CriteriaQuery query = new CriteriaQuery(criteria);
            query.setPageable(PageRequest.of(0, maxSuggestions));

            SearchHits<NodeDocument> hits = elasticsearchOperations.search(
                query, NodeDocument.class, IndexCoordinates.of(INDEX_NAME));

            return hits.stream()
                .map(hit -> hit.getContent().getName())
                .distinct()
                .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Get suggestions failed for prefix: {}", prefix, e);
            return Collections.emptyList();
        }
    }

    // === Private helper methods ===

    private Query buildFacetedQuery(FacetedSearchRequest request) {
        Criteria criteria = new Criteria();

        // Text search with optional boosting
        if (request.getQuery() != null && !request.getQuery().trim().isEmpty()) {
            String searchTerm = request.getQuery().trim();
            criteria = criteria.or("name").matches(searchTerm)
                .or("content").matches(searchTerm)
                .or("textContent").matches(searchTerm)
                .or("title").matches(searchTerm)
                .or("description").matches(searchTerm);
        }

        // Path prefix filter
        if (request.getPathPrefix() != null && !request.getPathPrefix().isEmpty()) {
            criteria = criteria.and("path").startsWith(request.getPathPrefix());
        }

        // Apply filters
        if (request.getFilters() != null) {
            criteria = applyFilters(criteria, request.getFilters());
        }

        // Exclude deleted by default
        if (request.getFilters() == null || !request.getFilters().isIncludeDeleted()) {
            criteria = criteria.and("deleted").is(false);
        }

        CriteriaQuery query = new CriteriaQuery(criteria);

        if (request.getPageable() != null) {
            query.setPageable(request.getPageable().toPageable());
        }

        return query;
    }

    private Criteria applyFilters(Criteria criteria, SearchFilters filters) {
        if (filters.getMimeTypes() != null && !filters.getMimeTypes().isEmpty()) {
            criteria = criteria.and(
                new Criteria("mimeType.keyword").in(filters.getMimeTypes())
                    .or(new Criteria("mimeType").in(filters.getMimeTypes()))
            );
        }

        if (filters.getNodeTypes() != null && !filters.getNodeTypes().isEmpty()) {
            criteria = criteria.and(
                new Criteria("nodeType.keyword").in(filters.getNodeTypes())
                    .or(new Criteria("nodeType").in(filters.getNodeTypes()))
            );
        }

        if (filters.getCreatedBy() != null && !filters.getCreatedBy().isEmpty()) {
            criteria = criteria.and(
                new Criteria("createdBy.keyword").is(filters.getCreatedBy())
                    .or(new Criteria("createdBy").is(filters.getCreatedBy()))
            );
        }

        if (filters.getDateFrom() != null) {
            criteria = criteria.and("createdDate").greaterThanEqual(filters.getDateFrom());
        }

        if (filters.getDateTo() != null) {
            criteria = criteria.and("createdDate").lessThanEqual(filters.getDateTo());
        }

        if (filters.getModifiedFrom() != null) {
            criteria = criteria.and("lastModifiedDate").greaterThanEqual(filters.getModifiedFrom());
        }

        if (filters.getModifiedTo() != null) {
            criteria = criteria.and("lastModifiedDate").lessThanEqual(filters.getModifiedTo());
        }

        if (filters.getMinSize() != null) {
            criteria = criteria.and("fileSize").greaterThanEqual(filters.getMinSize());
        }

        if (filters.getMaxSize() != null) {
            criteria = criteria.and("fileSize").lessThanEqual(filters.getMaxSize());
        }

        if (filters.getTags() != null && !filters.getTags().isEmpty()) {
            criteria = criteria.and(
                new Criteria("tags.keyword").in(filters.getTags())
                    .or(new Criteria("tags").in(filters.getTags()))
            );
        }

        if (filters.getCategories() != null && !filters.getCategories().isEmpty()) {
            criteria = criteria.and(
                new Criteria("categories.keyword").in(filters.getCategories())
                    .or(new Criteria("categories").in(filters.getCategories()))
            );
        }

        if (filters.getCorrespondents() != null && !filters.getCorrespondents().isEmpty()) {
            criteria = criteria.and(
                new Criteria("correspondent.keyword").in(filters.getCorrespondents())
                    .or(new Criteria("correspondent").in(filters.getCorrespondents()))
            );
        }

        if (filters.getPath() != null && !filters.getPath().isEmpty()) {
            criteria = criteria.and(
                new Criteria("path.keyword").startsWith(filters.getPath())
                    .or(new Criteria("path").startsWith(filters.getPath()))
            );
        }

        return criteria;
    }

    private Map<String, List<FacetValue>> buildFacets(SearchHits<NodeDocument> searchHits) {
        Map<String, List<FacetValue>> facets = new HashMap<>();

        // Build facets from actual results (simplified aggregation)
        Map<String, Integer> mimeTypeCounts = new HashMap<>();
        Map<String, Integer> createdByCounts = new HashMap<>();
        Map<String, Integer> tagCounts = new HashMap<>();
        Map<String, Integer> categoryCounts = new HashMap<>();
        Map<String, Integer> correspondentCounts = new HashMap<>();

        for (SearchHit<NodeDocument> hit : searchHits) {
            NodeDocument doc = hit.getContent();

            // Count mime types
            if (doc.getMimeType() != null) {
                mimeTypeCounts.merge(doc.getMimeType(), 1, Integer::sum);
            }

            // Count creators
            if (doc.getCreatedBy() != null) {
                createdByCounts.merge(doc.getCreatedBy(), 1, Integer::sum);
            }

            // Count tags
            if (doc.getTags() != null) {
                for (String tag : doc.getTags()) {
                    tagCounts.merge(tag, 1, Integer::sum);
                }
            }

            // Count categories
            if (doc.getCategories() != null) {
                for (String category : doc.getCategories()) {
                    categoryCounts.merge(category, 1, Integer::sum);
                }
            }

            // Count correspondents
            if (doc.getCorrespondent() != null) {
                correspondentCounts.merge(doc.getCorrespondent(), 1, Integer::sum);
            }
        }

        // Convert to FacetValue lists
        facets.put("mimeType", toFacetValues(mimeTypeCounts));
        facets.put("createdBy", toFacetValues(createdByCounts));
        facets.put("tags", toFacetValues(tagCounts));
        facets.put("categories", toFacetValues(categoryCounts));
        facets.put("correspondent", toFacetValues(correspondentCounts));

        return facets;
    }

    private List<FacetValue> toFacetValues(Map<String, Integer> counts) {
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .map(e -> new FacetValue(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
    }

    private SearchResult toSearchResult(SearchHit<NodeDocument> hit) {
        NodeDocument doc = hit.getContent();
        return SearchResult.builder()
            .id(doc.getId())
            .name(doc.getName())
            .description(doc.getDescription())
            .path(doc.getPath())
            .nodeType(doc.getNodeType() != null ? doc.getNodeType().name() : null)
            .parentId(doc.getParentId())
            .mimeType(doc.getMimeType())
            .fileSize(doc.getFileSize())
            .createdBy(doc.getCreatedBy())
            .createdDate(doc.getCreatedDate())
            .lastModifiedBy(doc.getLastModifiedBy())
            .lastModifiedDate(doc.getLastModifiedDate())
            .score(hit.getScore())
            .highlights(hit.getHighlightFields())
            .tags(doc.getTags() != null ? List.copyOf(doc.getTags()) : List.of())
            .categories(doc.getCategories() != null ? List.copyOf(doc.getCategories()) : List.of())
            .correspondent(doc.getCorrespondent())
            .build();
    }

    private String getMimeTypeLabel(String mimeType) {
        if (mimeType == null) return "Unknown";

        Map<String, String> labels = Map.of(
            "application/pdf", "PDF Documents",
            "application/msword", "Word Documents",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Word Documents",
            "application/vnd.ms-excel", "Excel Spreadsheets",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel Spreadsheets",
            "text/plain", "Text Files",
            "image/jpeg", "JPEG Images",
            "image/png", "PNG Images"
        );

        return labels.getOrDefault(mimeType, mimeType);
    }

    // === Request/Response DTOs ===

    @lombok.Data
    public static class FacetedSearchRequest {
        private String query;
        private SearchFilters filters;
        private SimplePageRequest pageable;
        private boolean highlightEnabled = true;
        private List<String> facetFields;
        private List<String> boostFields;
        private String pathPrefix;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FacetedSearchResponse {
        private Page<SearchResult> results;
        private Map<String, List<FacetValue>> facets;
        private long totalHits;
        private long queryTime;

        public static FacetedSearchResponse empty() {
            return FacetedSearchResponse.builder()
                .results(Page.empty())
                .facets(Collections.emptyMap())
                .totalHits(0)
                .queryTime(0)
                .build();
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class FacetValue {
        private String value;
        private int count;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SuggestedFilter {
        private String field;
        private String label;
        private String value;
        private Integer count;
    }
}
