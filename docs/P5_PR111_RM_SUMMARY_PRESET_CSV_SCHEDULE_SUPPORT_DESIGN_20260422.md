# P5 PR111 RM Summary Preset CSV Schedule Support Design

## Scope

This slice closes the remaining capability gap for the two summary-only preset kinds:

- `ACTIVITY_FAMILY_HIGHLIGHTS`
- `ACTIVITY_FAMILY_MIX`

The goal is to let these preset kinds participate in:

- preset-row `Export CSV`
- preset-row `Schedule`
- backend preset `execute?format=csv`
- scheduled CSV delivery

without introducing a new endpoint, table, or migration.

## Problem

The shipped preset flow already supports CRUD, execute, schedule, deliver, and ledger behavior for report-style kinds, but the two summary-only kinds were still blocked by contract:

- frontend hid `Export CSV` and `Schedule`
- schedule dialog showed a hard warning and skipped schedule/history load
- backend preset execute rejected CSV
- backend scheduled delivery rejected the two summary-only kinds

This left preset behavior inconsistent with the broader RM page, where summary-oriented cards already reuse the existing activity-family report CSV semantics.

## Design

### Backend

#### `RecordsManagementController`

For preset execute:

- keep JSON behavior unchanged for summary-only kinds
- allow `format=csv` for `ACTIVITY_FAMILY_HIGHLIGHTS` and `ACTIVITY_FAMILY_MIX`
- map those CSV executions onto the existing `activity-family-report` CSV renderer

The mapping rule is:

- if preset params already include `from/to`, use them as the authoritative comparison range
- otherwise derive a current rolling range from:
  - `windowDays` for `ACTIVITY_FAMILY_HIGHLIGHTS`
  - `days` for `ACTIVITY_FAMILY_MIX`

That keeps old/manual presets working while preserving the existing family-report CSV shape.

#### `RmReportPresetDeliveryService`

For scheduled delivery:

- extend `supportsScheduledDelivery(...)` to include the two summary-only kinds
- extend `renderCsv(...)` with the same range-derivation rule as preset execute
- reuse the existing family-report CSV builder instead of creating a second summary-only delivery format

This keeps scheduled delivery aligned with shipped page-level export semantics.

### Frontend

#### `recordsManagementService.ts`

- widen `supportsReportPresetCsvDelivery(...)` to include the two summary-only kinds

This is safe only because backend CSV support is added in the same slice.

#### `RecordsManagementPage.tsx`

- stop assuming preset export/apply/range-label always come from raw `from/to`
- add additive range resolution for summary-only presets:
  - first use explicit `from/to`
  - otherwise derive the current rolling window from `windowDays` or `days`
- keep using the existing `activity-family-report` CSV route for preset-row export once a comparison range has been resolved

This avoids a new frontend endpoint and keeps preset-row export behavior aligned with current RM card export semantics.

#### `ScheduleReportPresetDialog.tsx`

- summary-only presets no longer go down the warning-only branch
- dialog now loads schedule state, history, and save/deliver controls for those kinds just like other CSV-capable presets

## Non-goals

- no new summary-specific CSV shape
- no new preset authoring UI for creating these two kinds directly
- no email delivery channel
- no new page-level operator surface beyond existing preset row + schedule dialog

## Expected Outcome

After this slice:

- summary-only presets can export CSV
- summary-only presets can be scheduled for CSV delivery
- summary-only presets can be manually delivered now
- existing preset table and schedule dialog behavior stays additive and backwards compatible
