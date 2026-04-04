package com.ecm.core.controller;

import com.ecm.core.service.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/templates", "/api/v1/templates"})
@Tag(name = "Template Engine", description = "Manage and execute FreeMarker templates")
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping
    @Operation(summary = "List managed templates")
    public ResponseEntity<List<TemplateService.TemplateDefinitionDto>> listTemplates() {
        return ResponseEntity.ok(templateService.listTemplates());
    }

    @GetMapping("/{templateId}")
    @Operation(summary = "Get a managed template")
    public ResponseEntity<TemplateService.TemplateDefinitionDto> getTemplate(@PathVariable UUID templateId) {
        return ResponseEntity.ok(templateService.getTemplate(templateId));
    }

    @PostMapping
    @Operation(summary = "Create a managed template")
    public ResponseEntity<TemplateService.TemplateDefinitionDto> createTemplate(
        @RequestBody TemplateService.TemplateMutationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.createTemplate(request));
    }

    @PutMapping("/{templateId}")
    @Operation(summary = "Update a managed template")
    public ResponseEntity<TemplateService.TemplateDefinitionDto> updateTemplate(
        @PathVariable UUID templateId,
        @RequestBody TemplateService.TemplateMutationRequest request
    ) {
        return ResponseEntity.ok(templateService.updateTemplate(templateId, request));
    }

    @DeleteMapping("/{templateId}")
    @Operation(summary = "Delete a managed template")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID templateId) {
        templateService.deleteTemplate(templateId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/execute")
    @Operation(summary = "Execute a stored or inline template")
    public ResponseEntity<TemplateService.TemplateExecutionResult> executeTemplate(
        @RequestBody TemplateService.TemplateExecutionRequest request
    ) {
        return ResponseEntity.ok(templateService.executeTemplate(request));
    }
}
