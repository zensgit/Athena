package com.ecm.core.controller;

import com.ecm.core.entity.SavedSearch;
import com.ecm.core.search.FacetedSearchService.FacetedSearchResponse;
import com.ecm.core.service.SavedSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/search/saved")
@RequiredArgsConstructor
@Tag(name = "Saved Searches", description = "Manage user saved search queries")
public class SavedSearchController {

    private final SavedSearchService savedSearchService;

    @PostMapping
    @Operation(summary = "Save search", description = "Save current search criteria")
    public ResponseEntity<SavedSearch> saveSearch(@RequestBody SaveSearchRequest request) {
        SavedSearch saved = savedSearchService.saveSearch(request.name(), request.queryParams());
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    @Operation(summary = "List saved searches", description = "Get all saved searches for current user")
    public ResponseEntity<List<SavedSearch>> getSavedSearches() {
        return ResponseEntity.ok(savedSearchService.getMySavedSearches());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete saved search", description = "Delete a saved search configuration")
    public ResponseEntity<Void> deleteSavedSearch(@PathVariable UUID id) {
        savedSearchService.deleteSavedSearch(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/execute")
    @Operation(summary = "Execute saved search", description = "Run a saved search immediately")
    public ResponseEntity<FacetedSearchResponse> executeSavedSearch(@PathVariable UUID id) {
        return ResponseEntity.ok(savedSearchService.executeSavedSearch(id));
    }

    @PatchMapping("/{id}/pin")
    @Operation(summary = "Pin saved search", description = "Pin or unpin a saved search")
    public ResponseEntity<SavedSearch> updatePinned(@PathVariable UUID id, @RequestBody UpdatePinRequest request) {
        return ResponseEntity.ok(savedSearchService.updatePinned(id, request.pinned()));
    }

    public record SaveSearchRequest(String name, Map<String, Object> queryParams) {}

    public record UpdatePinRequest(boolean pinned) {}
}
