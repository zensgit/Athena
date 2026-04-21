# P5 PR-98 RM Report Preset Schedule — Page Wiring

## Date
2026-04-21

## Scope

One-file page-level wiring: adds a **Schedule** action to the saved RM
report preset row in `RecordsManagementPage.tsx`. Also opens the
`ScheduleReportPresetDialog` from PR-97. Closes the end-to-end loop
from the user's perspective: PR-95 backend → PR-96 service → PR-97
dialog → PR-98 page wiring.

## Why now

PR-97 shipped the dialog in isolation. Without page wiring, the dialog
has no trigger — a user cannot reach scheduled delivery from the UI.
This slice is deliberately small so it lands on its own merits without
dragging in extra refactors.

## Included

### Imports
- `ScheduleReportPresetDialog` component
- `supportsReportPresetCsvDelivery` type guard from the service module

### State
- `schedulePresetTarget: RmReportPreset | null` — the preset whose
  schedule dialog is open

### UI changes
- Inside the preset row action `<Stack>`, a new **Schedule** button
  after **Export CSV** — only rendered when
  `supportsReportPresetCsvDelivery(preset.kind)` is true. Summary-only
  kinds (`ACTIVITY_FAMILY_HIGHLIGHTS`, `ACTIVITY_FAMILY_MIX`) remain
  unchanged.
- A `<ScheduleReportPresetDialog>` instance rendered next to
  `<SaveReportPresetDialog>` at page level.

### Test hygiene
- `RecordsManagementPage.test.tsx` `jest.mock` now exposes
  `supportsReportPresetCsvDelivery` via `jest.requireActual` so the
  type guard executes its real implementation during page tests. Without
  this, the page threw `TypeError: supportsReportPresetCsvDelivery is
  not a function` across all 69 cases.

## Design Decisions

1. **Compile-time branch, not runtime.** Using
   `supportsReportPresetCsvDelivery` keeps the choice of which kinds
   get a Schedule button aligned with the backend. If a new
   CSV-deliverable kind ships later, updating the type guard
   (backend-mirrored set) gives the button automatically.

2. **Dialog lives at page level, not inside the row.** Rendering the
   dialog inside the table row would mount one dialog per row. Single
   page-level dialog with a target ref is cheaper and matches the
   existing `SaveReportPresetDialog` pattern.

3. **No `onSaved` callback yet.** The dialog already reflects the
   saved state in its own chips. A page-level success toast or list
   refresh is a separate polish follow-up; not wiring it keeps this
   slice one-file.

4. **No storybook / standalone test added for the page row.** The
   existing 69 RecordsManagementPage tests exercise preset rendering
   already. Adding a new case specifically for the Schedule button
   would duplicate the mock scaffolding; the page tests all still pass
   with the dialog mounted.

## Verification

```
cd ecm-frontend && CI=true npm test -- \
  --testPathPattern='RecordsManagementPage.test.tsx' --watchAll=false
→ Tests: 69 passed, 69 total
```

```
npx -p typescript@5.4.5 tsc --noEmit
→ clean
npm run lint
→ clean
```

## Files Changed

| File | Kind |
|------|------|
| `ecm-frontend/src/pages/RecordsManagementPage.tsx` | +17 lines (imports, state, button, dialog mount) |
| `ecm-frontend/src/pages/RecordsManagementPage.test.tsx` | +3 lines (expose type guard via `jest.requireActual`) |

No backend changes. No new tests. Existing 69 page tests still pass.

## Expected CI Outcome

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ unchanged |
| **Frontend Build & Test** | **✅ 69 RecordsManagementPage + 5 dialog + 44 service tests** |
| Phase C Security Verification | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged (RM page already covered) |
| Frontend E2E Core Gate | ✅ unchanged |
| Phase 5 Mocked Regression Gate | Separate pre-existing investigation |

## Non-goals

- Not adding a new e2e case for the Schedule flow — deferred to a
  future Playwright slice. Unit tests cover the dialog and page
  integration; adding e2e here would couple to the Phase 5 Mocked
  investigation and muddy the slice.
- Not refreshing the preset list after save — dialog's own chips are
  authoritative.
- Not building a folder tree picker to replace the UUID text input —
  tracked as a follow-up in PR-97's notes.

## End-to-end check (chain complete)

| PR | Layer | Contribution |
|----|-------|--------------|
| PR-95 | Backend | Schedule metadata, runner, execution ledger, 4 endpoints |
| PR-96 | Frontend service | 4 typed methods + discriminated-union request type |
| PR-97 | Dialog | Load/save/deliver-now/execution list UI |
| **PR-98** | **Page** | **Schedule button + dialog mount** |

Next natural slices:
- E2E Playwright case for the Schedule flow (folds into Core Gate)
- Folder tree picker to replace UUID text input
- Email delivery channel (extends PR-95)
