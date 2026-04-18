# P4 PR-31 Governed Operations Reason Drilldown Design

## Goal

Turn the existing import/transfer governance-reason breakdowns into actionable recent-job filters without changing any backend API.

## Scope

Frontend only:

- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

Documentation:

- `docs/P4_RECORDS_MANAGEMENT_EXECUTION_PLAN_20260414.md`
- `docs/P4_RECORDS_MANAGEMENT_ACCEPTANCE_20260414.md`

## Problem

The RM admin page already exposes:

- top import governance reasons
- top transfer governance reasons
- recent governed import jobs
- recent governed transfer jobs

But the reason breakdown is passive. Admins can see the dominant governance reasons, but they cannot immediately review which recent jobs contributed to a given reason.

## Design

`PR-31` adds local reason drilldown on top of the existing telemetry payload.

Delivered behavior:

- each import governance-reason chip is clickable
- each transfer governance-reason chip is clickable
- clicking a reason filters the corresponding recent-jobs table locally
- selected reason is surfaced in the recent-jobs filter strip as `Reason: ...`
- selected reason can be cleared from the filter strip
- existing status filters (`All / Active / Failed`) remain intact and combine with the selected reason

## Semantics

- no new backend query parameter
- no new telemetry endpoint
- filtering only applies to the recent jobs already loaded into the page
- import and transfer reason filters remain independent

## Non-Goals

- no cross-linking between import and transfer reason filters
- no full-history reason search
- no new charting or trend APIs

## Risks

- reason drilldown reflects only the current telemetry window, not the complete historical job set
- breakdown counts may exceed visible filtered rows if older jobs with the same reason are outside the recent-job window
- reason strings remain backend-authored opaque labels; the frontend does not normalize or group them

## Result

This slice makes the existing operations reason breakdown actionable while keeping the repository and API surface unchanged.
