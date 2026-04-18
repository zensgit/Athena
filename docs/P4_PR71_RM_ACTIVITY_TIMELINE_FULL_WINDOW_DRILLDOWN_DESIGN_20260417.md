# P4 PR-71 RM Activity Timeline Full-Window Drilldown Design

## Scope

`PR-71` is a thin frontend-only slice on top of the existing:

- `GET /api/v1/records/activity-timeline`
- `GET /api/v1/records/audit`

This slice adds a card-level shortcut on `RM Activity Timeline` and continues to reuse the existing `Records Audit` evidence table.

## Goals

- expose a full-window timeline drilldown without requiring the user to click individual day rows
- keep the range semantics aligned with the existing timeline data already shown on the page
- avoid introducing a second audit surface or a new backend endpoint

## UI

`ecm-frontend/src/pages/RecordsManagementPage.tsx`

- extend `RM Activity Timeline`
- add:
  - `Review full timeline audit`
- show the shortcut only when timeline points exist

## Drilldown Contract

- clicking the shortcut routes to the existing `Records Audit` section
- drilldown pre-fills:
  - `from` = first visible timeline day at `00:00:00`
  - `to` = last visible timeline day at `23:59:59`
- no extra family, event-type, or username filters are applied

## Non-Goals

- no backend change
- no CSV export in this slice
- no new evidence surface
- no change to row-level day drilldown semantics
