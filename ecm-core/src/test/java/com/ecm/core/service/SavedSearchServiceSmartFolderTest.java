package com.ecm.core.service;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.SavedSearch;
import com.ecm.core.repository.SavedSearchRepository;
import com.ecm.core.search.FacetedSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavedSearchServiceSmartFolderTest {

    @Mock private SavedSearchRepository savedSearchRepository;
    @Mock private SecurityService securityService;
    @Mock private FacetedSearchService facetedSearchService;
    @Mock private FolderService folderService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private SavedSearchService savedSearchService;

    @Test
    @DisplayName("Create smart folder forwards saved search query params into folder creation")
    void createSmartFolderUsesSavedSearchQueryCriteria() {
        UUID savedSearchId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();

        SavedSearch savedSearch = SavedSearch.builder()
            .id(savedSearchId)
            .userId("alice")
            .name("Invoices")
            .queryParams(Map.of("query", "invoice", "pathPrefix", "/finance"))
            .build();

        Folder createdFolder = new Folder();
        createdFolder.setId(UUID.randomUUID());
        createdFolder.setName("Invoices");
        createdFolder.setSmart(true);

        when(securityService.getCurrentUser()).thenReturn("alice");
        when(savedSearchRepository.findById(savedSearchId)).thenReturn(Optional.of(savedSearch));
        when(objectMapper.convertValue(any(), org.mockito.ArgumentMatchers.eq(java.util.LinkedHashMap.class)))
            .thenReturn(new java.util.LinkedHashMap<>(savedSearch.getQueryParams()));
        when(folderService.createFolder(any(FolderService.CreateFolderRequest.class))).thenReturn(createdFolder);

        Folder result = savedSearchService.createSmartFolder(savedSearchId, null, "folder from search", parentId);

        assertThat(result).isSameAs(createdFolder);

        ArgumentCaptor<FolderService.CreateFolderRequest> captor =
            ArgumentCaptor.forClass(FolderService.CreateFolderRequest.class);
        verify(folderService).createFolder(captor.capture());
        FolderService.CreateFolderRequest request = captor.getValue();
        assertThat(request.name()).isEqualTo("Invoices");
        assertThat(request.description()).isEqualTo("folder from search");
        assertThat(request.parentId()).isEqualTo(parentId);
        assertThat(request.isSmart()).isTrue();
        assertThat(request.queryCriteria()).containsEntry("query", "invoice");
        assertThat(request.queryCriteria()).containsEntry("pathPrefix", "/finance");
    }
}
