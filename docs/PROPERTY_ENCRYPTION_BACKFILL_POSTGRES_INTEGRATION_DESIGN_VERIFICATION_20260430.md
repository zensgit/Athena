# Property Encryption Backfill PostgreSQL Integration Design + Verification

Date: 2026-04-30

## Context

The property-encryption operations backend already had focused service, repository, controller-security, runner, and recovery tests. The remaining verification gap was that the full backfill execution path was not pinned against a real PostgreSQL JSONB database after job planning.

This slice adds that missing service-level integration test without expanding into full MockMvc, async executor, or security context startup.

## Design

### Added Test

`ecm-core/src/test/java/com/ecm/core/service/PropertyEncryptionBackfillPostgresIntegrationTest.java`

The test starts `postgres:15-alpine` through Testcontainers and wires a narrow Spring context:

- JPA repositories: `NodeRepository`, `ContentModelDefinitionRepository`, `TypeDefinitionRepository`, `PropertyDefinitionRepository`, `PropertyEncryptionBackfillJobRepository`
- Entities under `com.ecm.core.entity` and `com.ecm.core.model`
- Real `SecretCryptoService` with an in-test AES key
- Real `PropertyEncryptionOperationsService`

The test seeds:

- An active `cm` content model
- A `cm:folder` type
- An encrypted `cm:secretCode` property definition
- A folder node with plaintext JSONB property `cm:secretCode = SEC-42`

It then verifies:

- `dryRunBackfill(... v1 ...)` is executable
- `planBackfillJob(... v1 ...)` creates a `PLANNED` job with one ready value
- `runBackfillJob(...)` reaches `SUCCEEDED`
- Counters show one processed and one migrated value
- The node no longer has plaintext `properties.cm:secretCode`
- The node has `encrypted_properties.cm:secretCode`
- The encrypted value decrypts back to `"SEC-42"`
- The CAS update wrote the requested actor into `lastModifiedBy`

### Scope Control

This is intentionally a service/repository integration test, not a full HTTP test.

The purpose is to validate the database-sensitive path:

- PostgreSQL JSONB candidate discovery
- `jsonb_exists(...)` native predicates
- CAS native update
- property-definition-driven job planning
- job terminal-state persistence
- real encryption payload round-trip

Full controller, Spring Security, async proxy, and scheduler behavior remain covered by narrower existing tests.

### Gate Script Update

`scripts/property-encryption-backfill-gate.sh` now includes `PropertyEncryptionBackfillPostgresIntegrationTest` in its default `BACKEND_TESTS` list.

The gate already fails fast when Docker is unavailable because both `ecm-core/mvnw` and Testcontainers require a reachable Docker API in this environment.

## Verification

### Static Diff Check

Command:

```bash
git diff --check
```

Result: passed.

### Compile Check

Command:

```bash
cd ecm-core
CP="target/classes:target/test-classes:$(find .m2-cache/repository -name '*.jar' -type f | tr '\n' ':')"
LOMBOK="$(find .m2-cache/repository -name 'lombok-*.jar' -type f | head -n 1)"
javac -parameters -cp "$CP" -processorpath "$LOMBOK" \
  -d /tmp/athena-property-encryption-pg-test-classes \
  src/test/java/com/ecm/core/service/PropertyEncryptionBackfillPostgresIntegrationTest.java
```

Result: passed.

### Gate Script Dry Run

Command:

```bash
scripts/property-encryption-backfill-gate.sh
```

Result: failed before Maven execution because Docker is not reachable locally.

Observed output:

```text
property_encryption_backfill_gate: Docker API is not reachable; this gate requires Docker because ecm-core/mvnw and Testcontainers depend on it.
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock
```

This is an environment blocker, not a test failure. The script behavior is expected: it refuses to run a Docker-dependent gate without Docker instead of silently skipping the integration path.

## Remaining Work

To fully close the property-encryption operations gate:

1. Run `scripts/property-encryption-backfill-gate.sh` on a machine or CI runner with Docker available.
2. If green, treat the backfill plan/execute PostgreSQL path as covered.
3. Continue with the next product slice: admin operations UI or API-first rewrap execution, depending on priority.

