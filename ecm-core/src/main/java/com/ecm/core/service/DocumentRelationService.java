package com.ecm.core.service;

import com.ecm.core.entity.AssocDirection;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.DocumentRelation;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.NodeRelation;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.NodeRepository;
import org.springframework.data.domain.PageImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * @deprecated Use {@link NodeRelationService} for new code. This service is retained for
 * backward compatibility with existing REST API consumers and will be removed in a future release.
 */
@Deprecated
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentRelationService {

    private final NodeRelationService nodeRelationService;
    private final NodeRepository nodeRepository;
    private final SecurityService securityService;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;

    @Transactional
    public DocumentRelation createRelation(UUID sourceId, UUID targetId, String type) {
        Document source = requireWritableDocument(sourceId, "Source document not found");
        Document target = requireReadableDocument(targetId, "Target document not found");

        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot relate document to itself");
        }

        return toDocumentRelation(nodeRelationService.createRelation(sourceId, targetId, type));
    }

    @Transactional
    public void deleteRelation(UUID sourceId, UUID targetId, String type) {
        requireWritableDocument(sourceId, "Source document not found");
        requireReadableDocument(targetId, "Target document not found");
        nodeRelationService.deleteRelation(sourceId, targetId, type);
    }

    @Transactional(readOnly = true)
    public List<DocumentRelation> getRelations(UUID documentId) {
        requireReadableDocument(documentId, "Document not found");
        return toDocumentRelations(nodeRelationService.getRelations(documentId));
    }
    
    @Transactional(readOnly = true)
    public List<DocumentRelation> getIncomingRelations(UUID documentId) {
        requireReadableDocument(documentId, "Document not found");
        return toDocumentRelations(nodeRelationService.getIncomingRelations(documentId));
    }

    @Transactional(readOnly = true)
    public Page<DocumentRelation> getOutgoingRelationsPage(UUID documentId, Pageable pageable, String relationType) {
        requireReadableDocument(documentId, "Document not found");
        return toDocumentRelations(nodeRelationService.getRelationsPage(documentId, pageable, relationType), pageable);
    }

    @Transactional(readOnly = true)
    public Page<DocumentRelation> getIncomingRelationsPage(UUID documentId, Pageable pageable, String relationType) {
        requireReadableDocument(documentId, "Document not found");
        return toDocumentRelations(nodeRelationService.getIncomingRelationsPage(documentId, pageable, relationType), pageable);
    }

    // ---- peer associations --------------------------------------------------

    @Transactional
    public DocumentRelation createPeerAssociation(UUID sourceId, UUID targetId, String assocType) {
        return toDocumentRelation(createAssociation(sourceId, targetId, assocType, AssocDirection.PEER));
    }

    @Transactional(readOnly = true)
    public List<DocumentRelation> getTargetAssociations(UUID nodeId, String assocType) {
        requireReadableDocument(nodeId, "Source node not found");
        List<DocumentRelation> all = toDocumentRelations(nodeRelationService.getTargetAssociations(nodeId, assocType));
        if (assocType == null || assocType.isBlank()) return all;
        return all.stream().filter(r -> assocType.equals(r.getAssocType())).toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentRelation> getSourceAssociations(UUID nodeId, String assocType) {
        requireReadableDocument(nodeId, "Target node not found");
        List<DocumentRelation> all = toDocumentRelations(nodeRelationService.getSourceAssociations(nodeId, assocType));
        if (assocType == null || assocType.isBlank()) return all;
        return all.stream().filter(r -> assocType.equals(r.getAssocType())).toList();
    }

    @Transactional
    public void removePeerAssociation(UUID sourceId, UUID targetId) {
        requireWritableDocument(sourceId, "Source node not found");
        requireReadableDocument(targetId, "Target node not found");
        nodeRelationService.removeAssociation(sourceId, targetId);
    }

    // ---- secondary children -------------------------------------------------

    @Transactional
    public DocumentRelation addSecondaryChild(UUID parentId, UUID childId) {
        return toDocumentRelation(createAssociation(parentId, childId, "cm:contains", AssocDirection.CHILD_SECONDARY));
    }

    @Transactional
    public void removeSecondaryChild(UUID parentId, UUID childId) {
        requireWritableDocument(parentId, "Source node not found");
        requireReadableDocument(childId, "Target node not found");
        nodeRelationService.removeAssociation(parentId, childId);
    }

    @Transactional(readOnly = true)
    public List<DocumentRelation> getSecondaryChildren(UUID parentId) {
        requireReadableDocument(parentId, "Source node not found");
        return toDocumentRelations(nodeRelationService.getSourceRelationsByDirection(parentId, AssocDirection.CHILD_SECONDARY));
    }

    @Transactional(readOnly = true)
    public List<DocumentRelation> getSecondaryParents(UUID childId) {
        requireReadableDocument(childId, "Target node not found");
        return toDocumentRelations(nodeRelationService.getTargetRelationsByDirection(childId, AssocDirection.CHILD_SECONDARY));
    }

    // ---- internal -----------------------------------------------------------

    private NodeRelation createAssociation(UUID sourceId, UUID targetId, String assocType, AssocDirection direction) {
        Document source = requireWritableDocument(sourceId, "Source node not found: " + sourceId);
        Document target = requireReadableDocument(targetId, "Target node not found: " + targetId);

        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot associate a node with itself");
        }

        NodeRelation rel = new NodeRelation();
        rel.setSource(source);
        rel.setTarget(target);
        rel.setRelationType(assocType != null ? assocType.toUpperCase() : "RELATED");
        rel.setAssocType(assocType);
        rel.setDirection(direction);
        return nodeRelationService.saveRelation(rel);
    }

    private Document requireWritableDocument(UUID nodeId, String notFoundMessage) {
        Document document = loadVisibleDocument(nodeId, notFoundMessage);
        if (!securityService.hasPermission(document, PermissionType.WRITE)) {
            throw new SecurityException("No permission to modify document: " + document.getName());
        }
        return document;
    }

    private Document requireReadableDocument(UUID nodeId, String notFoundMessage) {
        Document document = loadVisibleDocument(nodeId, notFoundMessage);
        if (!securityService.hasPermission(document, PermissionType.READ)) {
            throw new SecurityException("No permission to read document: " + document.getName());
        }
        return document;
    }

    private Document loadVisibleDocument(UUID nodeId, String notFoundMessage) {
        Node node = nodeRepository.findById(nodeId)
            .filter(this::isNodeVisible)
            .orElseThrow(() -> new IllegalArgumentException(notFoundMessage));
        if (!(node instanceof Document document)) {
            throw new IllegalArgumentException(notFoundMessage);
        }
        return document;
    }

    private List<DocumentRelation> toDocumentRelations(List<NodeRelation> relations) {
        return relations.stream()
            .map(this::toDocumentRelation)
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    private Page<DocumentRelation> toDocumentRelations(Page<NodeRelation> page, Pageable pageable) {
        List<DocumentRelation> visible = toDocumentRelations(page.getContent());
        return new PageImpl<>(visible, pageable, visible.size());
    }

    private DocumentRelation toDocumentRelation(NodeRelation relation) {
        if (relation == null
            || !(relation.getSource() instanceof Document source)
            || !(relation.getTarget() instanceof Document target)
            || !isReadable(source)
            || !isReadable(target)) {
            return null;
        }

        DocumentRelation documentRelation = new DocumentRelation();
        documentRelation.setId(relation.getId());
        documentRelation.setSource(source);
        documentRelation.setTarget(target);
        documentRelation.setRelationType(relation.getRelationType());
        documentRelation.setAssocType(relation.getAssocType());
        documentRelation.setDirection(relation.getDirection());
        documentRelation.setOrderIndex(relation.getOrderIndex());
        documentRelation.setCreatedBy(relation.getCreatedBy());
        documentRelation.setCreatedDate(relation.getCreatedDate());
        documentRelation.setLastModifiedBy(relation.getLastModifiedBy());
        documentRelation.setLastModifiedDate(relation.getLastModifiedDate());
        return documentRelation;
    }

    private boolean isReadable(Document document) {
        return document != null
            && isNodeVisible(document)
            && securityService.hasPermission(document, PermissionType.READ);
    }

    private boolean isNodeVisible(Node node) {
        if (node == null || node.isDeleted()) {
            return false;
        }
        if (!tenantWorkspaceScopeService.hasScopedTenantWorkspace()) {
            return true;
        }
        return tenantWorkspaceScopeService.isPathVisible(node.getPath());
    }

}
