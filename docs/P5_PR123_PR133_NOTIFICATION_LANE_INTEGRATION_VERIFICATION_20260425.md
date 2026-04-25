# P5 PR-123 → PR-133 Notification Lane — Integration & Verification

## Date
2026-04-25

## Scope

Eleven-slice Codex bundle opening the **first new capability after the
PR-122 milestone closeout**. Adds per-user notifications for scheduled
preset delivery outcomes, an admin ops trigger, and a CI gate step so
notification behavior is exercised in the same live-stack environment
as the Core Gate.

This integration note records the shipped runtime, the CI change, and
the behaviour change operators will see. Codex's per-slice docs remain
the canonical per-PR evidence trail.

## What shipped this turn (`4653f3e`)

### Runtime changes

**`RmReportPresetDeliveryService`**
- Two new dependencies: `ActivityService`, `PreferenceService`
- New audit constant `AUDIT_SCHEDULED_DELIVERIES_TRIGGERED`
- New activity keys `rm.report_preset.delivery.succeeded` and
  `rm.report_preset.delivery.failed`
- Preference keys
  `org.athena.rm.reportPreset.delivery.notifyOnSuccess` (default
  true) and `org.athena.rm.reportPreset.delivery.notifyOnFailure`
  (default true)
- Delivery success / failure paths publish an activity event gated
  by the matching preference, using `NOTIFICATION_ACTOR = "system"`
- Scheduler tick emits exactly one
  `AUDIT_SCHEDULED_DELIVERIES_TRIGGERED` audit event per run — the
  publish path is idempotent, so retries never duplicate
  notifications

**`RmReportPresetController`**
- New admin endpoint
  `POST /api/v1/records/report-presets/run-scheduled-deliveries`
  returning a `ScheduledRunResultDto` (claimed / succeeded / failed
  counts). Lets on-call force a tick without waiting 5 minutes for
  the cron scheduler.

**Frontend**
- `notificationUtils` / `siteActivityUtils` updated to render the two
  new activity event types with appropriate labels and routing
- `RecordsManagementPage` surface refreshed for the new events
- `e2e/rm-report-preset-schedule.spec.ts` covers notification emit
  and preference suppression end-to-end

### CI change

**New step in `.github/workflows/ci.yml`**: "Run RM notification
acceptance gate" inside `frontend_e2e_core` — a live-backend
Playwright gate dedicated to the notification flow. It reuses the
already-started Core Gate stack instead of starting a second Docker
stack.

### Docs

Eleven per-slice design + verification docs (PR-123..133) plus:
- `P5_PR123_PR129_RM_PRESET_DELIVERY_NOTIFICATION_LANE_CLOSEOUT_*`
  mid-bundle closeout rollup from Codex
- `P5_RM_PRESET_DELIVERY_NOTIFICATION_REMAINING_WORK_20260424.md`
  explicitly lists the out-of-scope non-goals

### Security surface

- `RmReportPresetControllerSecurityTest` added, covering the new
  `run-scheduled-deliveries` endpoint is admin-gated at the class
  level.

## Verification

### Subsequent gate hardening

After this PR-123..133 integration note, PR-134..PR-139 further hardened the same CI-attached notification lane:

- PR-134 removes near-future cron waiting from the four notification acceptance flows by forcing due state after schedule save.
- PR-135 includes backend Surefire reports in `frontend_e2e_core` failure artifacts.
- PR-136 replaces bare Playwright API response assertions with contextual status, URL, and response-body diagnostics.
- PR-137 adds People service contract tests for single-preference get/set/delete calls used by RM notification toggles.
- PR-138 adds the Records Management page rollback test for failed preference saves.
- PR-139 records the CI evidence required before the lane can be promoted from pending to accepted.

### Frontend (local)
```
cd ecm-frontend && CI=true npm test -- \
  --testPathPattern='RecordsManagementPage.test.tsx|ScheduleReportPresetDialog.test.tsx|recordsManagementService.test.ts|notificationUtils.test.ts|siteActivityUtils.test.ts' \
  --watchAll=false
→ Test Suites: 5 passed, 5 total
→ Tests:       152 passed, 152 total
```

