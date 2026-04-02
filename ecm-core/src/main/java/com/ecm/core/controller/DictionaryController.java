package com.ecm.core.controller;

import com.ecm.core.dto.AspectDefinitionDto;
import com.ecm.core.dto.PropertyDefinitionDto;
import com.ecm.core.dto.TypeDefinitionDto;
import com.ecm.core.service.DictionaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/dictionary", "/api/v1/dictionary"})
@Tag(name = "Data Dictionary")
public class DictionaryController {

    private final DictionaryService dictionaryService;

    // ── Types ────────────────────────────────────────────────────────────

    @GetMapping("/types")
    @Operation(summary = "List all registered type definitions")
    public ResponseEntity<List<TypeDefinitionDto>> listTypes() {
        return ResponseEntity.ok(dictionaryService.listTypes().stream().map(TypeDefinitionDto::from).toList());
    }

    @GetMapping("/types/{qualifiedName}")
    @Operation(summary = "Get a type definition by qualified name")
    public ResponseEntity<TypeDefinitionDto> getType(
            @Parameter(description = "Qualified type name (e.g. cm:content)")
            @PathVariable String qualifiedName) {
        String decoded = URLDecoder.decode(qualifiedName, StandardCharsets.UTF_8);
        return ResponseEntity.ok(TypeDefinitionDto.from(dictionaryService.getType(decoded)));
    }

    @GetMapping("/types/{qualifiedName}/properties")
    @Operation(summary = "Get all properties defined on a type")
    public ResponseEntity<List<PropertyDefinitionDto>> getPropertiesForType(
            @Parameter(description = "Qualified type name")
            @PathVariable String qualifiedName) {
        String decoded = URLDecoder.decode(qualifiedName, StandardCharsets.UTF_8);
        return ResponseEntity.ok(dictionaryService.getPropertiesForType(decoded).stream().map(PropertyDefinitionDto::from).toList());
    }

    @GetMapping("/types/{qualifiedName}/hierarchy")
    @Operation(summary = "Resolve the full parent hierarchy of a type")
    public ResponseEntity<List<String>> resolveTypeHierarchy(
            @Parameter(description = "Qualified type name")
            @PathVariable String qualifiedName) {
        String decoded = URLDecoder.decode(qualifiedName, StandardCharsets.UTF_8);
        return ResponseEntity.ok(dictionaryService.resolveTypeHierarchy(decoded));
    }

    @GetMapping("/types/{qualifiedName}/mandatory-aspects")
    @Operation(summary = "Get mandatory aspects for a type")
    public ResponseEntity<List<String>> getMandatoryAspectsForType(
            @Parameter(description = "Qualified type name")
            @PathVariable String qualifiedName) {
        String decoded = URLDecoder.decode(qualifiedName, StandardCharsets.UTF_8);
        return ResponseEntity.ok(dictionaryService.getMandatoryAspectsForType(decoded));
    }

    // ── Aspects ──────────────────────────────────────────────────────────

    @GetMapping("/aspects")
    @Operation(summary = "List all registered aspect definitions")
    public ResponseEntity<List<AspectDefinitionDto>> listAspects() {
        return ResponseEntity.ok(dictionaryService.listAspects().stream().map(AspectDefinitionDto::from).toList());
    }

    @GetMapping("/aspects/{qualifiedName}")
    @Operation(summary = "Get an aspect definition by qualified name")
    public ResponseEntity<AspectDefinitionDto> getAspect(
            @Parameter(description = "Qualified aspect name (e.g. cm:titled)")
            @PathVariable String qualifiedName) {
        String decoded = URLDecoder.decode(qualifiedName, StandardCharsets.UTF_8);
        return ResponseEntity.ok(AspectDefinitionDto.from(dictionaryService.getAspect(decoded)));
    }

    @GetMapping("/aspects/{qualifiedName}/properties")
    @Operation(summary = "Get all properties defined on an aspect")
    public ResponseEntity<List<PropertyDefinitionDto>> getPropertiesForAspect(
            @Parameter(description = "Qualified aspect name")
            @PathVariable String qualifiedName) {
        String decoded = URLDecoder.decode(qualifiedName, StandardCharsets.UTF_8);
        return ResponseEntity.ok(dictionaryService.getPropertiesForAspect(decoded).stream().map(PropertyDefinitionDto::from).toList());
    }
}
