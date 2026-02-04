# Phase 41 - Audit Report Summary Development (2026-02-04)

## Scope
Provide a summary report of audit activity by category over a configurable time window.

## Backend Changes
- Added audit report aggregation to `AnalyticsService` using event-type counts.
- Added repository query `countByEventTypeSince` for time-windowed aggregation.
- Added endpoint `GET /api/v1/analytics/audit/report?days=30` returning:
  - `windowDays`
  - `totalEvents`
  - `countsByCategory` (NODE/VERSION/RULE/etc.)

## Frontend Changes
- Admin dashboard now displays audit summary chips (last N days + per-category counts).

## Files Updated
- `ecm-core/src/main/java/com/ecm/core/service/AnalyticsService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java`
- `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`
- `ecm-frontend/src/pages/AdminDashboard.tsx`
