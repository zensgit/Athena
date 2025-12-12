package com.ecm.core.repository;

import com.ecm.core.entity.DocumentRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRelationRepository extends JpaRepository<DocumentRelation, UUID> {
    
    List<DocumentRelation> findBySourceId(UUID sourceId);
    
    List<DocumentRelation> findByTargetId(UUID targetId);
    
    void deleteBySourceIdAndTargetIdAndRelationType(UUID sourceId, UUID targetId, String relationType);
}
