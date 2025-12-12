package com.ecm.core.controller;

import com.ecm.core.entity.Correspondent;
import com.ecm.core.service.CorrespondentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/correspondents")
@RequiredArgsConstructor
@Tag(name = "Metadata: Correspondents", description = "Manage document correspondents")
public class CorrespondentController {

    private final CorrespondentService correspondentService;

    @GetMapping
    @Operation(summary = "List correspondents")
    public ResponseEntity<Page<Correspondent>> list(Pageable pageable) {
        return ResponseEntity.ok(correspondentService.getCorrespondents(pageable));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('EDITOR')")
    @Operation(summary = "Create correspondent")
    public ResponseEntity<Correspondent> create(@RequestBody Correspondent correspondent) {
        return ResponseEntity.ok(correspondentService.createCorrespondent(correspondent));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('EDITOR')")
    @Operation(summary = "Update correspondent")
    public ResponseEntity<Correspondent> update(@PathVariable UUID id, @RequestBody Correspondent correspondent) {
        return ResponseEntity.ok(correspondentService.updateCorrespondent(id, correspondent));
    }
}
