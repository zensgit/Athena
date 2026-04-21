# P5 PR-106 RM Delivery Folder Tree Picker — Dev & Verification

## Date
2026-04-21

## Scope

Closes the "still UUID text input" follow-up explicitly flagged in the
PR-97, PR-98, and PR-101→104 non-goals. Swap the delivery folder UUID
text field on `ScheduleReportPresetDialog` for the existing shipped
`FolderTree` component (variant="picker"), matching the pattern used
by `MoveCopyDialog`. Frontend-only, additive, no API change.

## Why now

- The Codex auto-delivery pipeline is idle this turn (no pending
  changes in the working tree).
- PR-95 → PR-105 chain is shipped and CI-verified (5/6 green per
  commit — Phase 5 Mocked remains pre-existing cancelled).
- The UUID text input was the single operator friction point left in
  the schedule flow. Replacing it with the existing folder-picker
  component is a small, contained, high-leverage improvement.

## Design

### Why FolderTree variant="picker"
The `FolderTree` component already ships with a `variant="picker"`
mode (used by `MoveCopyDialog`) that:
- Lazy-loads children from the real folder service
- Highlights the `selectedNodeId`
- Fires `onNodeSelect` with `{ id, name, nodeType }` on click

Reusing it keeps this slice one-component-swap instead of building a
new folder browser. It also inherits permission + tenancy scoping
for free.

### UI change
- Old: single `TextField label="Delivery folder ID"` — operator had
  to obtain and paste a UUID.
- New: a bordered `<Box>` containing `<FolderTree/>`, height 260px,
  with a caption below ("Selected: <folder name>" after pick, or
  guidance text before).

### Request shape
Unchanged. `updateReportPresetSchedule` still sends
`{ enabled, cronExpression, timezone, deliveryFolderId }`. Only the
input mechanism changed.

### Validation
`folderError` semantics preserved — when `enabled=true` and no folder
is picked, Save still surfaces "Delivery folder id is required when
schedule is enabled", now rendered as red border + red caption on the
tree container rather than on a text input.

### Non-folder guard
Clicking a non-FOLDER node in the tree (a document, site root, etc.)
is silently ignored, matching the behavior in `MoveCopyDialog`. This
eliminates a whole class of "I picked a document by accident" bugs
that the UUID input could not catch.

## Testing strategy

Two test files mock `FolderTree`:

1. **`ScheduleReportPresetDialog.test.tsx`** — mocks the relative
   path `'../browser/FolderTree'`. Exposes two buttons in the mock:
   "Pick folder-42" (fires `onNodeSelect` with a FOLDER node) and
   "Pick non-folder" (fires with a DOCUMENT node). One new test case
   asserts both behaviors: non-folder clicks leave selection empty,
   folder clicks update state.
2. **`RecordsManagementPage.test.tsx`** — mocks the absolute path
   `'components/browser/FolderTree'` so the dialog rendered inside
   the page picks up the stub. The existing schedule-save test now
   clicks `Pick folder-1` instead of typing into a UUID text field.

The dialog is a deep child of the page, but Jest's module-mock
registry uses the module's resolved path, so both mock paths above
resolve to the same module and both sets of tests use a consistent
stub surface.

## Verification

```
cd ecm-frontend && CI=true npm test -- \
  --testPathPattern='RecordsManagementPage.test.tsx|ScheduleReportPresetDialog.test.tsx|recordsManagementService.test.ts' \
  --watchAll=false
→ Tests: 128 passed, 128 total
```

Breakdown:
- 74 RecordsManagementPage (unchanged scope)
- 47 recordsManagementService (unchanged)
- 7 ScheduleReportPresetDialog (+1 new case: folder-pick + non-folder guard)

```
npx -p typescript@5.4.5 tsc --noEmit  → clean
npm run lint                          → clean
```

## Files Changed

| File | Kind |
|------|------|
| `ecm-frontend/src/components/records/ScheduleReportPresetDialog.tsx` | +40 / -14 lines (UI swap, name state) |
| `ecm-frontend/src/components/records/ScheduleReportPresetDialog.test.tsx` | +44 / -3 lines (FolderTree mock + new test) |
| `ecm-frontend/src/pages/RecordsManagementPage.test.tsx` | +17 / -3 lines (FolderTree mock + test update) |

No backend changes. No migration. No e2e changes.

## Expected CI Outcome

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ unchanged (no backend change) |
| **Frontend Build & Test** | **✅ 128 unit tests green** |
| Phase C Security Verification | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| Frontend E2E Core Gate | ✅ unchanged (no component API change visible from e2e) |
| Phase 5 Mocked Regression Gate | Pre-existing cancelled — unchanged |

## Non-goals

- Did not touch the PR-97/98 full-stack or mocked e2e specs. Those
  use UI interactions that don't depend on the folder input shape —
  they enable the schedule, set cron, and trust the page-level mock.
  Updating them is a separate slice if the click-path has to change.
- Did not add any folder search within the picker. `MoveCopyDialog`
  doesn't have one either; parity is better than divergence for now.
- Did not repaint the dialog layout. Only the one input changed.

## End-to-end chain after this commit

| PR | Layer | Status |
|----|-------|--------|
| PR-95..98 | Scheduled delivery backend + service + dialog + page wiring | ✅ |
| PR-99..100 | UI hardening + mocked e2e | ✅ |
| PR-101..104 | Full-stack e2e + dialog polish + ledger API + page ledger | ✅ |
| PR-105 | Ledger operator polish (filter chips + empty state) | ✅ |
| **PR-106** | **Folder tree picker replaces UUID text input** | **✅ shipped** |

The scheduled delivery chain now has no remaining operator friction
points flagged in the PR-97..PR-104 non-goals aside from the email
delivery channel (separate capability).
