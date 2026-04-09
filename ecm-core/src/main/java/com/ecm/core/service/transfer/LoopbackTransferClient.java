package com.ecm.core.service.transfer;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.TransferTarget;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.NodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LoopbackTransferClient implements TransferClient {

    private final FolderService folderService;
    private final NodeService nodeService;
    private final NodeRepository nodeRepository;

    @Override
    public TransferTarget.TransportType transportType() {
        return TransferTarget.TransportType.LOOPBACK;
    }

    @Override
    public TransferVerificationResult verifyTarget(TransferTarget target) {
        String folderName = folderService.getFolder(target.getTargetFolderId()).getName();
        return new TransferVerificationResult("Verified local target folder: " + folderName);
    }

    @Override
    public TransferExecutionResult replicate(TransferTarget target, Node source, boolean includeChildren) {
        folderService.getFolder(target.getTargetFolderId());
        String replicaName = resolveReplicaName(target.getTargetFolderId(), source.getName());
        Node copied = nodeService.copyNode(source.getId(), target.getTargetFolderId(), replicaName, includeChildren);
        return new TransferExecutionResult(copied.getId(), "Loopback replication completed");
    }

    private String resolveReplicaName(UUID targetFolderId, String requestedName) {
        String baseName = requestedName;
        String extension = "";
        int dotIndex = requestedName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = requestedName.substring(0, dotIndex);
            extension = requestedName.substring(dotIndex);
        }
        String candidate = requestedName;
        int attempt = 1;
        Optional<?> existing = nodeRepository.findByParentIdAndName(targetFolderId, candidate);
        while (existing.isPresent()) {
            candidate = baseName + " (Replica " + attempt + ")" + extension;
            attempt++;
            existing = nodeRepository.findByParentIdAndName(targetFolderId, candidate);
        }
        return candidate;
    }
}
