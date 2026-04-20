# P5 PR-83 RM Saved Report Preset Foundation — Dev & Verification

## Date
2026-04-20

## Why now

Per `docs/P5_PR82_CI_E2E_STABILIZATION_CLOSEOUT_DEVELOPMENT_20260420.md`:

> After the next CI pass confirms the stricter readiness gates, the next slice should return to product work rather than more CI surgery. The most reasonable next direction remains: continue P5 search/index/runtime capability work.

And per `docs/P5_RM_INTAKE_OWNERSHIP_MATRIX_DEVELOPMENT_20260417.md`, the accepted first executable slice for "RM delivery workflows" is:

> saved RM report preset + scheduled export foundation

This PR delivers the **preset half** — pure persistence and CRUD. Scheduled export is deferred to a follow-up slice so this one stays additive and reviewable on its own.

## Scope

Backend-only, additive. No kernel, ACL, or search-index coupling.

### Included
- `rm_report_presets` table with unique `(owner, name)` + kind + JSONB params
- Liquibase migration `082-create-rm-report-presets.xml`
- `RmReportPreset` entity extending `BaseEntity` (soft-delete, audit fields)
- `RmReportPresetRepository` — owner-scoped finder
- `RmReportPresetService` — list / get / create / update / delete, enforces owner scoping, duplicate-name protection, soft-delete on remove
- `RmReportPresetController` — 5 endpoints at `/api/v1/records/report-presets`, admin-only (`@PreAuthorize("hasRole('ADMIN')")`), JSON DTOs via records
- 12 unit tests across create / list / getOwned / update / delete paths

### Excluded (by design)
- No scheduled execution — arrives in a follow-up slice
- No second evidence surface — presets never materialize saved result rows
- No cross-user admin read — admin-only endpoints already gate the controller; the service explicitly restricts owner reads/writes to the caller's own presets
- No frontend — the existing `RecordsManagementPage` can consume this API in a later frontend-only slice

## API

```
GET    /api/v1/records/report-presets        → List my presets
GET    /api/v1/records/report-presets/{id}   → Get one
POST   /api/v1/records/report-presets        → Create
PUT    /api/v1/records/report-presets/{id}   → Update name/description/params
DELETE /api/v1/records/report-presets/{id}   → Soft-delete
```

All endpoints require `ROLE_ADMIN` (consistent with existing RM surface). Service enforces owner-scoping regardless of role — admins cannot read/mutate a different user's preset through these endpoints.

## Entity Design

```java
@Table(name = "rm_report_presets",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_rm_report_preset_owner_name",
        columnNames = {"owner", "name"}),
    indexes = { idx_owner, idx_kind })
public class RmReportPreset extends BaseEntity {
    String owner;                  // Athena username
    String name;                   // unique per owner
    String description;            // optional
    Kind kind;                     // which report surface this preset targets
    Map<String, Object> params;    // JSONB — the saved from/to/eventTypeLimit/... knobs
}

enum Kind {
    ACTIVITY_FAMILY_REPORT,
    ACTIVITY_FAMILY_HIGHLIGHTS,
    ACTIVITY_FAMILY_MIX,
    ACTIVITY_EVENT_TYPE_REPORT,
    ACTIVITY_CONTRIBUTOR_REPORT,
    ACTIVITY_CONTRIBUTOR_FAMILY_REPORT,
    ACTIVITY_CONTRIBUTOR_EVENT_TYPE_REPORT
}
```

`Kind` matches the shipped RM report families exactly (from `RecordsManagementController`). New report surfaces added later append a new enum value; old presets continue to work.

## Design Decisions

1. **Owner-scoping in the service, not just the controller.** Admin-only at the controller keeps external callers out. The service additionally enforces that a preset's `owner` matches `securityService.getCurrentUser()` for reads, updates, and deletes. This means future admin delegation flows (impersonation, support access) need their own endpoint instead of silently crossing owners.

