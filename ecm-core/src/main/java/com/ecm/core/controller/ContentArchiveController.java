package com.ecm.core.controller;

import com.ecm.core.entity.Node.ArchiveStoreTier;
import com.ecm.core.service.ArchivePolicyService;
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
    private final ArchivePolicyService archivePolicyService;

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

    @GetMapping({"/api/folders/{folderId}/archive-policy", "/api/v1/folders/{folderId}/archive-policy"})
    @Operation(summary = "Get archive policy for a folder")
    public ResponseEntity<ArchivePolicyService.ArchivePolicyDto> getArchivePolicy(@PathVariable UUID folderId) {
        return archivePolicyService.getPolicy(folderId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping({"/api/folders/{folderId}/archive-policy", "/api/v1/folders/{folderId}/archive-policy"})
    @Operation(summary = "Create or update archive policy for a folder")
    public ResponseEntity<ArchivePolicyService.ArchivePolicyDto> upsertArchivePolicy(
        @PathVariable UUID folderId,
        @RequestBody ArchivePolicyService.ArchivePolicyUpsertRequest request
    ) {
        return ResponseEntity.ok(archivePolicyService.upsertPolicy(folderId, request));
    }

    @DeleteMapping({"/api/folders/{folderId}/archive-policy", "/api/v1/folders/{folderId}/archive-policy"})
    @Operation(summary = "Delete archive policy for a folder")
    public ResponseEntity<Void> deleteArchivePolicy(@PathVariable UUID folderId) {
        archivePolicyService.deletePolicy(folderId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping({"/api/folders/{folderId}/archive-policy/dry-run", "/api/v1/folders/{folderId}/archive-policy/dry-run"})
    @Operation(summary = "Preview archive policy candidates")
    public ResponseEntity<ArchivePolicyService.ArchivePolicyDryRunDto> dryRunArchivePolicy(
        @PathVariable UUID folderId,
        @RequestBody(required = false) ArchivePolicyService.ArchivePolicyUpsertRequest request
    ) {
        return ResponseEntity.ok(archivePolicyService.dryRunPolicy(folderId, request));
    }

    @PostMapping({"/api/folders/{folderId}/archive-policy/execute", "/api/v1/folders/{folderId}/archive-policy/execute"})
    @Operation(summary = "Execute archive policy for a folder")
    public ResponseEntity<ArchivePolicyService.ArchivePolicyExecutionDto> executeArchivePolicy(@PathVariable UUID folderId) {
        return ResponseEntity.ok(archivePolicyService.executePolicy(folderId));
    }

    @GetMapping({"/api/archive-policies", "/api/v1/archive-policies"})
    @Operation(summary = "List archive policies")
    public ResponseEntity<java.util.List<ArchivePolicyService.ArchivePolicyDto>> listArchivePolicies() {
        return ResponseEntity.ok(archivePolicyService.listPolicies());
    }

    @PostMapping({"/api/archive-policies/run", "/api/v1/archive-policies/run"})
    @Operation(summary = "Run all enabled archive policies")
    public ResponseEntity<ArchivePolicyService.ArchivePolicyBatchExecutionDto> runArchivePolicies() {
        return ResponseEntity.ok(archivePolicyService.runScheduledPolicies());
    }

    public record ArchiveNodeRequest(ArchiveStoreTier storageTier) {}
}
