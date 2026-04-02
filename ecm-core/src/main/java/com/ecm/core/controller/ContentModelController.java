package com.ecm.core.controller;

import com.ecm.core.dto.AspectDefinitionDto;
import com.ecm.core.dto.ConstraintDefinitionDto;
import com.ecm.core.dto.ContentModelDefinitionDto;
import com.ecm.core.dto.PropertyDefinitionDto;
import com.ecm.core.dto.TypeDefinitionDto;
import com.ecm.core.entity.AspectDefinition;
import com.ecm.core.entity.ConstraintDefinition;
import com.ecm.core.entity.ContentModelDefinition;
import com.ecm.core.entity.PropertyDefinition;
import com.ecm.core.entity.TypeDefinition;
import com.ecm.core.service.ContentModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/content-models", "/api/v1/content-models"})
@Tag(name = "Content Model Management")
public class ContentModelController {

    private final ContentModelService contentModelService;

    @GetMapping
    @Operation(summary = "List all content models")
    public ResponseEntity<List<ContentModelDefinitionDto>> listModels() {
        return ResponseEntity.ok(contentModelService.listModels().stream().map(ContentModelDefinitionDto::from).toList());
    }

    @GetMapping("/{modelId}")
    @Operation(summary = "Get a content model by ID")
    public ResponseEntity<ContentModelDefinitionDto> getModel(
            @Parameter(description = "Content model ID") @PathVariable UUID modelId) {
        return ResponseEntity.ok(ContentModelDefinitionDto.from(contentModelService.getModel(modelId)));
    }

    @PostMapping
    @Operation(summary = "Create a new content model")
    public ResponseEntity<ContentModelDefinitionDto> createModel(
            @RequestBody ContentModelDefinition definition) {
        ContentModelDefinition created = contentModelService.createModel(definition);
        return ResponseEntity.status(HttpStatus.CREATED).body(ContentModelDefinitionDto.from(created));
    }

    @PutMapping("/{modelId}")
    @Operation(summary = "Update a content model name and description")
    public ResponseEntity<ContentModelDefinitionDto> updateModel(
            @Parameter(description = "Content model ID") @PathVariable UUID modelId,
            @Parameter(description = "New model name") @RequestParam String name,
            @Parameter(description = "New model description") @RequestParam String description) {
        return ResponseEntity.ok(ContentModelDefinitionDto.from(contentModelService.updateModel(modelId, name, description)));
    }

    @PostMapping("/{modelId}/activate")
    @Operation(summary = "Activate a content model")
    public ResponseEntity<ContentModelDefinitionDto> activateModel(
            @Parameter(description = "Content model ID") @PathVariable UUID modelId) {
        return ResponseEntity.ok(ContentModelDefinitionDto.from(contentModelService.activateModel(modelId)));
    }

    @PostMapping("/{modelId}/deactivate")
    @Operation(summary = "Deactivate a content model")
    public ResponseEntity<ContentModelDefinitionDto> deactivateModel(
            @Parameter(description = "Content model ID") @PathVariable UUID modelId) {
        return ResponseEntity.ok(ContentModelDefinitionDto.from(contentModelService.deactivateModel(modelId)));
    }

