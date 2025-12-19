package com.ecm.core.controller;

import com.ecm.core.dto.NodeDto;
import com.ecm.core.entity.Node;
import com.ecm.core.service.NodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/nodes", "/api/v1/nodes"})
@RequiredArgsConstructor
@Tag(name = "Node Management", description = "APIs for managing nodes (files and folders)")
public class NodeController {
    
    private final NodeService nodeService;
    
    @GetMapping("/{nodeId}")
    @Operation(summary = "Get node by ID", description = "Retrieve a node by its ID")
    public ResponseEntity<NodeDto> getNode(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId) {
        Node node = nodeService.getNode(nodeId);
        return ResponseEntity.ok(NodeDto.from(node));
    }
    
    @GetMapping("/path")
    @Operation(summary = "Get node by path", description = "Retrieve a node by its path")
    public ResponseEntity<NodeDto> getNodeByPath(
            @Parameter(description = "Node path") @RequestParam String path) {
        Node node = nodeService.getNodeByPath(path);
        return ResponseEntity.ok(NodeDto.from(node));
    }
    
    @GetMapping("/{nodeId}/children")
    @Operation(summary = "Get node children", description = "List children of a node")
    public ResponseEntity<Page<NodeDto>> getChildren(
            @Parameter(description = "Parent node ID") @PathVariable UUID nodeId,
            Pageable pageable) {
        Page<Node> children = nodeService.getChildren(nodeId, pageable);
        return ResponseEntity.ok(children.map(NodeDto::from));
    }
    
    @PostMapping
    @Operation(summary = "Create node", description = "Create a new node")
    public ResponseEntity<NodeDto> createNode(
            @Valid @RequestBody Node node,
            @Parameter(description = "Parent node ID") @RequestParam(required = false) UUID parentId) {
        Node created = nodeService.createNode(node, parentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(NodeDto.from(created));
    }
    
    @PatchMapping("/{nodeId}")
    @Operation(summary = "Update node", description = "Update node properties")
    public ResponseEntity<NodeDto> updateNode(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId,
            @RequestBody Map<String, Object> updates) {
        Node updated = nodeService.updateNode(nodeId, updates);
        return ResponseEntity.ok(NodeDto.from(updated));
    }
    
    @PostMapping("/{nodeId}/move")
    @Operation(summary = "Move node", description = "Move a node to a different parent")
    public ResponseEntity<NodeDto> moveNode(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId,
            @Parameter(description = "Target parent ID") @RequestParam UUID targetParentId) {
        Node moved = nodeService.moveNode(nodeId, targetParentId);
        return ResponseEntity.ok(NodeDto.from(moved));
    }
    
    @PostMapping("/{nodeId}/copy")
    @Operation(summary = "Copy node", description = "Copy a node to a different location")
    public ResponseEntity<NodeDto> copyNode(
            @Parameter(description = "Source node ID") @PathVariable UUID nodeId,
            @Parameter(description = "Target parent ID") @RequestParam UUID targetParentId,
            @Parameter(description = "New name") @RequestParam(required = false) String newName,
            @Parameter(description = "Deep copy") @RequestParam(defaultValue = "true") boolean deep) {
        Node copied = nodeService.copyNode(nodeId, targetParentId, newName, deep);
        return ResponseEntity.status(HttpStatus.CREATED).body(NodeDto.from(copied));
    }
    
    @DeleteMapping("/{nodeId}")
    @Operation(summary = "Delete node", description = "Delete a node")
    public ResponseEntity<Void> deleteNode(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId,
            @Parameter(description = "Permanent delete") @RequestParam(defaultValue = "false") boolean permanent) {
        nodeService.deleteNode(nodeId, permanent);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{nodeId}/lock")
    @Operation(summary = "Lock node", description = "Lock a node to prevent modifications")
    public ResponseEntity<Void> lockNode(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId) {
        nodeService.lockNode(nodeId);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/{nodeId}/unlock")
    @Operation(summary = "Unlock node", description = "Unlock a locked node")
    public ResponseEntity<Void> unlockNode(
            @Parameter(description = "Node ID") @PathVariable UUID nodeId) {
        nodeService.unlockNode(nodeId);
        return ResponseEntity.ok().build();
    }
    
    @GetMapping("/search")
    @Operation(summary = "Search nodes", description = "Search for nodes based on criteria")
    public ResponseEntity<List<NodeDto>> searchNodes(
            @Parameter(description = "Search query") @RequestParam(required = false) String query,
            @Parameter(description = "Search filters") @RequestParam Map<String, Object> filters,
            Pageable pageable) {
        List<Node> results = nodeService.searchNodes(query, filters, pageable);
        return ResponseEntity.ok(results.stream().map(NodeDto::from).toList());
    }
}
