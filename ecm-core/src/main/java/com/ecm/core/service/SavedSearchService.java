package com.ecm.core.service;

import com.ecm.core.entity.Folder;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavedSearchService {

    private static final List<SavedSearchTemplate> BUILT_IN_TEMPLATES = List.of(
        new SavedSearchTemplate(
            "failed-preview-last7d",
            "Failed Preview (Last 7 Days)",
            "Find recently failed preview documents for triage and retry planning.",
            mapOf(
                "previewStatus", List.of("FAILED"),
                "dateRange", "week"
            ),
            List.of("governance", "preview", "triage")
        ),
        new SavedSearchTemplate(
            "unsupported-preview-last30d",
            "Unsupported Preview (Last 30 Days)",
            "Track unsupported preview files to optimize renderer coverage.",
            mapOf(
                "previewStatus", List.of("UNSUPPORTED"),
                "dateRange", "month"
            ),
            List.of("governance", "preview", "coverage")
        ),
        new SavedSearchTemplate(
            "octet-stream-risk",
            "Octet-stream Risk Files",
            "Review generic binary uploads that typically need metadata cleanup.",
            mapOf(
                "mimeTypes", List.of("application/octet-stream"),
                "dateRange", "month"
            ),
            List.of("governance", "metadata", "triage")
        ),
        new SavedSearchTemplate(
            "large-documents-last30d",
            "Large Documents (>=10MB)",
            "Find large files to control storage and preview pipeline pressure.",
            mapOf(
                "minSize", 10 * 1024 * 1024,
                "dateRange", "month"
            ),
            List.of("governance", "storage", "performance")
        ),
        new SavedSearchTemplate(
            "pdf-ready-last7d",
            "PDF Ready (Last 7 Days)",
            "Quickly verify PDF uploads with ready preview state.",
            mapOf(
                "mimeTypes", List.of("application/pdf"),
                "previewStatus", List.of("READY"),
                "dateRange", "week"
            ),
            List.of("governance", "quality", "preview")
        ),
        new SavedSearchTemplate(
            "cad-failed-last30d",
            "CAD Preview Failed (Last 30 Days)",
            "Identify CAD-like files that failed preview conversion.",
            mapOf(
                "mimeTypes", List.of(
                    "application/acad",
                    "application/dwg",
                    "image/vnd.dwg",
                    "application/dxf"
                ),
                "previewStatus", List.of("FAILED"),
                "dateRange", "month"
            ),
            List.of("governance", "cad", "preview")
        )
    );

    private final SavedSearchRepository savedSearchRepository;
    private final SecurityService securityService;
    private final FacetedSearchService facetedSearchService;
    private final FolderService folderService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<SavedSearchTemplate> listBuiltInTemplates(String tag) {
        String normalizedTag = tag != null ? tag.trim().toLowerCase() : "";
        if (normalizedTag.isEmpty()) {
            return BUILT_IN_TEMPLATES;
        }
        return BUILT_IN_TEMPLATES.stream()
            .filter(template -> template.tags().stream()
                .map(value -> value == null ? "" : value.trim().toLowerCase())
                .anyMatch(normalizedTag::equals))
            .toList();
    }

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
     * Get a single saved search for the current user.
     */
    @Transactional(readOnly = true)
    public SavedSearch getMySavedSearch(UUID id) {
        String userId = securityService.getCurrentUser();
        SavedSearch search = savedSearchRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Saved search not found"));

        if (!search.getUserId().equals(userId)) {
            throw new SecurityException("Not authorized to access this saved search");
        }

        return search;
    }

    /**
     * Update name and/or query params for a saved search owned by the current user.
     * Null fields are treated as "no change".
     */
    @Transactional
    public SavedSearch updateSavedSearch(UUID id, String name, Map<String, Object> queryParams) {
        String userId = securityService.getCurrentUser();
        SavedSearch search = savedSearchRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Saved search not found"));

        if (!search.getUserId().equals(userId)) {
            throw new SecurityException("Not authorized to update this saved search");
        }

        boolean updated = false;

        if (name != null) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("Saved search name must not be blank");
            }
            if (!trimmed.equals(search.getName()) && savedSearchRepository.existsByUserIdAndName(userId, trimmed)) {
                throw new IllegalArgumentException("A saved search with this name already exists");
            }
            search.setName(trimmed);
            updated = true;
        }

        if (queryParams != null) {
            search.setQueryParams(queryParams);
            updated = true;
        }

        if (!updated) {
            throw new IllegalArgumentException("Nothing to update");
        }

        log.info("User {} updated saved search {}", userId, id);
        return savedSearchRepository.save(search);
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

    @Transactional
    public Folder createSmartFolder(UUID id, String name, String description, UUID parentId) {
        SavedSearch search = getMySavedSearch(id);
        String folderName = name != null && !name.trim().isBlank() ? name.trim() : search.getName();
        if (folderName == null || folderName.isBlank()) {
            throw new IllegalArgumentException("Smart folder name must not be blank");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> queryCriteria = objectMapper.convertValue(
            search.getQueryParams() != null ? search.getQueryParams() : Map.of(),
            LinkedHashMap.class
        );

        return folderService.createFolder(new FolderService.CreateFolderRequest(
            folderName,
            description,
            parentId,
            Folder.FolderType.GENERAL,
            null,
            null,
            null,
            null,
            true,
            true,
            queryCriteria
        ));
    }

    public record SavedSearchTemplate(
        String id,
        String name,
        String description,
        Map<String, Object> queryParams,
        List<String> tags
    ) {}

    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            String key = String.valueOf(keyValues[i]);
            map.put(key, keyValues[i + 1]);
        }
        return Map.copyOf(map);
    }
}
