package com.ecm.core.controller;

import com.ecm.core.dto.PermissionTemplateVersionDetailDto;
import com.ecm.core.dto.PermissionTemplateVersionDto;
import com.ecm.core.dto.PermissionTemplateVersionDiffDto;
import com.ecm.core.entity.PermissionTemplate;
import com.ecm.core.service.AuditService;
import com.ecm.core.service.PermissionTemplateService;
import com.ecm.core.service.SecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/security/permission-templates")
@RequiredArgsConstructor
@Tag(name = "Permission Templates", description = "Manage permission templates")
public class PermissionTemplateController {

    private final PermissionTemplateService permissionTemplateService;
    private final AuditService auditService;
    private final SecurityService securityService;

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

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List permission template versions")
    public ResponseEntity<List<PermissionTemplateVersionDto>> listVersions(
        @Parameter(description = "Template ID") @PathVariable UUID id) {
        return ResponseEntity.ok(permissionTemplateService.listVersions(id).stream()
            .map(PermissionTemplateVersionDto::from)
            .toList());
    }

    @GetMapping("/{id}/versions/diff/export")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Export permission template version diff",
        description = "Export the diff between two permission template versions as CSV or JSON")
    public ResponseEntity<?> exportVersionDiff(
        @Parameter(description = "Template ID") @PathVariable UUID id,
        @Parameter(description = "From version ID") @RequestParam UUID from,
        @Parameter(description = "To version ID") @RequestParam UUID to,
        @Parameter(description = "Export format (csv or json)") @RequestParam(defaultValue = "csv") String format) {

        PermissionTemplateVersionDiffDto diff = permissionTemplateService.computeVersionDiff(id, from, to);

        String normalized = format == null ? "csv" : format.trim().toLowerCase(Locale.ROOT);
        if (!"csv".equals(normalized) && !"json".equals(normalized)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Unsupported format: " + format));
        }

        String baseName = diff.getTemplateName() != null ? diff.getTemplateName() : "template";
        baseName = baseName.replaceAll("\\s+", "-").replaceAll("[^A-Za-z0-9._-]", "");
        if (baseName.isBlank()) {
            baseName = "template";
        }
        String filename = String.format(
            "%s-diff-%s-to-%s.%s",
            baseName,
            diff.getFromVersionNumber() != null ? diff.getFromVersionNumber() : "from",
            diff.getToVersionNumber() != null ? diff.getToVersionNumber() : "to",
            normalized
        );

        String username = securityService.getCurrentUser();
        auditService.logEvent(
            "SECURITY_PERMISSION_TEMPLATE_DIFF_EXPORT",
            id,
            diff.getTemplateName(),
            username,
            String.format(
                "fromVersionId=%s toVersionId=%s fromVersion=%s toVersion=%s format=%s added=%d removed=%d changed=%d",
                diff.getFromVersionId(),
                diff.getToVersionId(),
                diff.getFromVersionNumber(),
                diff.getToVersionNumber(),
                normalized,
                diff.getAdded() != null ? diff.getAdded().size() : 0,
                diff.getRemoved() != null ? diff.getRemoved().size() : 0,
                diff.getChanged() != null ? diff.getChanged().size() : 0
            )
        );

        if ("json".equals(normalized)) {
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(diff);
        }

        String csv = permissionTemplateService.formatVersionDiffCsv(diff);
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .body(bytes);
    }

    @GetMapping("/{id}/versions/{versionId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get permission template version details")
    public ResponseEntity<PermissionTemplateVersionDetailDto> getVersion(
        @Parameter(description = "Template ID") @PathVariable UUID id,
        @Parameter(description = "Version ID") @PathVariable UUID versionId) {
        return ResponseEntity.ok(PermissionTemplateVersionDetailDto.from(
            permissionTemplateService.getVersion(id, versionId)
        ));
    }

    @PostMapping("/{id}/versions/{versionId}/rollback")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rollback permission template to a version")
    public ResponseEntity<PermissionTemplate> rollbackTemplate(
        @Parameter(description = "Template ID") @PathVariable UUID id,
        @Parameter(description = "Version ID") @PathVariable UUID versionId) {
        return ResponseEntity.ok(permissionTemplateService.rollback(id, versionId));
    }
}
