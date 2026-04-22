# P5 PR-115 RM Preset Delivery Ledger Operator Mocked E2E Design

## Scope

- extend the shipped mocked RM preset delivery browser spec
- cover page-level operator surfaces that sit above the preset schedule dialog:
  - `Scheduled Delivery Health`
  - `Preset Delivery Ledger`
- keep the slice frontend-test-only
- do not add or change any backend endpoint, migration, or runtime UI logic

## Why This Slice

`PR-114` gave the summary-only preset delivery chain a real full-stack proof, including a refreshed page-level ledger row after delivery. The remaining regression gap was broader operator behavior around the ledger surface itself:

- cross-preset ledger aggregation
- result/trigger filtering
- CSV export of the filtered ledger
- zero-match recovery
- health-card counts after recent deliveries

These are high-signal behaviors for operators, but they do not require another runtime change.

## Implementation

Primary file:

- `ecm-frontend/e2e/rm-report-preset-schedule.mock.spec.ts`

Main additions:

- mock `/records/report-presets/telemetry`
- mock `/records/report-presets/executions`
- mock `/records/report-presets/executions/export`
- enrich mocked preset list responses with additive schedule metadata
- derive ledger entries from the same preset execution state used by the schedule dialog flow
- seed one historical failed scheduled execution so result/trigger filtering has a stable negative path
- after the existing summary-only schedule/deliver flow:
  - assert `Scheduled Delivery Health` chips
  - assert page-level `Preset Delivery Ledger` rows
  - apply `Failed + Scheduled` filters
  - export filtered ledger CSV
  - force zero-match via preset filter
  - recover with `Show all deliveries`

## Non-Goals

- no runtime code
- no additional full-stack spec
- no email delivery channel
