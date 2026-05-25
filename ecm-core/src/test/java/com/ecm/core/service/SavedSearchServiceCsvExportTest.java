package com.ecm.core.service;

import com.ecm.core.entity.SavedSearch;
import com.ecm.core.repository.SavedSearchRepository;
import com.ecm.core.search.FacetedSearchService;
import com.ecm.core.search.FacetedSearchService.FacetedSearchRequest;
import com.ecm.core.search.FacetedSearchService.FacetedSearchResponse;
import com.ecm.core.search.SearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavedSearchServiceCsvExportTest {

    @Mock private SavedSearchRepository savedSearchRepository;
    @Mock private SecurityService securityService;
    @Mock private FacetedSearchService facetedSearchService;
    @Mock private FolderService folderService;

    private SavedSearchService service;

    private final UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @BeforeEach
    void setUp() {
        // Real ObjectMapper so queryParams -> FacetedSearchRequest conversion works.
        service = new SavedSearchService(
            savedSearchRepository, securityService, facetedSearchService, folderService, new ObjectMapper());
    }

    private SavedSearch ownedSearch() {
        SavedSearch s = new SavedSearch();
        s.setId(id);
        s.setName("My Contracts");
        s.setUserId("alice");
        s.setQueryParams(Map.of("query", "contract"));
        return s;
    }

    private SearchResult result(String name) {
        return SearchResult.builder()
            .id(UUID.randomUUID())
            .name(name)
            .path("/Sites/legal/" + name)
            .nodeType("DOCUMENT")
            .mimeType("application/pdf")
            .fileSize(2048L)
            .currentVersionLabel("1.0")
            .createdBy("alice")
            .createdDate(LocalDateTime.of(2026, 5, 25, 9, 0))
            .lastModifiedBy("bob")
            .lastModifiedDate(LocalDateTime.of(2026, 5, 25, 10, 0))
            .build();
    }

    private FacetedSearchResponse responseWith(SearchResult... results) {
        Page<SearchResult> page = new PageImpl<>(List.of(results));
        return FacetedSearchResponse.builder().results(page).totalHits(results.length).build();
    }

    @Test
    @DisplayName("export produces header + rows with RFC-4180 escaping")
    void exportProducesHeaderAndEscapedRows() {
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(savedSearchRepository.findById(id)).thenReturn(Optional.of(ownedSearch()));
        when(facetedSearchService.search(any())).thenReturn(responseWith(result("A,B\"C.pdf")));

        String csv = service.exportSavedSearchCsv(id, null);

        assertTrue(csv.startsWith("Name,Path,Type,MIME Type,Size (bytes),Version,Created By,Created Date,Last Modified By,Last Modified Date\n"),
            "header columns in agreed order");
        assertTrue(csv.contains("\"A,B\"\"C.pdf\""), "name with comma + quotes must be CSV-escaped");
        assertTrue(csv.contains("application/pdf") && csv.contains("2048") && csv.contains("DOCUMENT"));
    }

    @Test
    @DisplayName("default limit caps the search page size at 1000")
    void defaultLimitIs1000() {
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(savedSearchRepository.findById(id)).thenReturn(Optional.of(ownedSearch()));
        when(facetedSearchService.search(any())).thenReturn(responseWith());

        service.exportSavedSearchCsv(id, null);

        ArgumentCaptor<FacetedSearchRequest> captor = ArgumentCaptor.forClass(FacetedSearchRequest.class);
        verify(facetedSearchService).search(captor.capture());
        assertEquals(1000, captor.getValue().getPageable().getSize());
        assertEquals(0, captor.getValue().getPageable().getPage());
    }

    @Test
    @DisplayName("requested limit above the hard cap is clamped to 5000")
    void hardCapAt5000() {
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(savedSearchRepository.findById(id)).thenReturn(Optional.of(ownedSearch()));
        when(facetedSearchService.search(any())).thenReturn(responseWith());

        service.exportSavedSearchCsv(id, 99999);

        ArgumentCaptor<FacetedSearchRequest> captor = ArgumentCaptor.forClass(FacetedSearchRequest.class);
        verify(facetedSearchService).search(captor.capture());
        assertEquals(5000, captor.getValue().getPageable().getSize());
    }

    @Test
    @DisplayName("non-owner cannot export (SecurityException), search never runs")
    void nonOwnerRejected() {
        SavedSearch foreign = ownedSearch();
        foreign.setUserId("bob");
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(savedSearchRepository.findById(id)).thenReturn(Optional.of(foreign));

        assertThrows(SecurityException.class, () -> service.exportSavedSearchCsv(id, null));
    }

    @Test
    @DisplayName("missing saved search throws IllegalArgumentException")
    void notFound() {
        when(securityService.getCurrentUser()).thenReturn("alice");
        when(savedSearchRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.exportSavedSearchCsv(id, null));
    }
}
