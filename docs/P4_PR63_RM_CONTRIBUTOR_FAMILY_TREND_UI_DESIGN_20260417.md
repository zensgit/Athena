# P4 PR-63 RM Contributor Family Trend UI Design

## Scope

`PR-63` is a thin frontend-only consumption slice on top of:

- `GET /api/v1/records/activity-contributor-family-trend`

This slice adds a new RM admin-page card and reuses the existing `Records Audit` table for drilldown.

## Goals

- expose contributor-family trend on the RM page
- keep bucketed contributor trend semantics aligned with the backend payload
- route nested family actions into the existing audit evidence surface
- avoid creating a parallel analytics or evidence workflow

## UI

`ecm-frontend/src/pages/RecordsManagementPage.tsx`

- add `RM Contributor Family Trend`
- render:
  - bucket label
  - total count
  - active day count
  - `otherCount`
  - tracked contributor cards
  - nested family buttons per contributor
- reuse existing family label formatting

## Drilldown

Clicking a nested family button:

- keeps the current bucket `fromDay/toDay`
- sets:
  - `username`
  - `family`
- scrolls to the existing `Records Audit` table

## Non-Goals

- no backend change
- no new audit surface
- no export UI in this slice
- no charting beyond the existing bucket/card pattern
