# P5 PR-99 RM Report Preset Delivery UI Hardening — Verification

## Date
2026-04-21

## Verified Scope

- summary-only presets no longer expose invalid table-level `Export CSV`
- shipped `PR-98` page wiring is now covered by a real schedule-button test
- `ScheduleReportPresetDialog` reloads backend schedule/execution state after save and manual delivery
- `supportsReportPresetCsvDelivery()` remains the single frontend gating helper for CSV-capable preset kinds

## Commands

### Targeted frontend tests

```bash
cd ecm-frontend && CI=true npm test -- --watchAll=false --runInBand --testPathPattern='recordsManagementService.test.ts|ScheduleReportPresetDialog.test.tsx|RecordsManagementPage.test.tsx'
```

Result:

- `PASS src/pages/RecordsManagementPage.test.tsx`
- `PASS src/components/records/ScheduleReportPresetDialog.test.tsx`
- `PASS src/services/recordsManagementService.test.ts`
- `Test Suites: 3 passed, 3 total`
- `Tests: 121 passed, 121 total`

### Frontend production build

```bash
cd ecm-frontend && npm run build
```

Result:

- build passed
- no new build blocker introduced

Known existing note:

- CRA still reports the long-standing bundle-size warning; this slice does not change that gate

### Static diff check

```bash
git diff --check
```

Result:

- passed

## Review Outcome

### PR-96 review

No blocking API-path mismatch was found in the shipped service layer itself.

Two real follow-ups were confirmed from the shipped chain and resolved in `PR-99`:

1. UI needed explicit kind gating so summary-only presets would not keep surfacing invalid CSV/delivery actions
2. dialog state needed an authoritative reload after save/deliver so backend-updated schedule chips/history would not drift

### Residual Risk

- folder target is still raw UUID entry, not a picker
- preset table still does not surface schedule status inline; operators must open the dialog
- no e2e case has been added for the full schedule workflow yet
