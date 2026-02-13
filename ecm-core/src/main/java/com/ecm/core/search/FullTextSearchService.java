package com.ecm.core.search;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.preview.PreviewFailureClassifier;
import com.ecm.core.repository.DocumentRepository;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.service.SecurityService;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.json.JsonData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import co.elastic.clients.elasticsearch._types.SortOrder;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Full-Text Search Service
 *
 * Provides enhanced search capabilities with:
 * - Full-text search across document content
 * - Highlighting of search results
 * - Faceted search
 * - Index rebuild from PostgreSQL (Source of Truth)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FullTextSearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final DocumentRepository documentRepository;
    private final NodeRepository nodeRepository;
    private final SecurityService securityService;

    private static final List<String> SEARCH_FIELDS = List.of(
        "name^2",
        "description",
        "content",
        "textContent",
        "extractedText",
        "metadata.extractedText",
        "title"
    );

    private static final List<String> HIGHLIGHT_FIELDS = List.of(
        "name",
        "description",
        "content",
        "textContent",
        "extractedText",
        "metadata.extractedText",
        "title"
    );
    @Value("${ecm.search.enabled:true}")
    private boolean searchEnabled;

    private static final String INDEX_NAME = "ecm_documents";
    private static final DateTimeFormatter ES_DATE_TIME_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    private final AtomicBoolean rebuildInProgress = new AtomicBoolean(false);
    private final AtomicInteger rebuildProgress = new AtomicInteger(0);

    /**
     * Search documents with full-text matching.
     *
     * @param queryText Search query
     * @param page Page number
     * @param size Page size
     * @return Page of search results
     */
    public Page<SearchResult> search(String queryText, int page, int size) {
        return search(queryText, page, size, null, null);
    }

    public Page<SearchResult> search(String queryText, int page, int size, String sortBy, String sortDirection) {
        return search(queryText, page, size, sortBy, sortDirection, null, true);
    }

    public Page<SearchResult> search(
        String queryText,
        int page,
        int size,
        String sortBy,
        String sortDirection,
        String folderId,
        boolean includeChildren
    ) {
        return search(queryText, page, size, sortBy, sortDirection, folderId, includeChildren, null);
    }

    public Page<SearchResult> search(
        String queryText,
        int page,
        int size,
        String sortBy,
        String sortDirection,
        String folderId,
        boolean includeChildren,
        List<String> previewStatuses
    ) {
        if (!searchEnabled) {
            log.warn("Search is disabled");
            return Page.empty();
        }

        Pageable pageable = PageRequest.of(page, size);

        try {
            Query query = buildFullTextQuery(queryText, pageable, sortBy, sortDirection, folderId, includeChildren, previewStatuses);
            SearchHits<NodeDocument> searchHits = elasticsearchOperations.search(
                query, NodeDocument.class, IndexCoordinates.of(INDEX_NAME));

            List<SearchResult> results = filterAuthorizedResults(searchHits);

            long totalHits = searchHits.getTotalHits();
            return new PageImpl<>(results, pageable, totalHits);

        } catch (LinkageError e) {
            log.error("Search failed due to missing/invalid Elasticsearch client dependency", e);
            return new PageImpl<>(List.of(), pageable, 0);
        } catch (Exception e) {
            log.error("Search failed for query: {}", queryText, e);
            return new PageImpl<>(List.of(), pageable, 0);
        }
    }

    /**
     * Advanced search with filters.
     */
    public Page<SearchResult> advancedSearch(SearchRequest request) {
        if (!searchEnabled) {
            return Page.empty();
        }

        try {
            Pageable pageable = request.getPageable() != null
                ? request.getPageable().toPageable()
                : PageRequest.of(0, 20);

            Query query = buildAdvancedQuery(request, pageable);
            SearchHits<NodeDocument> searchHits = elasticsearchOperations.search(
                query, NodeDocument.class, IndexCoordinates.of(INDEX_NAME));

            List<SearchResult> results = filterAuthorizedResults(searchHits);

            long totalHits = searchHits.getTotalHits();
            return new PageImpl<>(results, pageable, totalHits);

        } catch (LinkageError e) {
            Pageable pageable = request.getPageable() != null
                ? request.getPageable().toPageable()
                : PageRequest.of(0, 20);
            log.error("Advanced search failed due to missing/invalid Elasticsearch client dependency", e);
            return new PageImpl<>(List.of(), pageable, 0);
        } catch (Exception e) {
            Pageable pageable = request.getPageable() != null
                ? request.getPageable().toPageable()
                : PageRequest.of(0, 20);
            log.error("Advanced search failed", e);
            return new PageImpl<>(List.of(), pageable, 0);
        }
    }

    /**
     * Rebuild the entire search index from PostgreSQL.
     * This is the recovery mechanism when ES data is lost.
     *
     * @return Number of documents indexed
     */
    public int rebuildIndex() {
        if (!rebuildInProgress.compareAndSet(false, true)) {
            log.warn("Index rebuild already in progress");
            return -1;
        }

        rebuildProgress.set(0);
        int totalIndexed = 0;

        try {
            log.info("Starting full index rebuild from PostgreSQL...");

            // Delete existing index
            IndexOperations indexOps = elasticsearchOperations.indexOps(NodeDocument.class);
            if (indexOps.exists()) {
                indexOps.delete();
                log.info("Deleted existing index");
            }

            // Recreate index with mappings
            indexOps.create();
            indexOps.putMapping();
            log.info("Created new index with mappings");

            // Fetch and index documents in batches
            int batchSize = 100;
            int pageNum = 0;
            Page<Document> documentPage;

            do {
                documentPage = documentRepository.findByDeletedFalse(PageRequest.of(pageNum, batchSize));
                List<Document> documents = documentPage.getContent();

                for (Document doc : documents) {
                    try {
                        NodeDocument nodeDoc = NodeDocument.fromNode(doc);
                        nodeDoc.setPermissions(securityService.resolveReadAuthorities(doc));
                        elasticsearchOperations.save(nodeDoc, IndexCoordinates.of(INDEX_NAME));
                        totalIndexed++;
                        rebuildProgress.incrementAndGet();
                    } catch (Exception e) {
                        log.warn("Failed to index document {}: {}", doc.getId(), e.getMessage());
                    }
                }

                log.info("Indexed batch {} ({} documents)", pageNum + 1, documents.size());
                pageNum++;

            } while (documentPage.hasNext());

            log.info("Index rebuild completed. Total indexed: {}", totalIndexed);
            return totalIndexed;

        } catch (LinkageError e) {
            log.error("Index rebuild failed due to missing/invalid Elasticsearch client dependency", e);
            return -1;
        } catch (Exception e) {
            log.error("Index rebuild failed", e);
            return -1;

        } finally {
            rebuildInProgress.set(false);
        }
    }

    private List<SearchResult> filterAuthorizedResults(SearchHits<NodeDocument> searchHits) {
        if (securityService.hasRole("ROLE_ADMIN")) {
            return searchHits.stream()
                .map(this::toSearchResult)
                .collect(Collectors.toList());
        }

        Map<UUID, Node> nodesById = loadNodes(searchHits);
        return searchHits.stream()
            .filter(hit -> {
                UUID nodeId = toUuid(hit.getContent().getId());
                Node node = nodeId != null ? nodesById.get(nodeId) : null;
                return node != null && securityService.hasPermission(node, PermissionType.READ);
            })
            .map(this::toSearchResult)
            .collect(Collectors.toList());
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

    /**
     * Get index rebuild progress.
     */
    public Map<String, Object> getRebuildStatus() {
        return Map.of(
            "inProgress", rebuildInProgress.get(),
            "documentsIndexed", rebuildProgress.get()
        );
    }

    /**
     * Get index statistics.
     */
    public Map<String, Object> getIndexStats() {
        try {
            long count = elasticsearchOperations.count(
                new CriteriaQuery(new Criteria()),
                NodeDocument.class,
                IndexCoordinates.of(INDEX_NAME)
            );

            return Map.of(
                "indexName", INDEX_NAME,
                "documentCount", count,
                "searchEnabled", searchEnabled
            );
        } catch (LinkageError e) {
            return Map.of(
                "indexName", INDEX_NAME,
                "error", e.toString(),
                "searchEnabled", searchEnabled
            );
        } catch (Exception e) {
            return Map.of(
                "indexName", INDEX_NAME,
                "error", e.getMessage(),
                "searchEnabled", searchEnabled
            );
        }
    }

    private Query buildFullTextQuery(String queryText, Pageable pageable, String sortBy, String sortDirection) {
        return buildFullTextQuery(queryText, pageable, sortBy, sortDirection, null, true, null);
    }

    private Query buildFullTextQuery(
        String queryText,
        Pageable pageable,
        String sortBy,
        String sortDirection,
        String folderId,
        boolean includeChildren
    ) {
        return buildFullTextQuery(queryText, pageable, sortBy, sortDirection, folderId, includeChildren, null);
    }

    private Query buildFullTextQuery(
        String queryText,
        Pageable pageable,
        String sortBy,
        String sortDirection,
        String folderId,
        boolean includeChildren,
        List<String> previewStatuses
    ) {
        String searchTerm = queryText != null ? queryText.trim() : "";

        NativeQueryBuilder builder = NativeQuery.builder()
            .withPageable(pageable)
            .withTrackTotalHits(true);

        builder.withQuery(q -> q.bool(b -> {
            if (searchTerm.isEmpty()) {
                b.must(m -> m.matchAll(ma -> ma));
            } else {
                b.must(m -> m.multiMatch(mm -> mm
                    .query(searchTerm)
                    .fields(SEARCH_FIELDS)
                    .type(TextQueryType.BestFields)
                    .operator(Operator.Or)
                ));
            }

            b.filter(f -> f.term(t -> t.field("deleted").value(false)));
            applyFolderScopeFilter(b, folderId, includeChildren);
            PreviewStatusFilterHelper.apply(b, previewStatuses);
            applyReadPermissionFilter(b);
            return b;
        }));

        if (!searchTerm.isBlank()) {
            applyHighlight(builder, HIGHLIGHT_FIELDS);
        }

        applySort(builder, sortBy, sortDirection);

        return builder.build();
    }

    private Query buildAdvancedQuery(SearchRequest request, Pageable pageable) {
        String searchTerm = request.getQuery() != null ? request.getQuery().trim() : "";
        SearchFilters filters = request.getFilters();

        NativeQueryBuilder builder = NativeQuery.builder()
            .withPageable(pageable)
            .withTrackTotalHits(true);

        builder.withQuery(q -> q.bool(b -> {
            // Text search
            if (searchTerm.isEmpty()) {
                b.must(m -> m.matchAll(ma -> ma));
            } else {
                b.must(m -> m.multiMatch(mq -> mq
                    .query(searchTerm)
                    .fields(SEARCH_FIELDS)
                    .type(TextQueryType.BestFields)
                    .operator(Operator.Or)
                ));
            }

            // Include deleted?
            if (filters == null || !filters.isIncludeDeleted()) {
                b.filter(f -> f.term(t -> t.field("deleted").value(false)));
            }

            if (filters != null) {
                addAnyOfTermsFilter(b, List.of("nodeType", "nodeType.keyword"), filters.getNodeTypes());
                addAnyOfTermsFilter(b, List.of("mimeType", "mimeType.keyword"), filters.getMimeTypes());
                addAnyOfTermsFilter(b, List.of("tags", "tags.keyword"), filters.getTags());
                addAnyOfTermsFilter(b, List.of("categories", "categories.keyword"), filters.getCategories());
                addAnyOfTermsFilter(b, List.of("correspondent", "correspondent.keyword"), filters.getCorrespondents());

                if (filters.getCreatedByList() != null && !filters.getCreatedByList().isEmpty()) {
                    addAnyOfTermsFilter(b, List.of("createdBy", "createdBy.keyword"), filters.getCreatedByList());
                } else if (filters.getCreatedBy() != null && !filters.getCreatedBy().isBlank()) {
                    addAnyOfTermsFilter(b, List.of("createdBy", "createdBy.keyword"), List.of(filters.getCreatedBy()));
                }

                addDateRangeFilter(b, "createdDate", filters.getDateFrom(), filters.getDateTo());
                addDateRangeFilter(b, "lastModifiedDate", filters.getModifiedFrom(), filters.getModifiedTo());

                addNumberRangeFilter(b, "fileSize", filters.getMinSize(), filters.getMaxSize());

                if (filters.getFolderId() != null && !filters.getFolderId().isBlank()) {
                    applyFolderScopeFilter(b, filters.getFolderId(), filters.isIncludeChildren());
                } else {
                    addAnyPrefixFilter(b, List.of("path.keyword", "path"), filters.getPath());
                }

                PreviewStatusFilterHelper.apply(b, filters.getPreviewStatuses());
            }

            applyReadPermissionFilter(b);

            return b;
        }));

        if (request.isHighlightEnabled() && !searchTerm.isBlank()) {
            applyHighlight(builder, HIGHLIGHT_FIELDS);
        }

        applySort(builder, request.getSortBy(), request.getSortDirection());

        return builder.build();
    }

    private void applyFolderScopeFilter(BoolQuery.Builder bool, String folderId, boolean includeChildren) {
        if (folderId == null || folderId.isBlank()) {
            return;
        }

        UUID id = UUID.fromString(folderId.trim());
        if (!includeChildren) {
            bool.filter(f -> f.term(t -> t.field("parentId").value(id.toString())));
            return;
        }

        Node folder = nodeRepository.findByIdAndDeletedFalse(id).orElse(null);
        if (folder == null || folder.getPath() == null || folder.getPath().isBlank()) {
            // Non-existent scope: return empty without leaking details.
            bool.filter(f -> f.term(t -> t.field("parentId").value("__none__")));
            return;
        }

        String prefix = folder.getPath();
        if (!prefix.endsWith("/")) {
            prefix += "/";
        }
        addAnyPrefixFilter(bool, List.of("path.keyword", "path"), prefix);
    }

    private void applyReadPermissionFilter(BoolQuery.Builder bool) {
        if (securityService.hasRole("ROLE_ADMIN")) {
            return;
        }

        String username = securityService.getCurrentUser();
        var authorities = securityService.getUserAuthorities(username);
        if (authorities == null || authorities.isEmpty()) {
            bool.filter(f -> f.term(t -> t.field("permissions").value("__none__")));
            return;
        }

        bool.filter(f -> f.bool(b -> {
            for (String authority : authorities) {
                b.should(s -> s.term(t -> t.field("permissions").value(authority)));
            }
            b.minimumShouldMatch("1");
            return b;
        }));
    }

    private void applySort(NativeQueryBuilder builder, String sortBy, String sortDirection) {
        if (sortBy == null || sortBy.isBlank() || "relevance".equalsIgnoreCase(sortBy)) {
            builder.withSort(s -> s.score(sc -> sc.order(SortOrder.Desc)));
            builder.withSort(s -> s.field(f -> f.field("nameSort").order(SortOrder.Asc)));
            return;
        }

        String field = switch (sortBy.toLowerCase()) {
            case "name" -> "nameSort";
            case "modified" -> "lastModifiedDate";
            case "size" -> "fileSize";
            default -> null;
        };

        if (field == null) {
            return;
        }

        SortOrder order = "asc".equalsIgnoreCase(sortDirection)
            ? SortOrder.Asc
            : SortOrder.Desc;

        builder.withSort(s -> s.field(f -> f.field(field).order(order)));
        if (!"nameSort".equals(field)) {
            builder.withSort(s -> s.field(f -> f.field("nameSort").order(SortOrder.Asc)));
        }
    }

    private void applyHighlight(NativeQueryBuilder builder, List<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }

        HighlightParameters parameters = HighlightParameters.builder()
            .withPreTags("<em>")
            .withPostTags("</em>")
            .withFragmentSize(160)
            .withNumberOfFragments(3)
            .withRequireFieldMatch(false)
            .build();

        List<HighlightField> highlightFields = fields.stream()
            .filter(field -> field != null && !field.isBlank())
            .distinct()
            .map(HighlightField::new)
            .toList();

        Highlight highlight = new Highlight(parameters, highlightFields);
        HighlightQuery highlightQuery = new HighlightQuery(highlight, NodeDocument.class);
        builder.withHighlightQuery(highlightQuery);
    }

    private static void addAnyOfTermsFilter(BoolQuery.Builder bool, List<String> fields, List<String> values) {
        if (fields == null || fields.isEmpty() || values == null || values.isEmpty()) {
            return;
        }

        List<String> normalizedFields = fields.stream()
            .filter(v -> v != null && !v.isBlank())
            .toList();
        if (normalizedFields.isEmpty()) {
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
                for (String field : normalizedFields) {
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

        List<String> normalizedFields = fields.stream()
            .filter(v -> v != null && !v.isBlank())
            .toList();
        if (normalizedFields.isEmpty()) {
            return;
        }

        bool.filter(f -> f.bool(b -> {
            for (String field : normalizedFields) {
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

    private SearchResult toSearchResult(SearchHit<NodeDocument> hit) {
        NodeDocument doc = hit.getContent();
        Map<String, List<String>> highlights = hit.getHighlightFields();
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
            .highlights(highlights)
            .matchFields(SearchHighlightHelper.resolveMatchFields(highlights))
            .highlightSummary(SearchHighlightHelper.resolveHighlightSummary(highlights))
            .tags(doc.getTags() != null ? List.copyOf(doc.getTags()) : List.of())
            .categories(doc.getCategories() != null ? List.copyOf(doc.getCategories()) : List.of())
            .correspondent(doc.getCorrespondent())
            .previewStatus(doc.getPreviewStatus())
            .previewFailureReason(doc.getPreviewFailureReason())
            .previewFailureCategory(PreviewFailureClassifier.classify(
                doc.getPreviewStatus(),
                doc.getMimeType(),
                doc.getPreviewFailureReason()
            ))
            .build();
    }

    private NodeDocument createNodeDocument(Document doc) {
        // Keep legacy helper for compatibility; delegate to central builder to ensure fields stay in sync
        NodeDocument nodeDoc = NodeDocument.fromNode(doc);
        nodeDoc.setPermissions(securityService.resolveReadAuthorities(doc));
        return nodeDoc;
    }
}
