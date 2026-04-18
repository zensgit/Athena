package com.ecm.core.controller;

import com.ecm.core.service.LegalHoldService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/legal-holds")
@RequiredArgsConstructor
@Tag(name = "Legal Holds", description = "Manage legal holds and held nodes")
@PreAuthorize("hasRole('ADMIN')")
public class LegalHoldController {

    private final LegalHoldService legalHoldService;

    @GetMapping
    @Operation(summary = "List legal holds")
    public ResponseEntity<List<LegalHoldService.LegalHoldSummaryDto>> listHolds() {
        return ResponseEntity.ok(legalHoldService.listHolds());
    }

    @GetMapping("/{holdId}")
    @Operation(summary = "Get legal hold detail")
    public ResponseEntity<LegalHoldService.LegalHoldDto> getHold(@PathVariable UUID holdId) {
        return ResponseEntity.ok(legalHoldService.getHold(holdId));
    }

    @PostMapping
    @Operation(summary = "Create legal hold")
    public ResponseEntity<LegalHoldService.LegalHoldDto> createHold(
        @RequestBody LegalHoldService.CreateLegalHoldRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(legalHoldService.createHold(request));
    }

    @PostMapping("/{holdId}/items")
    @Operation(summary = "Add nodes to legal hold")
    public ResponseEntity<LegalHoldService.LegalHoldDto> addItems(
        @PathVariable UUID holdId,
        @RequestBody LegalHoldService.AddHoldItemsRequest request
    ) {
        return ResponseEntity.ok(legalHoldService.addItems(holdId, request));
    }

    @DeleteMapping("/{holdId}/items/{nodeId}")
    @Operation(summary = "Remove node from legal hold")
    public ResponseEntity<LegalHoldService.LegalHoldDto> removeItem(
        @PathVariable UUID holdId,
        @PathVariable UUID nodeId
    ) {
        return ResponseEntity.ok(legalHoldService.removeItem(holdId, nodeId));
    }

    @PostMapping("/{holdId}/release")
    @Operation(summary = "Release legal hold")
    public ResponseEntity<LegalHoldService.LegalHoldDto> releaseHold(
        @PathVariable UUID holdId,
        @RequestBody(required = false) LegalHoldService.ReleaseLegalHoldRequest request
    ) {
        return ResponseEntity.ok(legalHoldService.releaseHold(holdId, request));
    }
}
