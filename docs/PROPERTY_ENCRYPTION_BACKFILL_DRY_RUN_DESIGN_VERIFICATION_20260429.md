# Property Encryption Backfill Dry-Run Design and Verification

**Code commit:** `ba4ece0`
**Date:** 2026-04-29
**Scope:** Adds a non-mutating admin dry-run endpoint for encrypted property backfill planning.

## 1. Context

The property encryption operations track now has three API-first slices:

| Endpoint | Commit | Behavior |
|---|---|---|
| `GET /api/v1/admin/property-encryption/status` | `b6c0116` | Reports crypto readiness and aggregate encrypted-property state |
| `GET /api/v1/admin/property-encryption/definitions` | `b6c0116` | Lists encrypted property definitions from the runtime model |
| `POST /api/v1/admin/property-encryption/rewrap-jobs/dry-run` | `62bb168` | Estimates encrypted payload rewrap safety by key version |

This slice adds the backfill planning endpoint:

| Endpoint | Behavior |
|---|---|
| `POST /api/v1/admin/property-encryption/backfill-jobs/dry-run` | Estimates plaintext encrypted-definition values that would be copied from `nodes.properties` into `nodes.encrypted_properties` |

The endpoint is intentionally a dry-run only. It does not create a job, return a `Location` header, update nodes, call encryption/decryption, write audit logs, create tasks, or expose node ids, paths, plaintext, ciphertext, or raw JSON payloads.

## 2. API Contract

### 2.1 Request

```json
{
  "targetKeyVersion": "v2"
}
```

If `targetKeyVersion` is omitted or blank, the service uses `ecm.security.secret.active-key-version`.

### 2.2 Response

| Field | Meaning |
|---|---|
| `targetKeyVersion` | requested or active target key version |
| `targetKeyConfigured` | whether the target key exists in configured key names |
| `secretCryptoEnabled` | whether secret crypto is enabled |
| `encryptedPropertyDefinitionCount` | current runtime-model definitions with `encrypted=true` |
| `plaintextValueCount` | total non-deleted node values for encrypted definitions still stored in `nodes.properties` |
| `alreadyEncryptedValueCount` | total non-deleted node values for encrypted definitions already stored in `nodes.encrypted_properties` |
| `dualStorageConflictValueCount` | total values where the same property key exists in both JSONB columns |
| `readyValueCount` | values ready for backfill: present in `properties` and absent from `encrypted_properties` |
| `orphanEncryptedValueCount` | encrypted-property JSONB values that do not correspond to current encrypted definitions |
| `definitionCounts` | per-definition aggregate counts by `qualifiedName` |
| `warnings` | machine-readable blockers or warnings |
| `executable` | true only when a future backfill job has all required prerequisites |

## 3. Repository Semantics

All counts exclude deleted nodes.

| Count | SQL predicate |
|---|---|
| Plaintext | `jsonb_exists(n.properties, :propertyKey)` |
| Already encrypted | `jsonb_exists(n.encrypted_properties, :propertyKey)` |
| Dual-storage conflict | key exists in both `n.properties` and `n.encrypted_properties` |
| Ready | key exists in `n.properties` and `n.encrypted_properties` is null or does not contain the key |
| Orphan encrypted payloads | total encrypted JSONB entries minus encrypted values matching current encrypted definitions |

The ready count is intentionally a direct SQL predicate. It is not derived as `plaintext - dual`, because that derived formula can mask future predicate drift if either source count changes independently.

## 4. Safety Rules

`executable=true` requires all of the following:

| Rule | Reason |
|---|---|
| `secretCryptoEnabled == true` | future execution must be able to encrypt values |
| `targetKeyConfigured == true` | future writes need a configured target key |
| at least one encrypted property definition exists | no model definition means there is nothing safe to backfill |
| `dualStorageConflictValueCount == 0` | conflicting columns need manual or purpose-built reconciliation |
| `readyValueCount > 0` | no-op jobs should not be started |

Warnings emitted by this slice:

| Warning | Trigger | Blocks executable |
|---|---|---|
| `secret_crypto_disabled` | crypto service disabled | yes |
| `target_key_version_required` | no request target and no active key | yes |
| `target_key_version_not_configured` | target key missing from configured key versions | yes |
| `no_encrypted_property_definitions` | runtime model has no encrypted property definitions | yes |
| `dual_storage_conflicts_detected` | a property key exists in both JSONB columns | yes |
| `orphan_encrypted_payloads_detected` | encrypted JSONB contains keys outside current encrypted definitions | no |

Orphan encrypted payloads are warning-only in this slice because the future backfill executor should migrate current encrypted-definition plaintext values. Historical orphan cleanup is a separate repair operation with different safety semantics.

## 5. Implementation

| File | Change |
|---|---|
| `PropertyEncryptionOperationsController` | adds `POST /backfill-jobs/dry-run` |
| `PropertyEncryptionOperationsService` | adds backfill dry-run calculation and response records |
| `NodeRepository` | adds JSONB count methods for encrypted keys, dual-storage conflicts, and direct ready values |
| `PropertyEncryptionOperationsControllerSecurityTest` | covers admin-only access and response shape |
| `PropertyEncryptionOperationsServiceTest` | covers executable, blocker, no-definition, no-ready, and orphan-warning scenarios |

No Liquibase migration was required.

## 6. Design Constraints and Limitations

| Constraint | Current decision |
|---|---|
| Scope | aggregate-only dry-run; no mutation path |
| Sensitive output | no node ids, paths, plaintext, ciphertext, raw JSON, or key material |
| Property matching | counts by `qualifiedName` JSON key across all non-deleted nodes |
| Owner/type filtering | not enforced in this slice |
| Future executor contract | must use the same key predicate or add owner-scoped SQL before mutation |
| Repository-level PostgreSQL smoke | deferred; required before trusting a future mutating executor |

The owner/type filtering limitation is acceptable for this dry-run because it mirrors the current JSONB key-based backfill predicate. It must not be silently widened into mutation execution without either preserving the exact predicate or adding owner-scoped repository tests.

## 7. Verification

### 7.1 Targeted tests

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
| PropertyEncryptionOperationsControllerSecurityTest | 6 | 0 | 0 | 0 |
| PropertyEncryptionOperationsServiceTest | 10 | 0 | 0 | 0 |
| **Total** | **16** | **0** | **0** | **0** |

### 7.2 Related encryption regression suite

Command:

```bash
cd ecm-core
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=PropertyEncryptionOperationsServiceTest,PropertyEncryptionOperationsControllerSecurityTest,NodePropertyEncryptionServiceTest,RuntimeModelValidationServiceTest,ContentModelServiceTest,SecretCryptoServiceTest,MailAccountSecretPersistenceTest,OAuthCredentialPersistenceTest \
  test
```

Result:

```text
related_files=16 tests=45 failures=0 errors=0 skipped=0
```

### 7.3 Full security sweep

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
security_files=46 tests=420 failures=0 errors=0 skipped=0
```

### 7.4 Diff hygiene

Command:

```bash
git diff --check
```

Result: clean.

## 8. Next Slice

Do not jump directly to mutation execution.

Recommended next step:

1. Add PostgreSQL-backed repository smoke coverage for the JSONB predicates used by this dry-run.
2. Design the actual backfill job ledger, batching, retry, cancellation, audit, and idempotency rules.
3. Only then add `POST /backfill-jobs` as a mutating operation.

