package com.ecm.core.service;

import com.ecm.core.entity.AssocDirection;
import com.ecm.core.entity.Document;
import com.ecm.core.entity.DocumentRelation;
import com.ecm.core.entity.Node;
import com.ecm.core.repository.DocumentRelationRepository;
import com.ecm.core.repository.NodeRepository;
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

    @Transactional
    public DocumentRelation createRelation(UUID sourceId, UUID targetId, String type) {
        Document source = (Document) nodeRepository.findById(sourceId)
            .orElseThrow(() -> new IllegalArgumentException("Source document not found"));
        Document target = (Document) nodeRepository.findById(targetId)
            .orElseThrow(() -> new IllegalArgumentException("Target document not found"));

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
        relationRepository.deleteBySourceIdAndTargetIdAndRelationType(sourceId, targetId, type.toUpperCase());
    }

    @Transactional(readOnly = true)
    public List<DocumentRelation> getRelations(UUID documentId) {
        // Return both outgoing and incoming relations?
        // For simplicity, let's return outgoing (source -> target)
        return relationRepository.findBySourceId(documentId);
    }
    
    @Transactional(readOnly = true)
    public List<DocumentRelation> getIncomingRelations(UUID documentId) {
        return relationRepository.findByTargetId(documentId);
    }

    @Transactional(readOnly = true)
    public Page<DocumentRelation> getOutgoingRelationsPage(UUID documentId, Pageable pageable, String relationType) {
        String normalizedType = normalizeRelationType(relationType);
        if (normalizedType == null) {
            return relationRepository.findBySourceId(documentId, pageable);
        }
        return relationRepository.findBySourceIdAndRelationTypeIgnoreCase(documentId, normalizedType, pageable);
    }

    @Transactional(readOnly = true)
    public Page<DocumentRelation> getIncomingRelationsPage(UUID documentId, Pageable pageable, String relationType) {
        String normalizedType = normalizeRelationType(relationType);
        if (normalizedType == null) {
            return relationRepository.findByTargetId(documentId, pageable);
        }
        return relationRepository.findByTargetIdAndRelationTypeIgnoreCase(documentId, normalizedType, pageable);
    }

    // ---- peer associations --------------------------------------------------

    @Transactional
    public DocumentRelation createPeerAssociation(UUID sourceId, UUID targetId, String assocType) {
        return createAssociation(sourceId, targetId, assocType, AssocDirection.PEER);
    }

    @Transactional(readOnly = true)
    public List<DocumentRelation> getTargetAssociations(UUID nodeId, String assocType) {
        List<DocumentRelation> all = relationRepository.findBySourceIdAndDirection(nodeId, AssocDirection.PEER);
        if (assocType == null || assocType.isBlank()) return all;
        return all.stream().filter(r -> assocType.equals(r.getAssocType())).toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentRelation> getSourceAssociations(UUID nodeId, String assocType) {
        List<DocumentRelation> all = relationRepository.findByTargetIdAndDirection(nodeId, AssocDirection.PEER);
        if (assocType == null || assocType.isBlank()) return all;
        return all.stream().filter(r -> assocType.equals(r.getAssocType())).toList();
    }

    @Transactional
    public void removePeerAssociation(UUID sourceId, UUID targetId) {
        relationRepository.deleteBySourceIdAndTargetId(sourceId, targetId);
    }

    // ---- secondary children -------------------------------------------------

    @Transactional
    public DocumentRelation addSecondaryChild(UUID parentId, UUID childId) {
        return createAssociation(parentId, childId, "cm:contains", AssocDirection.CHILD_SECONDARY);
    }

    @Transactional
    public void removeSecondaryChild(UUID parentId, UUID childId) {
        relationRepository.deleteBySourceIdAndTargetId(parentId, childId);
    }

    @Transactional(readOnly = true)
    public List<DocumentRelation> getSecondaryChildren(UUID parentId) {
        return relationRepository.findBySourceIdAndDirection(parentId, AssocDirection.CHILD_SECONDARY);
    }

    @Transactional(readOnly = true)
    public List<DocumentRelation> getSecondaryParents(UUID childId) {
        return relationRepository.findByTargetIdAndDirection(childId, AssocDirection.CHILD_SECONDARY);
    }

    // ---- internal -----------------------------------------------------------

    private DocumentRelation createAssociation(UUID sourceId, UUID targetId, String assocType, AssocDirection direction) {
        Node source = nodeRepository.findById(sourceId)
            .orElseThrow(() -> new IllegalArgumentException("Source node not found: " + sourceId));
        Node target = nodeRepository.findById(targetId)
            .orElseThrow(() -> new IllegalArgumentException("Target node not found: " + targetId));

        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Cannot associate a node with itself");
        }
        if (!(source instanceof Document)) {
            throw new IllegalArgumentException("Source must be a document: " + sourceId);
        }
        if (!(target instanceof Document)) {
            throw new IllegalArgumentException("Target must be a document: " + targetId);
        }

        DocumentRelation rel = new DocumentRelation();
        rel.setSource((Document) source);
        rel.setTarget((Document) target);
        rel.setRelationType(assocType != null ? assocType.toUpperCase() : "RELATED");
        rel.setAssocType(assocType);
        rel.setDirection(direction);
        return relationRepository.save(rel);
    }

    private String normalizeRelationType(String relationType) {
        if (relationType == null) {
            return null;
        }
        String trimmed = relationType.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
