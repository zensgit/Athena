# Property Encryption Operations API-First Design and Verification

**Code commit:** `b6c0116`
**Date:** 2026-04-29
**Scope:** Adds a read-only admin diagnostics API for model-backed property encryption operations.

## 1. Context

Property encryption was already implemented before this slice:

| Existing capability | Current implementation |
|---|---|
| Secret encryption foundation | `SecretCryptoService`, `EncryptedSecretConverter`, `SecretCryptoProperties` |
| Model-backed encrypted property metadata | `PropertyDefinition.encrypted` and `PropertyDefinitionDto.encrypted` |
| Encrypted node storage | `Node.encryptedProperties` backed by `nodes.encrypted_properties` |
| Write-path protection | `NodePropertyEncryptionService.prepareForPersistence(...)` |
| Read projection | `NodePropertyEncryptionService.resolveReadableProperties(...)` |
| Search safety | encrypted properties removed from indexable property maps |

Earlier closeout docs correctly avoided an admin UI-first approach. The missing piece was an API-first operations surface that lets admins inspect encryption readiness without exposing secret values or introducing destructive actions.

## 2. Design Decision

This slice intentionally ships read-only diagnostics only:

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/admin/property-encryption/status` | report secret crypto readiness, active key version name, configured key version names, encrypted definition counts, encrypted payload counts, and warnings |
| `GET /api/v1/admin/property-encryption/definitions` | list encrypted model property definitions with owner metadata |

No rotate, rewrap, backfill, reveal, or masking-policy mutation endpoint is included in this slice.

Reason: rotation and rewrap require a stronger design for dry-run, idempotency, audit, resumability, rollback, and failure recovery. Exposing those as a quick admin button would create operational risk.

## 3. API Contract

### 3.1 Status response

Returned by `GET /api/v1/admin/property-encryption/status`:

| Field | Meaning |
|---|---|
| `secretCryptoEnabled` | whether `SecretCryptoService` is enabled |
| `activeKeyVersion` | active key version name only |
| `activeKeyConfigured` | whether the active key version exists in configured key names |
| `configuredKeyVersions` | sorted key version names only, never key material |
| `encryptedPropertyDefinitionCount` | number of model property definitions marked encrypted |
| `encryptedTypePropertyDefinitionCount` | encrypted definitions owned by type definitions |
| `encryptedAspectPropertyDefinitionCount` | encrypted definitions owned by aspect definitions |
| `nodesWithEncryptedPropertiesCount` | non-deleted nodes with non-empty `encrypted_properties` |
| `encryptedPropertyValueCount` | total encrypted JSONB entries across non-deleted nodes |
| `warnings` | machine-readable diagnostics |

Current warnings:

| Warning | Trigger |
|---|---|
| `encrypted_property_definitions_require_secret_crypto` | encrypted definitions exist while secret crypto is disabled |
| `encrypted_node_payloads_require_secret_crypto` | encrypted node payloads exist while secret crypto is disabled |
| `active_secret_key_version_is_not_configured` | secret crypto is enabled but the active key version is not configured |

### 3.2 Definitions response

Returned by `GET /api/v1/admin/property-encryption/definitions`:

| Field | Meaning |
|---|---|
| `id` | property definition id |
| `qualifiedName` | model-qualified property name |
| `name` | local property name |
| `title` | display title |
| `ownerKind` | `TYPE`, `ASPECT`, or `UNASSIGNED` |
| `ownerQName` | owning type/aspect qualified name |
| `dataType` | property data type |
| `mandatory` | model mandatory flag |
| `multiValued` | model multi-value flag |
| `indexed` | index flag, expected false for valid encrypted properties |

The definitions endpoint does not read node values and does not return ciphertext or plaintext.

## 4. Security Model

The controller is class-level admin-only:

```java
@RequestMapping("/api/v1/admin/property-encryption")
@PreAuthorize("hasRole('ADMIN')")
```

Security assertions:

| Caller | Expected |
|---|---|
| anonymous | 401 |
| `ROLE_USER` | 403 for both endpoints |
| `ROLE_ADMIN` | 200 for both endpoints |

The API returns only key version names. It never returns encryption keys, encrypted payload strings, decrypted property values, or node identifiers.

## 5. Implementation

| File | Change |
|---|---|
| `PropertyEncryptionOperationsController` | new admin read-only REST controller |
| `PropertyEncryptionOperationsService` | new service for status and definition summaries |
| `PropertyDefinitionRepository` | `findByEncryptedTrue()` query derivation |
| `NodeRepository` | JSONB count helpers for encrypted payload diagnostics |
| `PropertyEncryptionOperationsControllerSecurityTest` | security and API shape tests |
| `PropertyEncryptionOperationsServiceTest` | warning, key-version redaction, count, and sorting tests |

No Liquibase migration was required. This slice reads fields added by existing migration `079-add-node-encrypted-properties.xml`.

## 6. Verification

### 6.1 Targeted API tests

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
| PropertyEncryptionOperationsControllerSecurityTest | 4 | 0 | 0 | 0 |
| PropertyEncryptionOperationsServiceTest | 3 | 0 | 0 | 0 |
| **Total** | **7** | **0** | **0** | **0** |

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
related_files=16 tests=36 failures=0 errors=0 skipped=0
```

This includes:

| Area | Evidence |
|---|---|
| New operations API | controller and service tests |
| Existing node property encryption | `NodePropertyEncryptionServiceTest` |
| Existing model validation and authoring | `RuntimeModelValidationServiceTest`, `ContentModelServiceTest` nested reports |
| Shared secret crypto | `SecretCryptoServiceTest` |
| Shared JPA converter smoke | `MailAccountSecretPersistenceTest`, `OAuthCredentialPersistenceTest` |

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
security_files=46 tests=418 failures=0 errors=0 skipped=0
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
| Rotation endpoint | needs dry-run, idempotency, audit, resumability, and failure recovery design |
| Rewrap/backfill endpoint | potentially mutates many nodes and requires batch ledger semantics |
| Reveal/masking policy API | would change current readable projection semantics and needs a product decision |
| Frontend admin page | should consume this API later; UI-first was intentionally avoided |
| Key material exposure | diagnostics must only expose key version names |

## 8. Next Slice

The next safe slice is an operations design for rewrap/backfill, not implementation:

1. Define a dry-run response: impacted node count, impacted property count, active key version, target key version, and unsupported states.
2. Define a resumable job ledger: job id, actor, started/finished timestamps, per-node failure count, retry state, and cancellation.
3. Define audit events: dry-run requested, job started, node batch completed, job failed, job completed.
4. Only after that, implement a mutation endpoint such as `POST /api/v1/admin/property-encryption/rewrap-jobs`.
