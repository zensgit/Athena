# P5 PR-111 → PR-116 Bundle — Integration & Verification

## Date
2026-04-22

## Scope

Six-slice Codex bundle that closes the loop on the scheduled-delivery
capability. Covers summary-only kind CSV schedulability, operator
surface refresh, ledger operator polish e2e, and the consolidated
capability reference doc.

## What changed in this commit (`93a387e`)

### PR-111 — summary preset CSV + schedule support

Backend (`RmReportPresetDeliveryService`):
- `supportsScheduledDelivery()` now returns `true` for
  `ACTIVITY_FAMILY_HIGHLIGHTS` and `ACTIVITY_FAMILY_MIX` alongside the
  original five kinds.
- `renderCsv()` adds cases for the two summary kinds. Both resolve
  their rolling parameter (`windowDays` for HIGHLIGHTS, `days` for
  MIX) to a concrete `from/to` range via the existing
  `requirePresetRollingDateTimeRange` helper, then render the
  family-report CSV shape so delivered files have consistent columns.

Frontend (`recordsManagementService.ts`):
- `DELIVERABLE_REPORT_PRESET_KINDS` expands to all 7 values.
- `supportsReportPresetCsvDelivery` accordingly returns true for all
  kinds — `Schedule` / `Deliver` / `Export CSV` buttons render
  uniformly.

### PR-112 — mocked e2e for summary preset schedule

Adds a Playwright spec using route interception to cover the schedule
→ deliver flow for a HIGHLIGHTS preset, proving the
rolling-window resolution reaches the backend correctly.

### PR-113 — full-stack smoke for summary preset schedule

Live-backend Playwright spec variant exercising the same flow against
the real DB + scheduler.

### PR-114 — delivery surface refresh + ledger consistency

Dialog now reloads schedule + execution list after save and deliver,
and surfaces `onChanged` so page-level ledger and telemetry can
refresh in lock-step.

### PR-115 — mocked ledger operator e2e

Locks in the PR-105 filter-summary chips + zero-match recovery UX
against regressions with a mocked spec.

### PR-116 — full-stack ledger e2e

Counterpart full-stack spec for the same operator journey.

### Reference doc

`P5_RM_SCHEDULED_DELIVERY_CAPABILITY_REFERENCE_20260422.md` now
indexes PR-95..116 and documents the final architecture. Key update
vs earlier draft: "all 7 kinds schedulable" — HIGHLIGHTS/MIX
included, with a note that their rolling-window params are resolved
before rendering.

## Retraction

Earlier this turn I surfaced a "FE/BE kind-set mismatch" regression.
That claim was based on a stale grep of the backend file taken at
the start of my analysis; the user had extended the backend to
support HIGHLIGHTS/MIX while I was still reviewing the frontend diff.
At commit time, 34 backend + 131 frontend tests all pass, including
the flipped `updateScheduleEnablesSummaryOnlyPresetKinds` case and
the new `deliverNowSupportsSummaryOnlyPresets` case. No regression.

A feedback-memory entry was added:
- `feedback_reread_code_before_regression_claim.md` — re-read the
  authoritative file fresh before surfacing a cross-boundary
  inconsistency, and run both test suites; don't rely on early
  grep snapshots.

The earlier (wrong) `feedback_type_guards_across_fe_be_boundary.md`
entry was removed to keep the memory clean.

## Verification

### Backend
```
cd ecm-core && ./mvnw -B test \
  -Dtest='RmReportPresetDeliveryServiceTest,RmReportPresetControllerTest,RmReportPresetServiceTest,RecordsManagementControllerSecurityTest'
→ BUILD SUCCESS
→ Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
```

Test delta (+2 over PR-110's 32):
- `updateScheduleRejectsSummaryOnlyPresetKinds` →
  `updateScheduleEnablesSummaryOnlyPresetKinds` (flipped)
- new `deliverNowSupportsSummaryOnlyPresets` case

### Frontend
```
cd ecm-frontend && CI=true npm test -- \
  --testPathPattern='RecordsManagementPage.test.tsx|ScheduleReportPresetDialog.test.tsx|recordsManagementService.test.ts' \
  --watchAll=false
→ Test Suites: 3 passed, 3 total
→ Tests: 131 passed, 131 total
```

Dialog test:
- `shows a disabled/non-CSV warning for summary-only kinds` →
  `loads schedule controls for summary-only kinds that now support CSV delivery`

```
npx -p typescript@5.4.5 tsc --noEmit  → clean
npm run lint                          → clean
```

## Files Changed

### Backend
- `RmReportPresetDeliveryService.java` — 2 kinds added to guard + renderCsv
- `RmReportPresetDeliveryServiceTest.java` — flipped + new summary-only case
- `RecordsManagementController.java` / test — minor polish

### Frontend
- `recordsManagementService.ts` — DELIVERABLE set expanded
- `recordsManagementService.test.ts` — mirrors backend change
- `ScheduleReportPresetDialog.tsx` + test — summary-only path updated
- `RecordsManagementPage.tsx` + test — surface refresh
- `e2e/rm-report-preset-schedule.mock.spec.ts`
- `e2e/rm-report-preset-schedule.spec.ts`

### Docs
- `P5_PR111_*.md`, `P5_PR112_*.md`, `P5_PR113_*.md`
- `P5_PR114_*.md`, `P5_PR115_*.md`, `P5_PR116_*.md`
- `P5_RM_SCHEDULED_DELIVERY_CAPABILITY_REFERENCE_20260422.md`
- Intake matrix

## Expected CI Outcome

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ 34 preset tests green |
| Frontend Build & Test | ✅ 131 unit tests green |
| Phase C Security Verification | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| Frontend E2E Core Gate | ✅ (new full-stack specs join the suite) |
| Phase 5 Mocked Regression Gate | Pre-existing cancelled — unchanged |

## End-to-end chain after this commit

| PR | Layer | Status |
|----|-------|--------|
| PR-95..98 | Scheduled delivery core | ✅ |
| PR-99..100 | UI hardening + mocked e2e | ✅ |
| PR-101..104 | Full-stack e2e + ledger API + ledger UI | ✅ |
| PR-105 | Ledger operator polish | ✅ |
| PR-106 | Delivery folder tree picker | ✅ |
| PR-107 | Telemetry endpoint | ✅ |
| PR-108 | Telemetry card | ✅ |
| PR-109 | Schedule metadata + drilldown + CAS claim | ✅ |
| PR-110 | Claim-before-upload hardening docs | ✅ |
| **PR-111** | **Summary preset CSV/schedule support (all 7 kinds)** | **✅ shipped** |
| **PR-112** | **Mocked e2e for summary preset schedule** | **✅ shipped** |
| **PR-113** | **Full-stack e2e for summary preset schedule** | **✅ shipped** |
| **PR-114** | **Delivery surface refresh + ledger consistency** | **✅ shipped** |
| **PR-115** | **Mocked ledger operator e2e** | **✅ shipped** |
| **PR-116** | **Full-stack ledger e2e** | **✅ shipped** |
| **REF**   | **Capability reference doc** | **✅ shipped** |

## Non-goals

- Email delivery channel remains deferred
- Admin delegation / cross-owner telemetry remains deferred
- SLO thresholds / alerting remains deferred

## Takeaway

All seven RmReportPresetKind values are now uniformly schedulable.
The frontend and backend guard sets are aligned and enforced by
matching tests on both sides; the dialog test and service test
suites would both fail if the sets diverged again.
