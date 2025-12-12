package com.ecm.core.controller;

import com.ecm.core.search.FacetedSearchService;
import com.ecm.core.search.FullTextSearchService;
import com.ecm.core.search.SearchIndexService;
import com.ecm.core.search.SearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    private MockMvc mockMvc;

    @Mock
    private FullTextSearchService fullTextSearchService;

    @Mock
    private SearchIndexService searchIndexService;

    @Mock
    private FacetedSearchService facetedSearchService;

    @InjectMocks
    private SearchController searchController;

    @BeforeEach
    void setup() {
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
        Mockito.when(fullTextSearchService.search("keyword", 0, 10)).thenReturn(page);

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
}
