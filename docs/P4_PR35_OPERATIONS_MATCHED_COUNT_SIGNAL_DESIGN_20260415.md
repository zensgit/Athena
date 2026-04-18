# P4 PR-35 Governed Operations Matched Count Signal Design

## Goal

Expose scope-level matched-count signals in `Selected operations filters` so admins can see import/transfer hit counts at a glance without changing any backend API.

## Scope

Frontend only:

- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

Documentation:

- `docs/P4_RECORDS_MANAGEMENT_EXECUTION_PLAN_20260414.md`
- `docs/P4_RECORDS_MANAGEMENT_ACCEPTANCE_20260414.md`

## Problem

`PR-33` and `PR-34` made the active filter state easier to read, but the summary bar still did not show scoped hit counts directly. Admins had to infer counts from the per-table copy lower on the page.

## Design

`PR-35` adds a lightweight matched-count signal next to each active scoped summary.

Delivered behavior:

- when import filters are active, summary shows `Import matches X/N`
- when transfer filters are active, summary shows `Transfer matches X/N`
- zero-match scopes show `0/N` directly in the summary while preserving current per-table empty-state text
- no new alert layer is added; the signal remains inside the existing summary bar

## Non-Goals

- no backend API changes
- no change to existing filter semantics
- no change to current per-table empty-state copy
- no cross-page navigation

## Risks

- matched counts still reflect only the current recent-job telemetry window
- zero-match signals can duplicate per-table empty-state messaging if over-emphasized

## Result

This slice makes the current operations drilldown more legible at the summary level without expanding the backend contract.
