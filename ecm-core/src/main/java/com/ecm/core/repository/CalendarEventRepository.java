package com.ecm.core.repository;

import com.ecm.core.entity.CalendarEvent;
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
public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {

    Page<CalendarEvent> findBySiteIdOrderByStartDateAsc(String siteId, Pageable pageable);

    @Query("SELECT e FROM CalendarEvent e WHERE e.siteId = :siteId AND e.startDate < :to AND e.endDate > :from ORDER BY e.startDate ASC")
    List<CalendarEvent> findBySiteIdAndRange(
        @Param("siteId") String siteId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );
}
