# P5 PR-119 RM Scheduled Delivery Health Success Ledger Full-Stack Design

## Scope

- extend the live RM preset schedule full-stack smoke into the `Last 24h success` operator path on `Scheduled Delivery Health`
- verify the health-success drilldown reaches the page-level `Preset Delivery Ledger`
- keep the slice test-only

## Why This Slice

`PR-117` already proved the live `Scheduled presets` telemetry and drilldown. `PR-118` then proved browser-level operator semantics for the health card in unit and mocked coverage. The remaining stable live-environment gap was whether the real `Last 24h success` counter also drives the ledger correctly after a real delivery.

This slice closes that gap with the most reliable live signal in the current stack: a freshly successful manual delivery.

## Implementation

Primary file:

- `ecm-frontend/e2e/rm-report-preset-schedule.spec.ts`

Main additions inside the existing deliverable preset full-stack case:

- after successful `Deliver now` and dialog close, click the real `Last 24h success` chip
- assert the page-level `Preset Delivery Ledger` enters an active filtered state
- assert the ledger shows:
  - `Result: Successful`
  - `From:` / `To:` chips
  - the delivered filename
- clear the ledger filters and continue with the existing preset-table and ledger operator flow

## Non-Goals

- no runtime code change
- no backend endpoint change
- no live `Due now` drilldown proof
- no live failure-path drilldown proof
- no email delivery channel
