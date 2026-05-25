package com.ecm.core.controller;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.SavedSearch;
import com.ecm.core.search.FacetedSearchService.FacetedSearchResponse;
import com.ecm.core.service.SavedSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

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

    @GetMapping("/templates")
    @Operation(summary = "List built-in saved search templates", description = "Get built-in governance/compliance search templates")
    public ResponseEntity<List<SavedSearchTemplateResponse>> getSavedSearchTemplates(
            @RequestParam(required = false) String tag) {
        List<SavedSearchTemplateResponse> templates = savedSearchService.listBuiltInTemplates(tag).stream()
            .map(template -> new SavedSearchTemplateResponse(
                template.id(),
                template.name(),
                template.description(),
                template.queryParams(),
                template.tags()
            ))
            .toList();
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get saved search", description = "Get a saved search by id for the current user")
    public ResponseEntity<SavedSearch> getSavedSearch(@PathVariable UUID id) {
        return ResponseEntity.ok(savedSearchService.getMySavedSearch(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update saved search", description = "Update name and/or query params for an existing saved search")
    public ResponseEntity<SavedSearch> updateSavedSearch(@PathVariable UUID id, @RequestBody UpdateSavedSearchRequest request) {
        SavedSearch updated = savedSearchService.updateSavedSearch(id, request.name(), request.queryParams());
        return ResponseEntity.ok(updated);
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

    @GetMapping("/{id}/export")
    @Operation(summary = "Export saved search results as CSV",
        description = "Run a saved search and export its results as a CSV attachment (one-shot, capped)")
    public ResponseEntity<String> exportSavedSearch(
            @PathVariable UUID id,
            @RequestParam(required = false) Integer limit) {
        // Owner-only auth + load (also gives the name for the filename); export re-checks the owner.
        SavedSearch search = savedSearchService.getMySavedSearch(id);
        String csv = savedSearchService.exportSavedSearchCsv(id, limit);

        String base = search != null && search.getName() != null && !search.getName().isBlank()
            ? search.getName()
            : id.toString();
        String safeName = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeName.isBlank()) {
            safeName = id.toString();
        }
        String timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String filename = safeName + "-search-" + timestamp + ".csv";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("text/csv"));
        headers.setContentDisposition(ContentDisposition.attachment()
            .filename(filename, StandardCharsets.UTF_8)
            .build());
        return ResponseEntity.ok().headers(headers).body(csv);
    }

    @PatchMapping("/{id}/pin")
    @Operation(summary = "Pin saved search", description = "Pin or unpin a saved search")
    public ResponseEntity<SavedSearch> updatePinned(@PathVariable UUID id, @RequestBody UpdatePinRequest request) {
        return ResponseEntity.ok(savedSearchService.updatePinned(id, request.pinned()));
    }

    @PostMapping("/{id}/smart-folder")
    @Operation(summary = "Create smart folder from saved search", description = "Create a smart folder backed by an existing saved search")
    public ResponseEntity<FolderController.FolderResponse> createSmartFolder(
            @PathVariable UUID id,
            @RequestBody CreateSmartFolderRequest request) {
        Folder folder = savedSearchService.createSmartFolder(id, request.name(), request.description(), request.parentId());
        return ResponseEntity.ok(FolderController.FolderResponse.from(folder));
    }

    public record SaveSearchRequest(String name, Map<String, Object> queryParams) {}

    public record UpdateSavedSearchRequest(String name, Map<String, Object> queryParams) {}

    public record UpdatePinRequest(boolean pinned) {}

    public record CreateSmartFolderRequest(
        String name,
        String description,
        UUID parentId
    ) {}

    public record SavedSearchTemplateResponse(
        String id,
        String name,
        String description,
        Map<String, Object> queryParams,
        List<String> tags
    ) {}
}
