package com.ecm.core.controller;

import com.ecm.core.entity.Favorite;
import com.ecm.core.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
@Tag(name = "Favorites", description = "Manage user favorite documents and folders")
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping("/{nodeId}")
    @Operation(summary = "Add to favorites", description = "Mark a document or folder as favorite")
    public ResponseEntity<Void> addFavorite(@PathVariable UUID nodeId) {
        favoriteService.addFavorite(nodeId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{nodeId}")
    @Operation(summary = "Remove from favorites", description = "Unmark a document or folder as favorite")
    public ResponseEntity<Void> removeFavorite(@PathVariable UUID nodeId) {
        favoriteService.removeFavorite(nodeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "List favorites", description = "Get all favorites for the current user")
    public ResponseEntity<Page<FavoriteResponse>> getFavorites(Pageable pageable) {
        Page<Favorite> favorites = favoriteService.getMyFavorites(pageable);
        Page<FavoriteResponse> response = favorites.map(FavoriteResponse::from);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{nodeId}/check")
    @Operation(summary = "Check status", description = "Check if a node is favorited")
    public ResponseEntity<Boolean> checkFavorite(@PathVariable UUID nodeId) {
        return ResponseEntity.ok(favoriteService.isFavorite(nodeId));
    }

    @PostMapping("/batch/check")
    @Operation(summary = "Batch check status", description = "Check which nodes are favorited by the current user")
    public ResponseEntity<BatchCheckResponse> batchCheckFavorites(@RequestBody BatchCheckRequest request) {
        Set<UUID> favoritedNodeIds = favoriteService.getFavoriteNodeIds(
            request != null ? request.nodeIds() : List.of()
        );
        return ResponseEntity.ok(new BatchCheckResponse(favoritedNodeIds));
    }

    // DTO
    public record FavoriteResponse(UUID id, UUID nodeId, String nodeName, String nodeType, java.time.LocalDateTime createdAt) {
        public static FavoriteResponse from(Favorite fav) {
            return new FavoriteResponse(
                fav.getId(), 
                fav.getNode().getId(), 
                fav.getNode().getName(), 
                fav.getNode().getNodeType().name(),
                fav.getCreatedAt()
            );
        }
    }

    public record BatchCheckRequest(List<UUID> nodeIds) {}

    public record BatchCheckResponse(Set<UUID> favoritedNodeIds) {}
}
