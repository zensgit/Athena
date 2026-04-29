# Property Encryption Backfill CAS Update - Design and Verification

Date: 2026-04-29

Code commit: `bc1019a feat(security): add property encryption backfill cas update`

## Context

This slice extends the property encryption backfill candidate-selection work with a one-row compare-and-set update. It is still an internal execution primitive only.

No controller endpoint, start endpoint, scheduler, or job-runner loop is added in this slice. The goal is to prove the atomic node mutation boundary before connecting it to the backfill job ledger.

## Design

### Repository CAS update

`NodeRepository.backfillEncryptedPropertyIfUnchanged(...)` performs a native PostgreSQL update against `nodes`.

The update moves one property from plaintext JSONB storage to encrypted JSONB storage:

- remove `propertyKey` from `nodes.properties`
- add `propertyKey -> encryptedValue` to `nodes.encrypted_properties`
- set `last_modified_date`
- set `last_modified_by`
- increment `version`

It updates only when all CAS predicates still hold:

- `id = :nodeId`
- `is_deleted = false`
- `version = :expectedVersion`
- plaintext key still exists in `properties`
- `CAST(properties -> :propertyKey AS text) = :expectedPlaintextJson`
- encrypted key is still absent from `encrypted_properties`

Return value semantics:

- `1`: candidate migrated
- `0`: CAS miss, stale version, plaintext drift, deleted row, key missing, or encrypted key already present

The repository deliberately does not expose the reason for a `0` result. The future executor can count it as skipped/CAS-miss without leaking node state details.

### Service contract

`PropertyEncryptionOperationsService.applyBackfillCandidateUpdate(...)` is the internal one-row service contract.

It validates:

- candidate exists
- `nodeId` exists
- `qualifiedName` exists
- `plaintextJson` exists
- `entityVersion` exists
- secret crypto is enabled

It then calls:

```java
secretCryptoService.protect(candidate.getPlaintextJson())
```

The plaintext JSON string is the exact value selected by the candidate query via `CAST(properties -> :propertyKey AS text)`. This avoids a deserialize/serialize round trip and preserves JSON literal shape for strings, numbers, booleans, objects, and arrays.

The service rejects the update if `protect(...)` does not return an encrypted payload. It returns only:

- `nodeId`
- `qualifiedName`
- `migrated`

It does not return plaintext JSON or encrypted payloads.

### Non-goals

- No public API.
- No controller method.
- No scheduled executor.
- No backfill job state transition.
- No batch loop.
- No retry policy.
- No sensitive payload returned to API callers.

## Verification

### Targeted service and PostgreSQL smoke command

```bash
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=.m2-cache/repository -Dspring.profiles.active=test -Dtest=NodeRepositoryJsonbBackfillSmokeTest,PropertyEncryptionOperationsServiceTest test
```

Result:

- `PropertyEncryptionOperationsServiceTest`: 18 tests, 0 failures, 0 errors, 0 skipped
- `NodeRepositoryJsonbBackfillSmokeTest`: 1 test, 0 failures, 0 errors, 1 skipped

Local limitation: the PostgreSQL JSONB smoke test was skipped because this machine does not expose `/var/run/docker.sock` for Testcontainers. The test code now covers the CAS SQL success and miss cases, but it needs Docker-enabled local or CI execution to actually exercise PostgreSQL.

### Controller security command

```bash
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=.m2-cache/repository -Dspring.profiles.active=test -Dtest=PropertyEncryptionOperationsServiceTest,PropertyEncryptionOperationsControllerSecurityTest test
```

Result:

- `PropertyEncryptionOperationsServiceTest`: 18 tests, 0 failures, 0 errors, 0 skipped
- `PropertyEncryptionOperationsControllerSecurityTest`: 8 tests, 0 failures, 0 errors, 0 skipped

### Full security sweep command

```bash
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=.m2-cache/repository -Dspring.profiles.active=test '-Dtest=*SecurityTest' test
```

Result:

- 46 `*SecurityTest` files
- 422 tests
- 0 failures
- 0 errors
- 0 skipped

### Static checks

```bash
git diff --check
perl -ne 'print "$ARGV:$.:$_" if /[^\x00-\x7F]/' ecm-core/src/main/java/com/ecm/core/repository/NodeRepository.java ecm-core/src/main/java/com/ecm/core/service/PropertyEncryptionOperationsService.java ecm-core/src/test/java/com/ecm/core/repository/NodeRepositoryJsonbBackfillSmokeTest.java ecm-core/src/test/java/com/ecm/core/service/PropertyEncryptionOperationsServiceTest.java
```

Result:

- no whitespace errors
- no non-ASCII introduced in edited code files

## Next Slice

The next safe slice is the internal batch executor that consumes planned backfill jobs:

- load planned job snapshot
- iterate encrypted property definitions
- fetch bounded candidate batches
- call the one-row CAS service method
- update processed/migrated/skipped/failed counters
- mark job completed or failed

That slice should still avoid adding an external start endpoint until the executor accounting is covered by service tests.
