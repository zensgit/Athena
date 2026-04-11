package com.ecm.core.service.transfer;

import com.ecm.core.config.RepositoryIdentityProvider;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.Folder;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.ReplicationDefinition;
import com.ecm.core.entity.TransferTarget;
import com.ecm.core.repository.NodeRepository;
import com.ecm.core.entity.TransferNodeMapping;
import com.ecm.core.service.ContentService;
import com.ecm.core.service.FolderService;
import com.ecm.core.service.NodeService;
import com.ecm.core.service.TransferNodeMappingService;
import com.ecm.core.service.VersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final RepositoryIdentityProvider repositoryIdentityProvider;
    private final TransferNodeMappingService transferNodeMappingService;

    @Override
    public TransferTarget.TransportType transportType() {
        return TransferTarget.TransportType.LOOPBACK;
    }

    @Override
    public TransferVerificationResult verifyTarget(TransferTarget target) {
        String folderName = folderService.getFolder(target.getTargetFolderId()).getName();
        return new TransferVerificationResult("Verified local target folder: " + folderName, repositoryIdentityProvider.getTransferRepositoryId());
    }

    @Override
    public TransferExecutionResult replicate(
        TransferTarget target,
        Node source,
        boolean includeChildren,
        ReplicationDefinition.ConflictPolicy conflictPolicy,
        LocalDateTime lastSuccessfulSyncAt
    ) {
        UUID targetFolderId = target.getTargetFolderId();
        folderService.getFolder(targetFolderId);

        ReplicationDefinition.ConflictPolicy effectivePolicy = conflictPolicy != null
            ? conflictPolicy
            : ReplicationDefinition.ConflictPolicy.RENAME;
        List<TransferExecutionEntry> entries = new ArrayList<>();
        LoopbackReplicationResult replicated;

        if (source instanceof Folder folder) {
            replicated = replicateFolderTree(
                targetFolderId,
                targetFolderId,
                folder,
                includeChildren,
                effectivePolicy,
                lastSuccessfulSyncAt,
                entries
            );
        } else if (source instanceof Document document) {
            replicated = replicateDocumentToParent(
                targetFolderId,
                targetFolderId,
                document,
                effectivePolicy,
                lastSuccessfulSyncAt
            );
            entries.add(entryFor(document, replicated));
        } else {
            throw new IllegalArgumentException("Unsupported node type for loopback replication: " + source.getNodeType());
        }

        return new TransferExecutionResult(
            replicated.node() != null ? replicated.node().getId() : null,
            replicated.message(),
            entries
        );
    }

    private boolean isUnchangedSinceWatermark(Node node, LocalDateTime watermark) {
        if (watermark == null) {
            return false;
        }
        LocalDateTime lastModified = node.getLastModifiedDate();
        return lastModified != null && !lastModified.isAfter(watermark);
    }

    private LoopbackReplicationResult replicateFolderTree(
        UUID receiverRootId,
        UUID targetParentId,
        Folder source,
        boolean includeChildren,
        ReplicationDefinition.ConflictPolicy conflictPolicy,
        LocalDateTime lastSuccessfulSyncAt,
        List<TransferExecutionEntry> entries
    ) {
        LoopbackReplicationResult folderResult = alignFolder(receiverRootId, targetParentId, source, conflictPolicy);
        entries.add(entryFor(source, folderResult));
        if (!includeChildren || "SKIPPED".equals(folderResult.action())) {
            return folderResult;
        }

        for (Node child : loadChildrenSorted(source.getId())) {
            if (child instanceof Folder childFolder) {
                replicateFolderTree(
                    receiverRootId,
                    folderResult.node().getId(),
                    childFolder,
                    true,
                    conflictPolicy,
                    lastSuccessfulSyncAt,
                    entries
                );
                continue;
            }
            if (child instanceof Document childDocument) {
                entries.add(entryFor(
                    childDocument,
                    replicateDocumentToParent(
                        receiverRootId,
                        folderResult.node().getId(),
                        childDocument,
                        conflictPolicy,
                        lastSuccessfulSyncAt
                    )
                ));
            }
        }
        return folderResult;
    }

    private LoopbackReplicationResult alignFolder(
        UUID receiverRootId,
        UUID targetParentId,
        Folder source,
        ReplicationDefinition.ConflictPolicy conflictPolicy
    ) {
        Optional<MappedNode> mappedFolder = resolveMappedNode(receiverRootId, source.getId());
        if (mappedFolder.isPresent() && mappedFolder.get().node() instanceof Folder existingFolder) {
            if (matchesSourceVersion(mappedFolder.get().mapping(), source.getLastModifiedDate())) {
                refreshMapping(receiverRootId, source.getId(), source.getLastModifiedDate());
                return new LoopbackReplicationResult(
                    existingFolder,
                    "UNCHANGED",
                    "Loopback mapped folder already up to date"
                );
            }

            Folder effectiveFolder = existingFolder;
            if (!Objects.equals(parentIdOf(existingFolder), targetParentId)) {
                effectiveFolder = requireFolder(nodeService.moveNode(existingFolder.getId(), targetParentId));
            }

            Map<String, Object> updates = new LinkedHashMap<>();
            if (!Objects.equals(source.getName(), effectiveFolder.getName())) {
                updates.put("name", source.getName());
            }
            if (!Objects.equals(source.getDescription(), effectiveFolder.getDescription())) {
                updates.put("description", source.getDescription());
            }
            if (!updates.isEmpty()) {
                effectiveFolder = requireFolder(nodeService.updateNode(effectiveFolder.getId(), updates));
            }

            upsertMapping(receiverRootId, source.getId(), effectiveFolder.getId(), source.getLastModifiedDate());
            return new LoopbackReplicationResult(
                effectiveFolder,
                "OVERWRITTEN",
                "Loopback updated mapped folder"
            );
        }

        return replicateFolderConflict(receiverRootId, targetParentId, source, conflictPolicy);
    }

    private LoopbackReplicationResult replicateFolderConflict(
        UUID receiverRootId,
        UUID targetParentId,
        Folder source,
        ReplicationDefinition.ConflictPolicy conflictPolicy
    ) {
        Optional<Node> existing = nodeRepository.findByParentIdAndName(targetParentId, source.getName());
        if (existing.isEmpty()) {
            Node created = nodeService.copyNode(source.getId(), targetParentId, source.getName(), false);
            upsertMapping(receiverRootId, source.getId(), created.getId(), source.getLastModifiedDate());
            return new LoopbackReplicationResult(
                created,
                "CREATED",
                "Loopback replication created target node"
            );
        }

        return switch (conflictPolicy) {
            case SKIP -> {
                if (!(existing.get() instanceof Folder)) {
                    throw new IllegalStateException("Cannot skip folder replication because a non-folder already exists: " + source.getName());
                }
                upsertMapping(receiverRootId, source.getId(), existing.get().getId(), source.getLastModifiedDate());
                yield new LoopbackReplicationResult(existing.get(), "SKIPPED", "Loopback replication skipped existing node");
            }
            case RENAME -> {
                Node renamed = nodeService.copyNode(
                    source.getId(),
                    targetParentId,
                    generateReplicaName(targetParentId, source.getName()),
                    false
                );
                upsertMapping(receiverRootId, source.getId(), renamed.getId(), source.getLastModifiedDate());
                yield new LoopbackReplicationResult(renamed, "RENAMED", "Loopback replication created renamed target node");
            }
            case OVERWRITE -> {
                Node overwritten = overwriteExisting(targetParentId, source, false, existing.get());
                upsertMapping(receiverRootId, source.getId(), overwritten.getId(), source.getLastModifiedDate());
                yield new LoopbackReplicationResult(overwritten, "OVERWRITTEN", "Loopback replication overwrote existing target node");
            }
        };
    }

    private LoopbackReplicationResult replicateDocumentToParent(
        UUID receiverRootId,
        UUID targetParentId,
        Document source,
        ReplicationDefinition.ConflictPolicy conflictPolicy,
        LocalDateTime lastSuccessfulSyncAt
    ) {
        Optional<MappedNode> mappedDocument = resolveMappedNode(receiverRootId, source.getId());
        if (mappedDocument.isPresent() && mappedDocument.get().node() instanceof Document existingDocument) {
            if (matchesSourceVersion(mappedDocument.get().mapping(), source.getLastModifiedDate())) {
                refreshMapping(receiverRootId, source.getId(), source.getLastModifiedDate());
                return new LoopbackReplicationResult(
                    existingDocument,
                    "UNCHANGED",
                    "Loopback mapped document already up to date"
                );
            }

            Document effectiveDocument = existingDocument;
            if (!Objects.equals(parentIdOf(existingDocument), targetParentId)) {
                effectiveDocument = requireDocument(nodeService.moveNode(existingDocument.getId(), targetParentId));
            }
            overwriteDocument(source, effectiveDocument);

            Map<String, Object> updates = new LinkedHashMap<>();
            if (!Objects.equals(source.getName(), effectiveDocument.getName())) {
                updates.put("name", source.getName());
            }
            if (!Objects.equals(source.getDescription(), effectiveDocument.getDescription())) {
                updates.put("description", source.getDescription());
            }
            if (!updates.isEmpty()) {
                effectiveDocument = requireDocument(nodeService.updateNode(effectiveDocument.getId(), updates));
            }

            upsertMapping(receiverRootId, source.getId(), effectiveDocument.getId(), source.getLastModifiedDate());
            return new LoopbackReplicationResult(
                effectiveDocument,
                "OVERWRITTEN",
                "Loopback updated mapped document"
            );
        }

        if (isUnchangedSinceWatermark(source, lastSuccessfulSyncAt)) {
            return new LoopbackReplicationResult(
                null,
                "SKIPPED_UNCHANGED",
                "Document unchanged since last successful sync"
            );
        }

        Optional<Node> existing = nodeRepository.findByParentIdAndName(targetParentId, source.getName());
        if (existing.isEmpty()) {
            Node created = nodeService.copyNode(source.getId(), targetParentId, source.getName(), false);
            upsertMapping(receiverRootId, source.getId(), created.getId(), source.getLastModifiedDate());
            return new LoopbackReplicationResult(
                created,
                "CREATED",
                "Loopback replication created target node"
            );
        }

        return switch (conflictPolicy) {
            case SKIP -> {
                if (!(existing.get() instanceof Document)) {
                    throw new IllegalStateException("Cannot skip document replication because a non-document already exists: " + source.getName());
                }
                upsertMapping(receiverRootId, source.getId(), existing.get().getId(), source.getLastModifiedDate());
                yield new LoopbackReplicationResult(existing.get(), "SKIPPED", "Loopback replication skipped existing node");
            }
            case RENAME -> {
                Node renamed = nodeService.copyNode(
                    source.getId(),
                    targetParentId,
                    generateReplicaName(targetParentId, source.getName()),
                    false
                );
                upsertMapping(receiverRootId, source.getId(), renamed.getId(), source.getLastModifiedDate());
                yield new LoopbackReplicationResult(renamed, "RENAMED", "Loopback replication created renamed target node");
            }
            case OVERWRITE -> {
                Node overwritten;
                if (existing.get() instanceof Document existingDocument) {
                    overwriteDocument(source, existingDocument);
                    syncDescription(existingDocument, source.getDescription());
                    overwritten = existingDocument;
                } else {
                    overwritten = overwriteExisting(targetParentId, source, false, existing.get());
                }
                upsertMapping(receiverRootId, source.getId(), overwritten.getId(), source.getLastModifiedDate());
                yield new LoopbackReplicationResult(overwritten, "OVERWRITTEN", "Loopback replication overwrote existing target node");
            }
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
    }

    private void syncDescription(Document target, String description) {
        if (Objects.equals(description, target.getDescription())) {
            return;
        }
        Map<String, Object> updates = new LinkedHashMap<>();
        updates.put("description", description);
        nodeService.updateNode(target.getId(), updates);
    }

    private Optional<MappedNode> resolveMappedNode(UUID receiverRootId, UUID sourceNodeId) {
        return transferNodeMappingService.findMapping(receiverRootId, repositoryIdentityProvider.getTransferRepositoryId(), sourceNodeId)
            .flatMap(mapping -> nodeRepository.findByIdAndDeletedFalseAndArchiveStatus(mapping.getLocalNodeId(), Node.ArchiveStatus.LIVE)
                .map(node -> new MappedNode(mapping, node)));
    }

    private boolean matchesSourceVersion(TransferNodeMapping mapping, LocalDateTime sourceLastModifiedAt) {
        return sourceLastModifiedAt != null && Objects.equals(mapping.getLastSourceModifiedAt(), sourceLastModifiedAt);
    }

    private void refreshMapping(UUID receiverRootId, UUID sourceNodeId, LocalDateTime sourceLastModifiedAt) {
        transferNodeMappingService.refreshSyncTimestamps(
            receiverRootId,
            repositoryIdentityProvider.getTransferRepositoryId(),
            sourceNodeId,
            sourceLastModifiedAt,
            LocalDateTime.now()
        );
    }

    private void upsertMapping(UUID receiverRootId, UUID sourceNodeId, UUID localNodeId, LocalDateTime sourceLastModifiedAt) {
        transferNodeMappingService.upsertMapping(
            receiverRootId,
            repositoryIdentityProvider.getTransferRepositoryId(),
            sourceNodeId,
            localNodeId,
            sourceLastModifiedAt,
            LocalDateTime.now()
        );
    }

    private List<Node> loadChildrenSorted(UUID parentId) {
        return nodeRepository.findByParentIdAndDeletedFalseAndArchiveStatus(parentId, Node.ArchiveStatus.LIVE).stream()
            .sorted((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.getName(), right.getName()))
            .toList();
    }

    private UUID parentIdOf(Node node) {
        return node.getParent() != null ? node.getParent().getId() : null;
    }

    private Folder requireFolder(Node node) {
        if (!(node instanceof Folder folder)) {
            throw new IllegalStateException("Expected folder node during loopback replication");
        }
        return folder;
    }

    private Document requireDocument(Node node) {
        if (!(node instanceof Document document)) {
            throw new IllegalStateException("Expected document node during loopback replication");
        }
        return document;
    }

    private TransferExecutionEntry entryFor(Node source, LoopbackReplicationResult replicated) {
        LocalDateTime now = LocalDateTime.now();
        return new TransferExecutionEntry(
            source.getId(),
            source.getPath(),
            source.getNodeType() != null ? source.getNodeType().name() : source.getClass().getSimpleName(),
            replicated.node() != null ? replicated.node().getId() : null,
            replicated.action(),
            replicated.message(),
            now,
            now
        );
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

    private record LoopbackReplicationResult(Node node, String action, String message) {
    }

    private record MappedNode(TransferNodeMapping mapping, Node node) {
    }
}
