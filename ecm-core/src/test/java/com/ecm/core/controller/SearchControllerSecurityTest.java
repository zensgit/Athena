package com.ecm.core.controller;

import com.ecm.core.preview.PreviewPreflightResolver;
import com.ecm.core.preview.PreviewQueueService;
import com.ecm.core.search.FacetedSearchService;
import com.ecm.core.search.FullTextSearchService;
import com.ecm.core.search.SearchIndexService;
import com.ecm.core.search.SearchResult;
import com.ecm.core.service.SecurityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
@ContextConfiguration(classes = {
    SearchController.class,
    SearchControllerSecurityTest.TestSecurityConfig.class
})
class SearchControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FullTextSearchService fullTextSearchService;

    @MockBean
    private SearchIndexService searchIndexService;

    @MockBean
    private FacetedSearchService facetedSearchService;

    @MockBean
    private SecurityService securityService;

    @MockBean
    private PreviewQueueService previewQueueService;

    @MockBean
    private PreviewPreflightResolver previewPreflightResolver;

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll()
                )
                .httpBasic(basic -> {});
            return http.build();
        }
    }

    @Test
    @DisplayName("Search endpoints require authentication")
    void searchRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                .param("q", "alpha"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("Authenticated users can search")
    void searchAllowsAuthenticatedUser() throws Exception {
        Mockito.when(fullTextSearchService.search("alpha", 0, 20, null, null, null, true, List.of()))
            .thenReturn(new PageImpl<>(
                List.of(SearchResult.builder().id("doc-1").build()),
                PageRequest.of(0, 20),
                1
            ));

        mockMvc.perform(get("/api/v1/search")
                .param("q", "alpha"))
            .andExpect(status().isOk());

        Mockito.verify(fullTextSearchService).search("alpha", 0, 20, null, null, null, true, List.of());
    }

    @Test
    @WithMockUser
    @DisplayName("Authenticated users can access advanced search context")
    void advancedSearchContextAllowsAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/v1/search/advanced/context")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"query\":\"invoice\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Authenticated USER can access advanced search stats")
    void advancedSearchStatsAllowsUser() throws Exception {
        Mockito.when(facetedSearchService.search(Mockito.any()))
            .thenReturn(FacetedSearchService.FacetedSearchResponse.builder()
                .results(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0))
                .facets(Map.of())
                .totalHits(0)
                .queryTime(0)
                .suggestions(List.of())
                .build());

        mockMvc.perform(post("/api/v1/search/advanced/stats")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"query\":\"invoice\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Authenticated ADMIN can access advanced search stats")
    void advancedSearchStatsAllowsAdmin() throws Exception {
        Mockito.when(facetedSearchService.search(Mockito.any()))
            .thenReturn(FacetedSearchService.FacetedSearchResponse.builder()
                .results(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0))
                .facets(Map.of())
                .totalHits(0)
                .queryTime(0)
                .suggestions(List.of())
                .build());

        mockMvc.perform(post("/api/v1/search/advanced/stats")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"query\":\"invoice\"}"))
            .andExpect(status().isOk());

        Mockito.verify(facetedSearchService).search(Mockito.any());
    }

    @Test
    @DisplayName("Advanced search pivot stats requires authentication")
    void advancedSearchPivotStatsRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/search/advanced/stats/pivot")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"query\":\"invoice\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Authenticated USER can access advanced search pivot stats")
    void advancedSearchPivotStatsAllowsUser() throws Exception {
        Mockito.when(facetedSearchService.search(Mockito.any()))
            .thenReturn(FacetedSearchService.FacetedSearchResponse.builder()
                .results(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0))
                .facets(Map.of())
                .totalHits(0)
                .queryTime(0)
                .suggestions(List.of())
                .build());

        mockMvc.perform(post("/api/v1/search/advanced/stats/pivot")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"query\":\"invoice\"}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Authenticated ADMIN can access advanced search pivot stats")
    void advancedSearchPivotStatsAllowsAdmin() throws Exception {
        Mockito.when(facetedSearchService.search(Mockito.any()))
            .thenReturn(FacetedSearchService.FacetedSearchResponse.builder()
                .results(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0))
                .facets(Map.of())
                .totalHits(0)
                .queryTime(0)
                .suggestions(List.of())
                .build());

        mockMvc.perform(post("/api/v1/search/advanced/stats/pivot")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"query\":\"invoice\"}"))
            .andExpect(status().isOk());

        Mockito.verify(facetedSearchService, Mockito.atLeastOnce()).search(Mockito.any());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Index rebuild requires admin role")
    void rebuildIndexRequiresAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/search/index/rebuild"))
            .andExpect(status().isForbidden());

        Mockito.verifyNoInteractions(fullTextSearchService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can rebuild index")
    void rebuildIndexAllowsAdmin() throws Exception {
        Mockito.when(fullTextSearchService.rebuildIndex()).thenReturn(3);

        mockMvc.perform(post("/api/v1/search/index/rebuild"))
            .andExpect(status().isOk());

        Mockito.verify(fullTextSearchService).rebuildIndex();
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Index stats require admin role")
    void indexStatsRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/search/index/stats"))
            .andExpect(status().isForbidden());

        Mockito.verifyNoInteractions(fullTextSearchService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can access index stats")
    void indexStatsAllowsAdmin() throws Exception {
        Mockito.when(fullTextSearchService.getIndexStats())
            .thenReturn(Map.of("documentCount", 0L));

        mockMvc.perform(get("/api/v1/search/index/stats"))
            .andExpect(status().isOk());

        Mockito.verify(fullTextSearchService).getIndexStats();
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Queue failed previews by search capabilities requires admin role")
    void queueFailedPreviewsBySearchCapabilitiesRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/search/preview/queue-failed/capabilities"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can access queue failed previews by search capabilities")
    void queueFailedPreviewsBySearchCapabilitiesAllowsAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/search/preview/queue-failed/capabilities"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Queue failed previews by search requires admin role")
    void queueFailedPreviewsBySearchRequiresAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/search/preview/queue-failed")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"query\":\"preview\",\"maxDocuments\":50}"))
            .andExpect(status().isForbidden());

        Mockito.verifyNoInteractions(previewQueueService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can queue failed previews by search")
    void queueFailedPreviewsBySearchAllowsAdmin() throws Exception {
        Mockito.when(fullTextSearchService.advancedSearch(Mockito.any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        mockMvc.perform(post("/api/v1/search/preview/queue-failed")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"query\":\"preview\",\"maxDocuments\":50}"))
            .andExpect(status().isOk());

        Mockito.verify(fullTextSearchService, Mockito.atLeastOnce()).advancedSearch(Mockito.any());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Dry-run queue failed previews by search requires admin role")
    void dryRunQueueFailedPreviewsBySearchRequiresAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"query\":\"preview\",\"maxDocuments\":50}"))
            .andExpect(status().isForbidden());

        Mockito.verifyNoInteractions(previewQueueService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can dry-run queue failed previews by search")
    void dryRunQueueFailedPreviewsBySearchAllowsAdmin() throws Exception {
        Mockito.when(fullTextSearchService.advancedSearch(Mockito.any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"query\":\"preview\",\"maxDocuments\":50}"))
            .andExpect(status().isOk());

        Mockito.verify(fullTextSearchService, Mockito.atLeastOnce()).advancedSearch(Mockito.any());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Dry-run queue failed previews CSV export requires admin role")
    void exportDryRunQueueFailedPreviewsBySearchCsvRequiresAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"query\":\"preview\",\"maxDocuments\":50}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can export dry-run queue failed previews CSV")
    void exportDryRunQueueFailedPreviewsBySearchCsvAllowsAdmin() throws Exception {
        Mockito.when(fullTextSearchService.advancedSearch(Mockito.any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"query\":\"preview\",\"maxDocuments\":50}"))
            .andExpect(status().isOk());

        Mockito.verify(fullTextSearchService, Mockito.atLeastOnce()).advancedSearch(Mockito.any());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Dry-run queue failed previews CSV async export requires admin role")
    void exportDryRunQueueFailedPreviewsBySearchCsvAsyncRequiresAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"query\":\"preview\",\"maxDocuments\":50}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can start dry-run queue failed previews CSV async export")
    void exportDryRunQueueFailedPreviewsBySearchCsvAsyncAllowsAdmin() throws Exception {
        Mockito.when(fullTextSearchService.advancedSearch(Mockito.any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"query\":\"preview\",\"maxDocuments\":50}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Dry-run queue failed previews CSV async export list requires admin role")
    void listDryRunQueueFailedPreviewsBySearchCsvAsyncRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can access dry-run queue failed previews CSV async export list")
    void listDryRunQueueFailedPreviewsBySearchCsvAsyncAllowsAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can list dry-run queue failed previews CSV async exports by status")
    void listDryRunQueueFailedPreviewsBySearchCsvAsyncByStatusAllowsAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async")
                .param("status", "COMPLETED"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Invalid dry-run queue failed previews CSV async export list status returns bad request")
    void listDryRunQueueFailedPreviewsBySearchCsvAsyncByInvalidStatusReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async")
                .param("status", "NOT_A_STATUS"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Dry-run queue failed previews CSV async export summary requires admin role")
    void summaryDryRunQueueFailedPreviewsBySearchCsvAsyncRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async/summary"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can access dry-run queue failed previews CSV async export summary")
    void summaryDryRunQueueFailedPreviewsBySearchCsvAsyncAllowsAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async/summary"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Invalid dry-run queue failed previews CSV async export summary status returns bad request")
    void summaryDryRunQueueFailedPreviewsBySearchCsvAsyncByInvalidStatusReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async/summary")
                .param("status", "NOT_A_STATUS"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Dry-run queue failed previews CSV async export cleanup requires admin role")
    void cleanupDryRunQueueFailedPreviewsBySearchCsvAsyncRequiresAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async/cleanup"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can cleanup dry-run queue failed previews CSV async export tasks")
    void cleanupDryRunQueueFailedPreviewsBySearchCsvAsyncAllowsAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async/cleanup"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Dry-run queue failed previews CSV async export cleanup rejects active status filter")
    void cleanupDryRunQueueFailedPreviewsBySearchCsvAsyncRejectsActiveStatusFilter() throws Exception {
        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async/cleanup")
                .param("status", "RUNNING"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Dry-run queue failed previews CSV async export cleanup rejects invalid status filter")
    void cleanupDryRunQueueFailedPreviewsBySearchCsvAsyncRejectsInvalidStatusFilter() throws Exception {
        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async/cleanup")
                .param("status", "NOT_A_STATUS"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Dry-run queue failed previews CSV async export cancel-active requires admin role")
    void cancelActiveDryRunQueueFailedPreviewsBySearchCsvAsyncRequiresAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async/cancel-active"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can cancel active dry-run queue failed previews CSV async export tasks")
    void cancelActiveDryRunQueueFailedPreviewsBySearchCsvAsyncAllowsAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async/cancel-active"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can cancel active dry-run queue failed previews CSV async export tasks by QUEUED status")
    void cancelActiveDryRunQueueFailedPreviewsBySearchCsvAsyncByQueuedStatusAllowsAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async/cancel-active")
                .param("status", "QUEUED"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Dry-run queue failed previews CSV async export cancel-active rejects terminal status filter")
    void cancelActiveDryRunQueueFailedPreviewsBySearchCsvAsyncRejectsTerminalStatusFilter() throws Exception {
        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async/cancel-active")
                .param("status", "COMPLETED"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Dry-run queue failed previews CSV async export cancel requires admin role")
    void cancelDryRunQueueFailedPreviewsBySearchCsvAsyncRequiresAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async/non-existent-task/cancel"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can access dry-run queue failed previews CSV async export cancel endpoint")
    void cancelDryRunQueueFailedPreviewsBySearchCsvAsyncAllowsAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/search/preview/queue-failed/dry-run/export-async/non-existent-task/cancel"))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Dry-run queue failed previews CSV async export status requires admin role")
    void exportDryRunQueueFailedPreviewsBySearchCsvAsyncStatusRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async/non-existent-task"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can access dry-run queue failed previews CSV async export status endpoint")
    void exportDryRunQueueFailedPreviewsBySearchCsvAsyncStatusAllowsAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async/non-existent-task"))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("Dry-run queue failed previews CSV async export download requires admin role")
    void exportDryRunQueueFailedPreviewsBySearchCsvAsyncDownloadRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async/non-existent-task/download"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can access dry-run queue failed previews CSV async export download endpoint")
    void exportDryRunQueueFailedPreviewsBySearchCsvAsyncDownloadAllowsAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/search/preview/queue-failed/dry-run/export-async/non-existent-task/download"))
            .andExpect(status().isNotFound());
    }
}
