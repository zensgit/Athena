package com.ecm.core.service;

import com.ecm.core.entity.SavedSearch;
import com.ecm.core.repository.SavedSearchRepository;
import com.ecm.core.search.FacetedSearchService;
import com.ecm.core.search.FacetedSearchService.FacetedSearchRequest;
import com.ecm.core.search.FacetedSearchService.FacetedSearchResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavedSearchService {

    private final SavedSearchRepository savedSearchRepository;
    private final SecurityService securityService;
    private final FacetedSearchService facetedSearchService;
    private final ObjectMapper objectMapper;

    /**
     * Save a search configuration.
     */
    @Transactional
    public SavedSearch saveSearch(String name, Map<String, Object> queryParams) {
        String userId = securityService.getCurrentUser();

        if (savedSearchRepository.existsByUserIdAndName(userId, name)) {
            throw new IllegalArgumentException("A saved search with this name already exists");
        }

        SavedSearch savedSearch = SavedSearch.builder()
            .userId(userId)
            .name(name)
            .queryParams(queryParams)
            .build();

        log.info("User {} saved search '{}'", userId, name);
        return savedSearchRepository.save(savedSearch);
    }

    /**
     * Get all saved searches for the current user.
     */
    @Transactional(readOnly = true)
    public List<SavedSearch> getMySavedSearches() {
        String userId = securityService.getCurrentUser();
        return savedSearchRepository.findByUserIdOrderByPinnedDescCreatedAtDesc(userId);
    }

    /**
     * Update pin status for a saved search.
     */
    @Transactional
    public SavedSearch updatePinned(UUID id, boolean pinned) {
        String userId = securityService.getCurrentUser();
        SavedSearch search = savedSearchRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Saved search not found"));

        if (!search.getUserId().equals(userId)) {
            throw new SecurityException("Not authorized to update this saved search");
        }

        search.setPinned(pinned);
        log.info("User {} updated pin for saved search {} -> {}", userId, id, pinned);
        return savedSearchRepository.save(search);
    }

    /**
     * Delete a saved search.
     */
    @Transactional
    public void deleteSavedSearch(UUID id) {
        String userId = securityService.getCurrentUser();
        SavedSearch search = savedSearchRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Saved search not found"));

        if (!search.getUserId().equals(userId)) {
            throw new SecurityException("Not authorized to delete this saved search");
        }

        savedSearchRepository.delete(search);
        log.info("User {} deleted saved search {}", userId, id);
    }

    /**
     * Execute a saved search.
     */
    @Transactional(readOnly = true)
    public FacetedSearchResponse executeSavedSearch(UUID id) {
        String userId = securityService.getCurrentUser();
        SavedSearch search = savedSearchRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Saved search not found"));

        if (!search.getUserId().equals(userId)) {
            throw new SecurityException("Not authorized to execute this saved search");
        }

        try {
            // Convert stored map back to request object
            FacetedSearchRequest request = objectMapper.convertValue(search.getQueryParams(), FacetedSearchRequest.class);
            return facetedSearchService.search(request);
        } catch (Exception e) {
            log.error("Failed to execute saved search {}", id, e);
            throw new RuntimeException("Failed to execute saved search", e);
        }
    }
}
