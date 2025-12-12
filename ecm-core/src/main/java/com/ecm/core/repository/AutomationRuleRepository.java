package com.ecm.core.repository;

import com.ecm.core.entity.AutomationRule;
import com.ecm.core.entity.AutomationRule.TriggerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for AutomationRule entity
 */
@Repository
public interface AutomationRuleRepository extends JpaRepository<AutomationRule, UUID> {

    /**
     * Find all enabled rules for a specific trigger type, ordered by priority
     */
    @Query("SELECT r FROM AutomationRule r " +
           "WHERE r.triggerType = :triggerType " +
           "AND r.enabled = true " +
           "AND r.deleted = false " +
           "ORDER BY r.priority ASC")
    List<AutomationRule> findByTriggerTypeAndEnabledTrue(@Param("triggerType") TriggerType triggerType);

    /**
     * Find all enabled rules for a trigger type within a specific folder scope
     */
    @Query("SELECT r FROM AutomationRule r " +
           "WHERE r.triggerType = :triggerType " +
           "AND r.enabled = true " +
           "AND r.deleted = false " +
           "AND (r.scopeFolderId IS NULL OR r.scopeFolderId = :folderId) " +
           "ORDER BY r.priority ASC")
    List<AutomationRule> findByTriggerTypeAndEnabledTrueWithScope(
        @Param("triggerType") TriggerType triggerType,
        @Param("folderId") UUID folderId);

    /**
     * Find rules by owner
     */
    @Query("SELECT r FROM AutomationRule r " +
           "WHERE r.owner = :owner " +
           "AND r.deleted = false")
    Page<AutomationRule> findByOwner(@Param("owner") String owner, Pageable pageable);

    /**
     * Find all rules (paged)
     */
    @Query("SELECT r FROM AutomationRule r WHERE r.deleted = false")
    Page<AutomationRule> findAllActive(Pageable pageable);

    /**
     * Find rule by name
     */
    @Query("SELECT r FROM AutomationRule r " +
           "WHERE r.name = :name " +
           "AND r.deleted = false")
    Optional<AutomationRule> findByName(@Param("name") String name);

    /**
     * Find rules by trigger type (all, including disabled)
     */
    @Query("SELECT r FROM AutomationRule r " +
           "WHERE r.triggerType = :triggerType " +
           "AND r.deleted = false")
    List<AutomationRule> findByTriggerType(@Param("triggerType") TriggerType triggerType);

    /**
     * Count enabled rules by trigger type
     */
    @Query("SELECT COUNT(r) FROM AutomationRule r " +
           "WHERE r.triggerType = :triggerType " +
           "AND r.enabled = true " +
           "AND r.deleted = false")
    long countEnabledByTriggerType(@Param("triggerType") TriggerType triggerType);

    /**
     * Count all rules by owner
     */
    @Query("SELECT COUNT(r) FROM AutomationRule r " +
           "WHERE r.owner = :owner " +
           "AND r.deleted = false")
    long countByOwner(@Param("owner") String owner);

    /**
     * Enable/disable a rule
     */
    @Modifying
    @Query("UPDATE AutomationRule r SET r.enabled = :enabled WHERE r.id = :id")
    int updateEnabled(@Param("id") UUID id, @Param("enabled") boolean enabled);

    /**
     * Increment execution count
     */
    @Modifying
    @Query("UPDATE AutomationRule r " +
           "SET r.executionCount = COALESCE(r.executionCount, 0) + 1 " +
           "WHERE r.id = :id")
    int incrementExecutionCount(@Param("id") UUID id);

    /**
     * Increment failure count
     */
    @Modifying
    @Query("UPDATE AutomationRule r " +
           "SET r.failureCount = COALESCE(r.failureCount, 0) + 1 " +
           "WHERE r.id = :id")
    int incrementFailureCount(@Param("id") UUID id);

    /**
     * Find rules with high failure rate (more than 50% failures)
     */
    @Query("SELECT r FROM AutomationRule r " +
           "WHERE r.executionCount > 10 " +
           "AND r.failureCount > r.executionCount / 2 " +
           "AND r.enabled = true " +
           "AND r.deleted = false")
    List<AutomationRule> findRulesWithHighFailureRate();

    /**
     * Find most active rules
     */
    @Query("SELECT r FROM AutomationRule r " +
           "WHERE r.deleted = false " +
           "ORDER BY r.executionCount DESC")
    List<AutomationRule> findMostActiveRules(Pageable pageable);

    /**
     * Check if a rule name exists (excluding a specific rule ID)
     */
    @Query("SELECT COUNT(r) > 0 FROM AutomationRule r " +
           "WHERE r.name = :name " +
           "AND r.id != :excludeId " +
           "AND r.deleted = false")
    boolean existsByNameExcludingId(@Param("name") String name, @Param("excludeId") UUID excludeId);

    /**
     * Search rules by name or description
     */
    @Query("SELECT r FROM AutomationRule r " +
           "WHERE r.deleted = false " +
           "AND (LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "     OR LOWER(r.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<AutomationRule> searchRules(@Param("query") String query, Pageable pageable);
}
