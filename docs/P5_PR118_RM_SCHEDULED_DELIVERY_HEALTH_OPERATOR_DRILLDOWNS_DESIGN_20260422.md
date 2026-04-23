# P5 PR-118 RM Scheduled Delivery Health Operator Drilldowns Design

## Scope

- extend the shipped `Scheduled Delivery Health` card from preset-only drilldowns into delivery-result operator drilldowns
- add a page-level `Last 24h failed` ledger drilldown
- broaden browser-level coverage so the health card now proves both:
  - `Due now` -> preset table
  - `Last 24h failed` -> preset delivery ledger
- keep the slice frontend-only

## Why This Slice

`PR-117` already closed the real-stack gap for `Scheduled presets` telemetry and drilldown. The remaining high-value operator gap was that the health card still treated recent delivery outcomes as passive counters instead of actionable entry points.

This slice keeps the shipped backend telemetry contract intact and turns the failure counter into a practical operator shortcut.

## Implementation

Primary runtime file:

- `ecm-frontend/src/pages/RecordsManagementPage.tsx`

Main runtime additions:

- add a page-level preset-delivery-ledger ref
- add a `Last 24h failed` drilldown helper that:
  - clears existing ledger filters
  - applies `status=FAILED`
  - applies a rolling last-24h `from/to` window
  - resets pagination
  - scrolls the user to the ledger card
- also make `Last 24h success` symmetric for consistency

Primary regression files:

- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`
- `ecm-frontend/e2e/rm-report-preset-schedule.mock.spec.ts`

Main regression additions:

- page unit coverage for `Last 24h failed` -> ledger filter state
- mocked browser coverage for:
  - `Due now` health drilldown into preset table
  - `Last 24h failed` health drilldown into ledger filter/export flow

## Non-Goals

- no backend endpoint change
- no migration
- no live full-stack failure-path proof
- no new email/delivery channel
