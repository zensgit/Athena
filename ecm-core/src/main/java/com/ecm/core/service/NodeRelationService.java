package com.ecm.core.service;

import com.ecm.core.entity.AssocDirection;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.NodeRelation;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.NodeRelationRepository;
import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NodeRelationService {

    private final NodeRelationRepository relationRepository;
    private final NodeRepository nodeRepository;
    private final SecurityService securityService;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;

    @Transactional
    public NodeRelation createRelation(UUID sourceId, UUID targetId, String relationType) {
        Node source = requireWritableNode(sourceId, "Source node not found");
        Node target = requireReadableNode(targetId, "Target node not found");

        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot relate a node to itself");
        }

        NodeRelation relation = new NodeRelation();
        relation.setSource(source);
        relation.setTarget(target);
        relation.setRelationType(relationType.toUpperCase());
        relation.setDirection(AssocDirection.PEER);
        return relationRepository.save(relation);
    }

    @Transactional
    public void deleteRelation(UUID sourceId, UUID targetId, String relationType) {
        requireWritableNode(sourceId, "Source node not found");
        requireReadableNode(targetId, "Target node not found");
        relationRepository.deleteBySourceIdAndTargetIdAndRelationType(sourceId, targetId, relationType.toUpperCase());
    }

    @Transactional(readOnly = true)
    public List<NodeRelation> getRelations(UUID nodeId) {
        requireReadableNode(nodeId, "Node not found");
        return filterVisible(relationRepository.findBySourceId(nodeId));
    }

    @Transactional(readOnly = true)
    public List<NodeRelation> getIncomingRelations(UUID nodeId) {
        requireReadableNode(nodeId, "Node not found");
        return filterVisible(relationRepository.findByTargetId(nodeId));
    }

    @Transactional(readOnly = true)
    public Page<NodeRelation> getRelationsPage(UUID nodeId, Pageable pageable, String relationType) {
        requireReadableNode(nodeId, "Node not found");
        String normalized = normalizeRelationType(relationType);
        Page<NodeRelation> page = normalized == null
            ? relationRepository.findBySourceId(nodeId, pageable)
            : relationRepository.findBySourceIdAndRelationTypeIgnoreCase(nodeId, normalized, pageable);
        return filterVisible(page, pageable);
    }

    @Transactional(readOnly = true)
    public Page<NodeRelation> getIncomingRelationsPage(UUID nodeId, Pageable pageable, String relationType) {
        requireReadableNode(nodeId, "Node not found");
        String normalized = normalizeRelationType(relationType);
        Page<NodeRelation> page = normalized == null
            ? relationRepository.findByTargetId(nodeId, pageable)
            : relationRepository.findByTargetIdAndRelationTypeIgnoreCase(nodeId, normalized, pageable);
        return filterVisible(page, pageable);
    }

    @Transactional
    public NodeRelation createPeerAssociation(UUID sourceId, UUID targetId, String assocType) {
        Node source = requireWritableNode(sourceId, "Source node not found");
        Node target = requireReadableNode(targetId, "Target node not found");

        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot associate a node with itself");
        }

        NodeRelation rel = new NodeRelation();
        rel.setSource(source);
        rel.setTarget(target);
        rel.setRelationType(assocType != null ? assocType.toUpperCase() : "RELATED");
        rel.setAssocType(assocType);
        rel.setDirection(AssocDirection.PEER);
        return relationRepository.save(rel);
    }

    @Transactional(readOnly = true)
    public List<NodeRelation> getTargetAssociations(UUID nodeId, String assocType) {
        requireReadableNode(nodeId, "Node not found");
        List<NodeRelation> all = filterVisible(
            relationRepository.findBySourceIdAndDirection(nodeId, AssocDirection.PEER));
        if (assocType == null || assocType.isBlank()) return all;
        return all.stream().filter(r -> assocType.equals(r.getAssocType())).toList();
    }

    @Transactional(readOnly = true)
    public List<NodeRelation> getSourceAssociations(UUID nodeId, String assocType) {
        requireReadableNode(nodeId, "Node not found");
        List<NodeRelation> all = filterVisible(
            relationRepository.findByTargetIdAndDirection(nodeId, AssocDirection.PEER));
        if (assocType == null || assocType.isBlank()) return all;
        return all.stream().filter(r -> assocType.equals(r.getAssocType())).toList();
    }

    @Transactional
    public void removeAssociation(UUID sourceId, UUID targetId) {
        requireWritableNode(sourceId, "Source node not found");
        requireReadableNode(targetId, "Target node not found");
        relationRepository.deleteBySourceIdAndTargetId(sourceId, targetId);
    }

    // --- internal ---

    private Node requireWritableNode(UUID nodeId, String msg) {
        Node node = loadVisibleNode(nodeId, msg);
        if (!securityService.hasPermission(node, PermissionType.WRITE)) {
            throw new SecurityException("No permission to modify node: " + node.getName());
        }
        return node;
    }

    private Node requireReadableNode(UUID nodeId, String msg) {
        Node node = loadVisibleNode(nodeId, msg);
        if (!securityService.hasPermission(node, PermissionType.READ)) {
            throw new SecurityException("No permission to read node: " + node.getName());
        }
        return node;
    }

    private Node loadVisibleNode(UUID nodeId, String msg) {
        return nodeRepository.findById(nodeId)
            .filter(this::isNodeVisible)
            .orElseThrow(() -> new IllegalArgumentException(msg));
    }

    private boolean isNodeVisible(Node node) {
        if (node == null || node.isDeleted()) return false;
        if (!tenantWorkspaceScopeService.hasScopedTenantWorkspace()) return true;
        return tenantWorkspaceScopeService.isPathVisible(node.getPath());
    }

    private List<NodeRelation> filterVisible(List<NodeRelation> relations) {
        return relations.stream().filter(this::isRelationVisible).toList();
    }

    private Page<NodeRelation> filterVisible(Page<NodeRelation> page, Pageable pageable) {
        List<NodeRelation> visible = page.getContent().stream().filter(this::isRelationVisible).toList();
        return new PageImpl<>(visible, pageable, visible.size());
    }

    private boolean isRelationVisible(NodeRelation relation) {
        return relation != null
            && isReadable(relation.getSource())
            && isReadable(relation.getTarget());
    }

    private boolean isReadable(Node node) {
        return node != null
            && isNodeVisible(node)
            && securityService.hasPermission(node, PermissionType.READ);
    }

    private String normalizeRelationType(String relationType) {
        if (relationType == null) return null;
        String trimmed = relationType.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
