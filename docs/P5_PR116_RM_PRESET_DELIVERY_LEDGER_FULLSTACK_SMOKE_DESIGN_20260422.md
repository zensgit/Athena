# P5 PR-116 RM Preset Delivery Ledger Full-Stack Smoke Design

## Scope

- extend the live RM preset schedule full-stack smoke into the page-level `Preset Delivery Ledger`
- verify page-level operator actions against the real frontend + real backend stack:
  - preset-scoped ledger filtering
  - ledger CSV export
  - zero-match empty state
  - zero-match recovery
- keep the slice test-only

## Why This Slice

`PR-114` already proved that a successful preset delivery refreshes the parent ledger and surfaces the newly created execution row. The remaining high-value live-environment gap was whether the page-level operator actions around that ledger still work end-to-end on the real stack.

This slice closes that gap without any new runtime surface.

## Implementation

Primary file:

- `ecm-frontend/e2e/rm-report-preset-schedule.spec.ts`

Main additions inside the existing full-stack deliverable preset case:

- after successful delivery and dialog close, locate the page-level `Preset Delivery Ledger`
- apply the `Preset` filter for the just-created preset
- assert active filter chips and `Showing 1 of 1 deliveries`
- export ledger CSV and assert the real `/records/report-presets/executions/export` request contains the created `presetId`
- force a zero-match state with a future `From` value
- recover with `Show all deliveries`

## Non-Goals

- no runtime code change
- no backend endpoint change
- no new mocked coverage
- no email delivery channel
