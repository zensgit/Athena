# P5 PR-117 RM Scheduled Delivery Health Full-Stack Smoke Design

## Scope

- extend the live RM preset schedule full-stack smoke into the page-level `Scheduled Delivery Health` card
- verify the shipped health telemetry against the real frontend + real backend stack
- verify the shipped `Scheduled presets` health drilldown into `Saved RM Report Presets`
- keep the slice test-only

## Why This Slice

`PR-109` already added the health telemetry card and preset-table drilldown, and later slices proved the same behavior with unit and mocked browser evidence. The remaining live-environment gap was whether the real stack still produced health telemetry and whether the real health-card drilldown actually narrowed the preset table.

This slice closes that gap without changing runtime behavior.

## Implementation

Primary file:

- `ecm-frontend/e2e/rm-report-preset-schedule.spec.ts`

Main additions inside the existing deliverable preset full-stack case:

- create one scheduled preset and one unscheduled control preset
- after successful `Save schedule` and `Deliver now`, assert the real `Scheduled Delivery Health` card shows positive scheduled and success counts
- click the real `Scheduled presets` chip
- assert the `Saved RM Report Presets` table switches into its scheduled filter state
- assert the scheduled preset remains visible while the unscheduled control preset drops out of the filtered table

## Non-Goals

- no runtime code change
- no backend endpoint change
- no new mocked coverage
- no `Due now` live-path coverage in this slice
- no email delivery channel
