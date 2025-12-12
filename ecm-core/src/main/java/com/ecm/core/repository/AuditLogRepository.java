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

    @Query("SELECT a.username, COUNT(a) FROM AuditLog a GROUP BY a.username ORDER BY COUNT(a) DESC")
    List<Object[]> findTopActiveUsers(Pageable pageable);
    
    @Query("SELECT DATE(a.eventTime) as date, COUNT(a) FROM AuditLog a WHERE a.eventTime >= :startTime GROUP BY DATE(a.eventTime) ORDER BY date ASC")
    List<Object[]> getDailyActivityStats(@Param("startTime") LocalDateTime startTime);
}
