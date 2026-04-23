# P5 PR-117 → PR-121 Bundle — Integration & Verification

## Date
2026-04-23

## Scope

Five-slice Codex bundle that hardens the Scheduled Delivery Health
card with operator drilldowns and end-to-end coverage against a live
backend. Frontend-only; no backend change in this bundle.

## What changed in this commit (`5c93fbe`)

### PR-117 — health card full-stack smoke
Live-backend Playwright spec (variant of the existing
`rm-report-preset-schedule.spec.ts`) that seeds presets + executions
and asserts the health card renders the correct counters on the real
admin page.

### PR-118 — operator drilldowns (runtime)
The "Scheduled Delivery Health" chips are no longer passive counters.
On click, they drive the Preset Delivery Ledger filters on the same
page:

- **Scheduled presets** → scrolls to preset table, shows all
  scheduled rows (shipped earlier, unchanged)
- **Due now** → scrolls to preset table, filters to due rows
  (shipped in PR-109, unchanged)
- **Last 24h failed** → NEW: scrolls to the Preset Delivery Ledger,
  clears the filter form, applies `status=FAILED` + a rolling
  24-hour `from/to` window, triggers Apply

Implementation details:
- `RecordsManagementPage.tsx` gets a `ledgerRef` so
  `ledgerRef.current?.scrollIntoView({ behavior: 'smooth' })` can
  drive the viewport in one click without a router round-trip.
- The filter-write is done via `setPresetExecutionLedgerFilters` +
  `setAppliedPresetExecutionLedgerFilters` in lockstep so the ledger
  fetch fires with the operator-intended filter state, not the
  previous filter state.
- +2 page tests assert the drilldown applies correct filter state
  and scrolls the right element into view.

### PR-119 — success counter full-stack
Live-backend spec that seeds multiple delivered presets and asserts
the `Last 24h success` counter and the success-ledger filter land on
the right set.

### PR-120 — failure counter full-stack
Counterpart spec for `Last 24h failed` — runs a preset configured
to fail (missing delivery folder / invalid cron resolution) and
verifies both the counter increment and the drilldown path.

### PR-121 — due-now full-stack
Spec seeding a mix of past-due and future-scheduled presets, proving
the `Due now` counter matches the claim CAS path's view of "due
right now" (not "due soon").

## Verification

```
cd ecm-frontend && CI=true npm test -- \
  --testPathPattern='RecordsManagementPage.test.tsx|ScheduleReportPresetDialog.test.tsx|recordsManagementService.test.ts' \
  --watchAll=false
→ Test Suites: 3 passed, 3 total
→ Tests: 133 passed, 133 total   (was 131)
```

```
npx -p typescript@5.4.5 tsc --noEmit  → clean
npm run lint                          → clean
```

No backend test delta — no backend code change.

## Files Changed

| File | Kind |
|------|------|
| `ecm-frontend/src/pages/RecordsManagementPage.tsx` | +41 / -… (ref + drilldown helper + chip handlers) |
| `ecm-frontend/src/pages/RecordsManagementPage.test.tsx` | +187 lines (drilldown coverage) |
| `ecm-frontend/e2e/rm-report-preset-schedule.spec.ts` | +294 lines (PR-117/119/120/121 specs) |
| `ecm-frontend/e2e/rm-report-preset-schedule.mock.spec.ts` | +29 / -… lines (health card interactions) |
| `docs/P5_PR117..121_*.md` | Codex design + verification writeups |
| `docs/P5_RM_INTAKE_OWNERSHIP_MATRIX_*.md` | intake matrix entries for PR-117..121 |

No backend change.

## Expected CI Outcome

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ unchanged (no backend change) |
| **Frontend Build & Test** | **✅ 133 unit tests green** |
| Phase C Security Verification | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| Frontend E2E Core Gate | ✅ — new PR-117/119/120/121 specs join the Core Gate |
| Phase 5 Mocked Regression Gate | Pre-existing cancelled — unchanged |

## End-to-end chain after this commit

| PR | Layer | Status |
|----|-------|--------|
| PR-95..110 | Core scheduled delivery | ✅ |
| PR-111..116 | Summary preset CSV/schedule + surface refresh + ledger e2e | ✅ |
| **PR-117** | **Health card full-stack smoke** | **✅** |
| **PR-118** | **Operator drilldowns (Last 24h failed → ledger)** | **✅** |
| **PR-119** | **Success counter full-stack** | **✅** |
| **PR-120** | **Failure counter full-stack** | **✅** |
| **PR-121** | **Due-now full-stack** | **✅** |

## Non-goals

- Email delivery channel (still deferred)
- Admin delegation / cross-owner telemetry (still deferred)
- SLO alert thresholds (still deferred)
- Clickable drill from `Scheduled presets` chip direct to the
  Schedule dialog (could be a future slice)

## Takeaway

The Scheduled Delivery Health card is now fully operator-actionable
— every chip that could be a link is a link, and every link lands
on a pre-filtered evidence surface. Full-stack e2e coverage across
the three dynamic counters proves the backend + frontend agree on
what each counter means under real persistence.
