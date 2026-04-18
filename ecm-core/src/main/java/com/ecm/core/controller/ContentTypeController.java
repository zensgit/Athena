package com.ecm.core.controller;

import com.ecm.core.dto.NodeDto;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.ContentType;
import com.ecm.core.service.ContentTypeService;
import com.ecm.core.service.NodePropertyEncryptionService;
import com.ecm.core.service.RenditionResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/types")
@Tag(name = "Content Types", description = "Manage content types and metadata schemas")
public class ContentTypeController {

    private final ContentTypeService contentTypeService;
    private final RenditionResourceService renditionResourceService;
    private final NodePropertyEncryptionService nodePropertyEncryptionService;

    @Autowired
    public ContentTypeController(
        ContentTypeService contentTypeService,
        RenditionResourceService renditionResourceService,
        NodePropertyEncryptionService nodePropertyEncryptionService
    ) {
        this.contentTypeService = contentTypeService;
        this.renditionResourceService = renditionResourceService;
        this.nodePropertyEncryptionService = nodePropertyEncryptionService;
    }

    // Test-only delegate
    ContentTypeController(
        ContentTypeService contentTypeService,
        RenditionResourceService renditionResourceService
    ) {
        this(contentTypeService, renditionResourceService, null);
    }

    @GetMapping
    @Operation(summary = "List types", description = "Get all defined content types")
    public ResponseEntity<List<ContentType>> getAllTypes() {
        return ResponseEntity.ok(contentTypeService.getAllTypes());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create type", description = "Define a new content type")
    public ResponseEntity<ContentType> createType(@RequestBody ContentType contentType) {
        return ResponseEntity.ok(contentTypeService.createType(contentType));
    }

    @GetMapping("/{name}")
    @Operation(summary = "Get type definition", description = "Get definition by unique name")
    public ResponseEntity<ContentType> getType(@PathVariable String name) {
        return ResponseEntity.ok(contentTypeService.getType(name));
    }

    @PutMapping("/{name}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update type", description = "Update display name, description, parent, or properties")
    public ResponseEntity<ContentType> updateType(
            @PathVariable String name,
            @RequestBody ContentType updates) {
        return ResponseEntity.ok(contentTypeService.updateType(name, updates));
    }

    @DeleteMapping("/{name}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete type", description = "Delete a content type by name")
    public ResponseEntity<Void> deleteType(@PathVariable String name) {
        contentTypeService.deleteType(name);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/nodes/{nodeId}/apply")
    @Operation(summary = "Apply type", description = "Apply a content type and properties to a node")
    public ResponseEntity<NodeDto> applyType(
            @PathVariable UUID nodeId,
            @RequestParam String type,
            @RequestBody Map<String, Object> properties) {
        return ResponseEntity.ok(toNodeDto(contentTypeService.applyType(nodeId, type, properties)));
    }

    private NodeDto toNodeDto(com.ecm.core.entity.Node node) {
        NodeDto base = NodeDto.from(
            node,
            nodePropertyEncryptionService != null
                ? nodePropertyEncryptionService.resolveReadableProperties(node)
                : node.getProperties()
        );
        if (!(node instanceof Document document)) {
            return base;
        }
        RenditionResourceService.RenditionSummary renditionSummary = renditionResourceService.summarizeDocument(document);
        if (renditionSummary == null || !renditionSummary.document()) {
            return base;
        }
        return base.withPreviewSemantics(
            renditionSummary.previewStatus(),
            renditionSummary.previewFailureReason(),
            renditionSummary.previewFailureCategory(),
            renditionSummary.previewLastUpdated()
        );
    }
}
