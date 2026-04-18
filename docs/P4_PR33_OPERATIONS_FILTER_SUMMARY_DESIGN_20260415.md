# P4 PR-33 Governed Operations Filter Summary Design

## Goal

Make the current governed-operations drilldown state easier to understand by exposing a unified filter summary for active import and transfer filters.

## Scope

Frontend only:

- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

Documentation:

- `docs/P4_RECORDS_MANAGEMENT_EXECUTION_PLAN_20260414.md`
- `docs/P4_RECORDS_MANAGEMENT_ACCEPTANCE_20260414.md`

## Problem

`PR-30`, `PR-31`, `PR-31B`, and `PR-32` made failure, reason, and exact-status drilldown actionable, but admins still had to visually infer the total filter state by inspecting multiple local chips in separate import/transfer sections.

## Design

`PR-33` adds a compact global summary bar at the top of `Governed Operations`.

Delivered behavior:

- show `Selected operations filters` when any import/transfer filter is active
- display scoped filter chips for:
  - import queue
  - import exact status
  - import reason
  - transfer queue
  - transfer exact status
  - transfer reason
- expose:
  - `Clear import filters`
  - `Clear transfer filters`
  - `Clear all filters`

## Semantics

- summary reflects only the current local frontend drilldown state
- clearing by scope resets that scope without reloading telemetry
- `Clear all filters` resets both import and transfer scopes

## Non-Goals

- no backend API changes
- no telemetry refresh
- no new cross-page navigation

## Risks

- summary duplicates some information already shown in the per-table filter strips, so it must stay compact
- filter-summary chips reflect local UI state only, not a server-side saved query

## Result

This slice keeps the operations drilldown understandable as more local filters are added, without expanding the backend contract.
