# P4 PR-36 Governed Operations Zero-Match Recover CTA Design

## Goal

Make zero-match governed-operations filters self-recoverable without changing any backend API.

## Scope

Frontend only:

- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

Documentation:

- `docs/P4_RECORDS_MANAGEMENT_EXECUTION_PLAN_20260414.md`
- `docs/P4_RECORDS_MANAGEMENT_ACCEPTANCE_20260414.md`

## Problem

`PR-35` exposed `0/N` matched counts in the summary bar, but the recent-jobs tables still fell back to plain empty-state copy. Admins could see that a scoped filter matched nothing, but the page did not give them a local recovery action where the failure was visible.

## Design

`PR-36` keeps the existing summary bar and adds scoped recovery at the table empty state.

Delivered behavior:

- when import filters produce zero matches against a non-empty recent-jobs telemetry window, the import table renders a warning alert instead of plain text
- when transfer filters produce zero matches against a non-empty recent-jobs telemetry window, the transfer table renders a warning alert instead of plain text
- each warning exposes a scope-local recover CTA:
  - `Show all imports`
  - `Show all transfers`
- each recover CTA clears only the corresponding scope filters and does not touch the other governed-operations scope

## Non-Goals

- no backend API or telemetry changes
- no new global alert layer
- no cross-page navigation
- no change to the existing summary-bar matched-count semantics

## Risks

- recover actions still operate only on the current recent-jobs telemetry window
- if more zero-match surfaces are added later, warning phrasing should stay consistent across import and transfer scopes

## Result

This slice closes the zero-match loop at the point of confusion: the table that lost all rows now also tells the admin how to recover the scope immediately.
