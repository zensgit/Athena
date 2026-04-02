package com.ecm.core.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ecm.core.entity.PreviewStatus;
import com.ecm.core.preview.PreviewPreflightResolver;
import com.ecm.core.preview.PreviewQueueService;
import com.ecm.core.search.FacetedSearchService;
import com.ecm.core.search.FullTextSearchService;
import com.ecm.core.search.SearchIndexService;
import com.ecm.core.search.SearchFilters;
import com.ecm.core.search.SearchResult;
import com.ecm.core.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private FullTextSearchService fullTextSearchService;

    @Mock
    private SearchIndexService searchIndexService;

    @Mock
    private FacetedSearchService facetedSearchService;

    @Mock
    private PreviewQueueService previewQueueService;

    @Mock
    private PreviewPreflightResolver previewPreflightResolver;

    @Mock
    private SecurityService securityService;

    @InjectMocks
    private SearchController searchController;

    @BeforeEach
    void setup() {
        Mockito.lenient().when(previewPreflightResolver.evaluateCandidate(
                Mockito.any(UUID.class),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()
            ))
            .thenAnswer(invocation -> {
                UUID documentId = invocation.getArgument(0);
                Long sourceSize = invocation.getArgument(3);
                return new PreviewPreflightResolver.PreflightDecision(
                    documentId,
                    "pdf",
                    "ACCEPTED",
                    null,
                    "Eligible for preview queue",
                    "pdf",
                    sourceSize,
                    268435456L,
                    List.of("local-pdfbox")
                );
            });
        mockMvc = MockMvcBuilders.standaloneSetup(searchController).build();
    }

    @Test
    @DisplayName("Full-text search returns 200 with content")
    void searchShouldReturnResults() throws Exception {
        Page<SearchResult> page = new PageImpl<>(
            List.of(SearchResult.builder().id("id-1").name("doc").build()),
            PageRequest.of(0, 10),
            1
        );
        Mockito.when(fullTextSearchService.search("keyword", 0, 10, null, null, null, true, List.of()))
            .thenReturn(page);

        mockMvc.perform(get("/api/v1/search")
                .param("q", "keyword")
                .param("page", "0")
                .param("size", "10"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("Faceted search endpoint returns 200")
    void facetedSearchShouldReturnOk() throws Exception {
        Page<SearchResult> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        FacetedSearchService.FacetedSearchResponse response =
            FacetedSearchService.FacetedSearchResponse.builder()
                .results(emptyPage)
                .facets(Map.of())
                .totalHits(0)
                .queryTime(0)
                .build();
        Mockito.when(facetedSearchService.search(Mockito.any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/search/faceted")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"test\"}"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("Advanced search context returns normalized request diagnostics")
    void advancedSearchContextShouldReturnNormalizedDiagnostics() throws Exception {
        mockMvc.perform(post("/api/v1/search/advanced/context")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "  invoice    2026   ",
                      "sortBy": "relevance",
                      "sortDirection": "desc",
                      "highlightEnabled": true,
                      "facets": ["mimeType", "createdBy"],
                      "pageable": {
                        "page": 2,
                        "size": 30
                      },
                      "filters": {
                        "mimeTypes": ["application/pdf"],
                        "tags": ["important"],
                        "folderId": "folder-1",
                        "includeChildren": false
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.normalizedQuery").value("invoice 2026"))
            .andExpect(jsonPath("$.hasFilters").value(true))
            .andExpect(jsonPath("$.filterCounts.mimeTypes").value(1))
            .andExpect(jsonPath("$.filterCounts.tags").value(1))
            .andExpect(jsonPath("$.filterCounts.folderId").value(1))
            .andExpect(jsonPath("$.filterCounts.facets").value(2))
            .andExpect(jsonPath("$.requestedFacetCount").value(2))
            .andExpect(jsonPath("$.includeChildren").value(false))
            .andExpect(jsonPath("$.page").value(2))
            .andExpect(jsonPath("$.size").value(30))
            .andExpect(jsonPath("$.activeFilterKeys[0]").isNotEmpty())
            .andExpect(jsonPath("$.generatedAt").isNotEmpty());
    }

    @Test
    @DisplayName("Unified search query envelope returns request echo, results, context, and stats")
    void queryEnvelopeShouldReturnCombinedResponse() throws Exception {
        Page<SearchResult> page = new PageImpl<>(
            List.of(SearchResult.builder().id("doc-1").name("invoice.pdf").build()),
            PageRequest.of(1, 5),
            1
        );
        Mockito.when(fullTextSearchService.advancedSearch(Mockito.any())).thenReturn(page);
        Mockito.when(facetedSearchService.search(Mockito.any())).thenReturn(
            FacetedSearchService.FacetedSearchResponse.builder()
                .results(Page.empty())
                .facets(Map.of(
                    "previewStatus", List.of(new FacetedSearchService.FacetValue("READY", 3)),
                    "mimeType", List.of(new FacetedSearchService.FacetValue("application/pdf", 3))
                ))
                .totalHits(3)
                .queryTime(0)
                .suggestions(List.of())
                .build()
        );

        mockMvc.perform(post("/api/v1/search/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "  invoice   2026 ",
                      "includeRequest": true,
                      "include": ["results", "context", "stats"],
                      "sortBy": "relevance",
                      "sortDirection": "desc",
                      "pageable": {
                        "page": 1,
                        "size": 5
                      },
                      "filters": {
                        "mimeTypes": ["application/pdf"],
                        "folderId": "folder-1"
                      },
                      "facets": ["previewStatus", "mimeType"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.request.normalizedQuery").value("invoice 2026"))
            .andExpect(jsonPath("$.request.include[0]").value("results"))
            .andExpect(jsonPath("$.results.content[0].id").value("doc-1"))
            .andExpect(jsonPath("$.context.filterCounts.mimeTypes").value(1))
            .andExpect(jsonPath("$.stats.totalHits").value(3))
            .andExpect(jsonPath("$.stats.previewStatusStats[0].value").value("READY"))
            .andExpect(jsonPath("$.pivot").isEmpty())
            .andExpect(jsonPath("$.generatedAt").isNotEmpty());

        Mockito.verify(fullTextSearchService).advancedSearch(Mockito.any());
        Mockito.verify(facetedSearchService).search(Mockito.any());
    }

    @Test
    @DisplayName("Unified search query envelope can return results with facets and suggestions")
    void queryEnvelopeShouldReturnFacetsAndSuggestions() throws Exception {
        FacetedSearchService.FacetedSearchResponse response =
            FacetedSearchService.FacetedSearchResponse.builder()
                .results(new PageImpl<>(
                    List.of(SearchResult.builder().id("doc-2").name("report.pdf").build()),
                    PageRequest.of(0, 20),
                    1
                ))
                .facets(Map.of(
                    "mimeType", List.of(new FacetedSearchService.FacetValue("application/pdf", 1))
                ))
                .totalHits(1)
                .queryTime(0)
                .suggestions(List.of("reporting"))
                .build();
        Mockito.when(facetedSearchService.search(Mockito.any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/search/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "report",
                      "include": ["results", "facets", "suggestions"],
                      "facets": ["mimeType"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results.content[0].id").value("doc-2"))
            .andExpect(jsonPath("$.facets.mimeType[0].value").value("application/pdf"))
            .andExpect(jsonPath("$.suggestions[0]").value("reporting"));

        Mockito.verifyNoInteractions(fullTextSearchService);
        Mockito.verify(facetedSearchService).search(Mockito.any());
    }

    @Test
    @DisplayName("Unified search query envelope supports pivot-only mode without results")
    void queryEnvelopeShouldSupportPivotOnlyMode() throws Exception {
        FacetedSearchService.FacetedSearchResponse bucketResponse =
            FacetedSearchService.FacetedSearchResponse.builder()
                .results(Page.empty())
                .facets(Map.of(
                    "previewStatus", List.of(
                        new FacetedSearchService.FacetValue("READY", 2),
                        new FacetedSearchService.FacetValue("FAILED", 1)
                    ),
                    "mimeType", List.of(
                        new FacetedSearchService.FacetValue("application/pdf", 2),
                        new FacetedSearchService.FacetValue("image/png", 1)
                    )
                ))
                .totalHits(9)
                .queryTime(0)
                .suggestions(List.of())
                .build();

        Mockito.when(facetedSearchService.search(Mockito.any())).thenAnswer(invocation -> {
            FacetedSearchService.FacetedSearchRequest req = invocation.getArgument(0);
            SearchFilters requestFilters = req.getFilters() != null ? req.getFilters() : new SearchFilters();
            boolean matrixCall = requestFilters.getPreviewStatuses() != null
                && requestFilters.getPreviewStatuses().size() == 1
                && requestFilters.getMimeTypes() != null
                && requestFilters.getMimeTypes().size() == 1;
            if (!matrixCall) {
                return bucketResponse;
            }
            return FacetedSearchService.FacetedSearchResponse.builder()
                .results(Page.empty())
                .facets(Map.of())
                .totalHits(7)
                .queryTime(0)
                .suggestions(List.of())
                .build();
        });

        mockMvc.perform(post("/api/v1/search/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "pivot only",
                      "include": ["pivot"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.request").isEmpty())
            .andExpect(jsonPath("$.results").isEmpty())
            .andExpect(jsonPath("$.pivot.totalHits").value(9))
            .andExpect(jsonPath("$.pivot.previewStatusCount").value(2))
            .andExpect(jsonPath("$.pivot.mimeTypeCount").value(2))
            .andExpect(jsonPath("$.pivot.matrix[0].mimeTypeCounts[0].count").value(7));

        Mockito.verifyNoInteractions(fullTextSearchService);
    }

    @Test
    @DisplayName("Advanced search stats returns sorted facet and range aggregations")
    void advancedSearchStatsShouldReturnSortedAggregations() throws Exception {
        FacetedSearchService.FacetedSearchResponse response =
            FacetedSearchService.FacetedSearchResponse.builder()
                .results(Page.empty())
                .facets(Map.of(
                    "previewStatus", List.of(
                        new FacetedSearchService.FacetValue("FAILED", 2),
                        new FacetedSearchService.FacetValue("READY", 5),
                        new FacetedSearchService.FacetValue("FAILED", 1),
                        new FacetedSearchService.FacetValue("PROCESSING", 5)
                    ),
                    "mimeType", List.of(
                        new FacetedSearchService.FacetValue("text/plain", 4),
                        new FacetedSearchService.FacetValue("application/pdf", 4)
                    ),
                    "createdBy", List.of(
                        new FacetedSearchService.FacetValue("admin", 3),
                        new FacetedSearchService.FacetValue("scanner", 1)
                    ),
                    "fileSizeRange", List.of(
                        new FacetedSearchService.FacetValue("0-1MB", 7),
                        new FacetedSearchService.FacetValue("1-10MB", 2)
                    ),
                    "createdDateRange", List.of(
                        new FacetedSearchService.FacetValue("last7d", 4),
                        new FacetedSearchService.FacetValue("last30d", 6)
                    )
                ))
                .totalHits(42)
                .queryTime(0)
                .suggestions(List.of())
                .build();
        Mockito.when(facetedSearchService.search(Mockito.any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/search/advanced/stats")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "  invoice   stats ",
                      "filters": {
                        "mimeTypes": ["application/pdf"]
                      },
                      "facets": ["mimeType", "createdBy", "previewStatus"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.normalizedQuery").value("invoice stats"))
            .andExpect(jsonPath("$.hasFilters").value(true))
            .andExpect(jsonPath("$.totalHits").value(42))
            .andExpect(jsonPath("$.facetFieldCount").value(5))
            .andExpect(jsonPath("$.previewStatusStats[0].value").value("PROCESSING"))
            .andExpect(jsonPath("$.previewStatusStats[0].count").value(5))
            .andExpect(jsonPath("$.previewStatusStats[1].value").value("READY"))
            .andExpect(jsonPath("$.previewStatusStats[1].count").value(5))
            .andExpect(jsonPath("$.previewStatusStats[2].value").value("FAILED"))
            .andExpect(jsonPath("$.previewStatusStats[2].count").value(3))
            .andExpect(jsonPath("$.mimeTypeStats[0].value").value("application/pdf"))
            .andExpect(jsonPath("$.mimeTypeStats[1].value").value("text/plain"))
            .andExpect(jsonPath("$.fileSizeRangeStats[0].value").value("0-1MB"))
            .andExpect(jsonPath("$.createdDateRangeStats[0].value").value("last30d"))
            .andExpect(jsonPath("$.generatedAt").isNotEmpty());

        ArgumentCaptor<FacetedSearchService.FacetedSearchRequest> requestCaptor = ArgumentCaptor.forClass(
            FacetedSearchService.FacetedSearchRequest.class
        );
        Mockito.verify(facetedSearchService).search(requestCaptor.capture());
        FacetedSearchService.FacetedSearchRequest captured = requestCaptor.getValue();
        assertEquals("  invoice   stats ", captured.getQuery());
        assertEquals(1, captured.getPageable().getSize());
        assertEquals(0, captured.getPageable().getPage());
        assertTrue(!captured.isHighlightEnabled());
        assertTrue(!captured.isIncludeSuggestions());
    }

    @Test
    @DisplayName("Advanced search pivot stats returns deterministic top buckets and matrix")
    void advancedSearchPivotStatsShouldReturnDeterministicMatrix() throws Exception {
        FacetedSearchService.FacetedSearchResponse bucketResponse =
            FacetedSearchService.FacetedSearchResponse.builder()
                .results(Page.empty())
                .facets(Map.of(
                    "previewStatus", List.of(
                        new FacetedSearchService.FacetValue("READY", 3),
                        new FacetedSearchService.FacetValue("FAILED", 2),
                        new FacetedSearchService.FacetValue("FAILED", 1),
                        new FacetedSearchService.FacetValue("PROCESSING", 6),
                        new FacetedSearchService.FacetValue("PENDING", 6),
                        new FacetedSearchService.FacetValue("UNSUPPORTED", 1)
                    ),
                    "mimeType", List.of(
                        new FacetedSearchService.FacetValue("text/plain", 5),
                        new FacetedSearchService.FacetValue("application/pdf", 5),
                        new FacetedSearchService.FacetValue("image/png", 7),
                        new FacetedSearchService.FacetValue("application/json", 7),
                        new FacetedSearchService.FacetValue("application/msword", 1)
                    )
                ))
                .totalHits(77)
                .queryTime(0)
                .suggestions(List.of())
                .build();

        Map<String, Long> matrixCounts = new LinkedHashMap<>();
        matrixCounts.put("PENDING|application/json", 61L);
        matrixCounts.put("PENDING|image/png", 62L);
        matrixCounts.put("PENDING|application/pdf", 63L);
        matrixCounts.put("PENDING|text/plain", 64L);
        matrixCounts.put("PROCESSING|application/json", 51L);
        matrixCounts.put("PROCESSING|image/png", 52L);
        matrixCounts.put("PROCESSING|application/pdf", 53L);
        matrixCounts.put("PROCESSING|text/plain", 54L);
        matrixCounts.put("FAILED|application/json", 31L);
        matrixCounts.put("FAILED|image/png", 32L);
        matrixCounts.put("FAILED|application/pdf", 33L);
        matrixCounts.put("FAILED|text/plain", 34L);
        matrixCounts.put("READY|application/json", 21L);
        matrixCounts.put("READY|image/png", 22L);
        matrixCounts.put("READY|application/pdf", 23L);
        matrixCounts.put("READY|text/plain", 24L);

        Mockito.when(facetedSearchService.search(Mockito.any())).thenAnswer(invocation -> {
            FacetedSearchService.FacetedSearchRequest req = invocation.getArgument(0);
            SearchFilters requestFilters = req.getFilters() != null ? req.getFilters() : new SearchFilters();
            List<String> previewStatuses = requestFilters.getPreviewStatuses();
            List<String> mimeTypes = requestFilters.getMimeTypes();
            boolean matrixCall = previewStatuses != null && previewStatuses.size() == 1
                && mimeTypes != null && mimeTypes.size() == 1;

            if (!matrixCall) {
                return bucketResponse;
            }

            String key = previewStatuses.get(0) + "|" + mimeTypes.get(0);
            return FacetedSearchService.FacetedSearchResponse.builder()
                .results(Page.empty())
                .facets(Map.of())
                .totalHits(matrixCounts.getOrDefault(key, 0L))
                .queryTime(0)
                .suggestions(List.of())
                .build();
        });

        mockMvc.perform(post("/api/v1/search/advanced/stats/pivot")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "  pivot   benchmark  ",
                      "filters": {
                        "tags": ["ops"],
                        "includeChildren": false
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.normalizedQuery").value("pivot benchmark"))
            .andExpect(jsonPath("$.hasFilters").value(true))
            .andExpect(jsonPath("$.totalHits").value(77))
            .andExpect(jsonPath("$.previewStatusCount").value(4))
            .andExpect(jsonPath("$.mimeTypeCount").value(4))
            .andExpect(jsonPath("$.previewStatusStats[0].value").value("PENDING"))
            .andExpect(jsonPath("$.previewStatusStats[1].value").value("PROCESSING"))
            .andExpect(jsonPath("$.previewStatusStats[2].value").value("FAILED"))
            .andExpect(jsonPath("$.previewStatusStats[3].value").value("READY"))
            .andExpect(jsonPath("$.mimeTypeStats[0].value").value("application/json"))
            .andExpect(jsonPath("$.mimeTypeStats[1].value").value("image/png"))
            .andExpect(jsonPath("$.mimeTypeStats[2].value").value("application/pdf"))
            .andExpect(jsonPath("$.mimeTypeStats[3].value").value("text/plain"))
            .andExpect(jsonPath("$.matrix[0].previewStatus").value("PENDING"))
            .andExpect(jsonPath("$.matrix[0].mimeTypeCounts[0].mimeType").value("application/json"))
            .andExpect(jsonPath("$.matrix[0].mimeTypeCounts[0].count").value(61))
            .andExpect(jsonPath("$.matrix[1].mimeTypeCounts[2].count").value(53))
            .andExpect(jsonPath("$.matrix[3].mimeTypeCounts[3].count").value(24))
            .andExpect(jsonPath("$.generatedAt").isNotEmpty());

        ArgumentCaptor<FacetedSearchService.FacetedSearchRequest> requestCaptor = ArgumentCaptor.forClass(
            FacetedSearchService.FacetedSearchRequest.class
        );
        Mockito.verify(facetedSearchService, Mockito.times(17)).search(requestCaptor.capture());
        List<FacetedSearchService.FacetedSearchRequest> capturedRequests = requestCaptor.getAllValues();
        FacetedSearchService.FacetedSearchRequest firstCall = capturedRequests.get(0);
        assertEquals("  pivot   benchmark  ", firstCall.getQuery());
        assertEquals(List.of("previewStatus", "mimeType"), firstCall.getFacetFields());
        assertEquals(1, firstCall.getPageable().getSize());
        assertEquals(0, firstCall.getPageable().getPage());
        assertTrue(!firstCall.isHighlightEnabled());
        assertTrue(!firstCall.isIncludeSuggestions());

        long matrixCallCount = 0L;
        for (int i = 1; i < capturedRequests.size(); i++) {
            FacetedSearchService.FacetedSearchRequest captured = capturedRequests.get(i);
            SearchFilters requestFilters = captured.getFilters();
            assertEquals(List.of("ops"), requestFilters.getTags());
            assertTrue(!requestFilters.isIncludeChildren());
            assertEquals(1, requestFilters.getPreviewStatuses().size());
            assertEquals(1, requestFilters.getMimeTypes().size());
            matrixCallCount++;
        }
        assertEquals(16L, matrixCallCount);
    }

    @Test
    @DisplayName("Queue failed previews by search capabilities returns bounded limits")
    void queueFailedPreviewsBySearchCapabilitiesShouldReturnLimits() throws Exception {
        mockMvc.perform(get("/api/v1/search/preview/queue-failed/capabilities"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.defaultMaxDocuments").value(100))
            .andExpect(jsonPath("$.maxMaxDocuments").value(500))
            .andExpect(jsonPath("$.scanPageSize").value(100))
            .andExpect(jsonPath("$.scanLimit").value(5000))
            .andExpect(jsonPath("$.defaultWorkerCount").value(4))
            .andExpect(jsonPath("$.maxWorkerCount").value(16));
    }

    @Test
    @DisplayName("Queue failed previews by search returns queued summary")
    void queueFailedPreviewsBySearchShouldReturnOk() throws Exception {
        String documentId = UUID.randomUUID().toString();
        Page<SearchResult> page = new PageImpl<>(
            List.of(
                SearchResult.builder()
                    .id(documentId)
                    .name("preview-failed.pdf")
                    .mimeType("application/pdf")
                    .previewStatus("FAILED")
                    .previewFailureReason("preview service timeout")
                    .build()
            ),
            PageRequest.of(0, 100),
            1
        );
        Mockito.when(fullTextSearchService.advancedSearch(Mockito.any())).thenReturn(page);
        Mockito.when(previewQueueService.enqueue(UUID.fromString(documentId), false))
            .thenReturn(new PreviewQueueService.PreviewQueueStatus(
                UUID.fromString(documentId),
                PreviewStatus.PROCESSING,
                true,
                1,
                Instant.now(),
                "queued",
                null,
                null,
                LocalDateTime.of(2026, 3, 29, 10, 30, 0)
            ));

        mockMvc.perform(post("/api/v1/search/preview/queue-failed")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "preview-failed",
                      "maxDocuments": 200,
                      "force": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.workerCount").value(4))
            .andExpect(jsonPath("$.scanSkipped").value(0))
            .andExpect(jsonPath("$.reasonBreakdown[0].reason").value("preview service timeout"))
            .andExpect(jsonPath("$.reasonBreakdown[0].count").value(1))
            .andExpect(jsonPath("$.results[0].queueState").value("QUEUED"))
            .andExpect(jsonPath("$.results[0].previewLastUpdated[0]").value(2026))
            .andExpect(jsonPath("$.results[0].previewLastUpdated[1]").value(3))
            .andExpect(jsonPath("$.results[0].previewLastUpdated[2]").value(29))
            .andExpect(jsonPath("$.results[0].previewLastUpdated[3]").value(10))
            .andExpect(jsonPath("$.results[0].previewLastUpdated[4]").value(30));

        Mockito.verify(fullTextSearchService).advancedSearch(Mockito.any());
        Mockito.verify(previewQueueService).enqueue(UUID.fromString(documentId), false);
    }

    @Test
    @DisplayName("Queue failed previews by search returns DECLINED queue state when enqueue skips")
    void queueFailedPreviewsBySearchShouldReturnDeclinedQueueStateWhenSkipped() throws Exception {
        String documentId = UUID.randomUUID().toString();
        Page<SearchResult> page = new PageImpl<>(
            List.of(
                SearchResult.builder()
                    .id(documentId)
                    .name("preview-failed.pdf")
                    .mimeType("application/pdf")
                    .previewStatus("FAILED")
                    .previewFailureReason("preview service timeout")
                    .previewFailureCategory("TEMPORARY")
                    .build()
            ),
            PageRequest.of(0, 100),
            1
        );
        Mockito.when(fullTextSearchService.advancedSearch(Mockito.any())).thenReturn(page);
        Mockito.when(previewQueueService.enqueue(UUID.fromString(documentId), false))
            .thenReturn(new PreviewQueueService.PreviewQueueStatus(
                UUID.fromString(documentId),
                PreviewStatus.FAILED,
                false,
                0,
                null,
                "Preview unsupported"
            ));

        mockMvc.perform(post("/api/v1/search/preview/queue-failed")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "preview-failed",
                      "maxDocuments": 200,
                      "force": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.results[0].outcome").value("SKIPPED"))
            .andExpect(jsonPath("$.results[0].queueState").value("DECLINED"))
            .andExpect(jsonPath("$.results[0].message").value("Preview unsupported"))
            .andExpect(jsonPath("$.results[0].previewFailureReason").value("preview service timeout"))
            .andExpect(jsonPath("$.results[0].previewFailureCategory").value("TEMPORARY"));
    }

    @Test
    @DisplayName("Queue failed previews by search skips unsupported effective failure categories")
    void queueFailedPreviewsBySearchSkipsUnsupportedEffectiveFailureCategory() throws Exception {
        String unsupportedId = UUID.randomUUID().toString();
        String retryableId = UUID.randomUUID().toString();
        Page<SearchResult> page = new PageImpl<>(
            List.of(
                SearchResult.builder()
                    .id(unsupportedId)
                    .name("unsupported.bin")
                    .mimeType("application/octet-stream")
                    .previewStatus("FAILED")
                    .previewFailureReason("preview generation failed")
                    .previewFailureCategory("UNSUPPORTED")
                    .build(),
                SearchResult.builder()
                    .id(retryableId)
                    .name("preview-failed.pdf")
                    .mimeType("application/pdf")
                    .previewStatus("FAILED")
                    .previewFailureReason("preview service timeout")
                    .previewFailureCategory("TEMPORARY")
                    .build()
            ),
            PageRequest.of(0, 100),
            2
        );
        Mockito.when(fullTextSearchService.advancedSearch(Mockito.any())).thenReturn(page);
        Mockito.when(previewQueueService.enqueue(UUID.fromString(retryableId), false))
            .thenReturn(new PreviewQueueService.PreviewQueueStatus(
                UUID.fromString(retryableId),
                PreviewStatus.PROCESSING,
                true,
                1,
                Instant.now(),
                "queued"
            ));

        mockMvc.perform(post("/api/v1/search/preview/queue-failed")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "preview-failed",
                      "maxDocuments": 200,
                      "force": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matched").value(1))
            .andExpect(jsonPath("$.scanSkipped").value(1))
            .andExpect(jsonPath("$.reasonBreakdown[0].reason").value("preview service timeout"))
            .andExpect(jsonPath("$.results", hasSize(1)))
            .andExpect(jsonPath("$.results[0].documentId").value(retryableId));

        Mockito.verify(previewQueueService, Mockito.never()).enqueue(UUID.fromString(unsupportedId), false);
        Mockito.verify(previewQueueService).enqueue(UUID.fromString(retryableId), false);
    }

    @Test
    @DisplayName("Queue failed previews by search clamps worker count")
    void queueFailedPreviewsBySearchShouldClampWorkerCount() throws Exception {
        String firstId = UUID.randomUUID().toString();
        String secondId = UUID.randomUUID().toString();
        Page<SearchResult> page = new PageImpl<>(
            List.of(
                SearchResult.builder()
                    .id(firstId)
                    .name("preview-failed-a.pdf")
                    .mimeType("application/pdf")
                    .previewStatus("FAILED")
                    .previewFailureReason("preview service timeout")
                    .build(),
                SearchResult.builder()
                    .id(secondId)
                    .name("preview-failed-b.pdf")
                    .mimeType("application/pdf")
                    .previewStatus("FAILED")
                    .previewFailureReason("preview service timeout")
                    .build()
            ),
            PageRequest.of(0, 100),
            2
        );
        Mockito.when(fullTextSearchService.advancedSearch(Mockito.any())).thenReturn(page);
        Mockito.when(previewQueueService.enqueue(Mockito.any(), Mockito.eq(false)))
            .thenAnswer(invocation -> new PreviewQueueService.PreviewQueueStatus(
                invocation.getArgument(0),
                PreviewStatus.PROCESSING,
                true,
                1,
                Instant.now(),
                "queued"
            ));

        mockMvc.perform(post("/api/v1/search/preview/queue-failed")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "preview-failed",
                      "maxDocuments": 200,
                      "force": false,
                      "workerCount": 99
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.workerCount").value(16))
            .andExpect(jsonPath("$.scanSkipped").value(0))
            .andExpect(jsonPath("$.queued").value(2))
            .andExpect(jsonPath("$.failed").value(0));

        Mockito.verify(previewQueueService).enqueue(UUID.fromString(firstId), false);
        Mockito.verify(previewQueueService).enqueue(UUID.fromString(secondId), false);
    }

    @Test
    @DisplayName("Dry-run queue failed previews by search returns matched samples")
    void dryRunQueueFailedPreviewsBySearchShouldReturnSamples() throws Exception {
        String documentId = UUID.randomUUID().toString();
        Page<SearchResult> page = new PageImpl<>(
            List.of(
                SearchResult.builder()
                    .id(documentId)
                    .name("preview-failed.pdf")
                    .mimeType("application/pdf")
                    .previewStatus("FAILED")
                    .previewFailureReason("preview service timeout")
                    .previewFailureCategory("TEMPORARY")
                    .build()
            ),
            PageRequest.of(0, 100),
            1
        );
        Mockito.when(fullTextSearchService.advancedSearch(Mockito.any())).thenReturn(page);

        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "preview-failed",
                      "maxDocuments": 200
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.matched").value(1))
            .andExpect(jsonPath("$.scanSkipped").value(0))
            .andExpect(jsonPath("$.workerCount").value(4))
            .andExpect(jsonPath("$.reasonBreakdown[0].reason").value("preview service timeout"))
            .andExpect(jsonPath("$.reasonBreakdown[0].count").value(1))
            .andExpect(jsonPath("$.sampleCount").value(1));

        Mockito.verify(fullTextSearchService).advancedSearch(Mockito.any());
        Mockito.verifyNoInteractions(previewQueueService);
    }

    @Test
    @DisplayName("Dry-run queue failed previews by search clamps worker count")
    void dryRunQueueFailedPreviewsBySearchShouldClampWorkerCount() throws Exception {
        String documentId = UUID.randomUUID().toString();
        Page<SearchResult> page = new PageImpl<>(
            List.of(
                SearchResult.builder()
                    .id(documentId)
                    .name("preview-failed.pdf")
                    .mimeType("application/pdf")
                    .previewStatus("FAILED")
                    .previewFailureReason("preview service timeout")
                    .previewFailureCategory("TEMPORARY")
                    .build()
            ),
            PageRequest.of(0, 100),
            1
        );
        Mockito.when(fullTextSearchService.advancedSearch(Mockito.any())).thenReturn(page);

        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "preview-failed",
                      "maxDocuments": 200,
                      "workerCount": 99
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.matched").value(1))
            .andExpect(jsonPath("$.scanSkipped").value(0))
            .andExpect(jsonPath("$.workerCount").value(16))
            .andExpect(jsonPath("$.sampleCount").value(1));

        Mockito.verify(fullTextSearchService).advancedSearch(Mockito.any());
        Mockito.verifyNoInteractions(previewQueueService);
    }

    @Test
    @DisplayName("Dry-run queue failed previews by search returns skip breakdown diagnostics")
    void dryRunQueueFailedPreviewsBySearchShouldReturnSkipBreakdown() throws Exception {
        String matchedId = UUID.randomUUID().toString();
        String reasonMismatchId = UUID.randomUUID().toString();
        Page<SearchResult> page = new PageImpl<>(
            List.of(
                SearchResult.builder()
                    .id(matchedId)
                    .name("preview-failed-match.pdf")
                    .mimeType("application/pdf")
                    .previewStatus("FAILED")
                    .previewFailureReason("preview service timeout")
                    .previewFailureCategory("TEMPORARY")
                    .build(),
                SearchResult.builder()
                    .id(UUID.randomUUID().toString())
                    .name("ready-node.pdf")
                    .mimeType("application/pdf")
                    .previewStatus("READY")
                    .previewFailureReason(null)
                    .previewFailureCategory(null)
                    .build(),
                SearchResult.builder()
                    .id(reasonMismatchId)
                    .name("preview-failed-mismatch.pdf")
                    .mimeType("application/pdf")
                    .previewStatus("FAILED")
                    .previewFailureReason("preview service timeout secondary")
                    .previewFailureCategory("TEMPORARY")
                    .build(),
                SearchResult.builder()
                    .id(matchedId)
                    .name("preview-failed-duplicate.pdf")
                    .mimeType("application/pdf")
                    .previewStatus("FAILED")
                    .previewFailureReason("preview service timeout")
                    .previewFailureCategory("TEMPORARY")
                    .build()
            ),
            PageRequest.of(0, 100),
            4
        );
        Mockito.when(fullTextSearchService.advancedSearch(Mockito.any())).thenReturn(page);

        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "preview-failed",
                      "reason": "preview service timeout",
                      "maxDocuments": 200
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.matched").value(1))
            .andExpect(jsonPath("$.scanSkipped").value(3))
            .andExpect(content().string(containsString("NON_RETRYABLE")))
            .andExpect(content().string(containsString("REASON_MISMATCH")))
            .andExpect(content().string(containsString("DUPLICATE_DOCUMENT_ID")));

        Mockito.verify(fullTextSearchService).advancedSearch(Mockito.any());
        Mockito.verifyNoInteractions(previewQueueService);
    }

    @Test
    @DisplayName("Dry-run queue failed previews by search includes preflight declined skip reason")
    void dryRunQueueFailedPreviewsBySearchShouldIncludePreflightDeclinedSkipReason() throws Exception {
        String documentId = UUID.randomUUID().toString();
        Page<SearchResult> page = new PageImpl<>(
            List.of(
                SearchResult.builder()
                    .id(documentId)
                    .name("preflight-cad.dwg")
                    .mimeType("application/dwg")
                    .fileSize(1024L)
                    .previewStatus("FAILED")
                    .previewFailureReason("temporary renderer timeout")
                    .previewFailureCategory("TEMPORARY")
                    .build()
            ),
            PageRequest.of(0, 100),
            1
        );
        Mockito.when(fullTextSearchService.advancedSearch(Mockito.any())).thenReturn(page);
        Mockito.when(previewPreflightResolver.evaluateCandidate(
                Mockito.eq(UUID.fromString(documentId)),
                Mockito.any(),
                Mockito.any(),
                Mockito.any()
            ))
            .thenReturn(new PreviewPreflightResolver.PreflightDecision(
                UUID.fromString(documentId),
                "cad",
                "DECLINED",
                "CAD_ENDPOINT_UNCONFIGURED",
                "CAD renderer endpoint is not configured",
                "cad",
                1024L,
                268435456L,
                List.of("cad-remote:unconfigured")
            ));

        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "preflight-cad",
                      "maxDocuments": 200
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matched").value(0))
            .andExpect(jsonPath("$.scanSkipped").value(1))
            .andExpect(content().string(containsString("PREFLIGHT_CAD_ENDPOINT_UNCONFIGURED")));

        Mockito.verifyNoInteractions(previewQueueService);
    }

    @Test
    @DisplayName("Dry-run queue failed previews CSV export returns attachment")
    void exportDryRunQueueFailedPreviewsBySearchCsvShouldReturnAttachment() throws Exception {
        String documentId = UUID.randomUUID().toString();
        Page<SearchResult> page = new PageImpl<>(
            List.of(
                SearchResult.builder()
                    .id(documentId)
                    .name("preview-failed.pdf")
                    .mimeType("application/pdf")
                    .previewStatus("FAILED")
                    .previewFailureReason("preview service timeout")
                    .previewFailureCategory("TEMPORARY")
                    .build()
            ),
            PageRequest.of(0, 100),
            1
        );
        Mockito.when(fullTextSearchService.advancedSearch(Mockito.any())).thenReturn(page);

        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "preview-failed",
                      "maxDocuments": 200
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("search-preview-dry-run-")))
            .andExpect(content().contentTypeCompatibleWith("text/csv"))
            .andExpect(content().string(containsString("reason,count")))
            .andExpect(content().string(containsString("skipReason,count")))
            .andExpect(content().string(containsString("\"workerCount\",\"4\"")))
            .andExpect(content().string(containsString("\"scanSkipped\",\"0\"")))
            .andExpect(content().string(containsString("preview service timeout")));

        Mockito.verify(fullTextSearchService).advancedSearch(Mockito.any());
    }

    @Test
    @DisplayName("Dry-run queue failed previews CSV async export returns completed status and downloadable attachment")
    void exportDryRunQueueFailedPreviewsBySearchCsvAsyncShouldCompleteAndDownload() throws Exception {
        String documentId = UUID.randomUUID().toString();
        Page<SearchResult> page = new PageImpl<>(
            List.of(
                SearchResult.builder()
                    .id(documentId)
                    .name("preview-failed.pdf")
                    .mimeType("application/pdf")
                    .previewStatus("FAILED")
                    .previewFailureReason("preview service timeout")
                    .previewFailureCategory("TEMPORARY")
                    .build()
            ),
            PageRequest.of(0, 100),
            1
        );
        Mockito.when(fullTextSearchService.advancedSearch(Mockito.any())).thenReturn(page);

        MvcResult startResult = mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "preview-failed",
                      "maxDocuments": 200
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").isNotEmpty())
            .andExpect(jsonPath("$.status").value("QUEUED"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andReturn();

        String taskId = objectMapper.readTree(startResult.getResponse().getContentAsString())
            .path("taskId")
            .asText();

        String finalStatus = awaitAsyncDryRunExportTaskTerminalStatus(taskId);
        assertEquals("COMPLETED", finalStatus);

        mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async/{taskId}", taskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").value(taskId))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.error").isEmpty())
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.finishedAt").isNotEmpty())
            .andExpect(jsonPath("$.filename").value(containsString("search-preview-dry-run-")));

        mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async/{taskId}/download", taskId))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", containsString("search-preview-dry-run-")))
            .andExpect(content().contentTypeCompatibleWith("text/csv"))
            .andExpect(content().string(containsString("reason,count")))
            .andExpect(content().string(containsString("\"workerCount\",\"4\"")))
            .andExpect(content().string(containsString("\"scanSkipped\",\"0\"")))
            .andExpect(content().string(containsString("preview service timeout")));
    }

    @Test
    @DisplayName("Dry-run queue failed previews CSV async export list returns recent tasks")
    void listDryRunQueueFailedPreviewsBySearchCsvAsyncTasksShouldReturnRecentTasks() throws Exception {
        Mockito.when(fullTextSearchService.advancedSearch(Mockito.any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        MvcResult startResult = mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "preview-failed",
                      "maxDocuments": 50
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();

        String taskId = objectMapper.readTree(startResult.getResponse().getContentAsString())
            .path("taskId")
            .asText();

        mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async")
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.items[0].taskId").value(taskId))
            .andExpect(jsonPath("$.items[0].createdAt").isNotEmpty());
    }

    @Test
    @DisplayName("Dry-run queue failed previews CSV async export cancel-active cancels all active tasks by default")
    void cancelActiveDryRunQueueFailedPreviewsBySearchCsvAsyncShouldCancelAllActiveTasks() throws Exception {
        CountDownLatch taskStarted = new CountDownLatch(2);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        Mockito.when(fullTextSearchService.advancedSearch(Mockito.any())).thenAnswer(invocation -> {
            taskStarted.countDown();
            allowCompletion.await(3, TimeUnit.SECONDS);
            return new PageImpl<>(List.of(), PageRequest.of(0, 100), 0);
        });

        MvcResult firstStartResult = mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "preview-failed-a",
                      "maxDocuments": 200
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();
        String firstTaskId = objectMapper.readTree(firstStartResult.getResponse().getContentAsString())
            .path("taskId")
            .asText();

        MvcResult secondStartResult = mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "preview-failed-b",
                      "maxDocuments": 200
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();
        String secondTaskId = objectMapper.readTree(secondStartResult.getResponse().getContentAsString())
            .path("taskId")
            .asText();

        assertTrue(taskStarted.await(1, TimeUnit.SECONDS));

        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async/cancel-active"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cancelledCount").value(2))
            .andExpect(jsonPath("$.remainingActiveCount").value(0))
            .andExpect(jsonPath("$.statusFilter").isEmpty())
            .andExpect(jsonPath("$.message").value(containsString("Cancelled 2 active async dry-run export tasks")));

        mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async/{taskId}", firstTaskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async/{taskId}", secondTaskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        allowCompletion.countDown();
    }

    @Test
    @DisplayName("Dry-run queue failed previews CSV async export cancel-active supports QUEUED filter and preserves running task")
    void cancelActiveDryRunQueueFailedPreviewsBySearchCsvAsyncByQueuedStatusShouldSucceed() throws Exception {
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        Mockito.when(fullTextSearchService.advancedSearch(Mockito.any())).thenAnswer(invocation -> {
            taskStarted.countDown();
            allowCompletion.await(3, TimeUnit.SECONDS);
            return new PageImpl<>(List.of(), PageRequest.of(0, 100), 0);
        });

        MvcResult startResult = mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "preview-failed",
                      "maxDocuments": 200
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();

        String taskId = objectMapper.readTree(startResult.getResponse().getContentAsString())
            .path("taskId")
            .asText();
        assertTrue(taskStarted.await(1, TimeUnit.SECONDS));

        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async/cancel-active")
                .param("status", "QUEUED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cancelledCount").value(0))
            .andExpect(jsonPath("$.remainingActiveCount").value(1))
            .andExpect(jsonPath("$.statusFilter").value("QUEUED"))
            .andExpect(jsonPath("$.message").value(containsString("No active async dry-run export tasks with status QUEUED")));

        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async/cancel-active"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cancelledCount").value(1))
            .andExpect(jsonPath("$.remainingActiveCount").value(0));

        mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async/{taskId}", taskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        allowCompletion.countDown();
    }

    @Test
    @DisplayName("Dry-run queue failed previews CSV async export cancel-active rejects terminal status filter")
    void cancelActiveDryRunQueueFailedPreviewsBySearchCsvAsyncByCompletedStatusShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async/cancel-active")
                .param("status", "COMPLETED"))
            .andExpect(status().isBadRequest())
            .andExpect(status().reason(containsString("status filter only supports active states: QUEUED, RUNNING")));
    }

    @Test
    @DisplayName("Dry-run queue failed previews CSV async export cancel-active rejects unknown status filter")
    void cancelActiveDryRunQueueFailedPreviewsBySearchCsvAsyncByInvalidStatusShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async/cancel-active")
                .param("status", "NOT_A_STATUS"))
            .andExpect(status().isBadRequest())
            .andExpect(status().reason(containsString("Unknown async export status: NOT_A_STATUS")));
    }

    @Test
    @DisplayName("Dry-run queue failed previews CSV async export can be cancelled")
    void cancelDryRunQueueFailedPreviewsBySearchCsvAsyncShouldCancelTask() throws Exception {
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        Mockito.when(fullTextSearchService.advancedSearch(Mockito.any())).thenAnswer(invocation -> {
            taskStarted.countDown();
            allowCompletion.await(3, TimeUnit.SECONDS);
            return new PageImpl<>(List.of(), PageRequest.of(0, 100), 0);
        });

        MvcResult startResult = mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "preview-failed",
                      "maxDocuments": 200
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();

        String taskId = objectMapper.readTree(startResult.getResponse().getContentAsString())
            .path("taskId")
            .asText();
        assertTrue(taskStarted.await(1, TimeUnit.SECONDS));

        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async/{taskId}/cancel", taskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").value(taskId))
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.error").value("Cancelled by user"))
            .andExpect(jsonPath("$.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.finishedAt").isNotEmpty())
            .andExpect(jsonPath("$.filename").isEmpty());

        mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async/{taskId}/download", taskId))
            .andExpect(status().isConflict());

        allowCompletion.countDown();
    }

    @Test
    @DisplayName("Dry-run queue failed previews CSV async export cancel returns 404 when task missing")
    void cancelDryRunQueueFailedPreviewsBySearchCsvAsyncShouldReturnNotFoundForMissingTask() throws Exception {
        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async/{taskId}/cancel", "missing-task"))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Dry-run queue failed previews CSV async export download returns 409 before completion")
    void downloadDryRunQueueFailedPreviewsBySearchCsvAsyncShouldReturnConflictWhenNotCompleted() throws Exception {
        CountDownLatch taskStarted = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        Mockito.when(fullTextSearchService.advancedSearch(Mockito.any())).thenAnswer(invocation -> {
            taskStarted.countDown();
            allowCompletion.await(3, TimeUnit.SECONDS);
            return new PageImpl<>(List.of(), PageRequest.of(0, 100), 0);
        });

        MvcResult startResult = mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "preview-failed",
                      "maxDocuments": 200
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();

        String taskId = objectMapper.readTree(startResult.getResponse().getContentAsString())
            .path("taskId")
            .asText();

        assertTrue(taskStarted.await(1, TimeUnit.SECONDS));

        mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async/{taskId}/download", taskId))
            .andExpect(status().isConflict());

        allowCompletion.countDown();
    }

    @Test
    @DisplayName("Dry-run queue failed previews CSV async export marks failed task and download returns 409")
    void exportDryRunQueueFailedPreviewsBySearchCsvAsyncShouldFailAndRejectDownload() throws Exception {
        Mockito.when(fullTextSearchService.advancedSearch(Mockito.any()))
            .thenThrow(new RuntimeException("mock async export failure"));

        MvcResult startResult = mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "preview-failed",
                      "maxDocuments": 200
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskId").isNotEmpty())
            .andReturn();

        String taskId = objectMapper.readTree(startResult.getResponse().getContentAsString())
            .path("taskId")
            .asText();

        String finalStatus = awaitAsyncDryRunExportTaskTerminalStatus(taskId);
        assertEquals("FAILED", finalStatus);

        mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async/{taskId}", taskId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.error").value(containsString("mock async export failure")))
            .andExpect(jsonPath("$.filename").isEmpty());

        mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async/{taskId}/download", taskId))
            .andExpect(status().isConflict());
    }

    private String awaitAsyncDryRunExportTaskTerminalStatus(String taskId) throws Exception {
        for (int i = 0; i < 60; i++) {
            MvcResult statusResult = mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async/{taskId}", taskId))
                .andExpect(status().isOk())
                .andReturn();
            JsonNode statusPayload = objectMapper.readTree(statusResult.getResponse().getContentAsString());
            String statusValue = statusPayload.path("status").asText();
            if ("COMPLETED".equals(statusValue) || "FAILED".equals(statusValue)) {
                return statusValue;
            }
            Thread.sleep(20L);
        }
        fail("async export task did not reach terminal state in time");
        return "";
    }
}
