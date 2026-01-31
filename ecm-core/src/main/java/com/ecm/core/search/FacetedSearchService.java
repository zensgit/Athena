package com.ecm.core.search;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.service.SecurityService;
import co.elastic.clients.elasticsearch._types.SuggestMode;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.AggregationRange;
import co.elastic.clients.elasticsearch._types.aggregations.DateRangeExpression;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.elasticsearch.core.search.Suggester;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.suggest.response.Suggest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final NodeRepository nodeRepository;
    private final SecurityService securityService;

    @Value("${ecm.search.enabled:true}")
    private boolean searchEnabled;

    private static final String INDEX_NAME = "ecm_documents";
    private static final int DEFAULT_FACET_SIZE = 20;
    private static final int DEFAULT_SUGGESTION_LIMIT = 6;
    private static final int DEFAULT_SPELLCHECK_LIMIT = 5;
    private static final long ONE_MB = 1_048_576L;
    private static final DateTimeFormatter ES_DATE_TIME_FORMAT = DateTimeFormatter.ISO_DATE_TIME;
    private static final List<String> DEFAULT_FACET_FIELDS = List.of(
        "mimeType",
        "createdBy",
        "tags",
        "categories",
        "correspondent",
        "nodeType"
    );

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
            NativeQuery query = buildFacetedQuery(request);

            // Execute search
            SearchHits<NodeDocument> searchHits = elasticsearchOperations.search(
                query, NodeDocument.class, IndexCoordinates.of(INDEX_NAME));

            List<SearchHit<NodeDocument>> authorizedHits = filterAuthorizedHits(searchHits);

            // Convert to results
            List<SearchResult> results = authorizedHits.stream()
                .map(this::toSearchResult)
                .collect(Collectors.toList());

            // Build facets from aggregations when available (admin), otherwise from authorized hits
            List<String> facetFields = resolveFacetFields(request);
            Map<String, List<FacetValue>> facets;
            if (securityService.hasRole("ROLE_ADMIN")) {
                facets = buildFacetsFromAggregations(searchHits, facetFields);
                if (facets.isEmpty()) {
                    facets = buildFacets(authorizedHits);
                }
            } else {
                facets = buildFacets(authorizedHits);
            }

            Pageable pageable = request.getPageable() != null
                ? request.getPageable().toPageable()
                : PageRequest.of(0, 20);

            long totalHits = securityService.hasRole("ROLE_ADMIN")
                ? searchHits.getTotalHits()
                : results.size();
            Page<SearchResult> resultPage = new PageImpl<>(results, pageable, totalHits);

            List<String> suggestions = request.isIncludeSuggestions() && request.getQuery() != null
                ? getSuggestions(request.getQuery(), DEFAULT_SUGGESTION_LIMIT)
                : List.of();

            return FacetedSearchResponse.builder()
                .results(resultPage)
                .facets(facets)
                .totalHits(totalHits)
                .suggestions(suggestions)
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
            pageable.setSize(1); // Aggregations will provide facet counts
            request.setPageable(pageable);

            NativeQuery esQuery = buildFacetedQuery(request);
            SearchHits<NodeDocument> searchHits = elasticsearchOperations.search(
                esQuery, NodeDocument.class, IndexCoordinates.of(INDEX_NAME));

            if (!securityService.hasRole("ROLE_ADMIN")) {
                return buildFacets(filterAuthorizedHits(searchHits));
            }

            Map<String, List<FacetValue>> facets = buildFacetsFromAggregations(searchHits, resolveFacetFields(request));
            if (facets.isEmpty()) {
                return buildFacets(filterAuthorizedHits(searchHits));
            }
            return facets;

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

    /**
     * Get spellcheck / "Did you mean?" suggestions for a query.
     */
    public List<String> getSpellcheckSuggestions(String query, Integer limit) {
        if (!searchEnabled || query == null) {
            return Collections.emptyList();
        }
        String trimmed = query.trim();
        if (trimmed.length() < 2) {
            return Collections.emptyList();
        }

        int maxSuggestions = limit != null && limit > 0 ? limit : DEFAULT_SPELLCHECK_LIMIT;

        try {
            Suggester suggester = Suggester.of(s -> s
                .text(trimmed)
                .suggesters("did_you_mean", fs -> fs.term(ts -> ts
                    .field("name")
                    .suggestMode(SuggestMode.Always)
                    .size(maxSuggestions)
                ))
            );

            NativeQuery queryBuilder = NativeQuery.builder()
                .withQuery(q -> q.matchAll(m -> m))
                .withSuggester(suggester)
                .build();

            SearchHits<NodeDocument> hits = elasticsearchOperations.search(
                queryBuilder, NodeDocument.class, IndexCoordinates.of(INDEX_NAME));

            if (!hits.hasSuggest()) {
                return Collections.emptyList();
            }
            Suggest suggest = hits.getSuggest();
            if (suggest == null) {
                return Collections.emptyList();
            }
            Suggest.Suggestion<?> suggestion = suggest.getSuggestion("did_you_mean");
            if (suggestion == null) {
                return Collections.emptyList();
            }

            List<String> options = new ArrayList<>();
            for (Suggest.Suggestion.Entry<?> entry : suggestion.getEntries()) {
                for (Suggest.Suggestion.Entry.Option option : entry.getOptions()) {
                    if (option.getText() != null && !option.getText().isBlank()) {
                        options.add(option.getText());
                    }
                }
            }

            if (options.isEmpty()) {
                return Collections.emptyList();
            }

            return options.stream()
                .filter(option -> !option.equalsIgnoreCase(trimmed))
                .distinct()
                .limit(maxSuggestions)
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Get spellcheck suggestions failed for query: {}", query, e);
            return Collections.emptyList();
        }
    }

    // === Private helper methods ===

    private NativeQuery buildFacetedQuery(FacetedSearchRequest request) {
        Pageable pageable = request.getPageable() != null
            ? request.getPageable().toPageable()
            : PageRequest.of(0, 20);

        String searchTerm = request.getQuery() != null ? request.getQuery().trim() : "";
        List<String> searchFields = resolveSearchFields(request);
        SearchFilters filters = request.getFilters();

        NativeQueryBuilder builder = NativeQuery.builder().withPageable(pageable);

        builder.withQuery(q -> q.bool(b -> {
            if (searchTerm.isBlank()) {
                b.must(m -> m.matchAll(ma -> ma));
            } else {
                b.must(m -> m.multiMatch(mq -> mq
                    .query(searchTerm)
                    .fields(searchFields)
                    .type(TextQueryType.BestFields)
                    .operator(Operator.Or)
                ));
            }

            if (request.getPathPrefix() != null && !request.getPathPrefix().isBlank()) {
                b.filter(f -> f.prefix(p -> p.field("path").value(request.getPathPrefix())));
            }

            if (filters == null || !filters.isIncludeDeleted()) {
                b.filter(f -> f.term(t -> t.field("deleted").value(false)));
            }

            if (filters != null) {
                applyFilters(b, filters);
            }

            return b;
        }));

        applyAggregations(builder, resolveFacetFields(request));

        return builder.build();
    }

    private void applyFilters(BoolQuery.Builder bool, SearchFilters filters) {
        addAnyOfTermsFilter(bool, List.of("mimeType"), filters.getMimeTypes());
        addAnyOfTermsFilter(bool, List.of("nodeType"), filters.getNodeTypes());
        addAnyOfTermsFilter(bool, List.of("tags"), filters.getTags());
        addAnyOfTermsFilter(bool, List.of("categories"), filters.getCategories());
        addAnyOfTermsFilter(bool, List.of("correspondent"), filters.getCorrespondents());

        if (filters.getCreatedByList() != null && !filters.getCreatedByList().isEmpty()) {
            addAnyOfTermsFilter(bool, List.of("createdBy"), filters.getCreatedByList());
        } else if (filters.getCreatedBy() != null && !filters.getCreatedBy().isBlank()) {
            addAnyOfTermsFilter(bool, List.of("createdBy"), List.of(filters.getCreatedBy()));
        }

        addDateRangeFilter(bool, "createdDate", filters.getDateFrom(), filters.getDateTo());
        addDateRangeFilter(bool, "lastModifiedDate", filters.getModifiedFrom(), filters.getModifiedTo());
        addNumberRangeFilter(bool, "fileSize", filters.getMinSize(), filters.getMaxSize());
        addAnyPrefixFilter(bool, List.of("path"), filters.getPath());
    }

    private static void addAnyOfTermsFilter(BoolQuery.Builder bool, List<String> fields, List<String> values) {
        if (fields == null || fields.isEmpty() || values == null || values.isEmpty()) {
            return;
        }

        List<String> normalizedValues = values.stream()
            .filter(v -> v != null && !v.isBlank())
            .toList();
        if (normalizedValues.isEmpty()) {
            return;
        }

        bool.filter(f -> f.bool(b -> {
            for (String value : normalizedValues) {
                for (String field : fields) {
                    b.should(s -> s.term(t -> t.field(field).value(value)));
                }
            }
            b.minimumShouldMatch("1");
            return b;
        }));
    }

    private static void addAnyPrefixFilter(BoolQuery.Builder bool, List<String> fields, String prefix) {
        if (fields == null || fields.isEmpty() || prefix == null || prefix.isBlank()) {
            return;
        }
        bool.filter(f -> f.bool(b -> {
            for (String field : fields) {
                b.should(s -> s.prefix(p -> p.field(field).value(prefix)));
            }
            b.minimumShouldMatch("1");
            return b;
        }));
    }

    private static void addDateRangeFilter(BoolQuery.Builder bool, String field, LocalDateTime from, LocalDateTime to) {
        if (from == null && to == null) {
            return;
        }
        bool.filter(f -> f.range(r -> {
            r.field(field);
            if (from != null) {
                r.gte(JsonData.of(from.format(ES_DATE_TIME_FORMAT)));
            }
            if (to != null) {
                r.lte(JsonData.of(to.format(ES_DATE_TIME_FORMAT)));
            }
            return r;
        }));
    }

    private static void addNumberRangeFilter(BoolQuery.Builder bool, String field, Long min, Long max) {
        if (min == null && max == null) {
            return;
        }
        bool.filter(f -> f.range(r -> {
            r.field(field);
            if (min != null) {
                r.gte(JsonData.of(min));
            }
            if (max != null) {
                r.lte(JsonData.of(max));
            }
            return r;
        }));
    }

    private List<String> resolveFacetFields(FacetedSearchRequest request) {
        if (request.getFacetFields() != null && !request.getFacetFields().isEmpty()) {
            return request.getFacetFields();
        }
        return DEFAULT_FACET_FIELDS;
    }

    private List<String> resolveSearchFields(FacetedSearchRequest request) {
        if (request.getBoostFields() != null && !request.getBoostFields().isEmpty()) {
            return request.getBoostFields();
        }
        return List.of("name^2", "title^2", "content", "textContent", "description", "extractedText");
    }

    private void applyAggregations(NativeQueryBuilder builder, List<String> facetFields) {
        List<String> fields = facetFields != null && !facetFields.isEmpty()
            ? facetFields
            : DEFAULT_FACET_FIELDS;

        for (String field : fields) {
            if (field == null || field.isBlank()) {
                continue;
            }
            if ("fileSizeRange".equalsIgnoreCase(field) || "createdDateRange".equalsIgnoreCase(field)) {
                continue;
            }
            builder.withAggregation(field, Aggregation.of(a -> a.terms(t -> t
                .field(field)
                .size(DEFAULT_FACET_SIZE)
            )));
        }

        builder.withAggregation("fileSizeRange", Aggregation.of(a -> a.range(r -> r
            .field("fileSize")
            .ranges(List.of(
                AggregationRange.of(range -> range.key("0-1MB").to(String.valueOf(ONE_MB))),
                AggregationRange.of(range -> range.key("1-10MB")
                    .from(String.valueOf(ONE_MB))
                    .to(String.valueOf(10 * ONE_MB))),
                AggregationRange.of(range -> range.key("10-100MB")
                    .from(String.valueOf(10 * ONE_MB))
                    .to(String.valueOf(100 * ONE_MB))),
                AggregationRange.of(range -> range.key("100MB+")
                    .from(String.valueOf(100 * ONE_MB)))
            ))
        )));

        builder.withAggregation("createdDateRange", Aggregation.of(a -> a.dateRange(dr -> dr
            .field("createdDate")
            .ranges(List.of(
                DateRangeExpression.of(range -> range.key("last24h")
                    .from(f -> f.expr("now-24h"))
                    .to(f -> f.expr("now"))),
                DateRangeExpression.of(range -> range.key("last7d")
                    .from(f -> f.expr("now-7d"))
                    .to(f -> f.expr("now"))),
                DateRangeExpression.of(range -> range.key("last30d")
                    .from(f -> f.expr("now-30d"))
                    .to(f -> f.expr("now"))),
                DateRangeExpression.of(range -> range.key("last365d")
                    .from(f -> f.expr("now-365d"))
                    .to(f -> f.expr("now")))
            ))
        )));
    }

    private Map<String, List<FacetValue>> buildFacetsFromAggregations(
        SearchHits<NodeDocument> searchHits,
        List<String> facetFields
    ) {
        if (searchHits == null || searchHits.getAggregations() == null) {
            return Collections.emptyMap();
        }

        ElasticsearchAggregations aggregations;
        try {
            aggregations = (ElasticsearchAggregations) searchHits.getAggregations();
        } catch (ClassCastException ex) {
            return Collections.emptyMap();
        }

        Map<String, List<FacetValue>> facets = new HashMap<>();
        List<String> resolvedFields = facetFields != null && !facetFields.isEmpty()
            ? facetFields
            : DEFAULT_FACET_FIELDS;

        var aggregationsMap = aggregations.aggregationsAsMap();
        for (String field : resolvedFields) {
            Aggregate agg = extractAggregate(aggregationsMap, field);
            List<FacetValue> values = extractTermsFacet(agg);
            if (!values.isEmpty()) {
                facets.put(field, values);
            }
        }

        Aggregate fileSizeAgg = extractAggregate(aggregationsMap, "fileSizeRange");
        List<FacetValue> fileSizeRanges = extractRangeFacet(fileSizeAgg);
        if (!fileSizeRanges.isEmpty()) {
            facets.put("fileSizeRange", fileSizeRanges);
        }

        Aggregate dateRangeAgg = extractAggregate(aggregationsMap, "createdDateRange");
        List<FacetValue> dateRanges = extractRangeFacet(dateRangeAgg);
        if (!dateRanges.isEmpty()) {
            facets.put("createdDateRange", dateRanges);
        }

        return facets;
    }

    private Aggregate extractAggregate(
        Map<String, org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation> aggregationsMap,
        String name
    ) {
        if (aggregationsMap == null || name == null) {
            return null;
        }
        var aggregation = aggregationsMap.get(name);
        if (aggregation == null || aggregation.aggregation() == null) {
            return null;
        }
        return aggregation.aggregation().getAggregate();
    }

    private List<FacetValue> extractTermsFacet(Aggregate aggregate) {
        if (aggregate == null || !aggregate.isSterms()) {
            return List.of();
        }
        return aggregate.sterms().buckets().array().stream()
            .map(bucket -> new FacetValue(bucket.key().stringValue(), toFacetCount(bucket.docCount())))
            .collect(Collectors.toList());
    }

    private List<FacetValue> extractRangeFacet(Aggregate aggregate) {
        if (aggregate == null) {
            return List.of();
        }
        if (aggregate.isRange()) {
            return aggregate.range().buckets().array().stream()
                .map(bucket -> new FacetValue(bucket.key(), toFacetCount(bucket.docCount())))
                .collect(Collectors.toList());
        }
        if (aggregate.isDateRange()) {
            return aggregate.dateRange().buckets().array().stream()
                .map(bucket -> new FacetValue(bucket.key(), toFacetCount(bucket.docCount())))
                .collect(Collectors.toList());
        }
        return List.of();
    }

    private int toFacetCount(long count) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private Map<String, List<FacetValue>> buildFacets(Iterable<SearchHit<NodeDocument>> searchHits) {
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

    private List<SearchHit<NodeDocument>> filterAuthorizedHits(SearchHits<NodeDocument> searchHits) {
        if (securityService.hasRole("ROLE_ADMIN")) {
            return searchHits.stream().collect(Collectors.toList());
        }

        Map<UUID, Node> nodesById = loadNodes(searchHits);
        List<SearchHit<NodeDocument>> authorized = new ArrayList<>();
        for (SearchHit<NodeDocument> hit : searchHits) {
            UUID nodeId = toUuid(hit.getContent().getId());
            Node node = nodeId != null ? nodesById.get(nodeId) : null;
            if (node != null && securityService.hasPermission(node, PermissionType.READ)) {
                authorized.add(hit);
            }
        }
        return authorized;
    }

    private Map<UUID, Node> loadNodes(SearchHits<NodeDocument> searchHits) {
        Map<UUID, Node> nodesById = new LinkedHashMap<>();
        List<UUID> ids = searchHits.stream()
            .map(hit -> toUuid(hit.getContent().getId()))
            .filter(id -> id != null)
            .distinct()
            .collect(Collectors.toList());

        if (ids.isEmpty()) {
            return nodesById;
        }

        nodeRepository.findAllById(ids).forEach(node -> nodesById.put(node.getId(), node));
        return nodesById;
    }

    private UUID toUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            log.warn("Invalid node id in search index: {}", value);
            return null;
        }
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
        private boolean includeSuggestions = false;
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
        private List<String> suggestions;

        public static FacetedSearchResponse empty() {
            return FacetedSearchResponse.builder()
                .results(Page.empty())
                .facets(Collections.emptyMap())
                .totalHits(0)
                .suggestions(List.of())
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