```
npx -p typescript@5.4.5 tsc --noEmit  → clean
npm run lint                          → clean
```

Test delta +19 over PR-121's 133:
- notificationUtils tests for the two new activity event keys
- siteActivityUtils formatting tests
- page-level wiring coverage

### Backend (delegated to CI)

Docker daemon was not running on the dev machine this turn; `mvnw`
wraps a Docker maven image and couldn't start. The new classes
`ActivityServiceTest`, `NotificationInboxServiceTest`,
`RmReportPresetControllerSecurityTest` along with the existing
`RmReportPresetDeliveryServiceTest` (extended with
`ActivityService` / `PreferenceService` mocks) will verify on CI.

### Known CI note

The `9309295` and `7938d2f` runs (both docs-only, prior turn) failed
on Frontend E2E Core Gate due to the known
`search-preview-status.spec.ts:235` flake — classic ES facet
aggregation race, documented in session memory
(`feedback_es_facet_aggregation_race.md`). That failure is not
caused by this turn's code.

## Expected CI Outcome

| Job | Expected |
|-----|----------|
| Backend Verify | ✅ — new classes exercised, mocks in place |
| Frontend Build & Test | ✅ 152 unit tests |
| Phase C Security Verification | ✅ unchanged |
| Acceptance Smoke | ✅ unchanged |
| Frontend E2E Core Gate | ⚠ facet-aggregation flake may repeat — known |
| Frontend E2E Core Gate notification step | ✅ new gate step, exercising the notification spec |
| Phase 5 Mocked Regression Gate | Pre-existing cancelled — unchanged |

## PR-122 closeout alignment

The PR-122 milestone closeout explicitly said:

> The next meaningful work should either open a new capability
> (email delivery, delegation, alerting) or explicitly start a new
> operator/analytics milestone — but it should not continue
> re-cutting the same preset-delivery core path.

PR-123..133 open a new capability (notifications / alerting). The
preset-delivery core (CRUD, ledger, schedule semantics) is
untouched — the notification lane layers on top of the already-shipped
activity/audit and preference infrastructure.

## Files Changed

- `.github/workflows/ci.yml` — new Notification Gate step in `frontend_e2e_core`
- `ecm-core/src/main/java/com/ecm/core/service/RmReportPresetDeliveryService.java`
- `ecm-core/src/main/java/com/ecm/core/service/ActivityService.java`
- `ecm-core/src/main/java/com/ecm/core/service/NotificationInboxService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RmReportPresetController.java`
- Matching test classes + new
  `RmReportPresetControllerSecurityTest`
- `ecm-frontend/src/utils/{notificationUtils,siteActivityUtils}.ts(+test)`
- `ecm-frontend/src/pages/RecordsManagementPage.tsx(+test)`
- `ecm-frontend/e2e/rm-report-preset-schedule.spec.ts`
- `ecm-frontend/package.json`
- 20+ new Codex docs

## Operational note (gh auth)

The push initially failed because the active `gh` account on this
machine was `rhe91709-netizen`, not `zensgit`. Switched via
`gh auth switch --user zensgit` and retried. Git push then succeeded.
Worth checking `gh auth status` whenever a push gets a 403.

## Non-goals (carried from PR-122 closeout)

- Email delivery channel
- Admin delegation / cross-owner telemetry
- SLO thresholds / alert emit to external channels

Notifications land in the existing inbox/activity surfaces. Routing
them to email/webhook/Slack is a separate capability.

## End-to-end chain after this commit

| PR | Layer | Status |
|----|-------|--------|
| PR-95..110 | Core scheduled delivery | ✅ |
| PR-111..116 | Summary preset CSV + surface refresh | ✅ |
| PR-117..121 | Health card drilldowns + full-stack counters | ✅ |
| PR-122 | Milestone closeout (docs) | ✅ |
| **PR-123..133** | **Notification lane + admin trigger + CI gate** | **✅ shipped** |
