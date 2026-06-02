package com.ecm.core.repository;

import com.ecm.core.entity.AssocDirection;
import com.ecm.core.entity.NodeRelation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NodeRelationRepository extends JpaRepository<NodeRelation, UUID> {

    List<NodeRelation> findBySourceId(UUID sourceId);
    Page<NodeRelation> findBySourceId(UUID sourceId, Pageable pageable);
    Page<NodeRelation> findBySourceIdAndRelationTypeIgnoreCase(UUID sourceId, String relationType, Pageable pageable);

    List<NodeRelation> findByTargetId(UUID targetId);
    Page<NodeRelation> findByTargetId(UUID targetId, Pageable pageable);
    Page<NodeRelation> findByTargetIdAndRelationTypeIgnoreCase(UUID targetId, String relationType, Pageable pageable);

    void deleteBySourceIdAndTargetIdAndRelationType(UUID sourceId, UUID targetId, String relationType);
    void deleteBySourceIdAndTargetId(UUID sourceId, UUID targetId);

    // Clear all relations a node participates in, before the node is permanently deleted
    // (FK fk_nr_source / fk_nr_target have no ON DELETE CASCADE, so a leftover relation
    // would make the node delete fail with a constraint violation).
    void deleteBySourceId(UUID sourceId);
    void deleteByTargetId(UUID targetId);

    List<NodeRelation> findBySourceIdAndDirection(UUID sourceId, AssocDirection direction);
    List<NodeRelation> findByTargetIdAndDirection(UUID targetId, AssocDirection direction);
}
