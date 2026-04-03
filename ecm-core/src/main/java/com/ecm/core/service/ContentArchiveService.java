package com.ecm.core.service;

import com.ecm.core.entity.Node;
import com.ecm.core.entity.Node.ArchiveStatus;
import com.ecm.core.entity.Node.ArchiveStoreTier;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentArchiveService {

    private final NodeRepository nodeRepository;
    private final SecurityService securityService;
    private final ActivityEventListener activityEventListener;
    private final com.ecm.core.search.SearchIndexService searchIndexService;

    @Transactional
    public ArchiveMutationDto archiveNode(UUID nodeId, ArchiveStoreTier storageTier) {
        Node node = loadActiveNode(nodeId);
        securityService.checkPermission(node, PermissionType.DELETE);
        return archiveNodeInternal(node, storageTier, securityService.getCurrentUser(), "archived");
    }

    @Transactional
    public ArchiveMutationDto archiveNodeByPolicy(UUID nodeId, ArchiveStoreTier storageTier, String actor) {
        Node node = loadActiveNode(nodeId);
        return archiveNodeInternal(node, storageTier, actor, "policy_archived");
    }

    @Transactional
    public ArchiveMutationDto restoreNode(UUID nodeId) {
        Node node = loadActiveNode(nodeId);

        if (node.getArchiveStatus() != ArchiveStatus.ARCHIVED) {
            throw new IllegalStateException("Node is not archived: " + nodeId);
        }

        String currentUser = securityService.getCurrentUser();
        boolean isArchiver = currentUser.equals(node.getArchivedBy());
        boolean isOwner = currentUser.equals(node.getCreatedBy());
        boolean isAdmin = securityService.isAdmin(currentUser);
        if (!isArchiver && !isOwner && !isAdmin) {
            throw new SecurityException("No permission to restore archived node");
        }

        List<Node> scope = collectArchiveScope(node);
        ArchiveStoreTier previousTier = node.getArchiveStoreTier();
        for (Node candidate : scope) {
            candidate.setArchiveStatus(ArchiveStatus.LIVE);
            candidate.setArchivedDate(null);
            candidate.setArchivedBy(null);
            candidate.setArchiveStoreTier(ArchiveStoreTier.HOT);
        }

        nodeRepository.saveAll(scope);
        syncSearchIndex(scope);
        activityEventListener.postNodeActivity(
            "node.restored",
            currentUser,
            node,
            Map.of(
                "action", "restored",
                "previousArchiveStoreTier", previousTier.name()
            )
        );
        return toMutationDto(node, scope.size());
    }

    public ArchiveStatusDto getArchiveStatus(UUID nodeId) {
        Node node = loadActiveNode(nodeId);
        securityService.checkPermission(node, PermissionType.READ);
        return toStatusDto(node);
    }

    public Page<ArchivedNodeDto> listArchivedNodes(Pageable pageable) {
        if (!securityService.hasRole("ROLE_ADMIN")) {
            throw new SecurityException("Admin access required to list archived nodes");
        }
        return nodeRepository
            .findByArchiveStatusAndDeletedFalseOrderByArchivedDateDesc(ArchiveStatus.ARCHIVED, pageable)
            .map(this::toArchivedNodeDto);
    }

    private Node loadActiveNode(UUID nodeId) {
        return nodeRepository.findByIdAndDeletedFalse(nodeId)
            .orElseThrow(() -> new NoSuchElementException("Node not found: " + nodeId));
    }

    private ArchiveMutationDto archiveNodeInternal(
        Node node,
        ArchiveStoreTier storageTier,
        String actor,
        String action
    ) {
        if (node.getArchiveStatus() == ArchiveStatus.ARCHIVED) {
            throw new IllegalStateException("Node is already archived: " + node.getId());
        }

        ArchiveStoreTier effectiveTier = storageTier != null ? storageTier : ArchiveStoreTier.COLD;
        LocalDateTime now = LocalDateTime.now();
        List<Node> scope = collectArchiveScope(node);

        for (Node candidate : scope) {
            candidate.setArchiveStatus(ArchiveStatus.ARCHIVED);
            candidate.setArchivedDate(now);
            candidate.setArchivedBy(actor);
            candidate.setArchiveStoreTier(effectiveTier);
        }

        nodeRepository.saveAll(scope);
        syncSearchIndex(scope);
        activityEventListener.postNodeActivity(
            "node.archived",
            actor,
            node,
            Map.of(
                "action", action,
                "archiveStoreTier", effectiveTier.name()
            )
        );
        return toMutationDto(node, scope.size());
    }

    private List<Node> collectArchiveScope(Node root) {
        List<Node> scope = new ArrayList<>();
        scope.add(root);
        if (root.isFolder()) {
            scope.addAll(nodeRepository.findByPathPrefix(root.getPath() + "/"));
        }
        return scope;
    }

    private void syncSearchIndex(List<Node> scope) {
        for (Node candidate : scope) {
            if (candidate instanceof com.ecm.core.entity.Document document) {
                searchIndexService.updateDocument(document);
            } else {
                searchIndexService.updateNode(candidate);
            }
        }
    }

    private ArchiveStatusDto toStatusDto(Node node) {
        return new ArchiveStatusDto(
            node.getId(),
            node.getName(),
            node.getNodeType().name(),
            node.getPath(),
            node.getArchiveStatus(),
            node.getArchiveStoreTier(),
            node.getArchivedDate(),
            node.getArchivedBy()
        );
    }

    private ArchivedNodeDto toArchivedNodeDto(Node node) {
        return new ArchivedNodeDto(
            node.getId(),
            node.getName(),
            node.getNodeType().name(),
            node.getPath(),
            node.getSize(),
            node.getCreatedBy(),
            node.getCreatedDate(),
            node.getArchiveStatus(),
            node.getArchiveStoreTier(),
            node.getArchivedDate(),
            node.getArchivedBy()
        );
    }

    private ArchiveMutationDto toMutationDto(Node node, int affectedNodeCount) {
        return new ArchiveMutationDto(
            node.getId(),
            node.getName(),
            node.getArchiveStatus(),
            node.getArchiveStoreTier(),
            node.getArchivedDate(),
            node.getArchivedBy(),
            affectedNodeCount
        );
    }

    public record ArchiveMutationDto(
        UUID nodeId,
        String name,
        ArchiveStatus archiveStatus,
        ArchiveStoreTier archiveStoreTier,
        LocalDateTime archivedDate,
        String archivedBy,
        int affectedNodeCount
    ) {}

    public record ArchiveStatusDto(
        UUID nodeId,
        String name,
        String nodeType,
        String path,
        ArchiveStatus archiveStatus,
        ArchiveStoreTier archiveStoreTier,
        LocalDateTime archivedDate,
        String archivedBy
    ) {}

    public record ArchivedNodeDto(
        UUID nodeId,
        String name,
        String nodeType,
        String path,
        Long size,
        String createdBy,
        LocalDateTime createdDate,
        ArchiveStatus archiveStatus,
        ArchiveStoreTier archiveStoreTier,
        LocalDateTime archivedDate,
        String archivedBy
    ) {}
}
