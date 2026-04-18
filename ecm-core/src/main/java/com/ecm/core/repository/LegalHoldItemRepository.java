package com.ecm.core.repository;

import com.ecm.core.entity.LegalHold;
import com.ecm.core.entity.LegalHoldItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LegalHoldItemRepository extends JpaRepository<LegalHoldItem, UUID> {

    @Query("""
        select item from LegalHoldItem item
        join fetch item.hold hold
        join fetch item.node node
        where hold.status = :status
          and hold.deleted = false
        """)
    List<LegalHoldItem> findActiveItems(@Param("status") LegalHold.HoldStatus status);

    @Query("""
        select item from LegalHoldItem item
        join fetch item.node node
        where item.hold.id = :holdId
        order by item.addedAt asc
        """)
    List<LegalHoldItem> findByHoldIdWithNode(@Param("holdId") UUID holdId);

    boolean existsByHoldIdAndNodeId(UUID holdId, UUID nodeId);

    Optional<LegalHoldItem> findByHoldIdAndNodeId(UUID holdId, UUID nodeId);

    long countByHoldId(UUID holdId);
}
