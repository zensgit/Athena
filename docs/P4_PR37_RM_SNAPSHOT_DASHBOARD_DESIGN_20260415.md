# P4 PR-37 RM Snapshot Dashboard Design

## Goal

Add a more scannable RM dashboard layer without changing backend contracts by turning existing summary and operations telemetry counts into lightweight snapshot visuals.

## Scope

Frontend only:

- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

Documentation:

- `docs/P4_RECORDS_MANAGEMENT_EXECUTION_PLAN_20260414.md`
- `docs/P4_RECORDS_MANAGEMENT_ACCEPTANCE_20260414.md`

## Problem

The RM admin page already had metrics, health signals, and drilldowns, but an admin still had to mentally assemble coverage and queue-health proportions from separate cards and tables. There was no compact visual layer showing the shape of current governance state at a glance.

## Design

`PR-37` adds two frontend-only snapshot cards near the top of the RM page.

Delivered behavior:

- `Declared Record Coverage Snapshot`
  - `Category Coverage`
    - `Categorized records`
    - `Uncategorized records`
  - `File Plan Coverage`
    - `Inside file plan`
    - `Outside file plan`
- `Governed Operations Snapshot`
  - `Import Queue Health`
    - `Active imports`
    - `Failed imports`
    - `Other imports`
  - `Transfer Queue Health`
    - `Active transfers`
    - `Failed transfers`
    - `Other transfers`

Each distribution is rendered as:

- scoped segment chips with counts
- a compact horizontal proportional bar
- a small total/context label

## Data Sources

No new API calls are added.

The snapshot uses:

- existing declared-record data already loaded for the RM page
- existing RM summary counts
- existing governed-operations telemetry counts

## Non-Goals

- no backend API or DTO changes
- no historical trend or time-series charts
- no new drilldown/filter semantics
- no changes to existing RM operations tables or recovery flows

## Risks

- queue-health visuals reflect current telemetry counts only, not historical trends
- `Other` operation segments depend on `total - active - failed`, so future backend count semantics must stay compatible

## Result

This slice adds a true dashboard layer to the RM page while staying inside the current API surface and without disturbing the existing drilldown workflow.
