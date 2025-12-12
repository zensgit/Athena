package com.ecm.core.controller;

import com.ecm.core.entity.Node;
import com.ecm.core.service.TrashService;
import com.ecm.core.service.TrashService.TrashStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Trash/Recycle Bin operations
 */
@Slf4j
@RestController
@RequestMapping({"/api/v1/trash", "/api/trash"})
@RequiredArgsConstructor
public class TrashController {

    private final TrashService trashService;

    /**
     * Move a node to trash (soft delete)
     */
    @PostMapping("/nodes/{nodeId}")
    public ResponseEntity<Void> moveToTrash(@PathVariable UUID nodeId) {
        trashService.moveToTrash(nodeId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Restore a node from trash
     */
    @PostMapping("/{nodeId}/restore")
    public ResponseEntity<Void> restore(@PathVariable UUID nodeId) {
        trashService.restore(nodeId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Permanently delete a node
     */
    @DeleteMapping("/{nodeId}")
    public ResponseEntity<Void> permanentDelete(@PathVariable UUID nodeId) {
        trashService.permanentDelete(nodeId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get all items in trash
     */
    @GetMapping
    public ResponseEntity<List<TrashItemResponse>> getTrashItems() {
        List<Node> trashItems = trashService.getTrashItems();
        return ResponseEntity.ok(trashItems.stream()
            .map(TrashItemResponse::from)
            .toList());
    }

    /**
     * Get trash items for a specific user (admin only)
     */
    @GetMapping("/user/{username}")
    public ResponseEntity<List<TrashItemResponse>> getTrashItemsForUser(@PathVariable String username) {
        List<Node> trashItems = trashService.getTrashItemsForUser(username);
        return ResponseEntity.ok(trashItems.stream()
            .map(TrashItemResponse::from)
            .toList());
    }

    /**
     * Empty trash (permanently delete all items)
     */
    @DeleteMapping("/empty")
    public ResponseEntity<Map<String, Integer>> emptyTrash() {
        int count = trashService.emptyTrash();
        return ResponseEntity.ok(Map.of("deletedCount", count));
    }

    /**
     * Get trash statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<TrashStatsResponse> getTrashStats() {
        TrashStats stats = trashService.getTrashStats();
        return ResponseEntity.ok(TrashStatsResponse.from(stats));
    }

    /**
     * Get items nearing auto-purge
     */
    @GetMapping("/nearing-purge")
    public ResponseEntity<List<TrashItemResponse>> getItemsNearingPurge(
            @RequestParam(defaultValue = "7") int daysBeforePurge) {
        List<Node> items = trashService.getItemsNearingPurge(daysBeforePurge);
        return ResponseEntity.ok(items.stream()
            .map(TrashItemResponse::from)
            .toList());
    }

    // Response DTOs

    public record TrashItemResponse(
        UUID id,
        String name,
        String path,
        String nodeType,
        Long size,
        String deletedBy,
        LocalDateTime deletedAt,
        String createdBy,
        LocalDateTime createdDate,
        boolean isFolder
    ) {
        public static TrashItemResponse from(Node node) {
            return new TrashItemResponse(
                node.getId(),
                node.getName(),
                node.getPath(),
                node.getNodeType().name(),
                node.getSize(),
                node.getDeletedBy(),
                node.getDeletedAt(),
                node.getCreatedBy(),
                node.getCreatedDate(),
                node.isFolder()
            );
        }
    }

    public record TrashStatsResponse(
        int fileCount,
        int folderCount,
        int totalCount,
        long totalSizeBytes,
        String formattedSize,
        LocalDateTime oldestItemDate
    ) {
        public static TrashStatsResponse from(TrashStats stats) {
            return new TrashStatsResponse(
                stats.fileCount(),
                stats.folderCount(),
                stats.fileCount() + stats.folderCount(),
                stats.totalSizeBytes(),
                stats.formattedSize(),
                stats.oldestItemDate()
            );
        }
    }
}
