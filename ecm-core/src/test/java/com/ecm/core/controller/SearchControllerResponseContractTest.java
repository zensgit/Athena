package com.ecm.core.controller;

import com.ecm.core.preview.PreviewPreflightResolver;
import com.ecm.core.preview.PreviewQueueService;
import com.ecm.core.search.FacetedSearchService;
import com.ecm.core.search.FullTextSearchService;
import com.ecm.core.search.SearchIndexService;
import com.ecm.core.search.SearchResult;
import com.ecm.core.service.SecurityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SearchControllerResponseContractTest {

    @Mock
    private FullTextSearchService fullTextSearchService;

    @Mock
    private SearchIndexService searchIndexService;

    @Mock
    private FacetedSearchService facetedSearchService;

    @Mock
    private SecurityService securityService;

    @Mock
    private PreviewQueueService previewQueueService;

    @Mock
    private PreviewPreflightResolver previewPreflightResolver;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        SearchController controller = new SearchController(
            fullTextSearchService,
            searchIndexService,
            facetedSearchService,
            securityService,
            previewQueueService,
            previewPreflightResolver
        );
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
    }

    @Test
    @DisplayName("POST /search/query locks SearchResult and envelope response contract")
    void queryEnvelopeLocksSearchResultAndEnvelopeContract() throws Exception {
        SearchResult result = SearchResult.builder()
            .id("doc-1")
            .name("quarterly-report.pdf")
            .description(null)
            .path(null)
            .nodeType("DOCUMENT")
            .parentId(null)
            .mimeType("application/pdf")
            .fileSize(null)
            .currentVersionLabel(null)
            .createdBy("alice")
            .createdDate(LocalDateTime.of(2026, 5, 22, 9, 30))
            .lastModifiedBy(null)
            .lastModifiedDate(null)
            .score(4.25f)
            .highlights(Map.of("name", List.of("<em>quarterly</em>")))
            .matchFields(List.of("name"))
            .highlightSummary(null)
            .tags(List.of("finance", "quarterly"))
            .categories(List.of())
            .correspondent(null)
            .record(true)
            .declaredBy("records-admin")
            .declaredAt(null)
            .declaredVersionLabel("1.0")
            .declarationComment(null)
            .recordCategoryId(null)
            .recordCategoryName("Reports")
            .recordCategoryPath(null)
            .previewStatus("FAILED")
            .previewFailureReason(null)
            .previewFailureCategory(null)
            .build();

        FacetedSearchService.FacetedSearchResponse envelopeResponse =
            facetedResponse(
                new PageImpl<>(List.of(result), PageRequest.of(0, 10), 1),
                Map.of("mimeType", List.of(new FacetedSearchService.FacetValue("application/pdf", 1))),
                List.of("quarterly reports"),
                1
            );
        FacetedSearchService.FacetedSearchResponse statsResponse =
            facetedResponse(
                Page.empty(),
                Map.of(
                    "previewStatus", List.of(new FacetedSearchService.FacetValue("FAILED", 1)),
                    "mimeType", List.of(new FacetedSearchService.FacetValue("application/pdf", 1)),
                    "createdBy", List.of(new FacetedSearchService.FacetValue("alice", 1)),
                    "fileSizeRange", List.of(new FacetedSearchService.FacetValue("unknown", 1)),
                    "createdDateRange", List.of(new FacetedSearchService.FacetValue("this-week", 1))
                ),
                List.of(),
                1
            );
        FacetedSearchService.FacetedSearchResponse pivotBucketResponse =
            facetedResponse(
                Page.empty(),
                Map.of(
                    "previewStatus", List.of(new FacetedSearchService.FacetValue("FAILED", 1)),
                    "mimeType", List.of(new FacetedSearchService.FacetValue("application/pdf", 1))
                ),
                List.of(),
                1
            );
        FacetedSearchService.FacetedSearchResponse pivotCellResponse =
            facetedResponse(Page.empty(), Map.of(), List.of(), 1);

        Mockito.when(facetedSearchService.search(Mockito.any()))
            .thenReturn(envelopeResponse, statsResponse, pivotBucketResponse, pivotCellResponse);

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/search/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "  quarterly   report ",
                      "includeRequest": true,
                      "include": ["results", "context", "stats", "pivot", "facets", "suggestions"],
                      "pageable": { "page": 0, "size": 10 },
                      "filters": {
                        "mimeTypes": ["application/pdf"],
                        "previewStatuses": ["FAILED"]
                      },
                      "facets": ["mimeType", "previewStatus"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.request.normalizedQuery").value("quarterly report"))
            .andExpect(jsonPath("$.request.include[0]").value("results"))
            .andExpect(jsonPath("$.facets.mimeType[0].value").value("application/pdf"))
            .andExpect(jsonPath("$.suggestions[0]").value("quarterly reports"))
            .andExpect(jsonPath("$.context.activeFilterKeys[0]").value("mimeTypes"))
            .andExpect(jsonPath("$.stats.previewStatusStats[0].value").value("FAILED"))
            .andExpect(jsonPath("$.pivot.matrix[0].mimeTypeCounts[0].count").value(1))
            .andExpect(jsonPath("$.generatedAt").isNotEmpty())
            .andReturn();

        JsonNode root = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        JsonNode searchResult = root.at("/results/content/0");

        assertEquals(searchResultWireFieldNames(), fieldNames(searchResult));
        assertTrue(searchResult.has("path"), "path must remain present even when null");
        assertTrue(searchResult.get("path").isNull(), "path null shape was trace-corrected and must stay locked");
        assertTrue(searchResult.has("fileSize"), "fileSize must remain present even when null");
        assertTrue(searchResult.get("fileSize").isNull(), "folder/sparse search hits may carry null fileSize");
        assertEquals("N/A", searchResult.get("fileSizeFormatted").asText());
        assertEquals("2026-05-22T09:30:00", searchResult.get("createdDate").asText());
        assertTrue(searchResult.get("lastModifiedDate").isNull());
        assertEquals(List.of(
            "request",
            "results",
            "facets",
            "suggestions",
            "context",
            "stats",
            "pivot",
            "generatedAt"
        ), fieldNames(root));
        assertFalse(root.get("request").isNull());
        assertFalse(root.get("results").isNull());
        assertFalse(root.get("facets").isNull());
        assertFalse(root.get("suggestions").isNull());
        assertFalse(root.get("context").isNull());
        assertFalse(root.get("stats").isNull());
        assertFalse(root.get("pivot").isNull());
    }

    private static FacetedSearchService.FacetedSearchResponse facetedResponse(
        Page<SearchResult> results,
        Map<String, List<FacetedSearchService.FacetValue>> facets,
        List<String> suggestions,
        long totalHits
    ) {
        return FacetedSearchService.FacetedSearchResponse.builder()
            .results(results)
            .facets(facets)
            .totalHits(totalHits)
            .queryTime(0)
            .suggestions(suggestions)
            .build();
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static List<String> searchResultWireFieldNames() {
        return List.of(
            "id",
            "name",
            "description",
            "path",
            "nodeType",
            "parentId",
            "mimeType",
            "fileSize",
            "currentVersionLabel",
            "createdBy",
            "createdDate",
            "lastModifiedBy",
            "lastModifiedDate",
            "score",
            "highlights",
            "matchFields",
            "highlightSummary",
            "tags",
            "categories",
            "correspondent",
            "record",
            "declaredBy",
            "declaredAt",
            "declaredVersionLabel",
            "declarationComment",
            "recordCategoryId",
            "recordCategoryName",
            "recordCategoryPath",
            "previewStatus",
            "previewFailureReason",
            "previewFailureCategory",
            "fileSizeFormatted"
        );
    }
}
