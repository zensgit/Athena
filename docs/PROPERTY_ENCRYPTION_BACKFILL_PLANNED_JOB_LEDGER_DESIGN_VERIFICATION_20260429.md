# Property Encryption Backfill Planned Job Ledger Design and Verification

**Code commit:** `9d0866e`
**Date:** 2026-04-29
**Scope:** Adds a durable planned-job ledger for property encryption backfill without mutating nodes.

## 1. Context

The property encryption operations track now has these steps:

| Slice | Commit | Result |
|---|---|---|
| Diagnostics API | `b6c0116` | status and encrypted definition listing |
| Rewrap dry-run | `62bb168` | aggregate key-version rewrap planning |
| Backfill dry-run | `ba4ece0` | aggregate plaintext-to-encrypted storage planning |
| JSONB predicate smoke | `e5fe7a9` | PostgreSQL-backed predicate test, skipped locally when Docker is unavailable |
| Planned job ledger | `9d0866e` | persists executable backfill dry-run snapshots as `PLANNED` jobs |

This slice deliberately does not start a mutating executor. It adds the durable ledger and admin visibility needed before any node update path exists.

## 2. API Contract

### 2.1 Plan a job

```http
POST /api/v1/admin/property-encryption/backfill-jobs/plan
```

Request:

```json
{
  "targetKeyVersion": "v2"
}
```

Behavior:

1. Runs the existing backfill dry-run.
2. Rejects the request if `dryRun.executable == false`.
3. Persists the dry-run snapshot as a `PLANNED` job.
4. Returns `201 Created` with the job DTO.
5. Does not mutate nodes, start a background runner, encrypt values, decrypt values, or create per-node work items.

### 2.2 List jobs

```http
GET /api/v1/admin/property-encryption/backfill-jobs?limit=20
```

Behavior:

| Rule | Value |
|---|---|
| Default limit | 20 |
| Max limit | 100 |
| Sort | `requestedAt DESC` |
| Security | admin-only |

### 2.3 Get one job

```http
GET /api/v1/admin/property-encryption/backfill-jobs/{jobId}
```

Missing jobs return `404` through `ResourceNotFoundException`.

## 3. Ledger Schema

New table:

```text
property_encryption_backfill_jobs
```

Migration:

```text
ecm-core/src/main/resources/db/changelog/changes/090-create-property-encryption-backfill-jobs.xml
```

Master changelog include:

```text
db/changelog/changes/090-create-property-encryption-backfill-jobs.xml
```

### 3.1 State fields

| Field | Purpose |
|---|---|
| `id` | stable job id |
| `status` | current job state |
| `target_key_version` | key version selected at plan time |
| `requested_by` | authenticated admin user |
| `requested_at` | plan timestamp |
| `started_at` | future executor start timestamp |
| `finished_at` | future terminal timestamp |
| `created_at`, `updated_at`, `version` | persistence and optimistic locking |

### 3.2 Snapshot fields

| Field | Source |
|---|---|
| `encrypted_property_definition_count` | dry-run definition count |
| `plaintext_value_count` | dry-run plaintext count |
| `already_encrypted_value_count` | dry-run encrypted count |
| `dual_storage_conflict_value_count` | dry-run conflict count |
| `ready_value_count` | dry-run ready count |
| `orphan_encrypted_value_count` | dry-run orphan warning count |
| `warnings` | dry-run warning list |
| `definition_counts` | per-definition dry-run count snapshot |

### 3.3 Future execution fields

| Field | Initial value |
|---|---:|
| `processed_value_count` | 0 |
| `migrated_value_count` | 0 |
| `skipped_value_count` | 0 |
| `failed_value_count` | 0 |
| `last_error` | null |

## 4. State Machine

Current slice creates only:

```text
PLANNED
```

The entity defines future states, but no transition code exists yet:

| State | Intended future meaning |
|---|---|
| `PLANNED` | dry-run snapshot persisted; no mutation has started |
| `RUNNING` | executor has claimed the job |
| `SUCCEEDED` | all selected values completed without terminal failure |
| `FAILED` | executor hit a terminal failure |
| `CANCEL_REQUESTED` | operator requested cancellation |
| `CANCELLED` | executor stopped after cancellation |

There is intentionally no `QUEUED` state yet because this slice does not introduce a scheduler or queue.

