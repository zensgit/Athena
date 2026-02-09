package com.ecm.core.controller;

import com.ecm.core.search.*;
import com.ecm.core.search.FacetedSearchService.*;
import com.ecm.core.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Search API Controller
 *
 * Provides REST endpoints for:
 * - Full-text search with highlighting
 * - Advanced search with filters
 * - Faceted search with aggregations
 * - Index management (rebuild, stats)
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Full-text search APIs")
public class SearchController {

    private final FullTextSearchService fullTextSearchService;
    private final SearchIndexService searchIndexService;
    private final FacetedSearchService facetedSearchService;
    private final SecurityService securityService;

    // ==================== Search Endpoints ====================

    @GetMapping
    @Operation(summary = "Full-text search",
               description = "Search documents with full-text matching and highlighting")
    public ResponseEntity<Page<SearchResult>> search(
            @Parameter(description = "Search query")
            @RequestParam String q,
            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field (relevance, name, modified, size)")
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "Sort direction (asc, desc)")
            @RequestParam(required = false) String sortDirection,
            @Parameter(description = "Optional folder scope (UUID). When set, search is limited to this folder.")
            @RequestParam(required = false) String folderId,
            @Parameter(description = "When folderId is set, whether to include subfolders (default true).")
            @RequestParam(defaultValue = "true") boolean includeChildren,
            @Parameter(description = "Optional preview status filter (CSV). Example: READY,FAILED,UNSUPPORTED,PENDING")
            @RequestParam(required = false, name = "previewStatus") String previewStatus) {

        List<String> previewStatuses = parseCsvParam(previewStatus);
        Page<SearchResult> results = fullTextSearchService.search(
            q,
            page,
            size,
            sortBy,
            sortDirection,
            folderId,
            includeChildren,
            previewStatuses
        );
        return ResponseEntity.ok(results);
    }

    private static List<String> parseCsvParam(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toList();
    }

    @GetMapping("/diagnostics")
    @Operation(summary = "Search diagnostics",
               description = "Return applied ACL filter context for the current user")
    public ResponseEntity<SearchDiagnosticsResponse> searchDiagnostics() {
        String username = securityService.getCurrentUser();
        boolean isAdmin = securityService.hasRole("ROLE_ADMIN");
        Set<String> authorities = securityService.getUserAuthorities(username);
        int authorityCount = authorities == null ? 0 : authorities.size();
        List<String> sampleAuthorities = authorities == null
            ? List.of()
            : authorities.stream().sorted().limit(8).toList();
        String note = isAdmin
            ? "Admin role bypasses read filter."
            : (authorityCount == 0
                ? "No authorities resolved; search results may be empty."
                : "Read filter applied to search results.");

        return ResponseEntity.ok(new SearchDiagnosticsResponse(
            username,
            isAdmin,
            !isAdmin,
            authorityCount,
            sampleAuthorities,
            note,
            java.time.Instant.now().toString()
        ));
    }

    @PostMapping("/advanced")
    @Operation(summary = "Advanced search",
               description = "Search with filters, facets, and pagination")
    public ResponseEntity<Page<SearchResult>> advancedSearch(
            @RequestBody SearchRequest request) {

        if (request.getPageable() == null) {
            request.setPageable(new SimplePageRequest());
        }

        Page<SearchResult> results = fullTextSearchService.advancedSearch(request);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/quick")
    @Operation(summary = "Quick search",
               description = "Simple keyword search for autocomplete/suggestions")
    public ResponseEntity<Page<SearchResult>> quickSearch(
            @Parameter(description = "Search query")
            @RequestParam String q,
            @Parameter(description = "Maximum results")
            @RequestParam(defaultValue = "10") int limit) {

        Page<SearchResult> results = fullTextSearchService.search(q, 0, limit);
        return ResponseEntity.ok(results);
    }

    public record SearchDiagnosticsResponse(
        String username,
        boolean admin,
        boolean readFilterApplied,
        int authorityCount,
        List<String> authoritySample,
        String note,
        String generatedAt
    ) {}

    // ==================== Index Management Endpoints ====================

    @PostMapping("/index/rebuild")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rebuild search index",
               description = "Rebuild the entire search index from PostgreSQL (admin only)")
    public ResponseEntity<Map<String, Object>> rebuildIndex() {
        int result = fullTextSearchService.rebuildIndex();

        if (result == -1) {
            return ResponseEntity.accepted().body(Map.of(
                "status", "in_progress",
                "message", "Index rebuild already in progress"
            ));
        }

        return ResponseEntity.ok(Map.of(
            "status", "completed",
            "documentsIndexed", result,
            "message", "Index rebuild completed successfully"
        ));
    }

    @GetMapping("/index/rebuild/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get rebuild status",
               description = "Check the progress of index rebuild operation")
    public ResponseEntity<Map<String, Object>> getRebuildStatus() {
        return ResponseEntity.ok(fullTextSearchService.getRebuildStatus());
    }

    @GetMapping("/index/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get index statistics",
               description = "Get search index statistics")
    public ResponseEntity<Map<String, Object>> getIndexStats() {
        return ResponseEntity.ok(fullTextSearchService.getIndexStats());
    }

    // ==================== Single Document Indexing ====================

    @PostMapping("/index/{documentId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('EDITOR')")
    @Operation(summary = "Index single document",
               description = "Add or update a single document in the search index")
    public ResponseEntity<Map<String, Object>> indexDocument(
            @PathVariable String documentId,
            @RequestParam(defaultValue = "false") boolean refresh) {

        try {
            searchIndexService.indexDocument(documentId, refresh);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "documentId", documentId,
                "refreshed", refresh,
                "message", "Document indexed successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "documentId", documentId,
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/index/query")
    @PreAuthorize("hasRole('ADMIN') or hasRole('EDITOR')")
    @Operation(summary = "Index documents by name",
               description = "Re-index documents whose names contain the provided query text")
    public ResponseEntity<Map<String, Object>> indexByQuery(
            @RequestParam String q,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "false") boolean refresh) {
        int indexed = searchIndexService.indexDocumentsByName(q, limit, refresh);
        return ResponseEntity.ok(Map.of(
            "status", "success",
            "query", q,
            "indexed", indexed,
            "refreshed", refresh
        ));
    }

    @GetMapping("/index/{documentId}/status")
    @PreAuthorize("hasRole('ADMIN') or hasRole('EDITOR')")
    @Operation(summary = "Check document index status",
               description = "Check if a document is present in the search index")
    public ResponseEntity<Map<String, Object>> getIndexStatus(
            @PathVariable String documentId) {

        boolean indexed = searchIndexService.isDocumentIndexed(documentId);
        return ResponseEntity.ok(Map.of(
            "documentId", documentId,
            "indexed", indexed
        ));
    }

    @DeleteMapping("/index/{documentId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Remove document from index",
               description = "Remove a document from the search index")
    public ResponseEntity<Map<String, Object>> removeFromIndex(
            @PathVariable String documentId) {

        try {
            searchIndexService.deleteDocument(documentId);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "documentId", documentId,
                "message", "Document removed from index"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "documentId", documentId,
                "message", e.getMessage()
            ));
        }
    }

    // ==================== Faceted Search Endpoints ====================

    @PostMapping("/faceted")
    @Operation(summary = "Faceted search",
               description = "Search with faceted navigation and aggregations")
    public ResponseEntity<FacetedSearchResponse> facetedSearch(
            @RequestBody FacetedSearchRequest request) {

        if (request.getPageable() == null) {
            request.setPageable(new SimplePageRequest());
        }

        FacetedSearchResponse response = facetedSearchService.search(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/smart")
    @Operation(summary = "Smart search",
               description = "Intelligent search with automatic query enhancement and facets")
    public ResponseEntity<FacetedSearchResponse> smartSearch(
            @Parameter(description = "Search query")
            @RequestParam String q) {

        FacetedSearchResponse response = facetedSearchService.smartSearch(q);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/spellcheck")
    @Operation(summary = "Spellcheck suggestions",
               description = "Return \"Did you mean\" suggestions for a query")
    public ResponseEntity<List<String>> spellcheck(
            @Parameter(description = "Search query")
            @RequestParam String q,
            @Parameter(description = "Maximum suggestions")
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(facetedSearchService.getSpellcheckSuggestions(q, limit));
    }

    @GetMapping("/folder/{folderPath}")
    @Operation(summary = "Search in folder",
               description = "Search within a specific folder and its subfolders")
    public ResponseEntity<FacetedSearchResponse> searchInFolder(
            @Parameter(description = "Search query")
            @RequestParam String q,
            @Parameter(description = "Folder path")
            @PathVariable String folderPath) {

        FacetedSearchResponse response = facetedSearchService.searchInFolder(q, "/" + folderPath);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/facets")
    @Operation(summary = "Get available facets",
               description = "Get facet values and counts for a query without full search results")
    public ResponseEntity<Map<String, List<FacetValue>>> getAvailableFacets(
            @Parameter(description = "Search query")
            @RequestParam(required = false) String q) {

        Map<String, List<FacetValue>> facets = facetedSearchService.getAvailableFacets(q);
        return ResponseEntity.ok(facets);
    }

    @GetMapping("/suggestions")
    @Operation(summary = "Get search suggestions",
               description = "Get autocomplete suggestions based on prefix")
    public ResponseEntity<List<String>> getSuggestions(
            @Parameter(description = "Search prefix")
            @RequestParam String prefix,
            @Parameter(description = "Maximum suggestions")
            @RequestParam(defaultValue = "10") int limit) {

        List<String> suggestions = facetedSearchService.getSuggestions(prefix, limit);
        return ResponseEntity.ok(suggestions);
    }

    @GetMapping("/similar/{documentId}")
    @Operation(summary = "Find similar documents",
               description = "Find documents similar to a given document")
    public ResponseEntity<List<SearchResult>> findSimilar(
            @Parameter(description = "Document ID")
            @PathVariable String documentId,
            @Parameter(description = "Maximum results")
            @RequestParam(defaultValue = "5") int maxResults) {

        List<SearchResult> similar = facetedSearchService.findSimilar(documentId, maxResults);
        return ResponseEntity.ok(similar);
    }

    @GetMapping("/filters/suggested")
    @Operation(summary = "Get suggested filters",
               description = "Get smart filter suggestions based on query results")
    public ResponseEntity<List<SuggestedFilter>> getSuggestedFilters(
            @Parameter(description = "Search query")
            @RequestParam(required = false) String q) {

        List<SuggestedFilter> filters = facetedSearchService.getSuggestedFilters(q);
        return ResponseEntity.ok(filters);
    }
}
