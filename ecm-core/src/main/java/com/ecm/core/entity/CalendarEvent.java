package com.ecm.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "calendar_events", indexes = {
    @Index(name = "idx_ce_site_id", columnList = "site_id"),
    @Index(name = "idx_ce_start_date", columnList = "start_date"),
    @Index(name = "idx_ce_end_date", columnList = "end_date")
})
@EqualsAndHashCode(callSuper = true)
public class CalendarEvent extends BaseEntity {

    @Column(name = "site_id", nullable = false, length = 100)
    private String siteId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "location")
    private String location;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "all_day")
    private boolean allDay = false;

    @Column(name = "recurrence", length = 500)
    private String recurrence;
}
