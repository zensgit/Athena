package com.ecm.core.service;

import com.ecm.core.entity.CalendarEvent;
import com.ecm.core.entity.Site;
import com.ecm.core.exception.ResourceNotFoundException;
import com.ecm.core.repository.CalendarEventRepository;
import com.ecm.core.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CalendarService {

    private final CalendarEventRepository calendarRepo;
    private final SiteRepository siteRepository;
    private final SecurityService securityService;
    private final ActivityEventListener activityEventListener;
    private final TenantWorkspaceScopeService tenantWorkspaceScopeService;

    @Transactional
    public CalendarEvent createEvent(String siteId, String title, String description,
                                      String location, LocalDateTime startDate, LocalDateTime endDate,
                                      boolean allDay, String recurrence) {
        validateEvent(title, startDate, endDate);
        String visibleSiteId = requireVisibleSiteId(siteId);
        CalendarEvent event = new CalendarEvent();
        event.setSiteId(visibleSiteId);
        event.setTitle(title.trim());
        event.setDescription(description);
        event.setLocation(location);
        event.setStartDate(startDate);
        event.setEndDate(endDate);
        event.setAllDay(allDay);
        event.setRecurrence(recurrence);
        CalendarEvent saved = calendarRepo.save(event);
        activityEventListener.postSiteActivity(
            "calendar.created", securityService.getCurrentUser(), visibleSiteId,
            Map.of("eventId", saved.getId().toString(), "title", saved.getTitle())
        );
        return saved;
    }

    @Transactional
    public CalendarEvent updateEvent(UUID eventId, String title, String description,
                                      String location, LocalDateTime startDate, LocalDateTime endDate,
                                      Boolean allDay, String recurrence) {
        CalendarEvent event = getEvent(eventId);
        requireAuthorOrAdmin(event);
        if (title != null) {
            String trimmed = title.trim();
            if (trimmed.isEmpty()) throw new IllegalArgumentException("Event title must not be blank");
            event.setTitle(trimmed);
        }
        if (description != null) event.setDescription(description);
        if (location != null) event.setLocation(location);
        if (startDate != null) event.setStartDate(startDate);
        if (endDate != null) event.setEndDate(endDate);
        if (allDay != null) event.setAllDay(allDay);
        if (recurrence != null) event.setRecurrence(recurrence.isBlank() ? null : recurrence);
        if (event.getStartDate() != null && event.getEndDate() != null && event.getEndDate().isBefore(event.getStartDate())) {
            throw new IllegalArgumentException("End date must be after start date");
        }
        CalendarEvent saved = calendarRepo.save(event);
        activityEventListener.postSiteActivity(
            "calendar.updated", securityService.getCurrentUser(), event.getSiteId(),
            Map.of("eventId", saved.getId().toString(), "title", saved.getTitle())
        );
        return saved;
    }

    @Transactional
    public void deleteEvent(UUID eventId) {
        CalendarEvent event = getEvent(eventId);
        requireAuthorOrAdmin(event);
        activityEventListener.postSiteActivity(
            "calendar.deleted", securityService.getCurrentUser(), event.getSiteId(),
            Map.of("eventId", event.getId().toString(), "title", event.getTitle())
        );
        calendarRepo.delete(event);
    }

    @Transactional(readOnly = true)
    public CalendarEvent getEvent(UUID eventId) {
        CalendarEvent event = calendarRepo.findById(eventId)
            .orElseThrow(() -> new NoSuchElementException("Calendar event not found: " + eventId));
        requireVisibleSiteId(event.getSiteId());
        return event;
    }

    @Transactional(readOnly = true)
    public Page<CalendarEvent> listEvents(String siteId, Pageable pageable) {
        String visibleSiteId = requireVisibleSiteId(siteId);
        return calendarRepo.findBySiteIdOrderByStartDateAsc(visibleSiteId, pageable);
    }

    @Transactional(readOnly = true)
    public List<CalendarEvent> getEventsByRange(String siteId, LocalDateTime from, LocalDateTime to) {
        String visibleSiteId = requireVisibleSiteId(siteId);
        return calendarRepo.findBySiteIdAndRange(visibleSiteId, from, to);
    }

    private void validateEvent(String title, LocalDateTime startDate, LocalDateTime endDate) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Event title is required");
        if (startDate == null) throw new IllegalArgumentException("Start date is required");
        if (endDate == null) throw new IllegalArgumentException("End date is required");
        if (endDate.isBefore(startDate)) throw new IllegalArgumentException("End date must be after start date");
    }

    private void requireAuthorOrAdmin(CalendarEvent event) {
        String currentUser = securityService.getCurrentUser();
        if (!currentUser.equals(event.getCreatedBy()) && !securityService.hasRole("ROLE_ADMIN")) {
            throw new SecurityException("Only event author or admin can modify this event");
        }
    }

    private String requireVisibleSiteId(String siteId) {
        String normalizedSiteId = siteId != null ? siteId.trim().toLowerCase(Locale.ROOT) : "";
        Site site = siteRepository.findBySiteIdIgnoreCaseAndDeletedFalse(normalizedSiteId)
            .orElseThrow(() -> new ResourceNotFoundException("Site not found: " + normalizedSiteId));
        String tenantRootPath = tenantWorkspaceScopeService.resolveCurrentTenantRootPath();
        if (tenantRootPath != null && !tenantWorkspaceScopeService.isSiteVisible(site.getSiteId(), tenantRootPath)) {
            throw new ResourceNotFoundException("Site not found: " + normalizedSiteId);
        }
        return site.getSiteId();
    }
}
