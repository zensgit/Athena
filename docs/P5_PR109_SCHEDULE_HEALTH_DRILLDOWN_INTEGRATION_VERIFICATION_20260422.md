# P5 PR-109 Schedule Health Drilldown — Integration & Verification

## Date
2026-04-22

## Scope

Codex-delivered integration of the schedule health card (PR-108) with
the preset table. Adds native schedule metadata to the preset list
response plus drilldown chips that filter the table by schedule state.
Backend-plus-frontend bundle; no migration.

## What changed in this commit (`bbf588f`)

### Backend

- **`RmReportPresetController` — `ReportPresetResponse`** gains four
  additive fields:
  - `scheduleEnabled: boolean`
  - `deliveryFolderId: UUID`
  - `nextRunAt: LocalDateTime`
  - `lastRunAt: LocalDateTime`
  
  These come from the entity (already persisted since PR-95). Zero
  migration; callers that didn't consume them before are unaffected.

- **`RmReportPresetRepository.claimScheduledRun`** — new `@Modifying`
  compare-and-set query:
  ```sql
  update rm_report_presets
     set next_run_at = :nextRunAt,
         entity_version = entity_version + 1
   where id = :presetId
     and deleted = false
     and schedule_enabled = true
     and next_run_at = :expectedNextRunAt
  ```
  Used by the scheduler loop to atomically claim a due preset. Rows
  update = 1 means this replica owns the run; 0 means another replica
  (or concurrent request) already advanced the schedule. Prevents
  duplicate deliveries when the scheduler ticks overlap.

- **Controller test** — new `listMine returns additive schedule
  metadata` case asserts the four new fields appear on the list
  response.

### Frontend

- **`RmReportPreset` type** in `types/index.ts` gains the matching
  four fields so the preset table can render schedule state per row
  without a second GET per preset.
- **Preset table filter chips** (`All`, `Scheduled`, `Due now`) above
  the preset list — filter client-side using the new metadata.
- **Drilldown from `Scheduled Delivery Health`**: clicking the
  "Scheduled presets" / "Due now" chips scrolls the preset table into
  view with the matching filter applied.
- **Per-row status**: preset rows now show a chip indicating
  schedule state (Scheduled / Due now / Last delivered at).

### Concurrency model change

Before PR-109, the scheduler loop fetched due presets and updated
them directly. Under two scheduler instances (e.g., rolling deploy)
both would fetch and deliver. The `claimScheduledRun` query makes
that race impossible — only one of the concurrent updates finds
`next_run_at = :expectedNextRunAt` and updates one row; the other
gets 0 rows-affected and skips. The entity version bump is belt +
braces for JPA's optimistic-lock check on any later save by the same
transaction.

## Verification

### Backend
```
cd ecm-core && ./mvnw -B test \
  -Dtest='RmReportPresetDeliveryServiceTest,RmReportPresetControllerTest,RmReportPresetServiceTest,RecordsManagementControllerSecurityTest'
→ BUILD SUCCESS
→ Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
```

### Frontend
```
cd ecm-frontend && CI=true npm test -- \
  --testPathPattern='RecordsManagementPage.test.tsx|ScheduleReportPresetDialog.test.tsx|recordsManagementService.test.ts' \
  --watchAll=false
→ Test Suites: 3 passed, 3 total
→ Tests: 131 passed, 131 total
```

```
npx -p typescript@5.4.5 tsc --noEmit  → clean
npm run lint                          → clean
```

Test delta:
- Backend: +1 case (`listMine returns additive schedule metadata`)
- Frontend: +1 case (health card drilldown into preset table)

## Files Changed

| File | Kind |
|------|------|
| `ecm-core/src/main/java/com/ecm/core/controller/RmReportPresetController.java` | +8 lines (4 response fields) |
| `ecm-core/src/main/java/com/ecm/core/repository/RmReportPresetRepository.java` | +15 lines (claim CAS query) |
| `ecm-core/src/main/java/com/ecm/core/service/RmReportPresetDeliveryService.java` | adopts claim query in scheduler loop |
| `ecm-core/src/test/java/com/ecm/core/controller/RmReportPresetControllerTest.java` | +28 lines (test + delivery metadata asserts) |
| `ecm-core/src/test/java/com/ecm/core/service/RmReportPresetDeliveryServiceTest.java` | scheduler-race coverage adjustments |
| `ecm-frontend/src/types/index.ts` | +4 RmReportPreset fields |
| `ecm-frontend/src/pages/RecordsManagementPage.tsx` | filter chips + drilldown + per-row status |
| `ecm-frontend/src/pages/RecordsManagementPage.test.tsx` | +test for drilldown |
| `docs/P5_PR109_*.md` | Codex design + verification writeups |
| `docs/P5_RM_INTAKE_OWNERSHIP_MATRIX_*.md` | intake matrix updated |

No new migration (all fields already on the entity from PR-95).

## Expected CI Outcome

| Job | Expected |
|-----|----------|
| **Backend Verify** | **✅ 32 preset tests green** |
| **Frontend Build & Test** | **✅ 131 unit tests green** |
| Phase C Security Verification | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| Frontend E2E Core Gate | ✅ unchanged |
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
| **PR-109** | **Schedule metadata on list + drilldown + CAS claim query** | **✅ shipped** |

## Non-goals

- No new migration (fields already on the entity)
- No cross-owner visibility
- No audit trail for drilldown clicks
- No email delivery channel
