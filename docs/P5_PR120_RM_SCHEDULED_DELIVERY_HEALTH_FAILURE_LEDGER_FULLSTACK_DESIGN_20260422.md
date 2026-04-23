# P5 PR-120 RM Scheduled Delivery Health Failure Ledger Full-Stack Design

## Scope

- extend the live RM preset schedule full-stack smoke into the `Last 24h failed` operator path on `Scheduled Delivery Health`
- verify the health-failure drilldown reaches the page-level `Preset Delivery Ledger`
- keep the slice test-only

## Why This Slice

`PR-119` already proved the live `Last 24h success` operator path. The remaining high-value live gap on the health card was whether a real failed delivery also shows up in telemetry and drills into the ledger correctly.

This slice closes that gap with an environment-controlled failure path: configure scheduled delivery successfully, then overwrite the preset schedule with a non-existent `deliveryFolderId` before triggering `Deliver now`.

## Implementation

Primary file:

- `ecm-frontend/e2e/rm-report-preset-schedule.spec.ts`

Main additions:

- reuse the existing best-effort node cleanup helper for live E2E teardown
- create a dedicated deliverable preset and folder
- save schedule normally
- overwrite the preset schedule through the real API with a non-existent target folder id before `Deliver now`
- verify the resulting execution becomes `FAILED`
- close the dialog and assert:
  - `Last 24h failed` becomes non-zero
  - clicking `Last 24h failed` produces:
    - `Active ledger filters`
    - `Result: Failed`
    - `From:` / `To:`
  - ledger CSV export goes out with `status=FAILED`

## Non-Goals

- no runtime code change
- no backend endpoint change
- no scheduled runner proof
- no due-now live proof
- no email delivery channel
