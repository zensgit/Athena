package com.ecm.core.controller;

import com.ecm.core.entity.RenditionResource;
import com.ecm.core.service.RenditionResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/nodes/{nodeId}/renditions")
@RequiredArgsConstructor
@Tag(name = "Renditions", description = "First-class rendition resource APIs")
public class RenditionResourceController {

    private final RenditionResourceService renditionResourceService;

    @GetMapping
    @Operation(
        summary = "List rendition resources for node",
        description = "Return first-class rendition resources for a document node, including registered-but-not-created resources."
    )
    public ResponseEntity<List<RenditionResourceResponse>> listNodeRenditions(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId,
        @Parameter(description = "Optional collection status filter: CREATED or NOT_CREATED")
        @RequestParam(required = false) String status,
        @Parameter(description = "Optional exact rendition state filter, comma separated")
        @RequestParam(required = false) String state
    ) {
        return ResponseEntity.ok(
            renditionResourceService.listForNode(nodeId, status, state).stream()
                .map(RenditionResourceController::toResponse)
                .toList()
        );
    }

    @GetMapping("/definitions")
    @Operation(
        summary = "List rendition definitions for node",
        description = "Return registered rendition definitions for a document node, including applicability and current resource state."
    )
    public ResponseEntity<List<RenditionDefinitionResponse>> listNodeRenditionDefinitions(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId
    ) {
        return ResponseEntity.ok(
            renditionResourceService.listDefinitionsForNode(nodeId).stream()
                .map(RenditionResourceController::toDefinitionResponse)
                .toList()
        );
    }

    @GetMapping("/{renditionKey}")
    @Operation(
        summary = "Get rendition resource for node",
        description = "Return one first-class rendition resource for a document node."
    )
    public ResponseEntity<RenditionResourceResponse> getNodeRendition(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId,
        @Parameter(description = "Rendition key") @PathVariable String renditionKey
    ) {
        return ResponseEntity.ok(toResponse(renditionResourceService.getForNode(nodeId, renditionKey)));
    }

    @PostMapping("/{renditionKey}/requeue")
    @Operation(
        summary = "Requeue rendition generation",
        description = "Queue preview-linked rendition generation for a document rendition resource."
    )
    public ResponseEntity<RenditionMutationResponse> requeueNodeRendition(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId,
        @Parameter(description = "Rendition key") @PathVariable String renditionKey,
        @RequestParam(defaultValue = "false") boolean force
    ) {
        return ResponseEntity.ok(toMutationResponse(
            renditionResourceService.requeueForNode(nodeId, renditionKey, force)
        ));
    }

    @PostMapping("/{renditionKey}/invalidate")
    @Operation(
        summary = "Invalidate rendition state",
        description = "Invalidate the current rendition state and optionally requeue preview-linked generation."
    )
    public ResponseEntity<RenditionMutationResponse> invalidateNodeRendition(
        @Parameter(description = "Node ID") @PathVariable UUID nodeId,
        @Parameter(description = "Rendition key") @PathVariable String renditionKey,
        @RequestParam(required = false) String reason,
        @RequestParam(defaultValue = "false") boolean requeue,
        @RequestParam(defaultValue = "true") boolean forceQueue
    ) {
        return ResponseEntity.ok(toMutationResponse(
            renditionResourceService.invalidateForNode(nodeId, renditionKey, reason, requeue, forceQueue)
        ));
    }

    private static RenditionResourceResponse toResponse(RenditionResource resource) {
        return new RenditionResourceResponse(
            resource.getId(),
            resource.getDocument().getId(),
            resource.getRenditionKey(),
            resource.getLabel(),
            resource.getMimeType(),
            resource.getState().name(),
            resource.isAvailable(),
            resource.isDownloadable(),
            resource.isApplicable(),
            resource.getApplicabilityReason(),
            resource.getGenerationMode(),
            resource.getDependencyRenditionKey(),
            resource.getContentUrl(),
            resource.getErrorReason(),
            resource.getErrorCategory(),
            resource.getSourceStatus(),
            resource.getVersionLabel(),
            resource.getSourceUpdatedAt(),
            resource.getLastSyncedAt(),
            resource.getSortOrder()
        );
    }

