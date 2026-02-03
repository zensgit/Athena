# Phase 18 - Audit Category Filters + Preset Support (Dev) - 2026-02-03

## Goal
Enable audit filtering/export by category (NODE/VERSION/RULE/etc.) to align with Phase 1 P0 requirements.

## Scope
- Add `category` parameter support in audit search/export API.
- Expose category selection in Admin Dashboard.
- Keep existing user/event/time filters working.

## Implementation Notes
- Repository queries now apply category mapping using event type prefixes (NODE_, VERSION_, etc.).
- Controller parses `category` and passes to analytics service.
- Admin Dashboard adds a Category dropdown that feeds both search and export requests.

## Files Updated
- `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`
- `ecm-core/src/main/java/com/ecm/core/service/AnalyticsService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java`
- `ecm-frontend/src/pages/AdminDashboard.tsx`
