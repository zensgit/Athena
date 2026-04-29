# Property Encryption Backfill Candidate Selection - Design and Verification

Date: 2026-04-29

Code commit: `eaf331e feat(security): add property encryption backfill candidate selection`

## Context

This slice follows the property encryption dry-run and backfill job ledger work. The goal is to lock down the executor's candidate-selection boundary before any node mutation is introduced.

The implementation intentionally does not add a controller endpoint. It adds an internal repository query and service preview contract so the next executor slice can reuse the same predicate and row shape.

## Design

### Repository candidate query

`NodeRepository.findBackfillCandidatesByPropertyKeyAndDeletedFalse(propertyKey, limit)` selects nodes where:

- `nodes.is_deleted = false`
- `jsonb_exists(nodes.properties, :propertyKey)`
- `nodes.encrypted_properties IS NULL OR NOT jsonb_exists(nodes.encrypted_properties, :propertyKey)`

This matches the existing backfill-ready count predicate and keeps dual-storage rows out of the executor candidate set.

The row projection is internal and typed:

- `nodeId`
- `propertyKey`
- `plaintextJson`
- `entityVersion`

`plaintextJson` is selected as `CAST(n.properties -> :propertyKey AS text)`, not `->>`. This preserves JSON literal semantics for strings, numbers, booleans, objects, and arrays so the future executor can encrypt the original JSON value shape instead of a lossy text conversion.

`entityVersion` is included now to support a future repository-level compare-and-set update. The next mutation slice should not load `Node` entities and call `save`, because that would risk entity lifecycle side effects outside the encryption backfill boundary.

### Service contract

`PropertyEncryptionOperationsService.previewBackfillCandidates(...)` adds a read-only service contract with:

- required trimmed `qualifiedName`
- bounded limit, clamped to `1..1000`
- repository-backed candidate lookup
- output that strips plaintext and exposes only `nodeId` plus `qualifiedName`

The service receives `plaintextJson` from the repository projection only for the future executor seam. It does not return plaintext or encrypted values to callers.

### Non-goals

- No node mutation.
- No encryption or decryption.
- No start endpoint.
- No controller API.
- No plaintext or encrypted payload in API DTOs.
- No `Node` entity load-and-save backfill path.

## Verification

### Targeted service and JSONB smoke command

```bash
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=.m2-cache/repository -Dspring.profiles.active=test -Dtest=NodeRepositoryJsonbBackfillSmokeTest,PropertyEncryptionOperationsServiceTest test
```

Result:

- `PropertyEncryptionOperationsServiceTest`: 14 tests, 0 failures, 0 errors, 0 skipped
- `NodeRepositoryJsonbBackfillSmokeTest`: 1 test, 0 failures, 0 errors, 1 skipped

Local limitation: the PostgreSQL JSONB smoke test was skipped because this machine does not expose a Docker socket for Testcontainers. The typed JSON literal assertions compile and are ready for Docker-enabled local or CI execution.

### Controller security command

```bash
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q -Dmaven.repo.local=.m2-cache/repository -Dspring.profiles.active=test -Dtest=PropertyEncryptionOperationsServiceTest,PropertyEncryptionOperationsControllerSecurityTest test
```

Result:

- `PropertyEncryptionOperationsServiceTest`: 14 tests, 0 failures, 0 errors, 0 skipped
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

The next safe slice is a repository-level compare-and-set update method for one candidate row. It should update only when:

- node id matches
- entity version matches
- plaintext property key still exists
- encrypted property key is still absent
- node is still not deleted

That method should be covered by the Docker-backed PostgreSQL smoke test before any scheduled executor or start endpoint is added.
