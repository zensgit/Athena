# Phase 369AE — Calendar Backbone

> **Scope**: Site calendar events with range query, activity integration, CalendarPage
> **Date**: 2026-04-03

---

## 1. What Was Built

### Backend

**CalendarEvent entity** — siteId, title, description, location, startDate, endDate, allDay, recurrence.

**CalendarService** — full CRUD with validation and activity integration:

| Method | Description |
|--------|-------------|
| `createEvent(...)` | Validates title/dates/range; posts `calendar.created` activity |
| `updateEvent(...)` | Author/admin; blank-title guard; end-before-start guard; posts `calendar.updated` |
| `deleteEvent(...)` | Author/admin |
| `getEvent(id)` | Single lookup |
| `listEvents(siteId, pageable)` | All events sorted by startDate |
| `getEventsByRange(siteId, from, to)` | Events overlapping [from, to] range |

**CalendarController** — 6 site-scoped endpoints:

| Method | Path | Description |
|--------|------|-------------|
| GET | `/sites/{siteId}/calendar/events` | List (paginated) |
| GET | `/sites/{siteId}/calendar/events/range?from=&to=` | Range query |
| POST | `/sites/{siteId}/calendar/events` | Create |
| GET | `/sites/{siteId}/calendar/events/{id}` | Get |
| PUT | `/sites/{siteId}/calendar/events/{id}` | Update |
| DELETE | `/sites/{siteId}/calendar/events/{id}` | Delete |

**DB Migration 054** — `calendar_events` table with 3 indexes.

### Frontend

**calendarService.ts** — 6 methods.

**CalendarPage.tsx** — range-filtered event list:
- From/To datetime pickers with Apply button
- Event cards: title, date range, location, all-day chip
- Delete button gated by author/admin
- New Event dialog: title, description, location, start/end datetime

**SitesPage.tsx** — "Open Calendar" link in site detail panel.

**Routing** — `/sites/:siteId/calendar` route.

### Activity integration

| Action | Activity Type | Routed to |
|--------|-------------|-----------|
| Create | `calendar.created` | Site followers |
| Update | `calendar.updated` | Site followers |

## 2. Files Created

| File | Purpose |
|------|---------|
| `entity/CalendarEvent.java` | Calendar event entity |
| `repository/CalendarEventRepository.java` | Queries + range query |
| `service/CalendarService.java` | CRUD + activity |
| `controller/CalendarController.java` | 6 endpoints |
| `db/changelog/changes/054-create-calendar-events-table.xml` | Migration |
| `services/calendarService.ts` | Frontend service |
| `pages/CalendarPage.tsx` | Calendar page |
| `test/service/CalendarServiceTest.java` | 9 focused tests |

## 3. Files Modified

| File | Change |
|------|--------|
| `App.tsx` | +CalendarPage import + route |
| `pages/SitesPage.tsx` | +"Open Calendar" card |
| `db/changelog/db.changelog-master.xml` | +054 |
