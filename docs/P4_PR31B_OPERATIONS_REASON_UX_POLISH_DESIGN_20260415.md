# P4 PR-31B Governed Operations Reason UX Polish Design

## Goal

Make the existing RM governance-reason drilldown easier to understand without changing any backend API.

## Scope

Frontend only:

- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

Documentation:

- `docs/P4_RECORDS_MANAGEMENT_EXECUTION_PLAN_20260414.md`
- `docs/P4_RECORDS_MANAGEMENT_ACCEPTANCE_20260414.md`

## Problem

`PR-31` made import and transfer governance reasons clickable, but the page still relied on filter chips alone to explain what happened after selection. Admins could filter the recent-jobs tables, but the current reason context and matched-job count were not explicit enough.

## Design

`PR-31B` is pure frontend polish on top of the existing reason drilldown.

Delivered behavior:

- when an import reason is selected, the import recent-jobs section shows:
  - selected reason
  - matched recent-job count
  - explicit clear action
- when a transfer reason is selected, the transfer recent-jobs section shows the same context
- recent-job rows visually highlight the selected reason in the reasons column
- status filters and reason filters continue to compose locally

## Non-Goals

- no backend API changes
- no new telemetry fields
- no cross-linking between import and transfer reason filters
- no full-history job browser

## Risks

- matched-job counts still reflect only the current telemetry window
- one job may have multiple governance reasons, so highlighted reason is explanatory rather than exclusive

## Result

This slice makes the existing reason drilldown self-explanatory while keeping the repository and API surface unchanged.
