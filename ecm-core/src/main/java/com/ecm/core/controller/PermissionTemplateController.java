package com.ecm.core.controller;

import com.ecm.core.entity.PermissionTemplate;
import com.ecm.core.service.PermissionTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/security/permission-templates")
@RequiredArgsConstructor
@Tag(name = "Permission Templates", description = "Manage permission templates")
public class PermissionTemplateController {

    private final PermissionTemplateService permissionTemplateService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List permission templates")
    public ResponseEntity<List<PermissionTemplate>> listTemplates() {
        return ResponseEntity.ok(permissionTemplateService.list());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create permission template")
    public ResponseEntity<PermissionTemplate> createTemplate(@RequestBody PermissionTemplate template) {
        return ResponseEntity.ok(permissionTemplateService.create(template));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update permission template")
    public ResponseEntity<PermissionTemplate> updateTemplate(
        @Parameter(description = "Template ID") @PathVariable UUID id,
        @RequestBody PermissionTemplate template) {
        return ResponseEntity.ok(permissionTemplateService.update(id, template));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete permission template")
    public ResponseEntity<Void> deleteTemplate(
        @Parameter(description = "Template ID") @PathVariable UUID id) {
        permissionTemplateService.delete(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/apply")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Apply permission template to node")
    public ResponseEntity<Void> applyTemplate(
        @Parameter(description = "Template ID") @PathVariable UUID id,
        @Parameter(description = "Node ID") @RequestParam UUID nodeId,
        @Parameter(description = "Replace existing permissions for listed principals")
        @RequestParam(defaultValue = "false") boolean replace) {
        permissionTemplateService.apply(id, nodeId, replace);
        return ResponseEntity.ok().build();
    }
}
