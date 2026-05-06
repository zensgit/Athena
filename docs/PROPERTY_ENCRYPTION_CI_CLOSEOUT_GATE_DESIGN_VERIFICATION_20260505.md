# Property Encryption CI Closeout Gate Design Verification

Date: 2026-05-05

## Context

Property Encryption code paths are locally verified, but the remaining closeout evidence is Docker-backed PostgreSQL/Testcontainers execution. The local machine still has no reachable Docker socket, so the final gate must run on CI or another Docker-capable host.

This slice wires the existing closeout preflight into GitHub Actions and hardens the Docker-backed backend gate so CI uses a local Maven binary instead of the Dockerized `ecm-core/./mvnw` wrapper when Maven is available.

## Design

### CI Job

Added a dedicated job to `.github/workflows/ci.yml`:

```text
property_encryption_closeout
```

The job:

- runs after `backend` and `frontend`
- sets up Java 17 with Maven cache
- sets up Node.js 20 with npm cache
- installs frontend dependencies
- runs `scripts/property-encryption-closeout-preflight.sh`
- sets `REQUIRE_DOCKER_BACKED_GATE=1`

`REQUIRE_DOCKER_BACKED_GATE=1` makes the Docker-backed PostgreSQL gate mandatory in CI. If Docker is unavailable or Testcontainers fails, the job fails instead of reporting a local-only closeout.

### Maven Selection

Updated `scripts/property-encryption-backfill-gate.sh` to resolve Maven in this order:

1. caller-provided `MAVEN_BIN`
2. `/tmp/apache-maven-3.9.9/bin/mvn`
3. `mvn` on `PATH`
4. fallback `./mvnw`

This matters because `ecm-core/./mvnw` runs Maven inside a Docker container. Testcontainers should run from the host runner Maven process so it can use the runner Docker API directly.

### Preflight Propagation

Updated `scripts/property-encryption-closeout-preflight.sh` to export the resolved `MAVEN_BIN` before invoking `scripts/property-encryption-backfill-gate.sh`.

This keeps non-Docker and Docker-backed backend evidence on the same Maven binary in CI.

## Verification

### Syntax And Workflow Parse

Command:

```bash
bash -n scripts/property-encryption-backfill-gate.sh
bash -n scripts/property-encryption-closeout-preflight.sh
ruby -e "require 'yaml'; YAML.load_file('.github/workflows/ci.yml'); puts 'workflow yaml ok'"
```

Result:

```text
workflow yaml ok
```

### Local Reduced Preflight

Command:

```bash
RUN_FRONTEND_BUILD=0 \
RUN_PHASE5_REGISTRY=0 \
RUN_DOCKER_BACKED_GATE=0 \
BACKEND_NON_DOCKER_TESTS=PropertyEncryptionOperationsServiceTest \
FRONTEND_TEST_PATHS=src/services/propertyEncryptionService.test.ts \
scripts/property-encryption-closeout-preflight.sh
```

Result:

```text
PropertyEncryptionOperationsServiceTest: Tests run: 38, Failures: 0, Errors: 0, Skipped: 0
propertyEncryptionService.test.ts: Test Suites: 1 passed, Tests: 3 passed
frontend lint: passed
property_encryption_closeout_preflight: ok
```

### Local Docker Blocker Check

Command:

```bash
DOCKER_CHECK_TIMEOUT_SECONDS=1 \
BACKEND_TESTS=PropertyEncryptionBackfillPostgresIntegrationTest \
scripts/property-encryption-backfill-gate.sh
```

Result:

```text
property_encryption_backfill_gate: Docker API is not reachable; this gate requires Docker because Testcontainers depend on it.
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock
expected_docker_block_exit=1
```

This confirms the local host still blocks Docker-backed evidence at the environment layer and the script fails before Maven/Testcontainers.

## Remaining Work

- Push this branch and let GitHub Actions run `Property Encryption Closeout Gate`.
- Record the first green CI run in the final Property Encryption acceptance matrix.
- If that job fails, treat the failure as the next concrete closeout bug: Maven/tooling, Testcontainers, PostgreSQL JSONB migration, or concurrency behavior.
