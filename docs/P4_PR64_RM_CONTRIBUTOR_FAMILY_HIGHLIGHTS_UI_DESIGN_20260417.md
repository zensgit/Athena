# P4 PR-64 RM Contributor Family Highlights UI Design

## Scope

`PR-64` is a thin frontend-only consumption slice on top of:

- `GET /api/v1/records/activity-contributor-family-highlights`

This slice adds a new RM admin-page card and continues to reuse the existing `Records Audit` table for drilldown.

## Goals

- expose contributor-family current/previous window comparison on the RM page
- keep interaction semantics aligned with contributor event-type highlights
- route nested family actions into the existing audit evidence surface
- avoid introducing a second comparison or evidence workflow

## UI

`ecm-frontend/src/pages/RecordsManagementPage.tsx`

- add `RM Contributor Family Highlights`
- render:
  - current / previous window labels
  - contributor-level current / previous totals
  - signed delta
  - nested family rows
  - current / previous audit drilldown actions

## Drilldown

Clicking a nested family current/previous action:

- keeps the current or previous window `fromDay/toDay`
- sets:
  - `username`
  - `family`
- scrolls to the existing `Records Audit` table

## Non-Goals

- no backend change
- no export UI in this slice
- no new evidence surface
- no new report or trend API
