package com.ecm.core.controller;

import com.ecm.core.service.ScriptService;
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
@RequestMapping({"/api/scripts", "/api/v1/scripts"})
@Tag(name = "Script Engine", description = "Manage and execute GraalJS scripts")
public class ScriptController {

    private final ScriptService scriptService;

    @GetMapping
    @Operation(summary = "List managed scripts")
    public ResponseEntity<List<ScriptService.ScriptDefinitionDto>> listScripts() {
        return ResponseEntity.ok(scriptService.listScripts());
    }

    @GetMapping("/{scriptId}")
    @Operation(summary = "Get a managed script")
    public ResponseEntity<ScriptService.ScriptDefinitionDto> getScript(@PathVariable UUID scriptId) {
        return ResponseEntity.ok(scriptService.getScript(scriptId));
    }

    @PostMapping
    @Operation(summary = "Create a managed script")
    public ResponseEntity<ScriptService.ScriptDefinitionDto> createScript(
        @RequestBody ScriptService.ScriptMutationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scriptService.createScript(request));
    }

    @PutMapping("/{scriptId}")
    @Operation(summary = "Update a managed script")
    public ResponseEntity<ScriptService.ScriptDefinitionDto> updateScript(
        @PathVariable UUID scriptId,
        @RequestBody ScriptService.ScriptMutationRequest request
    ) {
        return ResponseEntity.ok(scriptService.updateScript(scriptId, request));
    }

    @DeleteMapping("/{scriptId}")
    @Operation(summary = "Delete a managed script")
    public ResponseEntity<Void> deleteScript(@PathVariable UUID scriptId) {
        scriptService.deleteScript(scriptId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/execute")
    @Operation(summary = "Execute a stored or inline script")
    public ResponseEntity<ScriptService.ScriptExecutionResult> executeScript(
        @RequestBody ScriptService.ScriptExecutionRequest request
    ) {
        return ResponseEntity.ok(scriptService.executeScript(request));
    }
}
