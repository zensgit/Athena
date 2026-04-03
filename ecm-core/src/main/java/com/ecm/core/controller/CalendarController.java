package com.ecm.core.controller;

import com.ecm.core.entity.CalendarEvent;
import com.ecm.core.service.CalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/sites/{siteId}/calendar", "/api/v1/sites/{siteId}/calendar"})
@Tag(name = "Calendar", description = "Site calendar events")
public class CalendarController {

    private final CalendarService calendarService;

    @GetMapping("/events")
    @Operation(summary = "List calendar events for a site")
    public ResponseEntity<Page<CalendarEventDto>> listEvents(@PathVariable String siteId, Pageable pageable) {
        return ResponseEntity.ok(calendarService.listEvents(siteId, pageable).map(CalendarEventDto::from));
    }

    @GetMapping("/events/range")
    @Operation(summary = "Get events in a date range")
    public ResponseEntity<List<CalendarEventDto>> getEventsByRange(
            @PathVariable String siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(
            calendarService.getEventsByRange(siteId, from, to).stream().map(CalendarEventDto::from).toList()
        );
    }

    @PostMapping("/events")
    @Operation(summary = "Create a calendar event")
    public ResponseEntity<CalendarEventDto> createEvent(
            @PathVariable String siteId,
            @RequestBody CreateCalendarEventRequest request) {
        CalendarEvent event = calendarService.createEvent(
            siteId, request.title(), request.description(), request.location(),
            request.startDate(), request.endDate(), request.allDay() != null && request.allDay(),
            request.recurrence()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(CalendarEventDto.from(event));
    }

    @GetMapping("/events/{eventId}")
    @Operation(summary = "Get a calendar event")
    public ResponseEntity<CalendarEventDto> getEvent(@PathVariable String siteId, @PathVariable UUID eventId) {
        return ResponseEntity.ok(CalendarEventDto.from(calendarService.getEvent(eventId)));
    }

    @PutMapping("/events/{eventId}")
    @Operation(summary = "Update a calendar event")
    public ResponseEntity<CalendarEventDto> updateEvent(
            @PathVariable String siteId,
            @PathVariable UUID eventId,
            @RequestBody UpdateCalendarEventRequest request) {
        CalendarEvent event = calendarService.updateEvent(
            eventId, request.title(), request.description(), request.location(),
            request.startDate(), request.endDate(), request.allDay(), request.recurrence()
        );
        return ResponseEntity.ok(CalendarEventDto.from(event));
    }

    @DeleteMapping("/events/{eventId}")
    @Operation(summary = "Delete a calendar event")
    public ResponseEntity<Void> deleteEvent(@PathVariable String siteId, @PathVariable UUID eventId) {
        calendarService.deleteEvent(eventId);
        return ResponseEntity.noContent().build();
    }

    // ---- DTOs ---------------------------------------------------------------

    public record CreateCalendarEventRequest(
        String title, String description, String location,
        LocalDateTime startDate, LocalDateTime endDate,
        Boolean allDay, String recurrence
    ) {}

    public record UpdateCalendarEventRequest(
        String title, String description, String location,
        LocalDateTime startDate, LocalDateTime endDate,
        Boolean allDay, String recurrence
    ) {}

    public record CalendarEventDto(
        UUID id, String siteId, String title, String description, String location,
        LocalDateTime startDate, LocalDateTime endDate, boolean allDay, String recurrence,
        String createdBy, LocalDateTime createdDate
    ) {
        static CalendarEventDto from(CalendarEvent e) {
            return new CalendarEventDto(
                e.getId(), e.getSiteId(), e.getTitle(), e.getDescription(), e.getLocation(),
                e.getStartDate(), e.getEndDate(), e.isAllDay(), e.getRecurrence(),
                e.getCreatedBy(), e.getCreatedDate()
            );
        }
    }
}
