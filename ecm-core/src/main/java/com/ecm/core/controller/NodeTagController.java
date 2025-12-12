package com.ecm.core.controller;

import com.ecm.core.model.Tag;
import com.ecm.core.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * REST Controller for tagging nodes.
 */
@RestController
@RequestMapping("/api/v1/nodes/{nodeId}/tags")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "Node Tags", description = "APIs for tagging documents/folders")
public class NodeTagController {

    private final TagService tagService;

    public record TagNameRequest(String tagName) {}

    public record TagNamesRequest(List<String> tagNames) {}

    public record TagResponse(
        java.util.UUID id,
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
    @Operation(summary = "Add tag to node")
    public ResponseEntity<Void> addTagToNode(
            @PathVariable String nodeId,
            @RequestBody TagNameRequest request) {
        tagService.addTagToNode(nodeId, request.tagName());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/batch")
    @Operation(summary = "Add tags to node")
    public ResponseEntity<Void> addTagsToNode(
            @PathVariable String nodeId,
            @RequestBody TagNamesRequest request) {
        tagService.addTagsToNode(nodeId, request.tagNames());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{tagName}")
    @Operation(summary = "Remove tag from node")
    public ResponseEntity<Void> removeTagFromNode(
            @PathVariable String nodeId,
            @PathVariable String tagName) {
        tagService.removeTagFromNode(nodeId, tagName);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Get node tags")
    public ResponseEntity<Set<TagResponse>> getNodeTags(@PathVariable String nodeId) {
        Set<TagResponse> tags = tagService.getNodeTags(nodeId).stream()
            .map(TagResponse::from)
            .collect(java.util.stream.Collectors.toSet());
        return ResponseEntity.ok(tags);
    }
}

