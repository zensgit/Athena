package com.ecm.core.controller;

import com.ecm.core.model.Tag;
import com.ecm.core.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for tag management and node tagging.
 */
@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "Tags", description = "APIs for managing tags")
public class TagController {

    private final TagService tagService;

    // ===== Tag CRUD =====

    public record CreateTagRequest(String name, String description, String color) {}

    public record UpdateTagRequest(String name, String description, String color) {}

    public record MergeTagRequest(String targetTagId) {}

    public record TagResponse(
        UUID id,
        String name,
        String description,
        String color,
        Integer usageCount,
        java.util.Date created,
        String creator
    ) {
        static TagResponse from(Tag tag) {
            return new TagResponse(
                tag.getId(),
                tag.getName(),
                tag.getDescription(),
                tag.getColor(),
                tag.getUsageCount(),
                tag.getCreated(),
                tag.getCreator()
            );
        }
    }

    @PostMapping
    @Operation(summary = "Create tag")
    public ResponseEntity<TagResponse> createTag(@RequestBody CreateTagRequest request) {
        Tag created = tagService.createTag(request.name(), request.description(), request.color());
        return ResponseEntity.status(HttpStatus.CREATED).body(TagResponse.from(created));
    }

    @GetMapping
    @Operation(summary = "List tags")
    public ResponseEntity<List<TagResponse>> getAllTags() {
        List<TagResponse> tags = tagService.getAllTags().stream()
            .map(TagResponse::from)
            .toList();
        return ResponseEntity.ok(tags);
    }

    @GetMapping("/search")
    @Operation(summary = "Search tags")
    public ResponseEntity<List<TagResponse>> searchTags(
            @Parameter(description = "Search query") @RequestParam String q) {
        List<TagResponse> tags = tagService.searchTags(q).stream()
            .map(TagResponse::from)
            .toList();
        return ResponseEntity.ok(tags);
    }

    @GetMapping("/popular")
    @Operation(summary = "Popular tags")
    public ResponseEntity<List<TagResponse>> popularTags(
            @RequestParam(defaultValue = "10") int limit) {
        List<TagResponse> tags = tagService.getPopularTags(limit).stream()
            .map(TagResponse::from)
            .toList();
        return ResponseEntity.ok(tags);
    }

    @PutMapping("/{tagId}")
    @Operation(summary = "Update tag")
    public ResponseEntity<TagResponse> updateTag(
            @PathVariable String tagId,
            @RequestBody UpdateTagRequest request) {
        Tag updated = tagService.updateTag(tagId, request.name(), request.description(), request.color());
        return ResponseEntity.ok(TagResponse.from(updated));
    }

    @DeleteMapping("/{tagId}")
    @Operation(summary = "Delete tag")
    public ResponseEntity<Void> deleteTag(@PathVariable String tagId) {
        tagService.deleteTag(tagId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{sourceTagId}/merge")
    @Operation(summary = "Merge tags")
    public ResponseEntity<Void> mergeTags(
            @PathVariable String sourceTagId,
            @RequestBody MergeTagRequest request) {
        tagService.mergeTags(sourceTagId, request.targetTagId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/cloud")
    @Operation(summary = "Tag cloud")
    public ResponseEntity<List<TagService.TagCloudItem>> tagCloud() {
        return ResponseEntity.ok(tagService.getTagCloud());
    }
}
