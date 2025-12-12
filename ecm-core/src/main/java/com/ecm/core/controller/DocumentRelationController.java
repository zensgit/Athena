package com.ecm.core.controller;

import com.ecm.core.entity.DocumentRelation;
import com.ecm.core.service.DocumentRelationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/relations")
@RequiredArgsConstructor
@Tag(name = "Document Relations", description = "Manage links between documents")
public class DocumentRelationController {

    private final DocumentRelationService relationService;

    @PostMapping
    @Operation(summary = "Create relation")
    public ResponseEntity<DocumentRelation> createRelation(@RequestBody CreateRelationRequest request) {
        return ResponseEntity.ok(
            relationService.createRelation(request.sourceId(), request.targetId(), request.type())
        );
    }

    @DeleteMapping
    @Operation(summary = "Delete relation")
    public ResponseEntity<Void> deleteRelation(
            @RequestParam UUID sourceId,
            @RequestParam UUID targetId,
            @RequestParam String type) {
        relationService.deleteRelation(sourceId, targetId, type);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{documentId}")
    @Operation(summary = "Get relations", description = "Get relations where document is source")
    public ResponseEntity<List<DocumentRelation>> getRelations(@PathVariable UUID documentId) {
        return ResponseEntity.ok(relationService.getRelations(documentId));
    }
    
    @GetMapping("/{documentId}/incoming")
    @Operation(summary = "Get incoming relations", description = "Get relations where document is target")
    public ResponseEntity<List<DocumentRelation>> getIncomingRelations(@PathVariable UUID documentId) {
        return ResponseEntity.ok(relationService.getIncomingRelations(documentId));
    }

    public record CreateRelationRequest(UUID sourceId, UUID targetId, String type) {}
}