2. **Kind is immutable after create.** Params and name/description can change; kind cannot. Changing kind would invalidate the stored params contract — callers who want a different kind should create a new preset.

3. **Duplicate-name check uses the `deleted_false` partial view.** Soft-deleted presets don't block the same name being reused. Unique index remains on `(owner, name)` without a `is_deleted` filter because Liquibase/Postgres partial unique indexes complicate cross-DB portability, and the service-level check is authoritative.

4. **JSONB params stored as `Map<String, Object>`.** No schema per kind — each kind's expected shape lives in the corresponding report endpoint's contract. This keeps the preset store strictly additive: a new report endpoint with new params can be saved without a schema migration.

5. **Migration numbered 082** — continues from `081-record-category-foundation.xml`.

## Verification

### Unit Tests
```
./mvnw -B -q test -Dtest=RmReportPresetServiceTest
→ BUILD SUCCESS (exit 0)
```

12 tests across:
- `create`: persists, trims, rejects duplicate, rejects blank name, rejects null kind, rejects missing user
- `listForCurrentUser`: returns owned, returns empty when anonymous
- `getOwned`: returns on owner match, rejects on owner mismatch, 404 when not found
- `delete`: soft-deletes owned preset
- `update`: renames when unique, rejects rename to conflicting name

### Docker Build
```
docker compose build --build-arg SKIP_LIBREOFFICE=true ecm-core
→ "Image athena-ecm-core Built"
```

### Migration Applied
```
SELECT id FROM databasechangelog WHERE id = '082-create-rm-report-presets';
→ 1 row
```

Table created with expected schema:
```
\d rm_report_presets
→ id / owner / name / description / kind / params / audit columns
→ PK on id
→ UNIQUE (owner, name) — idx_rm_report_preset_owner_name
→ INDEX idx_rm_report_preset_kind
→ INDEX idx_rm_report_preset_owner
```

### App Health + Endpoint
```
GET http://localhost:7700/actuator/health → {"status":"UP"}
GET http://localhost:7700/api/v1/records/report-presets (no auth) → 401
```

401 (not 404) confirms the endpoint is wired in and correctly gated by `ROLE_ADMIN`.

## Files Changed

| File | Kind |
|------|------|
| `ecm-core/src/main/java/com/ecm/core/entity/RmReportPreset.java` | New |
| `ecm-core/src/main/java/com/ecm/core/repository/RmReportPresetRepository.java` | New |
| `ecm-core/src/main/java/com/ecm/core/service/RmReportPresetService.java` | New |
| `ecm-core/src/main/java/com/ecm/core/controller/RmReportPresetController.java` | New |
| `ecm-core/src/main/resources/db/changelog/changes/082-create-rm-report-presets.xml` | New |
| `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml` | Registered 082 |
| `ecm-core/src/test/java/com/ecm/core/service/RmReportPresetServiceTest.java` | New — 12 unit tests |

## Follow-up Slices (not in this PR)

1. **Scheduled preset execution** — cron-driven run against `/records/activity-*-report` plus an execution log table
2. **Frontend "Save as preset" affordance** on the Records Management page's report cards
3. **Preset execution endpoint** — `POST /api/v1/records/report-presets/{id}/execute` that expands params and invokes the matching report endpoint
4. **Scheduled delivery** — email/download-bundle channels per the P5 intake matrix's "RM delivery workflows" direction

Each is a well-bounded additive slice with no shared state beyond the `rm_report_presets` table.

## P4 Invariant Compliance

The P4 closeout recommended preserving three invariants; this PR honors all three:

- ✅ RM APIs remain the authoritative source — this endpoint is under `/api/v1/records/...`, not a new surface
- ✅ Records Audit remains the primary evidence surface — no new audit table or report materialization
- ✅ New work reuses shipped report/export APIs — preset `kind` values map 1:1 to existing report endpoints
