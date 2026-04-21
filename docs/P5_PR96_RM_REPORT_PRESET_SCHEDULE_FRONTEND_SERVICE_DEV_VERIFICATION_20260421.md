# P5 PR-96 RM Report Preset Schedule — Frontend Service Layer

## Date
2026-04-21

## Scope

Frontend plumbing for the PR-95 backend. Adds types and
`RecordsManagementService` methods for schedule get/update, on-demand
deliver, and execution history. Deliberately **does not** add any UI
component yet — that's a follow-up slice.

## Why now

PR-95 shipped the backend for scheduled preset delivery. Without a
frontend client, the capability is only reachable via `curl` or admin
tooling. Shipping the service layer first keeps the slice minimal and
unblocks the separate UI slice.

## Included

### Types (`ecm-frontend/src/types/index.ts`)
- `RmReportPresetExecutionTrigger = 'MANUAL' | 'SCHEDULED'`
- `RmReportPresetExecutionStatus = 'SUCCESS' | 'FAILED'`
- `RmReportPresetExecution` — 1:1 with backend `PresetExecutionDto`
- `RmReportPresetScheduleStatus` — 1:1 with backend `ScheduleStatusDto`

### Service methods (`ecm-frontend/src/services/recordsManagementService.ts`)
- `getReportPresetSchedule(id)`
- `updateReportPresetSchedule(id, { enabled, cronExpression?, timezone?, deliveryFolderId? })`
- `deliverReportPresetNow(id)`
- `listReportPresetExecutions(id, limit?)`

Also adds `UpdateReportPresetScheduleRequest` interface for callers.

### Design choices

1. **`updateReportPresetSchedule` always sends null for missing fields.**
   The backend already accepts nulls as "leave unchanged" for optional
   fields on disable, so the client normalizes missing fields to null
   rather than omitting them. This makes the disable flow one-shot:
   `{ enabled: false }` is enough.

2. **`deliverReportPresetNow` sends an empty body.** The backend needs
   no inputs for a manual deliver (everything comes from the preset
   record). Sending `{}` keeps the Content-Type header correct without
   sending any params.

3. **`listReportPresetExecutions` omits the `limit` param when not
   specified.** Lets the backend apply its default of 20 instead of
   forcing the client to replicate that default.

4. **No UI in this slice.** Existing `RecordsManagementPage.tsx` is
   5123 lines; bundling a new dialog + execution history + folder
   picker into the same PR as the service shape would be a
   hard-to-review slice. Splitting them means this one lands cleanly
   even if the UI wiring takes iteration.

## Verification

### Unit Tests
```
cd ecm-frontend && CI=true npm test -- \
  --testPathPattern='recordsManagementService.test.ts' --watchAll=false
→ Tests: 44 passed, 44 total
```

6 new test cases cover:
- `getReportPresetSchedule` → correct URL
- `updateReportPresetSchedule` with enable → full payload normalized
- `updateReportPresetSchedule` disable path → nulls for optional fields
- `deliverReportPresetNow` → POST with empty body
- `listReportPresetExecutions` with limit → params present
- `listReportPresetExecutions` without limit → params undefined

### Type check (matches CI pinned version)
```
npx -p typescript@5.4.5 tsc --noEmit
→ no output (clean)
```

### Lint
```
npm run lint
→ no errors
```

## Files Changed

| File | Kind |
|------|------|
| `ecm-frontend/src/types/index.ts` | +29 lines (3 types, 1 interface) |
| `ecm-frontend/src/services/recordsManagementService.ts` | +35 lines (1 interface, 4 methods, 2 imports) |
| `ecm-frontend/src/services/recordsManagementService.test.ts` | +92 lines (6 tests) |

No backend changes. No Playwright/e2e changes. No component changes.

## Expected CI Outcome

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ unchanged (no backend change) |
| **Frontend Build & Test** | **✅ includes 6 new service tests** |
| Phase C Security Verification | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged (no component change) |
| Frontend E2E Core Gate | ✅ unchanged (no UI change) |
| Phase 5 Mocked Regression Gate | Separate pre-existing investigation — see handoff |

## Follow-up Slices (not in this PR)

1. **Schedule dialog component** — cron field + timezone dropdown +
   folder picker + enable toggle, reusing the `SaveReportPresetDialog`
   pattern
2. **Execution history panel** — consumes
   `listReportPresetExecutions`, shows timestamps and success/fail
3. **Wire dialog into RecordsManagementPage** — a "Schedule" action on
   each preset card
4. **Email delivery channel** (backend) — extends PR-95 to deliver via
   SMTP in addition to folder upload

## Non-goals

- No component/UI work
- No additional backend endpoints
- No change to existing CSV export or preset CRUD flows

This is the smallest possible frontend slice that exposes the PR-95
capability to React code via a typed, tested client.
