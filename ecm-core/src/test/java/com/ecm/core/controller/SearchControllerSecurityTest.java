package com.ecm.core.controller;

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
        Mockito.when(fullTextSearchService.search("alpha", 0, 20, null, null))
            .thenReturn(new PageImpl<>(
                List.of(SearchResult.builder().id("doc-1").build()),
                PageRequest.of(0, 20),
                1
            ));

        mockMvc.perform(get("/api/v1/search")
                .param("q", "alpha"))
            .andExpect(status().isOk());

        Mockito.verify(fullTextSearchService).search("alpha", 0, 20, null, null);
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
}
