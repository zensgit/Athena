# P5 PR-121 RM Scheduled Delivery Health Due Now Full-Stack Smoke Design

## Scope

- extend the live RM preset schedule full-stack smoke into the `Due now` operator path on `Scheduled Delivery Health`
- verify the health due-now drilldown reaches the page-level `Saved RM Report Presets` table
- harden page-level `Refresh` so preset/health/ledger delivery surfaces stay in sync after external schedule-state changes

## Why This Slice

`PR-117` proved the live `Scheduled presets` telemetry and drilldown. `PR-119` and `PR-120` then proved the live `Last 24h success` and `Last 24h failed` drilldowns into the page-level ledger. The remaining operator gap on the health card was whether a preset that is truly due now also drives the preset-table filter correctly on the current live stack.

This slice closes that gap with two minimal changes:

- a frontend hardening fix so page-level `Refresh` reloads `report presets`, `scheduled delivery health`, and the cross-preset delivery ledger together
- controlled environment setup for the live smoke: save a real schedule through the shipped UI, keep a second scheduled control preset in the future, and then force the primary preset's `next_run_at` into the past through the live Docker Postgres instance before using the page-level `Refresh` and `Due now` operator path

## Implementation

Primary files:

- `ecm-frontend/e2e/rm-report-preset-schedule.spec.ts`
- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`

Main additions:

- include `refreshPresetDeliverySurfaces()` in the page-level `Refresh` action
- add a page-level regression test that proves `Refresh` reloads preset list, scheduled-delivery health, and preset ledger together
- add a small test-only helper that uses `docker exec ... psql` against the live Postgres container to force one preset `next_run_at` into the past
- create a due-now target preset and a future scheduled control preset
- save the primary preset schedule through the real UI
- configure the control preset schedule through the real backend schedule API, leaving its `nextRunAt` in the future
- refresh the RM page and assert:
  - `Scheduled Delivery Health` shows non-zero `Due now`
  - clicking `Due now` drives `Saved RM Report Presets` into the page-level `dueNow` filter
  - the due-now preset remains visible
  - the future scheduled control preset is filtered out

## Non-Goals

- no backend endpoint change
- no scheduler-run proof
- no email delivery channel
