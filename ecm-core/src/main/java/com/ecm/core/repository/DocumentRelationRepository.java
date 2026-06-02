package com.ecm.core.repository;

import com.ecm.core.entity.AssocDirection;
import com.ecm.core.entity.DocumentRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRelationRepository extends JpaRepository<DocumentRelation, UUID> {

    List<DocumentRelation> findBySourceId(UUID sourceId);
    Page<DocumentRelation> findBySourceId(UUID sourceId, Pageable pageable);
    Page<DocumentRelation> findBySourceIdAndRelationTypeIgnoreCase(UUID sourceId, String relationType, Pageable pageable);

    List<DocumentRelation> findByTargetId(UUID targetId);
    Page<DocumentRelation> findByTargetId(UUID targetId, Pageable pageable);
    Page<DocumentRelation> findByTargetIdAndRelationTypeIgnoreCase(UUID targetId, String relationType, Pageable pageable);

    void deleteBySourceIdAndTargetIdAndRelationType(UUID sourceId, UUID targetId, String relationType);

    // --- association direction queries ---
    List<DocumentRelation> findBySourceIdAndDirection(UUID sourceId, AssocDirection direction);
    Page<DocumentRelation> findBySourceIdAndDirection(UUID sourceId, AssocDirection direction, Pageable pageable);
    List<DocumentRelation> findByTargetIdAndDirection(UUID targetId, AssocDirection direction);
    Page<DocumentRelation> findByTargetIdAndDirection(UUID targetId, AssocDirection direction, Pageable pageable);

    void deleteBySourceIdAndTargetId(UUID sourceId, UUID targetId);

    // Clear all relations a node participates in, before it is permanently deleted
    // (FK fk_dr_source / fk_dr_target have no ON DELETE CASCADE).
    void deleteBySourceId(UUID sourceId);
    void deleteByTargetId(UUID targetId);
}
