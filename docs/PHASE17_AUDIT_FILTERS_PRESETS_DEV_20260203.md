# Phase 17 - Audit Filter Presets + Event Type Suggestions (Dev) - 2026-02-03

## Goal
Improve audit filtering UX by surfacing event type suggestions and aligning export/filter inputs with API presets.

## Scope
- Add API endpoint for audit event type counts.
- Use event type suggestions in Admin Dashboard filters.
- Provide user suggestions from dashboard top-users list.

## Implementation Notes
- `GET /api/v1/analytics/audit/event-types` returns event types with counts.
- Admin Dashboard now uses Autocomplete for user/event type filters (freeSolo).

## Files Updated
- `ecm-core/src/main/java/com/ecm/core/service/AnalyticsService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java`
- `ecm-frontend/src/pages/AdminDashboard.tsx`
