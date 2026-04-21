# P5 PR-95 RM Report Preset Scheduled Delivery — Dev & Verification

## Date
2026-04-21

## Scope

Delivers the **scheduled execution** half of the RM saved-report-preset
workflow. This closes the "scheduled export foundation" slice noted in
`PR-83`'s Follow-up list and advances the P5 intake matrix "RM delivery
workflows" direction.

Backend-only, additive. No frontend changes in this slice.

## Why now

Per `docs/P5_PR83_RM_SAVED_REPORT_PRESET_FOUNDATION_DEV_VERIFICATION_20260420.md`:

> **Scheduled preset execution** — cron-driven run against
> `/records/activity-*-report` plus an execution log table

`PR-83` delivered the persistence CRUD. `PR-92` delivered the on-demand
`/execute` endpoint. `PR-95` now adds:

1. A cron-expressed schedule per preset
2. A delivery target (folder inside the repository)
3. An execution log
4. The scheduled runner that claims due presets

## Included

### Persistence
- `RmReportPresetExecution` entity — one row per delivery attempt
  (MANUAL or SCHEDULED trigger, SUCCESS or FAILED status)
- `rm_report_presets` gains 6 columns: `schedule_enabled`,
  `cron_expression`, `schedule_timezone`, `delivery_folder_id`,
  `next_run_at`, `last_run_at`
- Migration `083-add-rm-report-preset-delivery-foundation.xml`
- Index on `(schedule_enabled, next_run_at)` for the due-preset scan
- Foreign key from `rm_report_preset_executions.preset_id` to `rm_report_presets.id`

### Service — `RmReportPresetDeliveryService`
- `getSchedule(presetId)` / `updateSchedule(presetId, req)`
- `deliverNow(presetId)` — manual one-shot delivery
- `listExecutions(presetId, limit)` — owner-scoped execution log
- `@Scheduled(cron = "${ecm.rm.report-presets.scheduler-cron:0 */5 * * * *}") runScheduledDeliveries()`
  — scans `scheduleEnabled=true AND nextRunAt <= now()`, runs each under
  the preset owner's `ROLE_ADMIN` security context, advances `nextRunAt`
  via the existing `ScheduledRuleValidation` helper

### Controller
```
GET  /api/v1/records/report-presets/{id}/schedule
PUT  /api/v1/records/report-presets/{id}/schedule
POST /api/v1/records/report-presets/{id}/deliver
GET  /api/v1/records/report-presets/{id}/executions?limit=20
```

All admin-gated, owner-scoped in the service, consistent with `PR-83`.

## Design Decisions

1. **Reuse existing ScheduledRuleValidation.** Cron parsing, timezone
   normalization, `nextRunAt` computation, and the 5-minute-minimum
   interval rule are all already implemented for `AutomationRule`.
   `RmReportPresetDeliveryService` delegates to the same utility instead
   of re-deriving cron behavior — same rules everywhere.

2. **Delivery is `render → CSV → upload as a document`.** The service
   dispatches the preset `Kind` to the matching `RecordsManagementService`
   report method, serializes the resulting DTO to CSV, and uploads it via
   the existing `DocumentUploadService` to the configured folder. This
   means:
   - delivery inherits every existing upload guarantee (content dedup,
     audit trail, version control, tenancy scoping)
   - the artifact is discoverable and permissioned exactly like any other
     document — no separate "report output" surface
   - no new evidence surface; Records Audit remains the authoritative log

3. **Five schedulable kinds.** Only the five activity-family reports that
   produce structured DTOs are schedulable. `ACTIVITY_FAMILY_HIGHLIGHTS`
   and `ACTIVITY_FAMILY_MIX` remain available for manual `/execute` but
   don't yet have CSV serialization. Attempting to enable a schedule for
   an unschedulable kind fails with a clear `IllegalArgumentException`.

4. **Failure writes an execution row too.** Both SUCCESS and FAILED
   outcomes persist a row with message and timing. The `next_run_at` is
   still advanced on failure so a transient error does not permanently
   block the schedule.

5. **Per-preset security context during scheduled runs.** The runner
   synthesizes an `UsernamePasswordAuthenticationToken` with the preset
   owner's username and `ROLE_ADMIN`, restores the prior context in a
   `finally`. This ensures audit attribution and owner-scoping work
   uniformly for scheduled and manual deliveries.

6. **5-minute default polling cadence.** The `@Scheduled` cron defaults
   to `0 */5 * * * *` and is overridable via the
   `ecm.rm.report-presets.scheduler-cron` property. Matches the
   5-minute minimum schedule interval enforced at the preset level.

## Verification

### Unit Tests

```
cd ecm-core && ./mvnw -B test \
  -Dtest='RmReportPresetDeliveryServiceTest,RmReportPresetControllerTest,RmReportPresetServiceTest'
→ BUILD SUCCESS
→ Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
```

Breakdown:
- 14 pre-existing `RmReportPresetServiceTest` — still green
- 4 new `RmReportPresetDeliveryServiceTest`
- 3 new `RmReportPresetControllerTest`

### Compile

```
./mvnw -B compile
→ BUILD SUCCESS
```

## Files Changed

| File | Kind |
|------|------|
| `ecm-core/src/main/java/com/ecm/core/entity/RmReportPresetExecution.java` | New |
| `ecm-core/src/main/java/com/ecm/core/repository/RmReportPresetExecutionRepository.java` | New |
| `ecm-core/src/main/java/com/ecm/core/service/RmReportPresetDeliveryService.java` | New |
| `ecm-core/src/main/resources/db/changelog/changes/083-add-rm-report-preset-delivery-foundation.xml` | New migration |
| `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml` | +include 083 |
| `ecm-core/src/main/java/com/ecm/core/entity/RmReportPreset.java` | +6 schedule columns |
| `ecm-core/src/main/java/com/ecm/core/repository/RmReportPresetRepository.java` | +due-preset finder |
| `ecm-core/src/main/java/com/ecm/core/controller/RmReportPresetController.java` | +4 schedule endpoints |
| `ecm-core/src/test/java/com/ecm/core/service/RmReportPresetDeliveryServiceTest.java` | New |
| `ecm-core/src/test/java/com/ecm/core/controller/RmReportPresetControllerTest.java` | New |

## Non-goals

- No email channel — only "deliver to folder" in this slice
- No frontend UI — existing Records Management page can consume the API in a later slice
- No backfill of `next_run_at` for historical presets — they remain
  `schedule_enabled=false` with null cron until an owner explicitly
  configures a schedule
- `ACTIVITY_FAMILY_HIGHLIGHTS` / `ACTIVITY_FAMILY_MIX` kinds remain
  non-schedulable until someone adds their CSV serializer

## Follow-up Slices

1. **Email delivery channel** — deliver CSV as attachment instead of (or
   in addition to) folder upload
2. **Frontend schedule UI** — cron editor on the preset card, execution
   history panel
3. **CSV serializers for HIGHLIGHTS / MIX kinds** — broadens schedulable
   kind coverage to all seven
4. **Retry/backoff on FAILED** — currently a failure just advances
   `nextRunAt` to the next cron slot

## P4 Invariant Compliance

- ✅ RM APIs remain the authoritative source — endpoints live under
  `/api/v1/records/report-presets/...`, not a new surface
- ✅ Records Audit remains the primary evidence surface — each delivery
  emits `RM_REPORT_PRESET_DELIVERED` / `RM_REPORT_PRESET_DELIVERY_FAILED`
  audit events, and the produced document flows through the normal audit
  path
- ✅ New work reuses shipped report/export APIs — delivery invokes the
  same `RecordsManagementService` methods that `/execute` already uses
