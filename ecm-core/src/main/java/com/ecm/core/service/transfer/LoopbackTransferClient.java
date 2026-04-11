package com.ecm.core.service.transfer;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.ReplicationDefinition;
import com.ecm.core.entity.TransferTarget;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.service.ContentService;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.VersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LoopbackTransferClient implements TransferClient {

    private final FolderService folderService;
    private final NodeService nodeService;
    private final NodeRepository nodeRepository;
    private final ContentService contentService;
    private final VersionService versionService;

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
    public TransferExecutionResult replicate(
        TransferTarget target,
        Node source,
        boolean includeChildren,
        ReplicationDefinition.ConflictPolicy conflictPolicy
    ) {
        folderService.getFolder(target.getTargetFolderId());
        ReplicationDefinition.ConflictPolicy effectivePolicy = conflictPolicy != null
            ? conflictPolicy
            : ReplicationDefinition.ConflictPolicy.RENAME;
        Node replicated = replicateToParent(target.getTargetFolderId(), source, includeChildren, effectivePolicy);
        String message = switch (effectivePolicy) {
            case SKIP -> "Loopback replication applied with SKIP policy";
            case RENAME -> "Loopback replication applied with RENAME policy";
            case OVERWRITE -> "Loopback replication applied with OVERWRITE policy";
        };
        return new TransferExecutionResult(replicated.getId(), message);
    }

    private Node replicateToParent(
        UUID targetParentId,
        Node source,
        boolean includeChildren,
        ReplicationDefinition.ConflictPolicy conflictPolicy
    ) {
        Optional<Node> existing = nodeRepository.findByParentIdAndName(targetParentId, source.getName());
        if (existing.isEmpty()) {
            return nodeService.copyNode(source.getId(), targetParentId, source.getName(), includeChildren);
        }

        return switch (conflictPolicy) {
            case SKIP -> existing.get();
            case RENAME -> nodeService.copyNode(
                source.getId(),
                targetParentId,
                generateReplicaName(targetParentId, source.getName()),
                includeChildren
            );
            case OVERWRITE -> overwriteExisting(targetParentId, source, includeChildren, existing.get());
        };
    }

    private Node overwriteExisting(UUID targetParentId, Node source, boolean includeChildren, Node existing) {
        if (source instanceof Document documentSource && existing instanceof Document documentTarget) {
            overwriteDocument(documentSource, documentTarget);
            return documentTarget;
        }

        nodeService.deleteNode(existing.getId(), false);
        return nodeService.copyNode(source.getId(), targetParentId, source.getName(), includeChildren);
    }

    private void overwriteDocument(Document source, Document target) {
        if (source.getContentId() == null || source.getContentId().isBlank()) {
            throw new IllegalStateException("Source document has no content for overwrite: " + source.getId());
        }
        try (InputStream content = contentService.getContent(source.getContentId())) {
            versionService.createVersion(
                target.getId(),
                content,
                source.getName(),
                "Replicated overwrite from " + source.getId(),
                false
            );
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read source content for loopback overwrite: " + source.getId(), ex);
        }
        if (source.getDescription() != null) {
            nodeService.updateNode(target.getId(), java.util.Map.of("description", source.getDescription()));
        }
    }

    private String generateReplicaName(UUID targetFolderId, String requestedName) {
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
