# P4 PR-70 RM Activity Family Mix Report Export UI Design

## Scope

`PR-70` is a thin frontend-only slice on top of:

- `GET /api/v1/records/activity-family-report`

This slice adds CSV export actions to the existing `RM Activity Family Mix` card and continues to reuse the existing backend report/export path.

## Goals

- expose current-window activity family CSV export on the RM page
- expose previous-window activity family CSV export on the RM page
- keep export semantics aligned with the existing family highlights export UI
- avoid introducing a new backend surface or a second evidence workflow

## Service

`ecm-frontend/src/services/recordsManagementService.ts`

- reuse existing `exportActivityFamilyReportCsv(...)`
- keep transport on `api.downloadFile(...)`
- send:
  - `from`
  - `to`
  - `format=csv`

## UI

`ecm-frontend/src/pages/RecordsManagementPage.tsx`

- extend `RM Activity Family Mix`
- add:
  - `Export current CSV`
  - `Export previous CSV`
- derive current / previous closed windows from the existing rolling `days` horizon used by the family mix card
- keep a small local in-flight state so only the active export scope is disabled

## UX

- current export success toast:
  - `Activity family current window CSV exported`
- previous export success toast:
  - `Activity family previous window CSV exported`
- export failure toast:
  - `Failed to export activity family report`

## Non-Goals

- no backend change
- no new report endpoint
- no new evidence surface
- no change to existing audit drilldown semantics
