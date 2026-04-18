# P4 PR-32 Governed Operations Status Drilldown Design

## Goal

Turn the existing import and transfer status breakdowns into actionable exact-status filters without changing any backend API.

## Scope

Frontend only:

- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

Documentation:

- `docs/P4_RECORDS_MANAGEMENT_EXECUTION_PLAN_20260414.md`
- `docs/P4_RECORDS_MANAGEMENT_ACCEPTANCE_20260414.md`

## Problem

The RM admin page already exposes `Import Status Breakdown` and `Transfer Status Breakdown`, but those chips were still passive summary only. Admins could review recent jobs, failed jobs, and governance reasons, but they could not drill into a specific exact status such as `FAILED` or `FAILED / DISCONNECTED`.

## Design

`PR-32` adds local exact-status drilldown on top of the existing telemetry payload.

Delivered behavior:

- import status-breakdown chips are clickable
- transfer status-breakdown chips are clickable
- clicking an exact status bucket filters the corresponding recent-jobs table locally
- selected exact status is surfaced in the filter strip as `Status: ...`
- selected exact status shows a matched-jobs context alert with explicit clear action
- recent-job status labels are visually highlighted for the selected exact status
- existing reason drilldown remains intact
- generic `All / Active / Failed` chips clear the exact-status selection before applying the broader status filter

## Semantics

- import exact-status drilldown matches `job.status`
- transfer exact-status drilldown matches `formatTransferStatus(job)`
- exact-status drilldown is independent from governance-reason drilldown
- exact-status drilldown remains limited to the current telemetry window

## Non-Goals

- no backend API changes
- no new telemetry fields
- no cross-table synchronized status filter
- no full-history job browser

## Risks

- exact-status counts still reflect only the currently loaded recent jobs
- transfer exact-status labels remain tied to the current `workflow / transport` composite string
- if backend renames bucket labels, the frontend will follow those labels exactly

## Result

This slice makes the existing status breakdown actionable while preserving the current RM API contract and recent-job workflow.
