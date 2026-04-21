# P5 PR-97 RM Report Preset Schedule Dialog — Dev & Verification

## Date
2026-04-21

## Scope

Adds the `ScheduleReportPresetDialog` React component that consumes the
PR-96 service layer. Pure frontend; no component wiring into
`RecordsManagementPage` yet (deferred to PR-98).

## Why now

PR-95 shipped the backend; PR-96 shipped the typed service client. With
those two slices landed, a dialog component can be built in isolation
and tested against a mocked service, keeping the slice reviewable.

## Included

### Component — `ScheduleReportPresetDialog.tsx` (268 lines)
- Props: `open`, `preset`, `onClose`, optional `onSaved`
- On open (when preset kind supports CSV delivery):
  - `getReportPresetSchedule(id)` → current schedule
  - `listReportPresetExecutions(id, 5)` → recent executions
- Controls:
  - Enable switch
  - Cron expression text field (required when enabled)
  - Timezone text field (defaults to `UTC`)
  - Delivery folder UUID text field (required when enabled)
- Current schedule chips: enabled/disabled, next run, last run
- "Deliver now" button → `deliverReportPresetNow(id)`; prepends result
  to the execution list and surfaces FAILED message inline
- "Save schedule" button → `updateReportPresetSchedule(id, request)`;
  validates required fields when enabling
- Summary-only kinds (`ACTIVITY_FAMILY_HIGHLIGHTS`,
  `ACTIVITY_FAMILY_MIX`) render a warning and suppress the save path —
  uses the `supportsReportPresetCsvDelivery` type guard from PR-96, so
  the branch is enforced at compile time in callers

### Tests — `ScheduleReportPresetDialog.test.tsx` (five cases)
1. Loads schedule and populates fields
2. Rejects save when enabling without a cron expression
3. Disables schedule with a minimal request body
4. Renders summary-only warning and does not call the schedule API
5. Deliver now prepends the new execution to the list

All tests wait for loading to complete (inputs/buttons to become
enabled) before interacting — mirrors the reliability pattern used by
the existing `DeclareRecordDialog` test.

## Design Decisions

1. **Plain UUID text input for delivery folder, not a picker.** A full
   folder tree picker touches folder-service plumbing and multi-tenant
   path scoping; scoping that in keeps this slice reviewable. A picker
   upgrade is a natural follow-up.

2. **Execution history limited to 5 rows.** Matches the "recent" intent
   without paging or filtering. A full audit surface lives in Records
   Audit already.

3. **Summary-only warning is load-gated.** If the preset kind is not
   CSV-deliverable, the schedule API is never called and no save button
   is rendered — the backend would reject the request, but avoiding a
   useless API call keeps CI logs clean and UX honest.

4. **Deliver-now failures surface inline, not globally.** The execution
   row is still persisted by the backend (PR-95 writes a FAILED row),
   and the inline alert makes it clear the attempt ran without blocking
   the user from adjusting the schedule config.

5. **No jest-dom dependency.** The existing tests in this codebase use
   plain `.not.toBeNull()` / `.toBe(false)` assertions; this dialog
   matches that pattern instead of pulling in
   `@testing-library/jest-dom`.

## Verification

```
cd ecm-frontend && CI=true npm test -- \
  --testPathPattern='ScheduleReportPresetDialog.test.tsx' --watchAll=false
→ Tests: 5 passed, 5 total
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
| `ecm-frontend/src/components/records/ScheduleReportPresetDialog.tsx` | New, +268 lines |
| `ecm-frontend/src/components/records/ScheduleReportPresetDialog.test.tsx` | New, +199 lines |

No backend changes. No service changes. No page wiring.

## Expected CI Outcome

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ unchanged |
| **Frontend Build & Test** | **✅ includes 5 new component tests** |
| Phase C Security Verification | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged (not wired into a page yet) |
| Frontend E2E Core Gate | ✅ unchanged |
| Phase 5 Mocked Regression Gate | Separate pre-existing investigation |

## Non-goals

- Not wiring the dialog into `RecordsManagementPage` — that will be
  PR-98, one-file change to the preset card action row
- Not adding a folder tree picker — UUID text input is sufficient for
  v1 and keeps scope tight
- Not adding cron preview (next-run estimate) — backend already
  computes `nextRunAt`, so the loaded chip shows it accurately

## Follow-up

1. **PR-98**: wire this dialog into the preset card in
   `RecordsManagementPage.tsx`. Adds a Schedule action button next to
   the existing Save/Apply/Export actions from PR-89/90/91.
2. **Folder picker upgrade**: replace the UUID text input with a
   folder tree picker reusing existing folder-service code.
