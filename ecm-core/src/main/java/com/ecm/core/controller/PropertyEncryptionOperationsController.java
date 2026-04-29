package com.ecm.core.controller;

import com.ecm.core.service.PropertyEncryptionOperationsService;
import com.ecm.core.service.PropertyEncryptionOperationsService.EncryptedPropertyDefinitionSummary;
import com.ecm.core.service.PropertyEncryptionOperationsService.PropertyEncryptionStatus;
import com.ecm.core.service.PropertyEncryptionOperationsService.PropertyEncryptionRewrapDryRunRequest;
import com.ecm.core.service.PropertyEncryptionOperationsService.PropertyEncryptionRewrapDryRunResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/property-encryption")
@RequiredArgsConstructor
@Tag(name = "Property Encryption Operations", description = "Read-only property encryption diagnostics")
@PreAuthorize("hasRole('ADMIN')")
public class PropertyEncryptionOperationsController {

    private final PropertyEncryptionOperationsService propertyEncryptionOperationsService;

    @GetMapping("/status")
    @Operation(
        summary = "Get property encryption status",
        description = "Returns secret crypto configuration health and encrypted-property storage counts without exposing key material."
    )
    public ResponseEntity<PropertyEncryptionStatus> getStatus() {
        return ResponseEntity.ok(propertyEncryptionOperationsService.getStatus());
    }

    @GetMapping("/definitions")
    @Operation(
        summary = "List encrypted property definitions",
        description = "Returns encrypted model properties for admin diagnostics without reading encrypted node values."
    )
    public ResponseEntity<List<EncryptedPropertyDefinitionSummary>> listDefinitions() {
        return ResponseEntity.ok(propertyEncryptionOperationsService.listEncryptedDefinitions());
    }

    @PostMapping("/rewrap-jobs/dry-run")
    @Operation(
        summary = "Dry-run property encryption rewrap",
        description = "Estimates encrypted property values requiring rewrap to a target key version without mutating nodes or creating jobs."
    )
    public ResponseEntity<PropertyEncryptionRewrapDryRunResult> dryRunRewrap(
        @RequestBody(required = false) PropertyEncryptionRewrapDryRunRequest request
    ) {
        return ResponseEntity.ok(propertyEncryptionOperationsService.dryRunRewrap(request));
    }
}
