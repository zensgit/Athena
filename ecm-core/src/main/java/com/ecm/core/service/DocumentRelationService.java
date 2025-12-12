package com.ecm.core.service;

import com.ecm.core.entity.Document;
import com.ecm.core.entity.DocumentRelation;
import com.ecm.core.repository.DocumentRelationRepository;
import com.ecm.core.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
}
