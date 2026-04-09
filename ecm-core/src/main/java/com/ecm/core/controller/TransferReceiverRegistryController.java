package com.ecm.core.controller;

import com.ecm.core.service.TransferReceiverRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/transfer/receivers")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Transfer Receiver Registry", description = "Inbound transfer receiver registry and diagnostics")
public class TransferReceiverRegistryController {

    private final TransferReceiverRegistryService transferReceiverRegistryService;

    @GetMapping
    @Operation(summary = "List transfer receivers")
    public ResponseEntity<List<TransferReceiverRegistryService.TransferReceiverDto>> listReceivers() {
        return ResponseEntity.ok(transferReceiverRegistryService.listReceivers());
    }

    @GetMapping("/{receiverId}")
    @Operation(summary = "Get transfer receiver")
    public ResponseEntity<TransferReceiverRegistryService.TransferReceiverDto> getReceiver(@PathVariable UUID receiverId) {
        return ResponseEntity.ok(transferReceiverRegistryService.getReceiver(receiverId));
    }

    @PostMapping
    @Operation(summary = "Create transfer receiver")
    public ResponseEntity<TransferReceiverRegistryService.TransferReceiverDto> createReceiver(
        @RequestBody TransferReceiverRegistryService.TransferReceiverMutationRequest request
    ) {
        return ResponseEntity.status(201).body(transferReceiverRegistryService.createReceiver(request));
    }

    @PutMapping("/{receiverId}")
    @Operation(summary = "Update transfer receiver")
    public ResponseEntity<TransferReceiverRegistryService.TransferReceiverDto> updateReceiver(
        @PathVariable UUID receiverId,
        @RequestBody TransferReceiverRegistryService.TransferReceiverMutationRequest request
    ) {
        return ResponseEntity.ok(transferReceiverRegistryService.updateReceiver(receiverId, request));
    }

    @PostMapping("/{receiverId}/verify")
    @Operation(summary = "Verify transfer receiver root folder")
    public ResponseEntity<TransferReceiverRegistryService.TransferReceiverDto> verifyReceiver(@PathVariable UUID receiverId) {
        return ResponseEntity.ok(transferReceiverRegistryService.verifyReceiver(receiverId));
    }

    @DeleteMapping("/{receiverId}")
    @Operation(summary = "Delete transfer receiver")
    public ResponseEntity<Void> deleteReceiver(@PathVariable UUID receiverId) {
        transferReceiverRegistryService.deleteReceiver(receiverId);
        return ResponseEntity.noContent().build();
    }
}
