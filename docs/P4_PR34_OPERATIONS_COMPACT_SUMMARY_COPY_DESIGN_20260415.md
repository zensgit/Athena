# P4 PR-34 Governed Operations Compact Summary Copy Design

## Goal

Make the `Selected operations filters` summary more compact without changing any filter semantics, clear actions, or backend API.

## Scope

Frontend only:

- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

Documentation:

- `docs/P4_RECORDS_MANAGEMENT_EXECUTION_PLAN_20260414.md`
- `docs/P4_RECORDS_MANAGEMENT_ACCEPTANCE_20260414.md`

## Problem

`PR-33` introduced a useful unified filter summary, but multi-condition scoped summaries still used a verbose join pattern. The page needed a lighter summary treatment without breaking existing single-condition strings or clear actions.

## Design

`PR-34` keeps the current filter summary structure and only tightens the scoped summary copy.

Delivered behavior:

- keep the title `Selected operations filters`
- keep `Clear import filters`, `Clear transfer filters`, and `Clear all filters`
- keep single-condition strings unchanged, for example:
  - `Import: FAILED`
  - `Transfer: TARGET_OUTSIDE_FILE_PLAN`
- compress multi-condition scoped summaries by using a tighter separator between active conditions

## Non-Goals

- no backend API changes
- no new filter state
- no change to existing clear behavior
- no summary redesign beyond copy compaction

## Risks

- tests already bind to single-condition strings, so copy changes must preserve those exact cases
- the compact separator must not blur the distinction between queue, exact-status, and reason conditions

## Result

This slice improves readability of multi-condition summaries while keeping the current drilldown behavior and test surface stable.
