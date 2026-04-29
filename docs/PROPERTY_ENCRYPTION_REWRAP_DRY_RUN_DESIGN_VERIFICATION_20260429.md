# Property Encryption Rewrap Dry-Run Design and Verification

**Code commit:** `62bb168`
**Date:** 2026-04-29
**Scope:** Adds a non-mutating admin dry-run endpoint for encrypted node-property rewrap planning.

## 1. Context

The previous slice added read-only property encryption diagnostics:

| Endpoint | Commit |
|---|---|
| `GET /api/v1/admin/property-encryption/status` | `b6c0116` |
| `GET /api/v1/admin/property-encryption/definitions` | `b6c0116` |

This slice adds the next safe operations step:

| Endpoint | Behavior |
|---|---|
| `POST /api/v1/admin/property-encryption/rewrap-jobs/dry-run` | Aggregates encrypted payload counts by key version and reports whether a future rewrap job would be safe to start |

The endpoint is intentionally a dry-run only. It does not create jobs, update nodes, call encryption/decryption, write audit logs, or expose node identifiers.

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
| `targetKeyConfigured` | whether target key version exists in configured key names |
| `secretCryptoEnabled` | whether secret crypto is enabled |
| `candidateNodeCount` | non-deleted nodes with non-empty `encrypted_properties` |
| `encryptedPropertyValueCount` | total encrypted JSONB entries across non-deleted nodes |
| `valuesAlreadyOnTargetKeyCount` | encrypted values already using target key version |
| `valuesRequiringRewrapCount` | encrypted values not currently on target key version |
| `unversionedOrMalformedValueCount` | payloads that do not match a valid `enc:<version>:<payload>` envelope |
| `keyVersionCounts` | aggregate counts by source key version |
| `missingSourceKeyVersions` | source key versions found in payloads but absent from configured keys |
| `warnings` | machine-readable blockers or warnings |
| `executable` | true only when a future job has all required prerequisites |

No key material, ciphertext, plaintext, node id, node path, or raw JSON property data is returned.

## 3. Safety Rules

`executable=true` requires all of the following:

| Rule | Reason |
|---|---|
| `secretCryptoEnabled == true` | rewrap cannot safely operate while crypto is disabled |
| `targetKeyConfigured == true` | future write must be able to encrypt with target key |
| `missingSourceKeyVersions` is empty | future read must be able to decrypt every source payload |
| `unversionedOrMalformedValueCount == 0` | malformed envelopes require manual triage before batch work |
| `valuesRequiringRewrapCount > 0` | no-op jobs should not be started |

Warnings emitted by this slice:

| Warning | Trigger |
|---|---|
| `secret_crypto_disabled` | crypto service disabled |
| `target_key_version_required` | no target and no active key |
| `target_key_version_not_configured` | target key missing from configured key versions |
| `encrypted_payloads_without_key_version` | encrypted payload count exceeds valid-envelope count |
| `source_key_versions_not_configured` | at least one source key version is missing |

## 4. Repository Semantics

The key-version aggregate uses a PostgreSQL JSONB query over `nodes.encrypted_properties`.

Valid versioned payloads are limited by:

```sql
payload.value ~ '^enc:[^:]+:.+$'
```

This deliberately excludes malformed payloads such as `enc::x` and `enc:v1:`. Those are counted indirectly as `unversionedOrMalformedValueCount` by subtracting valid-envelope totals from total encrypted-property values.

Deleted nodes are excluded from all counts.

## 5. Implementation

| File | Change |
|---|---|
| `PropertyEncryptionOperationsController` | adds `POST /rewrap-jobs/dry-run` |
| `PropertyEncryptionOperationsService` | adds dry-run calculation and response records |
| `NodeRepository` | adds JSONB aggregate for encrypted values by source key version |
| `PropertyEncryptionOperationsControllerSecurityTest` | adds admin-only dry-run API coverage |
| `PropertyEncryptionOperationsServiceTest` | adds executable and blocked dry-run scenarios |

No Liquibase migration was required.

## 6. Verification

### 6.1 Targeted tests

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
| PropertyEncryptionOperationsControllerSecurityTest | 5 | 0 | 0 | 0 |
| PropertyEncryptionOperationsServiceTest | 5 | 0 | 0 | 0 |
| **Total** | **10** | **0** | **0** | **0** |

### 6.2 Related encryption regression suite

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
related_files=16 tests=39 failures=0 errors=0 skipped=0
```

### 6.3 Full security sweep

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
security_files=46 tests=419 failures=0 errors=0 skipped=0
```

### 6.4 Diff hygiene

Command:

```bash
git diff --check
```

Result: clean.

## 7. Non-Goals

| Non-goal | Reason |
|---|---|
| Actual rewrap execution | needs resumable job ledger, batching, audit, cancellation, and retry design |
| Backfill dry-run | needs separate JSONB semantics for plaintext encrypted-definition values, dual-storage conflicts, and orphan encrypted payloads |
| Property subset filtering | should be added with repository-level tests to avoid misleading aggregate counts |
| Returning node ids or paths | aggregate-only response avoids metadata leakage |
| Calling `protect(...)` or `reveal(...)` | dry-run must not require plaintext access or key material |

## 8. Next Slice

The next safe slice is `backfill-jobs/dry-run`, not mutation execution.

Minimum required design:

1. Count plaintext values for encrypted property definitions that still live in `nodes.properties`.
2. Count already-encrypted values in `nodes.encrypted_properties`.
3. Count dual-storage conflicts where the same property exists in both JSONB columns.
4. Count orphan encrypted payloads where the stored key is no longer encrypted by the current model definition.
5. Add repository-level JSONB tests against PostgreSQL semantics before trusting the API counts.
