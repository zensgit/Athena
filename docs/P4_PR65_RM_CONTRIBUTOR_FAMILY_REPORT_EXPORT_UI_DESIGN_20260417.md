# P4 PR-65 RM Contributor Family Report Export UI Design

## Scope

`PR-65` is a thin frontend-only slice on top of:

- `GET /api/v1/records/activity-contributor-family-report`

This slice adds CSV export actions to the existing `RM Contributor Family Highlights` card and continues to reuse the existing backend report/export path.

## Goals

- expose current-window contributor-family CSV export on the RM page
- expose previous-window contributor-family CSV export on the RM page
- keep export semantics aligned with the existing contributor event-type export UI
- avoid introducing a new backend surface or a second evidence workflow

## Service

`ecm-frontend/src/services/recordsManagementService.ts`

- add `exportActivityContributorFamilyReportCsv(...)`
- reuse `api.downloadFile(...)`
- send:
  - `from`
  - `to`
  - `limit`
  - `format=csv`

## UI

`ecm-frontend/src/pages/RecordsManagementPage.tsx`

- extend `RM Contributor Family Highlights`
- add:
  - `Export current CSV`
  - `Export previous CSV`
- derive `from/to` from the existing current / previous highlight windows
- keep a small local in-flight state so only the active export scope is disabled

## UX

- current export success toast:
  - `Contributor family current window CSV exported`
- previous export success toast:
  - `Contributor family previous window CSV exported`
- export failure toast:
  - `Failed to export contributor family report`

## Non-Goals

- no backend change
- no new report endpoint
- no new evidence surface
- no change to existing audit drilldown semantics
