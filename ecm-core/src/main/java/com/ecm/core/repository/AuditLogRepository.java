package com.ecm.core.repository;

import com.ecm.core.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByNodeIdOrderByEventTimeDesc(UUID nodeId);

    List<AuditLog> findByUsernameOrderByEventTimeDesc(String username);

    @Query("SELECT a FROM AuditLog a WHERE a.eventTime >= :startTime AND a.eventTime <= :endTime ORDER BY a.eventTime DESC")
    Page<AuditLog> findByTimeRange(@Param("startTime") LocalDateTime startTime, 
                                   @Param("endTime") LocalDateTime endTime, 
                                   Pageable pageable);

    @Query("SELECT a.eventType, COUNT(a) FROM AuditLog a GROUP BY a.eventType")
    List<Object[]> countByEventType();

    @Query("SELECT a.eventType, COUNT(a) FROM AuditLog a WHERE a.eventTime >= :startTime AND a.eventType IN :eventTypes GROUP BY a.eventType")
    List<Object[]> countByEventTypeSince(@Param("startTime") LocalDateTime startTime,
                                         @Param("eventTypes") List<String> eventTypes);

    @Query("SELECT a.eventType, COUNT(a) FROM AuditLog a WHERE a.eventTime >= :startTime GROUP BY a.eventType")
    List<Object[]> countByEventTypeSince(@Param("startTime") LocalDateTime startTime);

    Page<AuditLog> findByEventTypeInOrderByEventTimeDesc(List<String> eventTypes, Pageable pageable);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.eventType LIKE CONCAT(:eventPrefix, '%')
        ORDER BY a.eventTime DESC
        """)
    Page<AuditLog> findByEventTypePrefix(@Param("eventPrefix") String eventPrefix, Pageable pageable);

    Page<AuditLog> findByEventTypeOrderByEventTimeDesc(String eventType, Pageable pageable);

    Page<AuditLog> findByEventTypeAndUsernameOrderByEventTimeDesc(
        String eventType,
        String username,
        Pageable pageable
    );

    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.eventType LIKE CONCAT(:eventPrefix, '%')
          AND a.eventTime >= :from
        ORDER BY a.eventTime DESC
        """)
    Page<AuditLog> findByEventTypePrefixSince(@Param("eventPrefix") String eventPrefix,
                                              @Param("from") LocalDateTime from,
                                              Pageable pageable);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.eventType LIKE CONCAT(:eventPrefix, '%')
          AND a.username = :username
        ORDER BY a.eventTime DESC
        """)
    Page<AuditLog> findByEventTypePrefixAndUsername(@Param("eventPrefix") String eventPrefix,
                                                     @Param("username") String username,
                                                     Pageable pageable);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.eventType LIKE CONCAT(:eventPrefix, '%')
          AND a.eventTime >= :from
          AND a.username = :username
        ORDER BY a.eventTime DESC
        """)
    Page<AuditLog> findByEventTypePrefixSinceAndUsername(@Param("eventPrefix") String eventPrefix,
                                                          @Param("from") LocalDateTime from,
                                                          @Param("username") String username,
                                                          Pageable pageable);

    Page<AuditLog> findByEventTypeAndEventTimeGreaterThanEqualOrderByEventTimeDesc(
        String eventType,
        LocalDateTime from,
        Pageable pageable
    );

    Page<AuditLog> findByEventTypeAndUsernameAndEventTimeGreaterThanEqualOrderByEventTimeDesc(
        String eventType,
        String username,
        LocalDateTime from,
        Pageable pageable
    );

    @Query("""
        SELECT a.eventType, COUNT(a) FROM AuditLog a
        WHERE a.eventType LIKE CONCAT(:eventPrefix, '%')
          AND (:from IS NULL OR a.eventTime >= :from)
          AND (:username IS NULL OR a.username = :username)
          AND (:eventType IS NULL OR a.eventType = :eventType)
        GROUP BY a.eventType
        ORDER BY COUNT(a) DESC
        """)
    List<Object[]> countByEventTypePrefixWithFilters(@Param("eventPrefix") String eventPrefix,
                                                      @Param("from") LocalDateTime from,
                                                      @Param("username") String username,
                                                      @Param("eventType") String eventType);

    @Query("""
        SELECT a.username, COUNT(a) FROM AuditLog a
        WHERE a.eventType LIKE CONCAT(:eventPrefix, '%')
          AND (:from IS NULL OR a.eventTime >= :from)
          AND (:username IS NULL OR a.username = :username)
          AND (:eventType IS NULL OR a.eventType = :eventType)
        GROUP BY a.username
        ORDER BY COUNT(a) DESC
        """)
    List<Object[]> countByUsernamePrefixWithFilters(@Param("eventPrefix") String eventPrefix,
                                                     @Param("from") LocalDateTime from,
                                                     @Param("username") String username,
                                                     @Param("eventType") String eventType);

    @Query("SELECT a.username, COUNT(a) FROM AuditLog a GROUP BY a.username ORDER BY COUNT(a) DESC")
    List<Object[]> findTopActiveUsers(Pageable pageable);
    
    @Query("SELECT DATE(a.eventTime) as date, COUNT(a) FROM AuditLog a WHERE a.eventTime >= :startTime GROUP BY DATE(a.eventTime) ORDER BY date ASC")
    List<Object[]> getDailyActivityStats(@Param("startTime") LocalDateTime startTime);

    /**
     * Find audit logs within a time range for export
     */
    @Query("SELECT a FROM AuditLog a WHERE a.eventTime >= :from AND a.eventTime <= :to ORDER BY a.eventTime DESC")
    List<AuditLog> findByTimeRangeForExport(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:username IS NULL OR :username = '' OR a.username = :username)
          AND (:eventType IS NULL OR :eventType = '' OR a.eventType = :eventType)
          AND a.eventTime >= COALESCE(:from, a.eventTime)
          AND a.eventTime <= COALESCE(:to, a.eventTime)
        ORDER BY a.eventTime DESC
        """)
    Page<AuditLog> findByFilters(@Param("username") String username,
                                 @Param("eventType") String eventType,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to,
                                 Pageable pageable);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:username IS NULL OR :username = '' OR a.username = :username)
          AND (:eventType IS NULL OR :eventType = '' OR a.eventType = :eventType)
          AND a.eventTime >= COALESCE(:from, a.eventTime)
          AND a.eventTime <= COALESCE(:to, a.eventTime)
          AND (:nodeId IS NULL OR a.nodeId = :nodeId)
          AND (
            :category IS NULL OR :category = ''
            OR (:category = 'NODE' AND UPPER(a.eventType) LIKE 'NODE_%')
            OR (:category = 'VERSION' AND UPPER(a.eventType) LIKE 'VERSION_%')
            OR (:category = 'RULE' AND (UPPER(a.eventType) LIKE 'RULE_%' OR UPPER(a.eventType) LIKE 'SCHEDULED_RULE%'))
            OR (:category = 'WORKFLOW' AND (UPPER(a.eventType) LIKE 'WORKFLOW_%' OR UPPER(a.eventType) LIKE 'STATUS_%'))
            OR (:category = 'MAIL' AND UPPER(a.eventType) LIKE 'MAIL_%')
            OR (:category = 'INTEGRATION' AND UPPER(a.eventType) LIKE 'WOPI_%')
            OR (:category = 'SECURITY' AND UPPER(a.eventType) LIKE 'SECURITY_%')
            OR (:category = 'PDF' AND UPPER(a.eventType) LIKE 'PDF_%')
            OR (:category = 'OTHER' AND UPPER(a.eventType) NOT LIKE 'NODE_%'
                AND UPPER(a.eventType) NOT LIKE 'VERSION_%'
                AND UPPER(a.eventType) NOT LIKE 'RULE_%'
                AND UPPER(a.eventType) NOT LIKE 'SCHEDULED_RULE%'
                AND UPPER(a.eventType) NOT LIKE 'WORKFLOW_%'
                AND UPPER(a.eventType) NOT LIKE 'STATUS_%'
                AND UPPER(a.eventType) NOT LIKE 'MAIL_%'
                AND UPPER(a.eventType) NOT LIKE 'WOPI_%'
                AND UPPER(a.eventType) NOT LIKE 'SECURITY_%'
                AND UPPER(a.eventType) NOT LIKE 'PDF_%')
          )
        ORDER BY a.eventTime DESC
        """)
    Page<AuditLog> findByFiltersAndCategory(@Param("username") String username,
                                            @Param("eventType") String eventType,
                                            @Param("category") String category,
                                            @Param("nodeId") UUID nodeId,
                                            @Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to,
                                            Pageable pageable);

    /**
     * Variant of {@link #findByFiltersAndCategory} that omits the nodeId predicate entirely.
     *
     * PostgreSQL can fail to infer the SQL type for a NULL UUID parameter when using patterns
     * like "(:nodeId IS NULL OR a.nodeId = :nodeId)" (SQLState 42P18). This variant avoids that.
     */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:username IS NULL OR :username = '' OR a.username = :username)
          AND (:eventType IS NULL OR :eventType = '' OR a.eventType = :eventType)
          AND a.eventTime >= COALESCE(:from, a.eventTime)
          AND a.eventTime <= COALESCE(:to, a.eventTime)
          AND (
            :category IS NULL OR :category = ''
            OR (:category = 'NODE' AND UPPER(a.eventType) LIKE 'NODE_%')
            OR (:category = 'VERSION' AND UPPER(a.eventType) LIKE 'VERSION_%')
            OR (:category = 'RULE' AND (UPPER(a.eventType) LIKE 'RULE_%' OR UPPER(a.eventType) LIKE 'SCHEDULED_RULE%'))
            OR (:category = 'WORKFLOW' AND (UPPER(a.eventType) LIKE 'WORKFLOW_%' OR UPPER(a.eventType) LIKE 'STATUS_%'))
            OR (:category = 'MAIL' AND UPPER(a.eventType) LIKE 'MAIL_%')
            OR (:category = 'INTEGRATION' AND UPPER(a.eventType) LIKE 'WOPI_%')
            OR (:category = 'SECURITY' AND UPPER(a.eventType) LIKE 'SECURITY_%')
            OR (:category = 'PDF' AND UPPER(a.eventType) LIKE 'PDF_%')
            OR (:category = 'OTHER' AND UPPER(a.eventType) NOT LIKE 'NODE_%'
                AND UPPER(a.eventType) NOT LIKE 'VERSION_%'
                AND UPPER(a.eventType) NOT LIKE 'RULE_%'
                AND UPPER(a.eventType) NOT LIKE 'SCHEDULED_RULE%'
                AND UPPER(a.eventType) NOT LIKE 'WORKFLOW_%'
                AND UPPER(a.eventType) NOT LIKE 'STATUS_%'
                AND UPPER(a.eventType) NOT LIKE 'MAIL_%'
                AND UPPER(a.eventType) NOT LIKE 'WOPI_%'
                AND UPPER(a.eventType) NOT LIKE 'SECURITY_%'
                AND UPPER(a.eventType) NOT LIKE 'PDF_%')
          )
        ORDER BY a.eventTime DESC
        """)
    Page<AuditLog> findByFiltersAndCategoryNoNodeId(@Param("username") String username,
                                                    @Param("eventType") String eventType,
                                                    @Param("category") String category,
                                                    @Param("from") LocalDateTime from,
                                                    @Param("to") LocalDateTime to,
                                                    Pageable pageable);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:username IS NULL OR :username = '' OR a.username = :username)
          AND (:eventType IS NULL OR :eventType = '' OR a.eventType = :eventType)
          AND (:nodeId IS NULL OR a.nodeId = :nodeId)
          AND a.eventTime >= COALESCE(:from, a.eventTime)
          AND a.eventTime <= COALESCE(:to, a.eventTime)
        ORDER BY a.eventTime DESC
        """)
    List<AuditLog> findByFiltersForExport(@Param("username") String username,
                                          @Param("eventType") String eventType,
                                          @Param("nodeId") UUID nodeId,
                                          @Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);

    /**
     * Export variant that omits nodeId filtering entirely. See note on {@link #findByFiltersAndCategoryNoNodeId}.
     */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:username IS NULL OR :username = '' OR a.username = :username)
          AND (:eventType IS NULL OR :eventType = '' OR a.eventType = :eventType)
          AND a.eventTime >= COALESCE(:from, a.eventTime)
          AND a.eventTime <= COALESCE(:to, a.eventTime)
        ORDER BY a.eventTime DESC
        """)
    List<AuditLog> findByFiltersForExportNoNodeId(@Param("username") String username,
                                                  @Param("eventType") String eventType,
                                                  @Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE (UPPER(a.eventType) LIKE 'RULE_%' OR UPPER(a.eventType) LIKE 'SCHEDULED_RULE%')
          AND (:eventType IS NULL OR :eventType = '' OR a.eventType = :eventType)
          AND (:username IS NULL OR :username = '' OR a.username = :username)
          AND (:nodeId IS NULL OR a.nodeId = :nodeId)
          AND (:from IS NULL OR a.eventTime >= :from)
          AND (:to IS NULL OR a.eventTime <= :to)
        ORDER BY a.eventTime DESC
        """)
    Page<AuditLog> findRuleAuditTimeline(@Param("eventType") String eventType,
                                         @Param("username") String username,
                                         @Param("nodeId") UUID nodeId,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to,
                                         Pageable pageable);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE UPPER(a.eventType) LIKE 'BULK_%'
          AND (:eventType IS NULL OR :eventType = '' OR a.eventType = :eventType)
          AND (:username IS NULL OR :username = '' OR a.username = :username)
          AND (:nodeId IS NULL OR a.nodeId = :nodeId)
          AND (:from IS NULL OR a.eventTime >= :from)
          AND (:to IS NULL OR a.eventTime <= :to)
        ORDER BY a.eventTime DESC
        """)
    Page<AuditLog> findBulkOperationTimeline(@Param("eventType") String eventType,
                                             @Param("username") String username,
                                             @Param("nodeId") UUID nodeId,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to,
                                             Pageable pageable);

    /**
     * Variant of {@link #findBulkOperationTimeline} without nodeId predicate to avoid NULL UUID inference issues.
     */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE UPPER(a.eventType) LIKE 'BULK_%'
          AND (:eventType IS NULL OR :eventType = '' OR a.eventType = :eventType)
          AND (:username IS NULL OR :username = '' OR a.username = :username)
          AND (:from IS NULL OR a.eventTime >= :from)
          AND (:to IS NULL OR a.eventTime <= :to)
        ORDER BY a.eventTime DESC
        """)
    Page<AuditLog> findBulkOperationTimelineNoNodeId(@Param("eventType") String eventType,
                                                     @Param("username") String username,
                                                     @Param("from") LocalDateTime from,
                                                     @Param("to") LocalDateTime to,
                                                     Pageable pageable);

    @Query("""
        SELECT a.eventType, COUNT(a) FROM AuditLog a
        WHERE UPPER(a.eventType) LIKE 'BULK_%'
          AND (:eventType IS NULL OR :eventType = '' OR a.eventType = :eventType)
          AND (:username IS NULL OR :username = '' OR a.username = :username)
          AND (:nodeId IS NULL OR a.nodeId = :nodeId)
          AND (:from IS NULL OR a.eventTime >= :from)
          AND (:to IS NULL OR a.eventTime <= :to)
        GROUP BY a.eventType
        ORDER BY COUNT(a) DESC
        """)
    List<Object[]> countBulkByEventTypeWithFilters(@Param("eventType") String eventType,
                                                   @Param("username") String username,
                                                   @Param("nodeId") UUID nodeId,
                                                   @Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to);

    @Query("""
        SELECT a.username, COUNT(a) FROM AuditLog a
        WHERE UPPER(a.eventType) LIKE 'BULK_%'
          AND (:eventType IS NULL OR :eventType = '' OR a.eventType = :eventType)
          AND (:username IS NULL OR :username = '' OR a.username = :username)
          AND (:nodeId IS NULL OR a.nodeId = :nodeId)
          AND (:from IS NULL OR a.eventTime >= :from)
          AND (:to IS NULL OR a.eventTime <= :to)
        GROUP BY a.username
        ORDER BY COUNT(a) DESC
        """)
    List<Object[]> countBulkByUsernameWithFilters(@Param("eventType") String eventType,
                                                  @Param("username") String username,
                                                  @Param("nodeId") UUID nodeId,
                                                  @Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE UPPER(a.eventType) LIKE 'RM_%'
          AND (:eventType IS NULL OR :eventType = '' OR a.eventType = :eventType)
          AND (:username IS NULL OR :username = '' OR a.username = :username)
          AND (:from IS NULL OR a.eventTime >= :from)
          AND (:to IS NULL OR a.eventTime <= :to)
        ORDER BY a.eventTime DESC
        """)
    Page<AuditLog> findRecordsManagementTimeline(@Param("eventType") String eventType,
                                                 @Param("username") String username,
                                                 @Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to,
                                                 Pageable pageable);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.eventType IN :eventTypes
          AND (:username IS NULL OR :username = '' OR a.username = :username)
          AND (:from IS NULL OR a.eventTime >= :from)
          AND (:to IS NULL OR a.eventTime <= :to)
        ORDER BY a.eventTime DESC
        """)
    Page<AuditLog> findByEventTypesAndFilters(@Param("eventTypes") List<String> eventTypes,
                                               @Param("username") String username,
                                               @Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to,
                                               Pageable pageable);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE UPPER(a.eventType) LIKE 'RM_%'
          AND a.eventType NOT IN :excludedEventTypes
          AND (:eventType IS NULL OR :eventType = '' OR a.eventType = :eventType)
          AND (:username IS NULL OR :username = '' OR a.username = :username)
          AND (:from IS NULL OR a.eventTime >= :from)
          AND (:to IS NULL OR a.eventTime <= :to)
        ORDER BY a.eventTime DESC
        """)
    Page<AuditLog> findOtherRecordsManagementTimeline(@Param("excludedEventTypes") List<String> excludedEventTypes,
                                                      @Param("eventType") String eventType,
                                                      @Param("username") String username,
                                                      @Param("from") LocalDateTime from,
                                                      @Param("to") LocalDateTime to,
                                                      Pageable pageable);

    @Query("""
        SELECT DATE(a.eventTime), a.eventType, COUNT(a) FROM AuditLog a
        WHERE UPPER(a.eventType) LIKE 'RM_%'
          AND a.eventTime >= :from
        GROUP BY DATE(a.eventTime), a.eventType
        ORDER BY DATE(a.eventTime) ASC, a.eventType ASC
        """)
    List<Object[]> countRecordsManagementEventsByDaySince(@Param("from") LocalDateTime from);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:username IS NULL OR :username = '' OR a.username = :username)
          AND (:eventType IS NULL OR :eventType = '' OR a.eventType = :eventType)
          AND (:nodeId IS NULL OR a.nodeId = :nodeId)
          AND a.eventTime >= COALESCE(:from, a.eventTime)
          AND a.eventTime <= COALESCE(:to, a.eventTime)
          AND (
            :category IS NULL OR :category = ''
            OR (:category = 'NODE' AND UPPER(a.eventType) LIKE 'NODE_%')
            OR (:category = 'VERSION' AND UPPER(a.eventType) LIKE 'VERSION_%')
            OR (:category = 'RULE' AND (UPPER(a.eventType) LIKE 'RULE_%' OR UPPER(a.eventType) LIKE 'SCHEDULED_RULE%'))
            OR (:category = 'WORKFLOW' AND (UPPER(a.eventType) LIKE 'WORKFLOW_%' OR UPPER(a.eventType) LIKE 'STATUS_%'))
            OR (:category = 'MAIL' AND UPPER(a.eventType) LIKE 'MAIL_%')
            OR (:category = 'INTEGRATION' AND UPPER(a.eventType) LIKE 'WOPI_%')
            OR (:category = 'SECURITY' AND UPPER(a.eventType) LIKE 'SECURITY_%')
            OR (:category = 'PDF' AND UPPER(a.eventType) LIKE 'PDF_%')
            OR (:category = 'OTHER' AND UPPER(a.eventType) NOT LIKE 'NODE_%'
                AND UPPER(a.eventType) NOT LIKE 'VERSION_%'
                AND UPPER(a.eventType) NOT LIKE 'RULE_%'
                AND UPPER(a.eventType) NOT LIKE 'SCHEDULED_RULE%'
                AND UPPER(a.eventType) NOT LIKE 'WORKFLOW_%'
                AND UPPER(a.eventType) NOT LIKE 'STATUS_%'
                AND UPPER(a.eventType) NOT LIKE 'MAIL_%'
                AND UPPER(a.eventType) NOT LIKE 'WOPI_%'
                AND UPPER(a.eventType) NOT LIKE 'SECURITY_%'
                AND UPPER(a.eventType) NOT LIKE 'PDF_%')
          )
        ORDER BY a.eventTime DESC
        """)
    List<AuditLog> findByFiltersForExportAndCategory(@Param("username") String username,
                                                     @Param("eventType") String eventType,
                                                     @Param("category") String category,
                                                     @Param("nodeId") UUID nodeId,
                                                     @Param("from") LocalDateTime from,
                                                     @Param("to") LocalDateTime to);

    /**
     * Export variant that omits nodeId filtering entirely. See note on {@link #findByFiltersAndCategoryNoNodeId}.
     */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:username IS NULL OR :username = '' OR a.username = :username)
          AND (:eventType IS NULL OR :eventType = '' OR a.eventType = :eventType)
          AND a.eventTime >= COALESCE(:from, a.eventTime)
          AND a.eventTime <= COALESCE(:to, a.eventTime)
          AND (
            :category IS NULL OR :category = ''
            OR (:category = 'NODE' AND UPPER(a.eventType) LIKE 'NODE_%')
            OR (:category = 'VERSION' AND UPPER(a.eventType) LIKE 'VERSION_%')
            OR (:category = 'RULE' AND (UPPER(a.eventType) LIKE 'RULE_%' OR UPPER(a.eventType) LIKE 'SCHEDULED_RULE%'))
            OR (:category = 'WORKFLOW' AND (UPPER(a.eventType) LIKE 'WORKFLOW_%' OR UPPER(a.eventType) LIKE 'STATUS_%'))
            OR (:category = 'MAIL' AND UPPER(a.eventType) LIKE 'MAIL_%')
            OR (:category = 'INTEGRATION' AND UPPER(a.eventType) LIKE 'WOPI_%')
            OR (:category = 'SECURITY' AND UPPER(a.eventType) LIKE 'SECURITY_%')
            OR (:category = 'PDF' AND UPPER(a.eventType) LIKE 'PDF_%')
            OR (:category = 'OTHER' AND UPPER(a.eventType) NOT LIKE 'NODE_%'
                AND UPPER(a.eventType) NOT LIKE 'VERSION_%'
                AND UPPER(a.eventType) NOT LIKE 'RULE_%'
                AND UPPER(a.eventType) NOT LIKE 'SCHEDULED_RULE%'
                AND UPPER(a.eventType) NOT LIKE 'WORKFLOW_%'
                AND UPPER(a.eventType) NOT LIKE 'STATUS_%'
                AND UPPER(a.eventType) NOT LIKE 'MAIL_%'
                AND UPPER(a.eventType) NOT LIKE 'WOPI_%'
                AND UPPER(a.eventType) NOT LIKE 'SECURITY_%'
                AND UPPER(a.eventType) NOT LIKE 'PDF_%')
          )
        ORDER BY a.eventTime DESC
        """)
    List<AuditLog> findByFiltersForExportAndCategoryNoNodeId(@Param("username") String username,
                                                             @Param("eventType") String eventType,
                                                             @Param("category") String category,
                                                             @Param("from") LocalDateTime from,
                                                             @Param("to") LocalDateTime to);

    Page<AuditLog> findByEventTypeInOrderByEventTimeAsc(List<String> eventTypes, Pageable pageable);

    Page<AuditLog> findByEventTypeInAndEventTimeAfterOrderByEventTimeAsc(List<String> eventTypes, LocalDateTime after, Pageable pageable);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.eventType IN :eventTypes
        ORDER BY a.eventTime ASC, a.id ASC
        """)
    Page<AuditLog> findByEventTypeInOrderByEventTimeAscIdAsc(@Param("eventTypes") List<String> eventTypes, Pageable pageable);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.eventType IN :eventTypes
          AND (a.eventTime > :afterTime OR (a.eventTime = :afterTime AND a.id > :afterId))
        ORDER BY a.eventTime ASC, a.id ASC
        """)
    Page<AuditLog> findByEventTypeInAfterCursorOrderByEventTimeAscIdAsc(@Param("eventTypes") List<String> eventTypes,
                                                                        @Param("afterTime") LocalDateTime afterTime,
                                                                        @Param("afterId") UUID afterId,
                                                                        Pageable pageable);

    @Query("""
        SELECT a.username, a.eventType, COUNT(a), MAX(a.eventTime) FROM AuditLog a
        WHERE UPPER(a.eventType) LIKE 'RM_%'
          AND a.eventTime >= :from
          AND a.eventTime <= :to
        GROUP BY a.username, a.eventType
        ORDER BY a.username ASC, a.eventType ASC
        """)
    List<Object[]> countRmEventsByUsernameAndTypeBetween(@Param("from") LocalDateTime from,
                                                          @Param("to") LocalDateTime to);

    @Query("""
        SELECT DATE(a.eventTime), a.username, a.eventType, COUNT(a) FROM AuditLog a
        WHERE UPPER(a.eventType) LIKE 'RM_%'
          AND a.eventTime >= :from
        GROUP BY DATE(a.eventTime), a.username, a.eventType
        ORDER BY DATE(a.eventTime) ASC, a.username ASC, a.eventType ASC
        """)
    List<Object[]> countRmEventsByDayUsernameAndTypeSince(@Param("from") LocalDateTime from);

    @Query("""
        SELECT a.eventType, COUNT(a), MAX(a.eventTime) FROM AuditLog a
        WHERE UPPER(a.eventType) LIKE 'RM_%'
          AND a.eventTime >= :from
          AND a.eventTime <= :to
        GROUP BY a.eventType
        ORDER BY COUNT(a) DESC, a.eventType ASC
        """)
    List<Object[]> countRmEventsByTypeBetween(@Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);

    /**
     * Delete audit logs older than the specified date (for retention policy)
     */
    void deleteByEventTimeBefore(LocalDateTime threshold);

    /**
     * Count audit logs older than the specified date
     */
    long countByEventTimeBefore(LocalDateTime threshold);
}
