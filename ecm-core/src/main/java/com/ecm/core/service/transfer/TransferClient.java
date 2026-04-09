package com.ecm.core.service.transfer;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.TransferTarget;

import java.util.UUID;

public interface TransferClient {

    TransferTarget.TransportType transportType();

    TransferVerificationResult verifyTarget(TransferTarget target);

    TransferExecutionResult replicate(TransferTarget target, Node source, boolean includeChildren);

    record TransferVerificationResult(String message) {}

    record TransferExecutionResult(UUID copiedNodeId, String message) {}
}
