package com.ecm.core.search;

import com.ecm.core.entity.Document;
import com.ecm.core.repository.DocumentRepository;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
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

    private static final List<String> SEARCH_FIELDS = List.of(
        "name^2",
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
        if (!searchEnabled) {
            log.warn("Search is disabled");
            return Page.empty();
        }

        Pageable pageable = PageRequest.of(page, size);

        try {
            Query query = buildFullTextQuery(queryText, pageable);
            SearchHits<NodeDocument> searchHits = elasticsearchOperations.search(
                query, NodeDocument.class, IndexCoordinates.of(INDEX_NAME));

            List<SearchResult> results = searchHits.stream()
                .map(this::toSearchResult)
                .collect(Collectors.toList());

            return new PageImpl<>(results, pageable, searchHits.getTotalHits());

        } catch (Exception e) {
            log.error("Search failed for query: {}", queryText, e);
            return Page.empty();
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

            List<SearchResult> results = searchHits.stream()
                .map(this::toSearchResult)
                .collect(Collectors.toList());

            return new PageImpl<>(results, pageable, searchHits.getTotalHits());

        } catch (Exception e) {
            log.error("Advanced search failed", e);
            return Page.empty();
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

        } catch (Exception e) {
            log.error("Index rebuild failed", e);
            return -1;

        } finally {
            rebuildInProgress.set(false);
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
        } catch (Exception e) {
            return Map.of(
                "indexName", INDEX_NAME,
                "error", e.getMessage(),
                "searchEnabled", searchEnabled
            );
        }
    }

    private Query buildFullTextQuery(String queryText, Pageable pageable) {
        String searchTerm = queryText != null ? queryText.trim() : "";

        NativeQueryBuilder builder = NativeQuery.builder()
            .withPageable(pageable)
            .withFilter(f -> f.term(t -> t.field("deleted").value(false)));

        if (searchTerm.isEmpty()) {
            builder.withQuery(q -> q.matchAll(ma -> ma));
        } else {
            builder.withQuery(q -> q.multiMatch(m -> m
                .query(searchTerm)
                .fields(SEARCH_FIELDS)
                .type(TextQueryType.BestFields)
                .operator(Operator.Or)
            ));
        }

        return builder.build();
    }

    private Query buildAdvancedQuery(SearchRequest request, Pageable pageable) {
        CriteriaQuery query = new CriteriaQuery(new Criteria());

        // Text search
        if (request.getQuery() != null && !request.getQuery().isEmpty()) {
            String searchTerm = request.getQuery().trim();
            query.addCriteria(
                new Criteria("name").matches(searchTerm)
                    .or("content").matches(searchTerm)
                    .or("textContent").matches(searchTerm)
                    .or("extractedText").matches(searchTerm)
                    .or("metadata.extractedText").matches(searchTerm)
            );
        }

        // Apply filters from SearchRequest
        if (request.getFilters() != null) {
            SearchFilters filters = request.getFilters();

            if (filters.getMimeTypes() != null && !filters.getMimeTypes().isEmpty()) {
                query.addCriteria(new Criteria("mimeType").in(filters.getMimeTypes()));
            }

            if (filters.getTags() != null && !filters.getTags().isEmpty()) {
                query.addCriteria(new Criteria("tags").in(filters.getTags()));
            }

            if (filters.getCategories() != null && !filters.getCategories().isEmpty()) {
                query.addCriteria(new Criteria("categories").in(filters.getCategories()));
            }

            if (filters.getCreatedBy() != null) {
                query.addCriteria(new Criteria("createdBy").is(filters.getCreatedBy()));
            }

            if (filters.getDateFrom() != null) {
                query.addCriteria(new Criteria("createdDate").greaterThanEqual(filters.getDateFrom()));
            }

            if (filters.getDateTo() != null) {
                query.addCriteria(new Criteria("createdDate").lessThanEqual(filters.getDateTo()));
            }

            if (filters.getModifiedFrom() != null) {
                query.addCriteria(new Criteria("lastModifiedDate").greaterThanEqual(filters.getModifiedFrom()));
            }

            if (filters.getModifiedTo() != null) {
                query.addCriteria(new Criteria("lastModifiedDate").lessThanEqual(filters.getModifiedTo()));
            }

            if (filters.getMinSize() != null) {
                query.addCriteria(new Criteria("fileSize").greaterThanEqual(filters.getMinSize()));
            }

            if (filters.getMaxSize() != null) {
                query.addCriteria(new Criteria("fileSize").lessThanEqual(filters.getMaxSize()));
            }
            
            if (filters.getPath() != null && !filters.getPath().isEmpty()) {
                query.addCriteria(new Criteria("path").startsWith(filters.getPath()));
            }

            if (!filters.isIncludeDeleted()) {
                query.addCriteria(new Criteria("deleted").is(false));
            }
        } else {
            query.addCriteria(new Criteria("deleted").is(false));
        }

        query.setPageable(pageable);

        return query;
    }

    private SearchResult toSearchResult(SearchHit<NodeDocument> hit) {
        NodeDocument doc = hit.getContent();
        return SearchResult.builder()
            .id(doc.getId())
            .name(doc.getName())
            .description(doc.getDescription())
            .mimeType(doc.getMimeType())
            .fileSize(doc.getFileSize())
            .createdBy(doc.getCreatedBy())
            .createdDate(doc.getCreatedDate())
            .score(hit.getScore())
            .highlights(hit.getHighlightFields())
            .tags(doc.getTags() != null ? List.copyOf(doc.getTags()) : List.of())
            .categories(doc.getCategories() != null ? List.copyOf(doc.getCategories()) : List.of())
            .build();
    }

    private NodeDocument createNodeDocument(Document doc) {
        // Keep legacy helper for compatibility; delegate to central builder to ensure fields stay in sync
        return NodeDocument.fromNode(doc);
    }
}
