package com.ecm.core.service;

import com.ecm.core.repository.SavedSearchRepository;
import com.ecm.core.search.FacetedSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SavedSearchServiceTemplateTest {

    @Mock
    private SavedSearchRepository savedSearchRepository;

    @Mock
    private SecurityService securityService;

    @Mock
    private FacetedSearchService facetedSearchService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SavedSearchService savedSearchService;

    @Test
    @DisplayName("List built-in templates returns non-empty catalog")
    void listBuiltInTemplatesShouldReturnCatalog() {
        List<SavedSearchService.SavedSearchTemplate> templates = savedSearchService.listBuiltInTemplates(null);

        assertThat(templates).isNotEmpty();
        assertThat(templates)
            .extracting(SavedSearchService.SavedSearchTemplate::id)
            .contains("failed-preview-last7d", "large-documents-last30d");
    }

    @Test
    @DisplayName("List built-in templates supports case-insensitive tag filter")
    void listBuiltInTemplatesShouldFilterByTagCaseInsensitive() {
        List<SavedSearchService.SavedSearchTemplate> templates = savedSearchService.listBuiltInTemplates("GoVeRnAnCe");

        assertThat(templates).isNotEmpty();
        assertThat(templates)
            .allMatch(template -> template.tags().stream().anyMatch(tag -> "governance".equalsIgnoreCase(tag)));
    }

    @Test
    @DisplayName("List built-in templates returns empty when tag not matched")
    void listBuiltInTemplatesShouldReturnEmptyForUnknownTag() {
        List<SavedSearchService.SavedSearchTemplate> templates = savedSearchService.listBuiltInTemplates("non-existent-tag");

        assertThat(templates).isEmpty();
    }
}