## 5. Safety Rules

Planning requires the existing dry-run to be executable. That means the service blocks planning when:

| Blocker | Reason |
|---|---|
| crypto disabled | future execution cannot encrypt |
| target key missing or unconfigured | future writes cannot target the requested key |
| no encrypted definitions | no safe model target |
| dual-storage conflicts exist | conflict needs a dedicated reconciliation path |
| ready count is zero | no-op jobs should not be planned |

Orphan encrypted payloads remain warning-only, matching the dry-run design. They are retained in the job snapshot for operator visibility.

## 6. Implementation

| File | Change |
|---|---|
| `PropertyEncryptionBackfillJob` | new durable ledger entity |
| `PropertyEncryptionBackfillJobRepository` | save/list/get planned jobs |
| `PropertyEncryptionOperationsService` | plan/list/get job service APIs |
| `PropertyEncryptionOperationsController` | admin endpoints for plan/list/get |
| `090-create-property-encryption-backfill-jobs.xml` | Liquibase table and indexes |
| `PropertyEncryptionOperationsServiceTest` | service planning and rejection coverage |
| `PropertyEncryptionOperationsControllerSecurityTest` | admin-only and response-shape coverage |

## 7. Non-Goals

| Non-goal | Reason |
|---|---|
| `POST /backfill-jobs/{id}/start` | needs separate executor design |
| Node mutation | must not happen before executor batching and transaction boundaries are defined |
| Per-node work item table | may be needed later, but not required to persist a plan snapshot |
| Async lifecycle integration | existing async lifecycle is a cross-domain view, not a durable execution engine |
| Frontend UI | admin API foundation first |

## 8. Verification

### 8.1 Targeted tests

Command:

```bash
cd ecm-core
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=PropertyEncryptionOperationsServiceTest,PropertyEncryptionOperationsControllerSecurityTest \
  test
```

Result:

| Test class | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| PropertyEncryptionOperationsServiceTest | 12 | 0 | 0 | 0 |
| PropertyEncryptionOperationsControllerSecurityTest | 8 | 0 | 0 | 0 |

### 8.2 Backfill regression with JSONB smoke

Command:

```bash
cd ecm-core
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=NodeRepositoryJsonbBackfillSmokeTest,PropertyEncryptionOperationsServiceTest,PropertyEncryptionOperationsControllerSecurityTest \
  test
```

Result:

| Test class | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| NodeRepositoryJsonbBackfillSmokeTest | 1 | 0 | 0 | 1 |
| PropertyEncryptionOperationsServiceTest | 12 | 0 | 0 | 0 |
| PropertyEncryptionOperationsControllerSecurityTest | 8 | 0 | 0 | 0 |

Local skip reason:

```text
Docker not available for Testcontainers: Could not find a valid Docker environment.
```

### 8.3 Full security sweep

Command:

```bash
cd ecm-core
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  '-Dtest=*SecurityTest' \
  test
```

Result:

```text
security_files=46 tests=422 failures=0 errors=0 skipped=0
```

### 8.4 XML and diff hygiene

Command:

```bash
xmllint --noout \
  ecm-core/src/main/resources/db/changelog/changes/090-create-property-encryption-backfill-jobs.xml \
  ecm-core/src/main/resources/db/changelog/db.changelog-master.xml
```

Result: clean.

Command:

```bash
git diff --check
```

Result: clean.

## 9. Remaining Risk

The migration XML was syntax-checked locally, but not applied against a local PostgreSQL database because this environment has no Docker socket. CI or a Docker-enabled developer machine should execute the changelog against PostgreSQL before a mutating executor is added.

The next mutating slice must not simply load each `Node` and call `save`. That would couple backfill to unrelated entity lifecycle behavior. The executor should use a deliberately scoped batch selection predicate, small transactions, optimistic job claiming, and explicit conflict rechecks.

## 10. Next Slice

Recommended next step:

1. Add a PostgreSQL-enabled migration smoke, or verify Liquibase `090` in CI.
2. Design `POST /backfill-jobs/{id}/start` with a claim transition from `PLANNED` to `RUNNING`.
3. Define the exact batch selection predicate, matching the JSONB dry-run predicate.
4. Add conflict recheck before each batch writes encrypted values.
5. Only then add mutation code.

