# P4 PR-61 RM Contributor Event-Type Report Export UI Design

## Scope

`PR-61` is a thin frontend-only consumption slice on top of the existing backend endpoint:

- `GET /api/v1/records/activity-contributor-event-type-report`

This slice does not add new backend aggregation, schema, or repository work. It only exposes CSV export actions from the existing `RM Contributor Event-Type Highlights` card.

## Goals

- let RM admins export the current highlight window as CSV
- let RM admins export the previous highlight window as CSV
- keep export semantics aligned with the existing backend report contract
- avoid creating a second analytics surface or new filter model

## Design

### Service

`ecm-frontend/src/services/recordsManagementService.ts`

- add `exportActivityContributorEventTypeReportCsv(...)`
- reuse `api.downloadFile(...)`
- send:
  - `from`
  - `to`
  - `limit`
  - `eventTypeLimit`
  - `format=csv`
- derive a deterministic filename from the requested date range

### UI

`ecm-frontend/src/pages/RecordsManagementPage.tsx`

- add two export actions to the existing `RM Contributor Event-Type Highlights` card:
  - `Export current CSV`
  - `Export previous CSV`
- map those buttons directly to:
  - `currentWindow.fromDay/toDay`
  - `previousWindow.fromDay/toDay`
- preserve the existing current/previous audit drilldown actions unchanged
- track in-flight export scope locally so the clicked button can disable while exporting

### UX

- success toast:
  - `Contributor event-type current window CSV exported`
  - `Contributor event-type previous window CSV exported`
- failure toast:
  - `Failed to export contributor event-type report`

## Non-Goals

- no new RM dashboard card
- no new backend endpoint
- no JSON export surface in frontend
- no drilldown contract change
- no new audit filter semantics
