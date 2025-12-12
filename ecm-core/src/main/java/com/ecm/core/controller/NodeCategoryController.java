package com.ecm.core.controller;

import com.ecm.core.model.Category;
import com.ecm.core.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

/**
 * REST Controller for categorizing nodes.
 */
@RestController
@RequestMapping("/api/v1/nodes/{nodeId}/categories")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "Node Categories", description = "APIs for assigning categories to documents/folders")
public class NodeCategoryController {

    private final CategoryService categoryService;

    public record CategoryIdRequest(String categoryId) {}

    public record CategoryResponse(
        UUID id,
        String name,
        String description,
        String path,
        Integer level
    ) {
        static CategoryResponse from(Category category) {
            return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getPath(),
                category.getLevel()
            );
        }
    }

    @PostMapping
    @Operation(summary = "Add category to node")
    public ResponseEntity<Void> addCategoryToNode(
            @PathVariable String nodeId,
            @RequestBody CategoryIdRequest request) {
        categoryService.addCategoryToNode(nodeId, request.categoryId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{categoryId}")
    @Operation(summary = "Remove category from node")
    public ResponseEntity<Void> removeCategoryFromNode(
            @PathVariable String nodeId,
            @PathVariable String categoryId) {
        categoryService.removeCategoryFromNode(nodeId, categoryId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "Get node categories")
    public ResponseEntity<Set<CategoryResponse>> getNodeCategories(@PathVariable String nodeId) {
        Set<CategoryResponse> categories = categoryService.getNodeCategories(nodeId).stream()
            .map(CategoryResponse::from)
            .collect(java.util.stream.Collectors.toSet());
        return ResponseEntity.ok(categories);
    }
}

