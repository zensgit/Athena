package com.ecm.core.service;

import com.ecm.core.entity.AssocDirection;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.DocumentRelation;
import com.ecm.core.entity.Node;
import com.ecm.core.entity.Permission.PermissionType;
import com.ecm.core.repository.DocumentRelationRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentRelationService {

    private final DocumentRelationRepository relationRepository;
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

        DocumentRelation relation = new DocumentRelation();
        relation.setSource(source);
        relation.setTarget(target);
        relation.setRelationType(type.toUpperCase());
        
        return relationRepository.save(relation);
    }

    @Transactional
    public void deleteRelation(UUID sourceId, UUID targetId, String type) {
        requireWritableDocument(sourceId, "Source document not found");
        requireReadableDocument(targetId, "Target document not found");
        relationRepository.deleteBySourceIdAndTargetIdAndRelationType(sourceId, targetId, type.toUpperCase());
    }

    @Transactional(readOnly = true)
    public List<DocumentRelation> getRelations(UUID documentId) {
        requireReadableDocument(documentId, "Document not found");
        return filterVisibleRelations(relationRepository.findBySourceId(documentId));
    }
    
    @Transactional(readOnly = true)
    public List<DocumentRelation> getIncomingRelations(UUID documentId) {
        requireReadableDocument(documentId, "Document not found");
        return filterVisibleRelations(relationRepository.findByTargetId(documentId));
    }

    @Transactional(readOnly = true)
    public Page<DocumentRelation> getOutgoingRelationsPage(UUID documentId, Pageable pageable, String relationType) {
        requireReadableDocument(documentId, "Document not found");
        String normalizedType = normalizeRelationType(relationType);
        Page<DocumentRelation> page = normalizedType == null
            ? relationRepository.findBySourceId(documentId, pageable)
            : relationRepository.findBySourceIdAndRelationTypeIgnoreCase(documentId, normalizedType, pageable);
        return filterVisibleRelations(page, pageable);
    }

    @Transactional(readOnly = true)
    public Page<DocumentRelation> getIncomingRelationsPage(UUID documentId, Pageable pageable, String relationType) {
        requireReadableDocument(documentId, "Document not found");
        String normalizedType = normalizeRelationType(relationType);
        Page<DocumentRelation> page = normalizedType == null
            ? relationRepository.findByTargetId(documentId, pageable)
            : relationRepository.findByTargetIdAndRelationTypeIgnoreCase(documentId, normalizedType, pageable);
        return filterVisibleRelations(page, pageable);
    }

    // ---- peer associations --------------------------------------------------

    @Transactional
    public DocumentRelation createPeerAssociation(UUID sourceId, UUID targetId, String assocType) {
        return createAssociation(sourceId, targetId, assocType, AssocDirection.PEER);
    }

    @Transactional(readOnly = true)
    public List<DocumentRelation> getTargetAssociations(UUID nodeId, String assocType) {
        requireReadableDocument(nodeId, "Source node not found");
        List<DocumentRelation> all = filterVisibleRelations(
            relationRepository.findBySourceIdAndDirection(nodeId, AssocDirection.PEER)
        );
        if (assocType == null || assocType.isBlank()) return all;
        return all.stream().filter(r -> assocType.equals(r.getAssocType())).toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentRelation> getSourceAssociations(UUID nodeId, String assocType) {
        requireReadableDocument(nodeId, "Target node not found");
        List<DocumentRelation> all = filterVisibleRelations(
            relationRepository.findByTargetIdAndDirection(nodeId, AssocDirection.PEER)
        );
        if (assocType == null || assocType.isBlank()) return all;
        return all.stream().filter(r -> assocType.equals(r.getAssocType())).toList();
    }

    @Transactional
    public void removePeerAssociation(UUID sourceId, UUID targetId) {
        requireWritableDocument(sourceId, "Source node not found");
        requireReadableDocument(targetId, "Target node not found");
        relationRepository.deleteBySourceIdAndTargetId(sourceId, targetId);
    }

    // ---- secondary children -------------------------------------------------

    @Transactional
    public DocumentRelation addSecondaryChild(UUID parentId, UUID childId) {
        return createAssociation(parentId, childId, "cm:contains", AssocDirection.CHILD_SECONDARY);
    }

    @Transactional
    public void removeSecondaryChild(UUID parentId, UUID childId) {
        requireWritableDocument(parentId, "Source node not found");
        requireReadableDocument(childId, "Target node not found");
        relationRepository.deleteBySourceIdAndTargetId(parentId, childId);
    }

    @Transactional(readOnly = true)
    public List<DocumentRelation> getSecondaryChildren(UUID parentId) {
        requireReadableDocument(parentId, "Source node not found");
        return filterVisibleRelations(relationRepository.findBySourceIdAndDirection(parentId, AssocDirection.CHILD_SECONDARY));
    }

    @Transactional(readOnly = true)
    public List<DocumentRelation> getSecondaryParents(UUID childId) {
        requireReadableDocument(childId, "Target node not found");
        return filterVisibleRelations(relationRepository.findByTargetIdAndDirection(childId, AssocDirection.CHILD_SECONDARY));
    }

    // ---- internal -----------------------------------------------------------

    private DocumentRelation createAssociation(UUID sourceId, UUID targetId, String assocType, AssocDirection direction) {
        Document source = requireWritableDocument(sourceId, "Source node not found: " + sourceId);
        Document target = requireReadableDocument(targetId, "Target node not found: " + targetId);

        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot associate a node with itself");
        }

        DocumentRelation rel = new DocumentRelation();
        rel.setSource(source);
        rel.setTarget(target);
        rel.setRelationType(assocType != null ? assocType.toUpperCase() : "RELATED");
        rel.setAssocType(assocType);
        rel.setDirection(direction);
        return relationRepository.save(rel);
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

    private List<DocumentRelation> filterVisibleRelations(List<DocumentRelation> relations) {
        return relations.stream()
            .filter(this::isRelationVisible)
            .toList();
    }

    private Page<DocumentRelation> filterVisibleRelations(Page<DocumentRelation> page, Pageable pageable) {
        List<DocumentRelation> visible = page.getContent().stream()
            .filter(this::isRelationVisible)
            .toList();
        return new PageImpl<>(visible, pageable, visible.size());
    }

    private boolean isRelationVisible(DocumentRelation relation) {
        return relation != null
            && isReadable(relation.getSource())
            && isReadable(relation.getTarget());
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

    private String normalizeRelationType(String relationType) {
        if (relationType == null) {
            return null;
        }
        String trimmed = relationType.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
