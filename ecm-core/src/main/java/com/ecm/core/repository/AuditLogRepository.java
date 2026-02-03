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

    Page<AuditLog> findByEventTypeInOrderByEventTimeDesc(List<String> eventTypes, Pageable pageable);

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
                                            @Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to,
                                            Pageable pageable);

    @Query("""
        SELECT a FROM AuditLog a
        WHERE (:username IS NULL OR :username = '' OR a.username = :username)
          AND (:eventType IS NULL OR :eventType = '' OR a.eventType = :eventType)
          AND a.eventTime >= COALESCE(:from, a.eventTime)
          AND a.eventTime <= COALESCE(:to, a.eventTime)
        ORDER BY a.eventTime DESC
        """)
    List<AuditLog> findByFiltersForExport(@Param("username") String username,
                                          @Param("eventType") String eventType,
                                          @Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);

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
    List<AuditLog> findByFiltersForExportAndCategory(@Param("username") String username,
                                                     @Param("eventType") String eventType,
                                                     @Param("category") String category,
                                                     @Param("from") LocalDateTime from,
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
