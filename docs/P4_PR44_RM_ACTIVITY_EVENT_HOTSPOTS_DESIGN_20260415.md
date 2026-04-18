# PR-44 RM Activity Event Hotspots Design

## Goal

Add a deeper RM analytics slice that surfaces the hottest exact RM event types over a recent window, so operators can see which specific RM actions dominate activity before drilling into the existing `Records Audit` evidence table.

## Scope

Backend:

- add `GET /api/v1/records/activity-event-types`
- aggregate existing `RM_%` audit data by exact `eventType`
- classify each event type into a best-effort activity family

Frontend:

- add `RM Activity Event Hotspots` card to the RM admin page
- keep load isolated from the rest of the RM dashboard
- drill from hotspot rows into the existing `Records Audit` table with `eventType + from + to`

## Backend Contract

`GET /api/v1/records/activity-event-types?days=28&limit=8`

Parameters:

- `days`
  - default: `28`
  - clamp: `7..90`
- `limit`
  - default: `8`
  - clamp: `1..20`

Window semantics:

- start-of-day of the oldest included day
- through `23:59:59` of today

Response shape:

- `days`
- `limit`
- `eventTypes`

Each row contains:

- `eventType`
- `family`
- `count`
- `lastEventTime`

Ordering:

1. `count` descending
2. `eventType` ascending

## Family Classification

`family` is best-effort and derived from the existing RM family model:

- `DECLARED`
  - `RM_RECORD_DECLARED`
- `UNDECLARED`
  - `RM_RECORD_UNDECLARED`
- `CATEGORY_ASSIGNED`
  - `RM_RECORD_CATEGORY_ASSIGNED`
- `GOVERNANCE_CHANGE`
  - events in `RM_TIMELINE_GOVERNANCE_EVENTS`
- `OTHER`
  - remaining `RM_%` events, such as blocked or exception-style RM audit rows

This keeps the new hotspot slice compatible with the existing timeline/highlights/breakdown/contributors family model while still exposing exact event-type hotspots.

## Frontend Behavior

The RM page adds an `RM Activity Event Hotspots` card that:

- lists the top recent RM event types
- shows:
  - humanized event label
  - raw `eventType`
  - family chip
  - count
  - last event timestamp
- provides `Review event audit`

Drilldown behavior:

- computes the same recent closed window semantics in local date terms
- pre-fills:
  - `eventType`
  - `from`
  - `to`
- reuses the existing `Records Audit` banner and table

## Files

Backend:

- `ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java`
- `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java`
- `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`
- `ecm-core/src/test/java/com/ecm/core/controller/RecordsManagementControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/service/RecordsManagementServiceTest.java`

Frontend:

- `ecm-frontend/src/types/index.ts`
- `ecm-frontend/src/services/recordsManagementService.ts`
- `ecm-frontend/src/services/recordsManagementService.test.ts`
- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

Documentation:

- `docs/P4_PR44_RM_ACTIVITY_EVENT_HOTSPOTS_DESIGN_20260415.md`
- `docs/P4_PR44_RM_ACTIVITY_EVENT_HOTSPOTS_VERIFICATION_20260415.md`
- `docs/P4_RECORDS_MANAGEMENT_EXECUTION_PLAN_20260414.md`
- `docs/P4_RECORDS_MANAGEMENT_ACCEPTANCE_20260414.md`

## Deferred

- richer event-type trend series over time
- family filter auto-sync from hotspot family chips
- backend-side humanized event labels
