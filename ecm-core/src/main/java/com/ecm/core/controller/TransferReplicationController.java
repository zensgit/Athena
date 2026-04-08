package com.ecm.core.controller;

import com.ecm.core.service.TransferReplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Transfer Replication", description = "Local transfer target and replication backbone")
public class TransferReplicationController {

    private final TransferReplicationService transferReplicationService;

    @GetMapping("/transfer/targets")
    @Operation(summary = "List transfer targets")
    public ResponseEntity<List<TransferReplicationService.TransferTargetDto>> listTargets() {
        return ResponseEntity.ok(transferReplicationService.listTargets());
    }

    @GetMapping("/transfer/targets/{targetId}")
    @Operation(summary = "Get transfer target")
    public ResponseEntity<TransferReplicationService.TransferTargetDto> getTarget(@PathVariable UUID targetId) {
        return ResponseEntity.ok(transferReplicationService.getTarget(targetId));
    }

    @PostMapping("/transfer/targets")
    @Operation(summary = "Create transfer target")
    public ResponseEntity<TransferReplicationService.TransferTargetDto> createTarget(
        @RequestBody TransferReplicationService.TransferTargetMutationRequest request
    ) {
        return ResponseEntity.status(201).body(transferReplicationService.createTarget(request));
    }

    @PutMapping("/transfer/targets/{targetId}")
    @Operation(summary = "Update transfer target")
    public ResponseEntity<TransferReplicationService.TransferTargetDto> updateTarget(
        @PathVariable UUID targetId,
        @RequestBody TransferReplicationService.TransferTargetMutationRequest request
    ) {
        return ResponseEntity.ok(transferReplicationService.updateTarget(targetId, request));
    }

    @DeleteMapping("/transfer/targets/{targetId}")
    @Operation(summary = "Delete transfer target")
    public ResponseEntity<Void> deleteTarget(@PathVariable UUID targetId) {
        transferReplicationService.deleteTarget(targetId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/replication/definitions")
    @Operation(summary = "List replication definitions")
    public ResponseEntity<List<TransferReplicationService.ReplicationDefinitionDto>> listDefinitions() {
        return ResponseEntity.ok(transferReplicationService.listDefinitions());
    }

    @GetMapping("/replication/definitions/{definitionId}")
    @Operation(summary = "Get replication definition")
    public ResponseEntity<TransferReplicationService.ReplicationDefinitionDto> getDefinition(@PathVariable UUID definitionId) {
        return ResponseEntity.ok(transferReplicationService.getDefinition(definitionId));
    }

    @PostMapping("/replication/definitions")
    @Operation(summary = "Create replication definition")
    public ResponseEntity<TransferReplicationService.ReplicationDefinitionDto> createDefinition(
        @RequestBody TransferReplicationService.ReplicationDefinitionMutationRequest request
    ) {
        return ResponseEntity.status(201).body(transferReplicationService.createDefinition(request));
    }

    @PutMapping("/replication/definitions/{definitionId}")
    @Operation(summary = "Update replication definition")
    public ResponseEntity<TransferReplicationService.ReplicationDefinitionDto> updateDefinition(
        @PathVariable UUID definitionId,
        @RequestBody TransferReplicationService.ReplicationDefinitionMutationRequest request
    ) {
        return ResponseEntity.ok(transferReplicationService.updateDefinition(definitionId, request));
    }

    @DeleteMapping("/replication/definitions/{definitionId}")
    @Operation(summary = "Delete replication definition")
    public ResponseEntity<Void> deleteDefinition(@PathVariable UUID definitionId) {
        transferReplicationService.deleteDefinition(definitionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/replication/definitions/{definitionId}/run")
    @Operation(summary = "Execute replication definition now")
    public ResponseEntity<TransferReplicationService.ReplicationJobDto> runDefinition(@PathVariable UUID definitionId) {
        return ResponseEntity.accepted().body(transferReplicationService.runDefinition(definitionId));
    }

    @GetMapping("/replication/jobs")
    @Operation(summary = "List replication jobs")
    public ResponseEntity<Page<TransferReplicationService.ReplicationJobDto>> listJobs(Pageable pageable) {
        return ResponseEntity.ok(transferReplicationService.listJobs(pageable));
    }

    @GetMapping("/replication/jobs/{jobId}")
    @Operation(summary = "Get replication job")
    public ResponseEntity<TransferReplicationService.ReplicationJobDto> getJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(transferReplicationService.getJob(jobId));
    }
}
