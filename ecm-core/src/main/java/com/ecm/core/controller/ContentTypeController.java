package com.ecm.core.controller;

import com.ecm.core.dto.NodeDto;
import com.ecm.core.entity.ContentType;
import com.ecm.core.service.ContentTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/types")
@RequiredArgsConstructor
@Tag(name = "Content Types", description = "Manage content types and metadata schemas")
public class ContentTypeController {

    private final ContentTypeService contentTypeService;

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

    @PostMapping("/nodes/{nodeId}/apply")
    @Operation(summary = "Apply type", description = "Apply a content type and properties to a node")
    public ResponseEntity<NodeDto> applyType(
            @PathVariable UUID nodeId,
            @RequestParam String type,
            @RequestBody Map<String, Object> properties) {
        return ResponseEntity.ok(NodeDto.from(contentTypeService.applyType(nodeId, type, properties)));
    }
}