    private static RenditionDefinitionResponse toDefinitionResponse(
        RenditionResourceService.RenditionDefinitionStatus definition
    ) {
        return new RenditionDefinitionResponse(
            definition.nodeId(),
            definition.renditionKey(),
            definition.label(),
            definition.targetMimeType(),
            definition.generationMode(),
            definition.downloadable(),
            definition.sortOrder(),
            definition.dependencyRenditionKey(),
            definition.registered(),
            definition.applicable(),
            definition.applicabilityReason(),
            definition.currentState(),
            definition.available(),
            definition.contentUrl(),
            definition.canRequeue(),
            definition.canInvalidate(),
            definition.mutationBlockedReason()
        );
    }

    private RenditionMutationResponse toMutationResponse(RenditionResourceService.RenditionMutationResult result) {
        RenditionResourceService.PreviewMutationStatus mutationStatus =
            renditionResourceService.resolvePreviewMutationStatus(result.previewSummary(), result.queueStatus());
        PreviewQueueStatusResponse queueStatus = mutationStatus == null
            ? null
            : new PreviewQueueStatusResponse(
                mutationStatus.documentId(),
                mutationStatus.previewStatus(),
                mutationStatus.queued(),
                mutationStatus.attempts(),
                mutationStatus.nextAttemptAt(),
                mutationStatus.message()
            );
        PreviewSummaryResponse previewSummary = result.previewSummary() == null
            ? null
            : new PreviewSummaryResponse(
                result.previewSummary().nodeId(),
                result.previewSummary().document(),
                result.previewSummary().previewStatus(),
                result.previewSummary().renditionAvailable(),
                result.previewSummary().previewFailureReason(),
                result.previewSummary().previewFailureCategory(),
                result.previewSummary().previewLastUpdated(),
                result.previewSummary().currentVersionLabel()
            );
        return new RenditionMutationResponse(
            result.renditionKey(),
            result.action(),
            result.invalidated(),
            result.previewLinked(),
            result.message(),
            queueStatus,
            toResponse(result.resource()),
            previewSummary
        );
    }

    private record RenditionResourceResponse(
        UUID id,
        UUID documentId,
        String renditionKey,
        String label,
        String mimeType,
        String state,
        boolean available,
        boolean downloadable,
        boolean applicable,
        String applicabilityReason,
        String generationMode,
        String dependencyRenditionKey,
        String contentUrl,
        String errorReason,
        String errorCategory,
        String sourceStatus,
        String versionLabel,
        java.time.LocalDateTime sourceUpdatedAt,
        java.time.LocalDateTime lastSyncedAt,
        int sortOrder
    ) {}

    private record RenditionDefinitionResponse(
        UUID nodeId,
        String renditionKey,
        String label,
        String targetMimeType,
        String generationMode,
        boolean downloadable,
        int sortOrder,
        String dependencyRenditionKey,
        boolean registered,
        boolean applicable,
        String applicabilityReason,
        String currentState,
        boolean available,
        String contentUrl,
        boolean canRequeue,
        boolean canInvalidate,
        String mutationBlockedReason
    ) {}

    private record RenditionMutationResponse(
        String renditionKey,
        String action,
        boolean invalidated,
        boolean previewLinked,
        String message,
        PreviewQueueStatusResponse queueStatus,
        RenditionResourceResponse resource,
        PreviewSummaryResponse previewSummary
    ) {}

    private record PreviewSummaryResponse(
        UUID nodeId,
        boolean document,
        String previewStatus,
        boolean renditionAvailable,
        String previewFailureReason,
        String previewFailureCategory,
        java.time.LocalDateTime previewLastUpdated,
        String currentVersionLabel
    ) {}

    private record PreviewQueueStatusResponse(
        UUID documentId,
        String previewStatus,
        boolean queued,
        int attempts,
        java.time.Instant nextAttemptAt,
        String message
    ) {}
}
