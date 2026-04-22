# P5 PR-114 RM Preset Delivery Surface Refresh And Ledger Full-Stack Design

## Scope

- add a minimal page-level refresh chain after successful preset schedule save and manual delivery
- keep the change frontend-only
- extend the existing full-stack admin smoke so summary-only preset delivery is proven all the way into the page-level `Preset Delivery Ledger`

## Why This Slice

`PR-113` proved that a summary-only preset could be exported, scheduled, and delivered from the dialog on a live stack. The remaining operator gap was that the parent `Records Management` page did not actively refresh its preset list, scheduled-delivery health, or cross-preset ledger after those dialog actions.

That made the dialog locally correct but left the page-level operating surfaces stale until a manual refresh.

## Implementation

### Frontend runtime

- `ecm-frontend/src/components/records/ScheduleReportPresetDialog.tsx`
  - add `onChanged?: () => void | Promise<void>`
  - call it after successful `Save schedule`
  - call it after successful `Deliver now`
- `ecm-frontend/src/pages/RecordsManagementPage.tsx`
  - add a small `refreshPresetDeliverySurfaces()` callback
  - refresh only the affected surfaces:
    - `listReportPresets`
    - `getScheduledDeliveryTelemetry`
    - `listReportPresetExecutionLedger`
  - wire that callback into `ScheduleReportPresetDialog`

### Tests

- `ecm-frontend/src/components/records/ScheduleReportPresetDialog.test.tsx`
  - assert `onChanged` fires after schedule save
  - assert `onChanged` fires after manual delivery
- `ecm-frontend/src/pages/RecordsManagementPage.test.tsx`
  - assert page-level schedule save triggers a second load of preset list, scheduled-delivery telemetry, and preset delivery ledger
- `ecm-frontend/e2e/rm-report-preset-schedule.spec.ts`
  - keep the existing deliverable preset smoke green
  - extend the summary-only full-stack case:
    - deliver the preset
    - close the dialog
    - assert the delivered execution appears in the page-level `Preset Delivery Ledger`

## Non-Goals

- no backend endpoint change
- no migration
- no new preset kind
- no new scheduler behavior
- no email delivery channel
