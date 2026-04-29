# Property Encryption Backfill JSONB Predicate Smoke Design and Verification

**Test commit:** `e5fe7a9`
**Date:** 2026-04-29
**Scope:** Adds PostgreSQL-backed smoke coverage for the JSONB predicates used by property encryption backfill dry-run.

## 1. Context

The previous slice added:

| Endpoint | Commit | Behavior |
|---|---|---|
| `POST /api/v1/admin/property-encryption/backfill-jobs/dry-run` | `ba4ece0` | Aggregate-only dry-run for plaintext encrypted-property backfill planning |

That endpoint depends on PostgreSQL JSONB native queries in `NodeRepository`. Unit tests already covered service arithmetic and response safety, but they mocked repository counts. This slice closes that gap by adding a repository smoke test that validates the actual JSONB predicates against PostgreSQL.

H2 is intentionally not used here. The production queries rely on PostgreSQL-specific features:

| Query feature | Why H2 is insufficient |
|---|---|
| `jsonb_exists(...)` | PostgreSQL JSONB function |
| `jsonb_object_length(...)` | PostgreSQL JSONB aggregate helper |
| `jsonb_each_text(...)` | PostgreSQL lateral JSONB expansion |
| `'{}'::jsonb` | PostgreSQL type cast |
| regex against encrypted envelopes | PostgreSQL regex semantics |

## 2. Test Design

Added test class:

```text
ecm-core/src/test/java/com/ecm/core/repository/NodeRepositoryJsonbBackfillSmokeTest.java
```

The test uses:

| Component | Reason |
|---|---|
| `PostgreSQLContainer("postgres:15-alpine")` | validates real PostgreSQL JSONB behavior |
| `ApplicationContextRunner` | creates a narrow JPA context without booting the full application |
| `@EnableJpaRepositories` narrowed to `NodeRepository` | avoids unrelated repository startup cost |
| `@EntityScan({"com.ecm.core.entity", "com.ecm.core.model"})` | includes `Node`, `Folder`, and directly referenced model entities |
| `AuditorAware<String>` | satisfies JPA auditing defaults |
| JUnit `Assumptions` on container startup | matches existing Testcontainers skip pattern when Docker is unavailable |

The test saves `Folder` instances rather than abstract `Node` instances. Minimal entity fields are:

| Field | Purpose |
|---|---|
| `name` | required by `nodes.name`; also drives generated `path` |
| `typeQName` | stable runtime model type marker |
| `createdBy`, `createdDate`, `lastModifiedDate` | auditing columns |
| `properties` | plaintext JSONB column |
| `encryptedProperties` | encrypted JSONB column |
| `deleted` | verifies deleted rows are excluded |
| `status`, `deletedAt`, `deletedBy` | set only for the deleted sample |

## 3. Fixture Matrix

| Fixture | `properties` | `encrypted_properties` | Deleted | Purpose |
|---|---|---|---:|---|
| `plain-only` | `cm:title` | `{}` | false | plaintext candidate |
| `plain-null-encrypted` | `cm:title` | null | false | ready predicate handles null encrypted JSONB |
| `encrypted-only` | `{}` | `cm:title` | false | already encrypted |
| `dual-storage` | `cm:title` | `cm:title` | false | conflict blocker |
| `orphan-encrypted` | `{}` | `custom:orphan` | false | orphan encrypted payload warning |
| `deleted-ignored` | `cm:title` | `cm:title` | true | deleted rows excluded |

## 4. Expected Predicate Counts

For `cm:title`:

| Repository method | Expected |
|---|---:|
| `countByPropertyKeyAndDeletedFalse("cm:title")` | 3 |
| `countByEncryptedPropertyKeyAndDeletedFalse("cm:title")` | 2 |
| `countByPropertyKeyInBothStorageAndDeletedFalse("cm:title")` | 1 |
| `countBackfillReadyByPropertyKeyAndDeletedFalse("cm:title")` | 2 |
| `countByPropertyKeyAcrossStorageAndDeletedFalse("cm:title")` | 4 |

For `custom:orphan`:

| Repository method | Expected |
|---|---:|
| `countByPropertyKeyAndDeletedFalse("custom:orphan")` | 0 |
| `countByEncryptedPropertyKeyAndDeletedFalse("custom:orphan")` | 1 |
| `countBackfillReadyByPropertyKeyAndDeletedFalse("custom:orphan")` | 0 |

Encrypted aggregate checks:

| Repository method | Expected |
|---|---:|
| `countNodesWithEncryptedPropertiesAndDeletedFalse()` | 3 |
| `countEncryptedPropertyValuesAndDeletedFalse()` | 3 |
| `countEncryptedPropertyValuesByKeyVersionAndDeletedFalse()` | one row: `v1 -> 3` |

## 5. Verification

### 5.1 Targeted repository smoke

Command:

```bash
cd ecm-core
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=NodeRepositoryJsonbBackfillSmokeTest \
  test
```

Local result:

```text
NodeRepositoryJsonbBackfillSmokeTest tests=1 failures=0 errors=0 skipped=1
```

Skip reason:

```text
Docker not available for Testcontainers: Could not find a valid Docker environment.
```

This is an environment skip, not a test failure. It matches the repository's existing Testcontainers pattern used by Redis-backed tests.

### 5.2 Backfill dry-run regression suite

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
| PropertyEncryptionOperationsControllerSecurityTest | 6 | 0 | 0 | 0 |
| PropertyEncryptionOperationsServiceTest | 10 | 0 | 0 | 0 |
| NodeRepositoryJsonbBackfillSmokeTest | 1 | 0 | 0 | 1 |
| **Total** | **17** | **0** | **0** | **1** |

### 5.3 Diff hygiene

Command:

```bash
git diff --check
```

Result: clean.

## 6. Operational Interpretation

| Environment | Expected behavior |
|---|---|
| Local machine without Docker socket | smoke test is skipped by assumption |
| CI or developer machine with Docker | smoke test starts PostgreSQL and executes all JSONB predicate assertions |
| H2-only local unit flow | not used for this coverage because it would produce false confidence |

The presence of a skipped local smoke should not be treated as production validation of PostgreSQL JSONB behavior. It only proves that the test is wired safely into the suite. A Docker-enabled run is still required before promoting a mutating backfill executor.

## 7. Next Slice

The next safe step is backfill job design, not immediate mutation code.

Minimum design topics:

1. Job ledger schema and state machine.
2. Batch selection predicate that exactly matches the smoke-tested ready predicate.
3. Idempotency when a row is partially migrated.
4. Conflict handling if a dual-storage value appears between dry-run and execution.
5. Audit and operator visibility for skipped, migrated, conflicted, and failed values.
6. Cancellation and retry behavior.

