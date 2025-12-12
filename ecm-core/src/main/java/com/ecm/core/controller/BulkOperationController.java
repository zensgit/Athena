package com.ecm.core.controller;

import com.ecm.core.service.BulkOperationService;
import com.ecm.core.service.BulkOperationService.BulkOperationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bulk")
@RequiredArgsConstructor
@Tag(name = "Bulk Operations", description = "APIs for batch processing nodes")
public class BulkOperationController {

    private final BulkOperationService bulkService;

    @PostMapping("/move")
    @Operation(summary = "Bulk move", description = "Move multiple nodes to a target folder")
    public ResponseEntity<BulkOperationResult> bulkMove(@RequestBody BulkRequest request) {
        if (request.targetId() == null) {
            throw new IllegalArgumentException("Target folder ID is required for move");
        }
        return ResponseEntity.ok(bulkService.bulkMove(request.ids(), request.targetId()));
    }

    @PostMapping("/copy")
    @Operation(summary = "Bulk copy", description = "Copy multiple nodes to a target folder")
    public ResponseEntity<BulkOperationResult> bulkCopy(@RequestBody BulkRequest request) {
        if (request.targetId() == null) {
            throw new IllegalArgumentException("Target folder ID is required for copy");
        }
        return ResponseEntity.ok(bulkService.bulkCopy(request.ids(), request.targetId()));
    }

    @PostMapping("/delete")
    @Operation(summary = "Bulk delete", description = "Move multiple nodes to trash")
    public ResponseEntity<BulkOperationResult> bulkDelete(@RequestBody BulkRequest request) {
        return ResponseEntity.ok(bulkService.bulkDelete(request.ids()));
    }

    @PostMapping("/restore")
    @Operation(summary = "Bulk restore", description = "Restore multiple nodes from trash")
    public ResponseEntity<BulkOperationResult> bulkRestore(@RequestBody BulkRequest request) {
        return ResponseEntity.ok(bulkService.bulkRestore(request.ids()));
    }

    // DTO
    public record BulkRequest(List<UUID> ids, UUID targetId) {}
}
