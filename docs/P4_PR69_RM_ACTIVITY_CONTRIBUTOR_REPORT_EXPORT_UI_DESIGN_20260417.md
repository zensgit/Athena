# P4 PR-69 RM Activity Contributor Report Export UI Design

> Superseded: `PR-69` is retained only for traceability. The shipped contributor-report export UI is authored and accepted under `PR-68`.

## Scope

`PR-69` was drafted as a thin frontend-only slice on top of:

- `GET /api/v1/records/activity-contributor-report`

This document is retained only as a historical duplicate. No distinct product delta exists beyond `PR-68`.

## Goals

- expose current-window activity contributor CSV export on the RM page
- expose previous-window activity contributor CSV export on the RM page
- keep export semantics aligned with the existing family and event-type export UI
- avoid introducing a new backend surface or a second evidence workflow

## Service

`ecm-frontend/src/services/recordsManagementService.ts`

- add `exportActivityContributorReportCsv(...)`
- reuse `api.downloadFile(...)`
- send:
  - `from`
  - `to`
  - `limit`
  - `format=csv`

## UI

`ecm-frontend/src/pages/RecordsManagementPage.tsx`

- extend `RM Activity Contributors`
- add:
  - `Export current CSV`
  - `Export previous CSV`
- derive current / previous closed windows from the existing rolling `days` horizon used by the contributors card
- keep a small local in-flight state so only the active export scope is disabled

## UX

- current export success toast:
  - `Activity contributor current window CSV exported`
- previous export success toast:
  - `Activity contributor previous window CSV exported`
- export failure toast:
  - `Failed to export activity contributor report`

## Non-Goals

- no backend change
- no new report endpoint
- no new evidence surface
- no change to existing audit drilldown semantics
