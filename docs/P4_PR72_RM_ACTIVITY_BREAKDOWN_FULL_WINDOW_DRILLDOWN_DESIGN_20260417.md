# P4 PR-72 RM Activity Breakdown Full-Window Drilldown Design

## Scope

`PR-72` is a thin frontend-only slice on top of the existing:

- `GET /api/v1/records/activity-breakdown`
- `GET /api/v1/records/audit`

This slice adds a card-level shortcut on `RM Activity Breakdown` and continues to reuse the existing `Records Audit` evidence table.

## Goals

- expose a full-window breakdown drilldown without requiring the user to click individual buckets
- keep the range semantics aligned with the existing bucket data already shown on the page
- avoid introducing a second audit surface or a new backend endpoint

## UI

`ecm-frontend/src/pages/RecordsManagementPage.tsx`

- extend `RM Activity Breakdown`
- add:
  - `Review full breakdown audit`
- show the shortcut only when breakdown buckets exist

## Drilldown Contract

- clicking the shortcut routes to the existing `Records Audit` section
- drilldown pre-fills:
  - `from` = first visible bucket `fromDay` at `00:00:00`
  - `to` = last visible bucket `toDay` at `23:59:59`
- no extra family, event-type, or username filters are applied

## Non-Goals

- no backend change
- no CSV export in this slice
- no new evidence surface
- no change to row-level bucket drilldown semantics
