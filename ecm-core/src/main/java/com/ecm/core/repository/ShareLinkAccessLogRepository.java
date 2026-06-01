package com.ecm.core.repository;

import com.ecm.core.entity.ShareLinkAccessLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShareLinkAccessLogRepository extends JpaRepository<ShareLinkAccessLog, UUID> {
    List<ShareLinkAccessLog> findByShareLinkIdOrderByAccessedAtDesc(UUID shareLinkId);
    Page<ShareLinkAccessLog> findByShareLinkId(UUID shareLinkId, Pageable pageable);
    long countByShareLinkId(UUID shareLinkId);
    long countByShareLinkIdAndSuccessTrue(UUID shareLinkId);
    long countByShareLinkIdAndSuccessFalse(UUID shareLinkId);

    @Modifying
    @Query(
        value = """
            DELETE FROM share_link_access_log
            WHERE share_link_id IN (
                SELECT id FROM share_links WHERE node_id = :nodeId
            )
            """,
        nativeQuery = true
    )
    int deleteByShareLinkNodeId(@Param("nodeId") UUID nodeId);
}
