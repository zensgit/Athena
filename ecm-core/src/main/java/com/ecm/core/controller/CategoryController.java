package com.ecm.core.controller;

import com.ecm.core.model.Category;
import com.ecm.core.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for category management.
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "Categories", description = "APIs for managing categories")
public class CategoryController {

    private final CategoryService categoryService;

    public record CreateCategoryRequest(String name, String description, String parentId) {}

    public record UpdateCategoryRequest(String name, String description) {}

    public record MoveCategoryRequest(String newParentId) {}

    public record CategoryResponse(
        UUID id,
        String name,
        String description,
        String path,
        Integer level,
        Boolean active,
        java.util.Date created,
        String creator,
        String parentId
    ) {
        static CategoryResponse from(Category category) {
            return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getPath(),
                category.getLevel(),
                category.getActive(),
                category.getCreated(),
                category.getCreator(),
                category.getParent() != null ? category.getParent().getId().toString() : null
            );
        }
    }

    @PostMapping
    @Operation(summary = "Create category")
    public ResponseEntity<CategoryResponse> createCategory(@RequestBody CreateCategoryRequest request) {
        Category created = categoryService.createCategory(
            request.name(),
            request.description(),
            request.parentId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoryResponse.from(created));
    }

    @GetMapping("/tree")
    @Operation(summary = "Get category tree")
    public ResponseEntity<List<CategoryService.CategoryTreeNode>> getCategoryTree() {
        return ResponseEntity.ok(categoryService.getCategoryTree());
    }

    @GetMapping("/{categoryId}/path")
    @Operation(summary = "Get category path")
    public ResponseEntity<List<CategoryResponse>> getCategoryPath(
            @PathVariable String categoryId) {
        List<CategoryResponse> path = categoryService.getCategoryPath(categoryId).stream()
            .map(CategoryResponse::from)
            .toList();
        return ResponseEntity.ok(path);
    }

    @PutMapping("/{categoryId}")
    @Operation(summary = "Update category")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable String categoryId,
            @RequestBody UpdateCategoryRequest request) {
        Category updated = categoryService.updateCategory(categoryId, request.name(), request.description());
        return ResponseEntity.ok(CategoryResponse.from(updated));
    }

    @PostMapping("/{categoryId}/move")
    @Operation(summary = "Move category")
    public ResponseEntity<CategoryResponse> moveCategory(
            @PathVariable String categoryId,
            @RequestBody MoveCategoryRequest request) {
        Category moved = categoryService.moveCategory(categoryId, request.newParentId());
        return ResponseEntity.ok(CategoryResponse.from(moved));
    }

    @DeleteMapping("/{categoryId}")
    @Operation(summary = "Delete category")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable String categoryId,
            @RequestParam(defaultValue = "false") boolean deleteChildren) {
        categoryService.deleteCategory(categoryId, deleteChildren);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{categoryId}/stats")
    @Operation(summary = "Category statistics")
    public ResponseEntity<CategoryService.CategoryStatistics> getCategoryStats(
            @PathVariable String categoryId) {
        return ResponseEntity.ok(categoryService.getCategoryStatistics(categoryId));
    }
}

