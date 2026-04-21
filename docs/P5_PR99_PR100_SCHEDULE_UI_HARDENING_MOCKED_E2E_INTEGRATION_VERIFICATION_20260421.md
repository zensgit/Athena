# P5 PR-99 / PR-100 Integration — Schedule UI Hardening & Mocked E2E

## Date
2026-04-21

## Scope

Consolidated integration + verification for two Codex-delivered slices that
land together on top of the end-to-end scheduled delivery chain
(PR-95 → PR-96 → PR-97 → PR-98):

- **PR-99** — UI hardening and dialog auto-refresh
- **PR-100** — mocked Playwright spec for the full schedule flow

Both are additive. No new backend endpoints, no migration.

## What changed in this commit (`49aa6b5`)

### PR-99 — UI hardening

- `RecordsManagementPage`: Export CSV and Schedule actions are both now
  scoped under `supportsReportPresetCsvDelivery(preset.kind)`. Summary-only
  kinds (`ACTIVITY_FAMILY_HIGHLIGHTS`, `ACTIVITY_FAMILY_MIX`) no longer
  offer Export CSV either, matching the backend's CSV capability contract.
  Previously the Export CSV button would render but the backend would
  reject the request.
- `ScheduleReportPresetDialog`: after a save or deliver-now, the dialog
  re-fetches the schedule + execution list so the backend-computed
  `nextRunAt` and the just-persisted execution row appear without a manual
  reopen. `loadState` now returns the loaded snapshot so the deliver-now
  flow can use it for immediate FAILED message surfacing.
- Helper-text copy on the preset section clarifies the summary-only vs
  CSV-capable split.

### PR-100 — mocked e2e spec

- `ecm-frontend/e2e/rm-report-preset-schedule.mock.spec.ts` — ~394 lines
  exercising: list presets → open schedule dialog → enable + save →
  deliver now → execution list reflects the new row → dialog closes.
- Route interceptors cover all four PR-96 endpoints:
  `GET/PUT /records/report-presets/:id/schedule`,
  `POST /records/report-presets/:id/deliver`,
  `GET /records/report-presets/:id/executions`.
- Uses the same `seedBypassSessionE2E` helper pattern as the other
  `*.mock.spec.ts` files in the Phase 5 Mocked suite.

## Test hygiene tightening

The dialog tests now mock `getReportPresetSchedule` twice per
save/deliver scenario (initial load + post-operation refresh) and assert
`toHaveBeenCalledTimes(2)`. This mirrors the new two-roundtrip flow
introduced by the auto-refresh and prevents silent regressions where a
developer removes the refresh but keeps the tests green.

## Verification

```
cd ecm-frontend && CI=true npm test -- \
  --testPathPattern='RecordsManagementPage.test.tsx|ScheduleReportPresetDialog.test.tsx|recordsManagementService.test.ts' \
  --watchAll=false
→ Test Suites: 3 passed, 3 total
→ Tests:       121 passed, 121 total
```

```
npx -p typescript@5.4.5 tsc --noEmit  → clean
npm run lint                          → clean
```

## Files Changed

| File | Kind |
|------|------|
| `ecm-frontend/src/pages/RecordsManagementPage.tsx` | Export CSV scoped under CSV-deliverable guard; helper text |
| `ecm-frontend/src/pages/RecordsManagementPage.test.tsx` | Unchanged tests still pass (update was mock-exposure only) |
| `ecm-frontend/src/components/records/ScheduleReportPresetDialog.tsx` | `loadState` returns snapshot; save/deliver refresh |
| `ecm-frontend/src/components/records/ScheduleReportPresetDialog.test.tsx` | Two-roundtrip mock + `toHaveBeenCalledTimes(2)` assertions |
| `ecm-frontend/src/services/recordsManagementService.test.ts` | Adds named-export import of the type guard for completeness |
| `ecm-frontend/e2e/rm-report-preset-schedule.mock.spec.ts` | New — PR-100 mocked e2e spec |
| `docs/P5_PR99_…DESIGN_20260421.md` | Codex design writeup |
| `docs/P5_PR99_…VERIFICATION_20260421.md` | Codex verification writeup |
| `docs/P5_PR100_…DESIGN_20260421.md` | Codex design writeup |
| `docs/P5_PR100_…VERIFICATION_20260421.md` | Codex verification writeup |
| `docs/P5_RM_INTAKE_OWNERSHIP_MATRIX_*.md` | Intake matrix now lists PR-93/96/97/98 |

## Expected CI Outcome

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ unchanged |
| **Frontend Build & Test** | **✅ 121 unit tests green** |
| Phase C Security Verification | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| Frontend E2E Core Gate | ✅ unchanged (mocked spec targets Phase 5 Mocked) |
| Phase 5 Mocked Regression Gate | Pre-existing cancelled — see memory entry |

## Note on Phase 5 Mocked

The PR-100 spec joins the Phase 5 Mocked suite. Per the feedback memory
entry recorded this session, Phase 5 Mocked has been timing out at 30 min
on every recent run due to a larger systemic issue (~7 unrelated mocked
tests across 4 files all hitting the 1.1m Playwright timeout). Adding
this new spec does not change that outcome — the gate was pre-existing
cancelled. If/when that investigation lands, PR-100 will run cleanly.

## End-to-end chain after this commit

| PR | Layer | Status |
|----|-------|--------|
| PR-95 | Backend (scheduled delivery, runner, execution ledger) | ✅ shipped |
| PR-96 | Frontend service layer (4 typed methods + kind guard) | ✅ shipped |
| PR-97 | Schedule dialog component | ✅ shipped |
| PR-98 | Page wiring (Schedule action + dialog mount) | ✅ shipped |
| **PR-99** | **UI hardening + dialog auto-refresh** | ✅ shipped |
| **PR-100** | **Mocked e2e spec** | ✅ shipped |

## Non-goals

- Did not address the pre-existing Phase 5 Mocked 30-minute timeout
- Did not add a folder tree picker (still UUID text input)
- Did not add an email delivery channel (tracked as future slice)
- Did not add real-backend e2e — the mocked spec covers the logical flow;
  a Core Gate e2e would need the scheduled runner to actually tick
