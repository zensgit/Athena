# P4 PR-67 RM Activity Event Type Report Export UI Design

## Scope

`PR-67` is a thin frontend-only slice on top of:

- `GET /api/v1/records/activity-event-type-report`

This slice adds CSV export actions to the existing `RM Activity Event Hotspots` card and continues to reuse the existing backend report/export path.

## Goals

- expose current-window activity event-type CSV export on the RM page
- expose previous-window activity event-type CSV export on the RM page
- keep export semantics aligned with the existing family and contributor export UI
- avoid introducing a new backend surface or a second evidence workflow

## Service

`ecm-frontend/src/services/recordsManagementService.ts`

- add `exportActivityEventTypeReportCsv(...)`
- reuse `api.downloadFile(...)`
- send:
  - `from`
  - `to`
  - `limit`
  - `format=csv`

## UI

`ecm-frontend/src/pages/RecordsManagementPage.tsx`

- extend `RM Activity Event Hotspots`
- add:
  - `Export current CSV`
  - `Export previous CSV`
- derive current / previous closed windows from the existing rolling `days` horizon used by the hotspots card
- keep a small local in-flight state so only the active export scope is disabled

## UX

- current export success toast:
  - `Activity event-type current window CSV exported`
- previous export success toast:
  - `Activity event-type previous window CSV exported`
- export failure toast:
  - `Failed to export activity event-type report`

## Non-Goals

- no backend change
- no new report endpoint
- no new evidence surface
- no change to existing audit drilldown semantics