    @DeleteMapping("/{modelId}")
    @Operation(summary = "Delete a content model")
    public ResponseEntity<Void> deleteModel(
            @Parameter(description = "Content model ID") @PathVariable UUID modelId) {
        contentModelService.deleteModel(modelId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{modelId}/types")
    @Operation(summary = "Add a type definition to a content model")
    public ResponseEntity<TypeDefinitionDto> addType(
            @Parameter(description = "Content model ID") @PathVariable UUID modelId,
            @RequestBody TypeDefinition typeDefinition) {
        TypeDefinition created = contentModelService.addType(modelId, typeDefinition);
        return ResponseEntity.status(HttpStatus.CREATED).body(TypeDefinitionDto.from(created));
    }

    @PostMapping("/{modelId}/aspects")
    @Operation(summary = "Add an aspect definition to a content model")
    public ResponseEntity<AspectDefinitionDto> addAspectDefinition(
            @Parameter(description = "Content model ID") @PathVariable UUID modelId,
            @RequestBody AspectDefinition aspectDefinition) {
        AspectDefinition created = contentModelService.addAspectDefinition(modelId, aspectDefinition);
        return ResponseEntity.status(HttpStatus.CREATED).body(AspectDefinitionDto.from(created));
    }

    @PostMapping("/types/{typeId}/properties")
    @Operation(summary = "Add a property to a type definition")
    public ResponseEntity<PropertyDefinitionDto> addPropertyToType(
            @Parameter(description = "Type definition ID") @PathVariable UUID typeId,
            @RequestBody PropertyDefinition propertyDefinition) {
        PropertyDefinition created = contentModelService.addProperty(typeId, propertyDefinition, false);
        return ResponseEntity.status(HttpStatus.CREATED).body(PropertyDefinitionDto.from(created));
    }

    @PostMapping("/aspects/{aspectId}/properties")
    @Operation(summary = "Add a property to an aspect definition")
    public ResponseEntity<PropertyDefinitionDto> addPropertyToAspect(
            @Parameter(description = "Aspect definition ID") @PathVariable UUID aspectId,
            @RequestBody PropertyDefinition propertyDefinition) {
        PropertyDefinition created = contentModelService.addProperty(aspectId, propertyDefinition, true);
        return ResponseEntity.status(HttpStatus.CREATED).body(PropertyDefinitionDto.from(created));
    }

    @PostMapping("/properties/{propertyId}/constraints")
    @Operation(summary = "Add a constraint to a property definition")
    public ResponseEntity<ConstraintDefinitionDto> addConstraint(
            @Parameter(description = "Property definition ID") @PathVariable UUID propertyId,
            @RequestBody ConstraintDefinition constraintDefinition) {
        ConstraintDefinition created = contentModelService.addConstraint(propertyId, constraintDefinition);
        return ResponseEntity.status(HttpStatus.CREATED).body(ConstraintDefinitionDto.from(created));
    }

    // ---- update / delete --------------------------------------------------

    @PutMapping("/types/{typeId}")
    @Operation(summary = "Update a type definition")
    public ResponseEntity<TypeDefinitionDto> updateType(
            @PathVariable UUID typeId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String parentName) {
        return ResponseEntity.ok(TypeDefinitionDto.from(contentModelService.updateType(typeId, title, description, parentName)));
    }

    @DeleteMapping("/types/{typeId}")
    @Operation(summary = "Delete a type definition")
    public ResponseEntity<Void> deleteType(@PathVariable UUID typeId) {
        contentModelService.deleteType(typeId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/aspects/{aspectId}")
    @Operation(summary = "Update an aspect definition")
    public ResponseEntity<AspectDefinitionDto> updateAspect(
            @PathVariable UUID aspectId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String parentName) {
        return ResponseEntity.ok(AspectDefinitionDto.from(contentModelService.updateAspect(aspectId, title, description, parentName)));
    }

    @DeleteMapping("/aspects/{aspectId}")
    @Operation(summary = "Delete an aspect definition")
    public ResponseEntity<Void> deleteAspect(@PathVariable UUID aspectId) {
        contentModelService.deleteAspect(aspectId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/properties/{propertyId}")
    @Operation(summary = "Delete a property definition")
    public ResponseEntity<Void> deleteProperty(@PathVariable UUID propertyId) {
        contentModelService.deleteProperty(propertyId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/constraints/{constraintId}")
    @Operation(summary = "Delete a constraint definition")
    public ResponseEntity<Void> deleteConstraint(@PathVariable UUID constraintId) {
        contentModelService.deleteConstraint(constraintId);
        return ResponseEntity.noContent().build();
    }
}
