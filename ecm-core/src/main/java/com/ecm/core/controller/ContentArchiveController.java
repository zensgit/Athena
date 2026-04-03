package com.ecm.core.controller;

import com.ecm.core.entity.Node.ArchiveStoreTier;
import com.ecm.core.service.ContentArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Content Archive", description = "Archive and restore content to archive storage tiers")
public class ContentArchiveController {

    private final ContentArchiveService contentArchiveService;

    @PostMapping({"/api/nodes/{nodeId}/archive", "/api/v1/nodes/{nodeId}/archive"})
    @Operation(summary = "Archive a node")
    public ResponseEntity<ContentArchiveService.ArchiveMutationDto> archiveNode(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId,
        @RequestBody(required = false) ArchiveNodeRequest request
    ) {
        return ResponseEntity.ok(contentArchiveService.archiveNode(
            nodeId,
            request != null ? request.storageTier() : null
        ));
    }

    @PostMapping({"/api/nodes/{nodeId}/restore", "/api/v1/nodes/{nodeId}/restore"})
    @Operation(summary = "Restore an archived node")
    public ResponseEntity<ContentArchiveService.ArchiveMutationDto> restoreNode(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId
    ) {
        return ResponseEntity.ok(contentArchiveService.restoreNode(nodeId));
    }

    @GetMapping({"/api/nodes/{nodeId}/archive-status", "/api/v1/nodes/{nodeId}/archive-status"})
    @Operation(summary = "Get archive status for a node")
    public ResponseEntity<ContentArchiveService.ArchiveStatusDto> getArchiveStatus(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId
    ) {
        return ResponseEntity.ok(contentArchiveService.getArchiveStatus(nodeId));
    }

    @GetMapping({"/api/nodes/archived", "/api/v1/nodes/archived"})
    @Operation(summary = "List archived nodes")
    public ResponseEntity<Page<ContentArchiveService.ArchivedNodeDto>> listArchivedNodes(Pageable pageable) {
        return ResponseEntity.ok(contentArchiveService.listArchivedNodes(pageable));
    }

    public record ArchiveNodeRequest(ArchiveStoreTier storageTier) {}
}
