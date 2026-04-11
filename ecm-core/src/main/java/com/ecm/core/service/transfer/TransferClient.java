package com.ecm.core.service.transfer;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.ReplicationDefinition;
import com.ecm.core.entity.TransferTarget;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TransferClient {

    TransferTarget.TransportType transportType();

    TransferVerificationResult verifyTarget(TransferTarget target);

    TransferExecutionResult replicate(
        TransferTarget target,
        Node source,
        boolean includeChildren,
        ReplicationDefinition.ConflictPolicy conflictPolicy,
        LocalDateTime lastSuccessfulSyncAt
    );

    default TransferExecutionResult replicate(
        TransferTarget target,
        Node source,
        boolean includeChildren,
        ReplicationDefinition.ConflictPolicy conflictPolicy
    ) {
        return replicate(target, source, includeChildren, conflictPolicy, null);
    }

    record TransferVerificationResult(String message, String remoteRepositoryId) {
        public TransferVerificationResult(String message) {
            this(message, null);
        }
    }

    record TransferExecutionResult(UUID copiedNodeId, String message, List<TransferExecutionEntry> entries) {
        public TransferExecutionResult(UUID copiedNodeId, String message) {
            this(copiedNodeId, message, List.of());
        }
    }

    record TransferExecutionEntry(
        UUID sourceNodeId,
        String sourcePath,
        String sourceType,
        UUID targetNodeId,
        String action,
        String message,
        LocalDateTime startedAt,
        LocalDateTime completedAt
    ) {}
}
