# Athena Repository Kernel Hardening Liquibase Migration Draft

## Date
- 2026-04-13

## Status
- Draft

## References
- [ATHENA_REPOSITORY_KERNEL_HARDENING_DEV_AND_ACCEPTANCE_20260413.md](/Users/chouhua/Downloads/Github/Athena/docs/ATHENA_REPOSITORY_KERNEL_HARDENING_DEV_AND_ACCEPTANCE_20260413.md)
- [ATHENA_REPOSITORY_KERNEL_HARDENING_TASK_BREAKDOWN_20260413.md](/Users/chouhua/Downloads/Github/Athena/docs/ATHENA_REPOSITORY_KERNEL_HARDENING_TASK_BREAKDOWN_20260413.md)

## Goal
- Predefine the likely Liquibase changes required for P0 and P1 kernel hardening.
- Reduce migration drift before implementation starts.

## Migration Ordering

| Draft ChangeSet | Phase | Purpose |
| --- | --- | --- |
| 072 | P0A | content reference ledger |
| 073 | P0A | backfill content reference rows |
| 074 | P1 | site membership request first-class persistence |
| 075 optional | P1 optional | model validation support tables only if new persistence is required |

## Numbering Decision
- Repository source currently includes Liquibase files through `071`.
- New schema work in this plan therefore continues from `072`.
- If any environment reports previously run change sets beyond `071`, that environment must be reconciled with source control before new migrations are merged.

## Draft ChangeSet 072: Content Reference Ledger

### New Tables

#### `content_references`
- `id uuid primary key`
- `content_id varchar(...) not null`
- `owner_type varchar(32) not null`
- `owner_id uuid not null`
- `active boolean not null default true`
- `created_at timestamp`
- `updated_at timestamp`

### Indexes
- unique index on `(content_id, owner_type, owner_id)`
- index on `(content_id, active)`
- index on `(owner_type, owner_id)`

### Notes
- Keep the table generic enough to support future rendition or archive owners.
- Do not store file metadata here; this is ownership state only.
- Use `WORKING_COPY` as a dedicated `owner_type`.

### Rollback
- Drop `content_references` only if no downstream change set depends on it.

## Draft ChangeSet 073: Backfill Content References

### Backfill Sources
- `documents.content_id`
- `versions.content_id`
- optionally working copies if represented only as documents with `working_copy=true`

### Backfill Rules
- skip null or blank `content_id`
- create one row per owner
- use `owner_type=WORKING_COPY` when `documents.working_copy=true`
- use `active=true` for all migrated owners

### Validation Queries
- count of migrated `DOCUMENT` rows equals count of non-null document `content_id`
- count of migrated `VERSION` rows equals count of non-null version `content_id`
- no duplicate `(content_id, owner_type, owner_id)` rows

### Rollback
- remove backfilled rows only for owner types introduced by this migration if rollback is explicitly required

## Draft ChangeSet 074: Site Membership Request Persistence

### New Tables

#### `site_membership_requests`
- `id uuid primary key`
- `site_id uuid not null`
- `username varchar(255) not null`
- `requested_role varchar(64) not null`
- `message text`
- `status varchar(32) not null`
- `requested_at timestamp not null`
- `decision_by varchar(255)`
- `decision_at timestamp`
- `decision_comment text`

### Foreign Keys
- `site_id -> sites.id`

### Indexes
- unique index on `(site_id, username, status)` if pending uniqueness is modeled this way
- index on `(site_id, status)`
- index on `(username, status)`
- index on `(requested_at)`

### Backfill Strategy
- Perform one-time backfill from `User.preferences.siteMembershipRequests`.
- Keep a compatibility reader for one release only.
- Remove the compatibility reader in the next release after migration validation passes.

### Rollback
- drop `site_membership_requests`

## Draft ChangeSet 075 Optional: Optional Governance Persistence

### Use Only If Needed
- Add persistence only if model validation requires durable dependency snapshots or validation state.
- Prefer runtime validation against existing model/type/property/workflow tables first.

### Default Recommendation
- No schema change for initial model governance validation.

## Migration Verification Checklist
- Upgrade from current head succeeds on an existing non-empty dataset.
- Fresh install succeeds.
- Backfill creates expected reference rows.
- Application starts cleanly with new schema.
- Repository read/write smoke passes after migration.

## Operational Notes
- Run content-reference backfill before enabling orphan cleanup scheduler.
- Keep orphan cleanup disabled by feature flag until verification passes in at least one staging restore.
- If site membership request backfill is selected, snapshot `users.preferences` before migration.
- Control orphan cleanup grace period from configuration:
  - `ecm.storage.orphan-cleanup.grace-hours: 24`

## Feature Flags Recommended
- `ecm.storage.reference-ledger.enabled`
- `ecm.storage.orphan-cleanup.enabled`
- `ecm.security.permission-index-delta.enabled`
- `ecm.site.membership.persistence.enabled`

## Adopted Decisions
- Orphan cleanup grace period is config-driven, not DB-stored.
- Working-copy ownership uses dedicated `WORKING_COPY` owner type.
- Existing JSONB site request payloads are migrated once, with one-release compatibility reader retained temporarily.
