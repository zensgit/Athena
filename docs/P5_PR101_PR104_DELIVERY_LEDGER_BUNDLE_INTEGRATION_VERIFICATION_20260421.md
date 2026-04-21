# P5 PR-101 → PR-104 Delivery Ledger Bundle — Integration & Verification

## Date
2026-04-21

## Scope

Consolidated integration + verification for four Codex-delivered slices
that landed together on top of the end-to-end scheduled delivery chain
(PR-95 → PR-96 → PR-97 → PR-98 → PR-99/100):

- **PR-101** — full-stack Playwright smoke for the schedule flow (live
  backend variant of PR-100)
- **PR-102** — execution ledger UI polish on the schedule dialog
- **PR-103** — backend filter/export API for the preset execution
  ledger (new repository query + two new controller endpoints)
- **PR-104** — `RecordsManagementPage` "Preset Delivery Ledger"
  section with filters, pagination, CSV export, and open-target
  actions

All additive. No schema migration beyond what PR-95 already shipped.

## This commit (`a474444`)

### PR-101 — full-stack schedule smoke (`rm-report-preset-schedule.spec.ts`)
Variant of PR-100 that runs against a real backend. Exercises the full
chain with actual persistence and scheduled-runner tick. Complements the
mocked PR-100 spec by proving the backend paths work end-to-end.

### PR-102 — dialog ledger polish
Column formatting, FAILED message surfacing, improved filter UX in
`ScheduleReportPresetDialog.tsx`. Dialog tests increased to 6 cases
with the new polish assertions.

### PR-103 — backend ledger API
- `RmReportPresetExecutionRepository`: new filter query supporting
  presetId, status, triggerType, from, to with Spring `Pageable` paging.
- `RmReportPresetDeliveryService`: owner-scoped `listExecutionLedger`
  and `exportExecutionLedgerCsv` methods that reuse the existing
  filter/aggregation rules.
- `RmReportPresetController`: two new endpoints
  - `GET /records/report-presets/executions` — paged ledger
  - `POST /records/report-presets/executions/export` — CSV download
- 28 JUnit tests green covering the new paths.

### PR-104 — `RecordsManagementPage` ledger section
New card "Preset Delivery Ledger" with:
- Status (All/Successful/Failed), Trigger (All/Manual/Scheduled) and
  date-range filters
- Paginated table showing run time, preset, trigger, status, filename,
  message, delivery document/folder
- "Open delivered file" + "Open target folder" actions that launch the
  `/browse/<id>` surface in a new tab
- "Export ledger CSV" button calling the PR-103 export endpoint
- Render header is "Preset Delivery Ledger" — tests scope through that
  card

## Frontend test-scoping fixes (this commit)

The new ledger section rendered two new DOM ambiguities that broke
7 tests in `RecordsManagementPage.test.tsx`. Fixed in this commit:

1. **Preset-row tests**: the ledger displays `presetName` text, so the
   same "HR family current" string appears three times on the page
   (once in the preset table, twice in the ledger rows from the
   two-row test mock). `findByText('HR family current')` now matches
   multiple elements. Fix: scope `findByText` within the
   "Saved RM Report Presets" card via `findByRole('heading', …).closest('.MuiCard-root')`.

2. **Audit drilldown tests**: the ledger's own From/To datetime inputs
   duplicate the audit filter's From/To labels. `getByLabelText('From')`
   now matches two inputs. Fix: scope the assertion within the
   "Records Audit" card.

These fixes mirror the scoping pattern the new PR-104 tests already
use for the ledger card. Consistent technique across the suite.

## Verification

### Backend
```
cd ecm-core && ./mvnw -B test \
  -Dtest='RmReportPresetDeliveryServiceTest,RmReportPresetControllerTest,RmReportPresetServiceTest,RecordsManagementControllerSecurityTest'
→ BUILD SUCCESS
→ Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
```

### Frontend
```
cd ecm-frontend && CI=true npm test -- \
  --testPathPattern='RecordsManagementPage.test.tsx|ScheduleReportPresetDialog.test.tsx|recordsManagementService.test.ts' \
  --watchAll=false
→ Tests: 126 passed, 126 total

npx -p typescript@5.4.5 tsc --noEmit  → clean
npm run lint                          → clean
```

Breakdown:
- 73 RecordsManagementPage
- 47 recordsManagementService (adds 3 new ledger service tests)
- 6 ScheduleReportPresetDialog

## Files Changed

| File | Kind |
|------|------|
| `ecm-core/src/main/java/com/ecm/core/controller/RmReportPresetController.java` | +69 lines (2 endpoints) |
| `ecm-core/src/main/java/com/ecm/core/repository/RmReportPresetExecutionRepository.java` | +4 lines (filter query) |
| `ecm-core/src/main/java/com/ecm/core/service/RmReportPresetDeliveryService.java` | +154 lines (ledger + export) |
| `ecm-core/src/test/java/com/ecm/core/controller/RmReportPresetControllerTest.java` | +85 lines |
| `ecm-core/src/test/java/com/ecm/core/service/RmReportPresetDeliveryServiceTest.java` | +135 lines |
| `ecm-frontend/src/pages/RecordsManagementPage.tsx` | +372 lines (ledger UI) |
| `ecm-frontend/src/pages/RecordsManagementPage.test.tsx` | +105 lines (new) / -scoping (existing) |
| `ecm-frontend/src/components/records/ScheduleReportPresetDialog.tsx` | +152 lines (polish) |
| `ecm-frontend/src/components/records/ScheduleReportPresetDialog.test.tsx` | +86 lines |
| `ecm-frontend/src/services/recordsManagementService.ts` | +76 lines (2 methods) |
| `ecm-frontend/src/services/recordsManagementService.test.ts` | +78 lines |
| `ecm-frontend/src/types/index.ts` | +2 types |
| `ecm-frontend/e2e/helpers/api.ts` | +4 lines |
| `ecm-frontend/e2e/rm-report-preset-schedule.spec.ts` | New — full-stack smoke |
| `docs/P5_PR101..104_*.md` | Codex design + verification writeups |

## Expected CI Outcome

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ — 28 RM preset tests green |
| **Frontend Build & Test** | **✅ — 126 unit tests, tsc + lint clean** |
| Phase C Security Verification | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| Frontend E2E Core Gate | ✅ — PR-101 full-stack spec joins the suite |
| Phase 5 Mocked Regression Gate | Pre-existing cancelled — unchanged |

## End-to-end chain after this commit

| PR | Layer | Status |
|----|-------|--------|
| PR-95 | Backend: scheduled delivery, runner, execution ledger | ✅ |
| PR-96 | Frontend service layer | ✅ |
| PR-97 | Schedule dialog component | ✅ |
| PR-98 | Page wiring (Schedule action) | ✅ |
| PR-99 | UI hardening + dialog auto-refresh | ✅ |
| PR-100 | Mocked e2e spec | ✅ |
| **PR-101** | **Full-stack e2e spec** | **✅ shipped** |
| **PR-102** | **Dialog ledger polish** | **✅ shipped** |
| **PR-103** | **Backend ledger filter/export API** | **✅ shipped** |
| **PR-104** | **Page ledger section** | **✅ shipped** |

## Non-goals

- Phase 5 Mocked 30-minute timeout investigation remains separate
- No email delivery channel (tracked as future slice)
- No per-owner admin impersonation on ledger reads — still
  caller-owner scoped
- No folder tree picker for delivery folder — still UUID text input
