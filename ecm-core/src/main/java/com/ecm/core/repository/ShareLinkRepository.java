package com.ecm.core.repository;

import com.ecm.core.entity.ShareLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ShareLink entity
 */
@Repository
public interface ShareLinkRepository extends JpaRepository<ShareLink, UUID> {

    /**
     * Find share link by token
     */
    Optional<ShareLink> findByToken(String token);

    /**
     * Find all share links for a node
     */
    List<ShareLink> findByNodeId(UUID nodeId);

    /**
     * Find all active share links for a node
     */
    @Query("SELECT sl FROM ShareLink sl WHERE sl.node.id = :nodeId AND sl.active = true")
    List<ShareLink> findActiveByNodeId(@Param("nodeId") UUID nodeId);

    /**
     * Find all share links created by a user
     */
    List<ShareLink> findByCreatedBy(String username);

    /**
     * Find all active share links created by a user
     */
    @Query("SELECT sl FROM ShareLink sl WHERE sl.createdBy = :username AND sl.active = true")
    List<ShareLink> findActiveByCreatedBy(@Param("username") String username);

    /**
     * Find expired share links
     */
    @Query("SELECT sl FROM ShareLink sl WHERE sl.active = true AND sl.expiryDate IS NOT NULL AND sl.expiryDate < :now")
    List<ShareLink> findExpiredLinks(@Param("now") LocalDateTime now);

    /**
     * Find share links that have reached their access limit
     */
    @Query("SELECT sl FROM ShareLink sl WHERE sl.active = true AND sl.maxAccessCount IS NOT NULL AND sl.accessCount >= sl.maxAccessCount")
    List<ShareLink> findAccessLimitReachedLinks();

    /**
     * Deactivate expired links
     */
    @Modifying
    @Query("UPDATE ShareLink sl SET sl.active = false WHERE sl.active = true AND sl.expiryDate IS NOT NULL AND sl.expiryDate < :now")
    int deactivateExpiredLinks(@Param("now") LocalDateTime now);

    /**
     * Deactivate links that have reached their access limit
     */
    @Modifying
    @Query("UPDATE ShareLink sl SET sl.active = false WHERE sl.active = true AND sl.maxAccessCount IS NOT NULL AND sl.accessCount >= sl.maxAccessCount")
    int deactivateAccessLimitReachedLinks();

    /**
     * Delete all share links for a node
     */
    void deleteByNodeId(UUID nodeId);

    /**
     * Check if a token exists
     */
    boolean existsByToken(String token);

    /**
     * Count active share links for a node
     */
    @Query("SELECT COUNT(sl) FROM ShareLink sl WHERE sl.node.id = :nodeId AND sl.active = true")
    long countActiveByNodeId(@Param("nodeId") UUID nodeId);

    /**
     * Find recently accessed share links
     */
    @Query("SELECT sl FROM ShareLink sl WHERE sl.lastAccessedAt IS NOT NULL ORDER BY sl.lastAccessedAt DESC")
    List<ShareLink> findRecentlyAccessed();
}
