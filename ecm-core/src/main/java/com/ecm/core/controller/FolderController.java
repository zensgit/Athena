package com.ecm.core.controller;

import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder.FolderType;
import com.ecm.core.entity.Node;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.FolderService.*;
import com.ecm.core.service.NodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller for Folder operations
 */
@Slf4j
@RestController
@RequestMapping({"/api/folders", "/api/v1/folders"})
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;
    private final NodeService nodeService;

    /**
     * Create a new folder
     */
    @PostMapping
    public ResponseEntity<FolderResponse> createFolder(@RequestBody CreateFolderRequestDto request) {
        CreateFolderRequest serviceRequest = new CreateFolderRequest(
            request.name(),
            request.description(),
            request.parentId(),
            request.folderType(),
            request.maxItems(),
            request.allowedTypes(),
            request.autoFileNaming(),
            request.namingPattern(),
            request.inheritPermissions(),
            request.isSmart(),
            request.queryCriteria()
        );

        Folder folder = folderService.createFolder(serviceRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(FolderResponse.from(folder));
    }

    /**
     * Get folder by ID
     */
    @GetMapping("/{folderId}")
    public ResponseEntity<FolderResponse> getFolder(@PathVariable UUID folderId) {
        Folder folder = folderService.getFolder(folderId);
        return ResponseEntity.ok(FolderResponse.from(folder));
    }

    /**
     * Get folder by path
     */
    @GetMapping("/path")
    public ResponseEntity<FolderResponse> getFolderByPath(@RequestParam String path) {
        Folder folder = folderService.getFolderByPath(path);
        return ResponseEntity.ok(FolderResponse.from(folder));
    }

    /**
     * Get root folders
     */
    @GetMapping("/roots")
    public ResponseEntity<List<FolderResponse>> getRootFolders() {
        List<Folder> roots = folderService.getRootFolders();
        return ResponseEntity.ok(roots.stream()
            .map(FolderResponse::from)
            .toList());
    }

    /**
     * Get folder contents (paginated)
     */
    @GetMapping("/{folderId}/contents")
    public ResponseEntity<Page<NodeResponse>> getFolderContents(
            @PathVariable UUID folderId,
            Pageable pageable) {
        Page<Node> contents = folderService.getFolderContents(folderId, pageable);
        return ResponseEntity.ok(contents.map(NodeResponse::from));
    }

    /**
     * Get folder contents with filtering
     */
    @GetMapping("/{folderId}/contents/filtered")
    public ResponseEntity<FolderContentsResponseDto> getFolderContentsFiltered(
            @PathVariable UUID folderId,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection) {

        FolderContentsFilter filter = new FolderContentsFilter(sortBy, sortDirection);
        FolderContentsResponse response = folderService.getFolderContentsFiltered(folderId, filter);

        return ResponseEntity.ok(FolderContentsResponseDto.from(response));
    }

    /**
     * Update folder
     */
    @PutMapping("/{folderId}")
    public ResponseEntity<FolderResponse> updateFolder(
            @PathVariable UUID folderId,
            @RequestBody UpdateFolderRequestDto request) {

        UpdateFolderRequest serviceRequest = new UpdateFolderRequest(
            request.name(),
            request.description(),
            request.folderType(),
            request.maxItems(),
            request.allowedTypes(),
            request.autoFileNaming(),
            request.namingPattern()
        );

        Folder folder = folderService.updateFolder(folderId, serviceRequest);
        return ResponseEntity.ok(FolderResponse.from(folder));
    }

    /**
     * Rename folder
     */
    @PatchMapping("/{folderId}/rename")
    public ResponseEntity<FolderResponse> renameFolder(
            @PathVariable UUID folderId,
            @RequestBody RenameRequest request) {
        Folder folder = folderService.renameFolder(folderId, request.name());
        return ResponseEntity.ok(FolderResponse.from(folder));
    }

    /**
     * Delete folder
     */
    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> deleteFolder(
            @PathVariable UUID folderId,
            @RequestParam(defaultValue = "false") boolean permanent,
            @RequestParam(defaultValue = "false") boolean recursive) {
        folderService.deleteFolder(folderId, permanent, recursive);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get folder tree
     */
    @GetMapping("/tree")
    public ResponseEntity<List<FolderTreeNode>> getFolderTree(
            @RequestParam(required = false) UUID rootId,
            @RequestParam(defaultValue = "3") int maxDepth) {
        List<FolderTreeNode> tree = folderService.getFolderTree(rootId, maxDepth);
        return ResponseEntity.ok(tree);
    }

    /**
     * Get folder breadcrumb
     */
    @GetMapping("/{folderId}/breadcrumb")
    public ResponseEntity<List<BreadcrumbItem>> getFolderBreadcrumb(@PathVariable UUID folderId) {
        List<BreadcrumbItem> breadcrumb = folderService.getFolderBreadcrumb(folderId);
        return ResponseEntity.ok(breadcrumb);
    }

    /**
     * Get folders by type
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<List<FolderResponse>> getFoldersByType(@PathVariable FolderType type) {
        List<Folder> folders = folderService.getFoldersByType(type);
        return ResponseEntity.ok(folders.stream()
            .map(FolderResponse::from)
            .toList());
    }

    /**
     * Get folder statistics
     */
    @GetMapping("/{folderId}/stats")
    public ResponseEntity<FolderStats> getFolderStats(@PathVariable UUID folderId) {
        FolderStats stats = folderService.getFolderStats(folderId);
        return ResponseEntity.ok(stats);
    }

    /**
     * Check if folder can accept items
     */
    @GetMapping("/{folderId}/can-accept")
    public ResponseEntity<Map<String, Object>> canAcceptItems(
            @PathVariable UUID folderId,
            @RequestParam(defaultValue = "1") int itemCount,
            @RequestParam(required = false) String mimeType) {

        boolean canAccept = folderService.canAcceptItems(folderId, itemCount);
        boolean typeAllowed = mimeType == null || folderService.canAcceptFileType(folderId, mimeType);

        return ResponseEntity.ok(Map.of(
            "canAcceptItems", canAccept,
            "typeAllowed", typeAllowed,
            "allowed", canAccept && typeAllowed
        ));
    }

    /**
     * Move node to folder
     */
    @PostMapping("/{folderId}/move")
    public ResponseEntity<NodeResponse> moveToFolder(
            @PathVariable UUID folderId,
            @RequestBody MoveRequest request) {
        Node moved = nodeService.moveNode(request.nodeId(), folderId);
        return ResponseEntity.ok(NodeResponse.from(moved));
    }

    /**
     * Copy node to folder
     */
    @PostMapping("/{folderId}/copy")
    public ResponseEntity<NodeResponse> copyToFolder(
            @PathVariable UUID folderId,
            @RequestBody CopyRequest request) {
        Node copied = nodeService.copyNode(
            request.nodeId(),
            folderId,
            request.newName(),
            request.deep() != null && request.deep()
        );
        return ResponseEntity.ok(NodeResponse.from(copied));
    }

    /**
     * Batch move nodes to folder
     */
    @PostMapping("/{folderId}/batch-move")
    public ResponseEntity<BatchOperationResult> batchMoveToFolder(
            @PathVariable UUID folderId,
            @RequestBody BatchMoveRequest request) {

        int success = 0;
        int failed = 0;

        for (UUID nodeId : request.nodeIds()) {
            try {
                nodeService.moveNode(nodeId, folderId);
                success++;
            } catch (Exception e) {
                log.warn("Failed to move node {} to folder {}: {}", nodeId, folderId, e.getMessage());
                failed++;
            }
        }

        return ResponseEntity.ok(new BatchOperationResult(
            success, failed, request.nodeIds().size()
        ));
    }

    /**
     * Batch copy nodes to folder
     */
    @PostMapping("/{folderId}/batch-copy")
    public ResponseEntity<BatchOperationResult> batchCopyToFolder(
            @PathVariable UUID folderId,
            @RequestBody BatchCopyRequest request) {

        int success = 0;
        int failed = 0;

        for (UUID nodeId : request.nodeIds()) {
            try {
                nodeService.copyNode(nodeId, folderId, null, request.deep() != null && request.deep());
                success++;
            } catch (Exception e) {
                log.warn("Failed to copy node {} to folder {}: {}", nodeId, folderId, e.getMessage());
                failed++;
            }
        }

        return ResponseEntity.ok(new BatchOperationResult(
            success, failed, request.nodeIds().size()
        ));
    }

    // === Request/Response DTOs ===

    public record CreateFolderRequestDto(
        String name,
        String description,
        UUID parentId,
        FolderType folderType,
        Integer maxItems,
        String allowedTypes,
        Boolean autoFileNaming,
        String namingPattern,
        Boolean inheritPermissions,
        Boolean isSmart,
        Map<String, Object> queryCriteria
    ) {}

    public record UpdateFolderRequestDto(
        String name,
        String description,
        FolderType folderType,
        Integer maxItems,
        String allowedTypes,
        Boolean autoFileNaming,
        String namingPattern
    ) {}

    public record RenameRequest(String name) {}

    public record MoveRequest(UUID nodeId) {}

    public record CopyRequest(UUID nodeId, String newName, Boolean deep) {}

    public record BatchMoveRequest(List<UUID> nodeIds) {}

    public record BatchCopyRequest(List<UUID> nodeIds, Boolean deep) {}

    public record BatchOperationResult(int success, int failed, int total) {}

    public record FolderResponse(
        UUID id,
        String name,
        String description,
        String path,
        UUID parentId,
        FolderType folderType,
        Integer maxItems,
        String allowedTypes,
        boolean autoFileNaming,
        String namingPattern,
        boolean inheritPermissions,
        boolean smart,
        Map<String, Object> queryCriteria,
        boolean locked,
        String lockedBy,
        String createdBy,
        LocalDateTime createdDate,
        String lastModifiedBy,
        LocalDateTime lastModifiedDate
    ) {
        public static FolderResponse from(Folder folder) {
            return new FolderResponse(
                folder.getId(),
                folder.getName(),
                folder.getDescription(),
                folder.getPath(),
                folder.getParent() != null ? folder.getParent().getId() : null,
                folder.getFolderType(),
                folder.getMaxItems(),
                folder.getAllowedTypes(),
                folder.isAutoFileNaming(),
                folder.getNamingPattern(),
                folder.isInheritPermissions(),
                folder.isSmart(),
                folder.getQueryCriteria(),
                folder.isLocked(),
                folder.getLockedBy(),
                folder.getCreatedBy(),
                folder.getCreatedDate(),
                folder.getLastModifiedBy(),
                folder.getLastModifiedDate()
            );
        }
    }

    public record NodeResponse(
        UUID id,
        String name,
        String description,
        String path,
        String nodeType,
        UUID parentId,
        Long size,
        String contentType,
        boolean isFolder,
        boolean locked,
        String lockedBy,
        String createdBy,
        LocalDateTime createdDate,
        String lastModifiedBy,
        LocalDateTime lastModifiedDate
    ) {
        public static NodeResponse from(Node node) {
            String contentType = null;
            if (node instanceof Document document) {
                contentType = document.getMimeType();
            }
            return new NodeResponse(
                node.getId(),
                node.getName(),
                node.getDescription(),
                node.getPath(),
                node.getNodeType().name(),
                node.getParent() != null ? node.getParent().getId() : null,
                node.getSize(),
                contentType,
                node.isFolder(),
                node.isLocked(),
                node.getLockedBy(),
                node.getCreatedBy(),
                node.getCreatedDate(),
                node.getLastModifiedBy(),
                node.getLastModifiedDate()
            );
        }
    }

    public record FolderContentsResponseDto(
        FolderResponse folder,
        List<NodeResponse> contents,
        int folderCount,
        int documentCount,
        long totalSize,
        String formattedTotalSize
    ) {
        public static FolderContentsResponseDto from(FolderContentsResponse response) {
            return new FolderContentsResponseDto(
                FolderResponse.from(response.folder()),
                response.contents().stream().map(NodeResponse::from).toList(),
                response.folderCount(),
                response.documentCount(),
                response.totalSize(),
                formatSize(response.totalSize())
            );
        }

        private static String formatSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
