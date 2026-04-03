package com.ecm.core.service;

import com.ecm.core.entity.CalendarEvent;
import com.ecm.core.repository.CalendarEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    @Mock private CalendarEventRepository calendarRepo;
    @Mock private SecurityService securityService;
    @Mock private ActivityEventListener activityEventListener;

    private CalendarService service;

    @BeforeEach
    void setUp() {
        service = new CalendarService(calendarRepo, securityService, activityEventListener);
    }

    @Nested
    @DisplayName("createEvent")
    class Create {

        @Test
        @DisplayName("creates event and posts calendar.created activity")
        void createsEvent() {
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(calendarRepo.save(any())).thenAnswer(inv -> {
                CalendarEvent e = inv.getArgument(0);
                e.setId(UUID.randomUUID());
                return e;
            });
            LocalDateTime start = LocalDateTime.of(2026, 4, 10, 9, 0);
            LocalDateTime end = LocalDateTime.of(2026, 4, 10, 17, 0);

            CalendarEvent e = service.createEvent("finance", "Q1 Review", "Board meeting", "Room A", start, end, false, null);

            assertEquals("finance", e.getSiteId());
            assertEquals("Q1 Review", e.getTitle());
            assertEquals(start, e.getStartDate());
            assertEquals(end, e.getEndDate());
            verify(activityEventListener).postSiteActivity(eq("calendar.created"), eq("alice"), eq("finance"), anyMap());
        }

        @Test
        @DisplayName("rejects blank title")
        void rejectsBlankTitle() {
            assertThrows(IllegalArgumentException.class,
                () -> service.createEvent("finance", "", null, null,
                    LocalDateTime.now(), LocalDateTime.now().plusHours(1), false, null));
        }

        @Test
        @DisplayName("rejects end before start")
        void rejectsEndBeforeStart() {
            LocalDateTime start = LocalDateTime.of(2026, 4, 10, 17, 0);
            LocalDateTime end = LocalDateTime.of(2026, 4, 10, 9, 0);

            assertThrows(IllegalArgumentException.class,
                () -> service.createEvent("finance", "Bad", null, null, start, end, false, null));
        }

        @Test
        @DisplayName("rejects null dates")
        void rejectsNullDates() {
            assertThrows(IllegalArgumentException.class,
                () -> service.createEvent("finance", "No Date", null, null, null, null, false, null));
        }
    }

    @Nested
    @DisplayName("updateEvent")
    class Update {

        @Test
        @DisplayName("author can update and posts calendar.updated activity")
        void authorUpdates() {
            CalendarEvent event = event("alice");
            when(calendarRepo.findById(event.getId())).thenReturn(Optional.of(event));
            when(securityService.getCurrentUser()).thenReturn("alice");
            when(calendarRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            CalendarEvent result = service.updateEvent(event.getId(), "Updated Title", null, "Room B", null, null, null, null);

            assertEquals("Updated Title", result.getTitle());
            assertEquals("Room B", result.getLocation());
            verify(activityEventListener).postSiteActivity(eq("calendar.updated"), eq("alice"), eq("finance"), anyMap());
        }

        @Test
        @DisplayName("non-author non-admin cannot update")
        void nonAuthorCannotUpdate() {
            CalendarEvent event = event("alice");
            when(calendarRepo.findById(event.getId())).thenReturn(Optional.of(event));
            when(securityService.getCurrentUser()).thenReturn("bob");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

            assertThrows(SecurityException.class,
                () -> service.updateEvent(event.getId(), "Hacked", null, null, null, null, null, null));
        }

        @Test
        @DisplayName("rejects blank title on update")
        void rejectsBlankTitle() {
            CalendarEvent event = event("alice");
            when(calendarRepo.findById(event.getId())).thenReturn(Optional.of(event));
            when(securityService.getCurrentUser()).thenReturn("alice");

            assertThrows(IllegalArgumentException.class,
                () -> service.updateEvent(event.getId(), "  ", null, null, null, null, null, null));
        }
    }

    @Nested
    @DisplayName("deleteEvent")
    class Delete {

        @Test
        @DisplayName("author can delete")
        void authorDeletes() {
            CalendarEvent event = event("alice");
            when(calendarRepo.findById(event.getId())).thenReturn(Optional.of(event));
            when(securityService.getCurrentUser()).thenReturn("alice");

            service.deleteEvent(event.getId());
            verify(calendarRepo).delete(event);
        }

        @Test
        @DisplayName("non-author non-admin cannot delete")
        void nonAuthorCannotDelete() {
            CalendarEvent event = event("alice");
            when(calendarRepo.findById(event.getId())).thenReturn(Optional.of(event));
            when(securityService.getCurrentUser()).thenReturn("bob");
            when(securityService.hasRole("ROLE_ADMIN")).thenReturn(false);

            assertThrows(SecurityException.class, () -> service.deleteEvent(event.getId()));
        }
    }

    private CalendarEvent event(String author) {
        CalendarEvent e = new CalendarEvent();
        e.setId(UUID.randomUUID());
        e.setSiteId("finance");
        e.setTitle("Meeting");
        e.setStartDate(LocalDateTime.of(2026, 4, 10, 9, 0));
        e.setEndDate(LocalDateTime.of(2026, 4, 10, 17, 0));
        e.setCreatedBy(author);
        return e;
    }
}
