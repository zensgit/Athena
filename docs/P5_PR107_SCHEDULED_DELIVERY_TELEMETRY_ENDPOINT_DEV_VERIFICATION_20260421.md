# P5 PR-107 Scheduled Delivery Health Telemetry Endpoint — Dev & Verification

## Date
2026-04-21

## Scope

New backend endpoint that summarises the caller's scheduled-delivery
health in one response. Purely backend; no migration; additive.

```
GET /api/v1/records/report-presets/telemetry
→ {
    "scheduleEnabledCount":   5,
    "duePresetCount":         2,
    "last24hSuccessCount":    7,
    "last24hFailedCount":     1,
    "lastExecutionAt":        "2026-04-21T09:00:00",
    "generatedAt":            "2026-04-21T16:00:00"
  }
```

## Why now

- Codex auto-delivery tree was idle; I picked a slice.
- The PR-95..106 chain shipped the scheduled-delivery capability and
  the ledger UI, but there was no single endpoint an ops dashboard
  could hit to answer "is the scheduler healthy for me right now?"
- Each existing endpoint answers a slice of that question. Fanning
  out to four endpoints from the frontend is wasteful when the
  backend can aggregate server-side in one query pass.

## Design

### Shape mirrors `RecordsOperationsTelemetryDto`
The page already has a `GET /records/operations` endpoint used by
the Governed Operations section. The new telemetry endpoint follows
the same pattern: one GET, owner-scoped, single JSON payload with
all the relevant counts.

### Six fields, chosen to answer three questions
| Question | Field(s) |
|----------|----------|
| *How many schedules do I have?* | `scheduleEnabledCount` |
| *Is the scheduler falling behind?* | `duePresetCount` (should normally be ≤ ~cron-interval worth) |
| *Did recent runs succeed?* | `last24hSuccessCount`, `last24hFailedCount`, `lastExecutionAt` |

`generatedAt` documents server time at response so clients can
reason about clock skew.

### 24-hour window
Default operator tolerance. Cron ticks every 5 minutes
(per PR-95's `ecm.rm.report-presets.scheduler-cron` default), so 24h
captures ~288 potential runs per preset — enough to surface a
meaningful success/fail ratio without being a full ledger dump.

### Owner scoping
Counts are filtered by the caller's owner. Admin delegation /
impersonation is intentionally not supported here; it would need
its own endpoint design consistent with the PR-83 owner-scoping
decision ("admin-only at the controller, owner-scoped at the
service").

### Anonymous rejection
`securityService.getCurrentUser()` returning null/blank raises
`IllegalStateException`. Covered by test.

## Implementation

### Repositories
- `RmReportPresetRepository`:
  - `countByOwnerAndScheduleEnabledTrueAndDeletedFalse(owner)`
  - `countByOwnerAndScheduleEnabledTrueAndDeletedFalseAndNextRunAtLessThanEqual(owner, now)`
- `RmReportPresetExecutionRepository`:
  - `findFirstByOwnerOrderByStartedAtDesc(owner)`
  - `countByOwnerAndStatusAndStartedAtGreaterThanEqual(owner, status, since)`

Spring Data derived queries — no JPQL, no `@Query` needed.

### Service
`RmReportPresetDeliveryService.getScheduledDeliveryTelemetry()`
runs all five queries and packages the result. `@Transactional(readOnly = true)`.

### Controller
`GET /telemetry` is placed before `/{id}/…` routes in the controller
source order so Spring matches the literal path before the path
variable route. Same admin-gate as the rest of `RmReportPresetController`.

## Verification

```
cd ecm-core && ./mvnw -B test \
  -Dtest='RmReportPresetDeliveryServiceTest,RmReportPresetControllerTest,RmReportPresetServiceTest,RecordsManagementControllerSecurityTest'
→ BUILD SUCCESS
→ Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
```

Breakdown (net new tests: +3):
- `RmReportPresetDeliveryServiceTest`: +2 cases
  - `getScheduledDeliveryTelemetry aggregates owner-scoped counts`
  - `getScheduledDeliveryTelemetry rejects anonymous callers`
- `RmReportPresetControllerTest`: +1 case
  - `getTelemetry returns scheduled delivery health summary`

## Files Changed

| File | Kind |
|------|------|
| `ecm-core/src/main/java/com/ecm/core/repository/RmReportPresetRepository.java` | +8 lines (2 count methods) |
| `ecm-core/src/main/java/com/ecm/core/repository/RmReportPresetExecutionRepository.java` | +8 lines (1 find + 1 count) |
| `ecm-core/src/main/java/com/ecm/core/service/RmReportPresetDeliveryService.java` | +50 lines (service method + DTO record) |
| `ecm-core/src/main/java/com/ecm/core/controller/RmReportPresetController.java` | +6 lines (endpoint) |
| `ecm-core/src/test/java/com/ecm/core/service/RmReportPresetDeliveryServiceTest.java` | +64 lines (2 tests) |
| `ecm-core/src/test/java/com/ecm/core/controller/RmReportPresetControllerTest.java` | +22 lines (1 test) |

No frontend changes. No migration.

## Expected CI Outcome

| Job | Expected |
|-----|----------|
| **Backend Verify** | **✅ 31 preset tests green** |
| Frontend Build & Test | ✅ unchanged |
| Phase C Security Verification | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| Frontend E2E Core Gate | ✅ unchanged |
| Phase 5 Mocked Regression Gate | Pre-existing cancelled — unchanged |

## Follow-up slices (not in this PR)

1. **Frontend consumption** — add a "Scheduled delivery health" card
   to `RecordsManagementPage` that renders these six numbers. Small
   frontend-only slice; would reuse the existing telemetry-card
   pattern used by Governed Operations.
2. **Alert thresholds** — if `duePresetCount` stays above a threshold
   for N minutes, emit an audit event. Deferred until a real
   operator need materialises.
3. **Email delivery channel** — still tracked as the last open
   non-goal from the PR-97..PR-106 chain.

## End-to-end chain after this commit

| PR | Layer | Status |
|----|-------|--------|
| PR-95..98 | Scheduled delivery backend + service + dialog + page wiring | ✅ |
| PR-99..100 | UI hardening + mocked e2e | ✅ |
| PR-101..104 | Full-stack e2e + dialog polish + ledger API + page ledger | ✅ |
| PR-105 | Ledger operator polish | ✅ |
| PR-106 | Delivery folder tree picker | ✅ |
| **PR-107** | **Scheduled delivery health telemetry endpoint** | **✅ shipped** |

## Non-goals

- No frontend wiring for this endpoint — PR-108 candidate
- No admin delegation / cross-owner telemetry
- No SLO / alert surface
- No export of the telemetry snapshot
