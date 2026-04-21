# P5 PR-105 Ledger Operator Polish — Integration & Verification

## Date
2026-04-21

## Scope

Codex-delivered operator polish on top of the PR-104 page-level
ledger. Pure frontend, additive. No backend endpoint or migration.

## What changed in this commit (`e824c5c`)

### Active filter summary bar
When any of the ledger filters (preset, status, trigger, date range)
are set, the card now shows an `Active ledger filters` line with a
chip for each applied filter. Operators no longer have to scroll each
form input to know what's filtering the current view.

### Zero-match empty-state recovery
When filtered results return zero rows, the ledger renders a clear
"No matching deliveries" panel with a "Clear filters" button instead
of an empty table. One click resets all filters back to the default
view.

### Tests
+1 unit test covering the zero-match → Clear filters recovery path.
Total `RecordsManagementPage.test.tsx` cases now 74.

## Verification

```
cd ecm-frontend && CI=true npm test -- \
  --testPathPattern='RecordsManagementPage.test.tsx|ScheduleReportPresetDialog.test.tsx|recordsManagementService.test.ts' \
  --watchAll=false
→ Tests: 127 passed, 127 total
```

```
npx -p typescript@5.4.5 tsc --noEmit  → clean
npm run lint                          → clean
```

## Files Changed

| File | Kind |
|------|------|
| `ecm-frontend/src/pages/RecordsManagementPage.tsx` | +82 lines (filter summary, empty state) |
| `ecm-frontend/src/pages/RecordsManagementPage.test.tsx` | +113 lines (tests around new UI) |
| `docs/P5_PR105_…DESIGN_20260421.md` | Codex design writeup |
| `docs/P5_PR105_…VERIFICATION_20260421.md` | Codex verification writeup |
| `docs/P5_RM_INTAKE_OWNERSHIP_MATRIX_*.md` | Intake matrix now lists PR-105 |

## Expected CI Outcome

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ unchanged (no backend change) |
| **Frontend Build & Test** | **✅ 127 unit tests green** |
| Phase C Security Verification | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| Frontend E2E Core Gate | ✅ unchanged (no e2e change) |
| Phase 5 Mocked Regression Gate | Pre-existing cancelled — unchanged |

## End-to-end chain after this commit

| PR | Layer | Status |
|----|-------|--------|
| PR-95..98 | Scheduled delivery backend + service + dialog + page wiring | ✅ |
| PR-99..100 | UI hardening + mocked e2e | ✅ |
| PR-101..104 | Full-stack e2e + dialog polish + ledger API + page ledger | ✅ |
| **PR-105** | **Ledger operator polish (filter chips + empty state)** | **✅ shipped** |

## Non-goals

- Phase 5 Mocked timeout investigation remains separate
- No folder tree picker yet (still UUID text input)
- No email delivery channel yet
- No scheduled runner metrics / alerting endpoints yet
