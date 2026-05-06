# Property Encryption Closeout Preflight Design Verification

Date: 2026-05-05

## Context

Property Encryption code work is now functionally complete except for the Docker-backed PostgreSQL gate that must run on a Docker-capable host. The previous closeout checklist had the required commands spread across several design and verification documents.

This slice adds a single executable local preflight that gathers the non-Docker evidence chain and makes the Docker-backed gate state explicit.

## Design

### Script

Added:

```bash
scripts/property-encryption-closeout-preflight.sh
```

The script runs from the repo root and performs:

- shell syntax checks for the property-encryption backfill gate and Phase 5 regression script
- `git diff --check`
- backend non-Docker evidence suite
- frontend targeted Jest suite
- frontend lint
- frontend production build
- Phase 5 registry-only preflight
- Docker-backed PostgreSQL gate precheck

### Backend Evidence Suite

Default backend suite:

```text
PropertyEncryptionOperationsServiceTest
PropertyEncryptionOperationsControllerSecurityTest
NodePropertyEncryptionServiceTest
NodeControllerAspectTest
DocumentControllerCheckoutTest
ContentTypeControllerPreviewSemanticsTest
SearchIndexServiceSubtreeReindexTest
```

This covers:

- backfill and rewrap operations service behavior
- admin controller security
- protected payload redaction
- public response masking
- node, document, and content-type response DTO wiring
- search index projection hardening

The script uses `MAVEN_BIN` if provided. Otherwise it prefers `/tmp/apache-maven-3.9.9/bin/mvn`, then falls back to `mvn` on `PATH`.

### Frontend Evidence Suite

Default frontend suite:

```text
src/services/propertyEncryptionService.test.ts
src/pages/PropertyEncryptionOperationsPage.test.tsx
src/utils/propertyRedactionUtils.test.ts
```

This covers:

- property-encryption service API wrappers
- admin rewrap execution UI
- frontend protected-payload redaction utility

### Docker Gate Handling

By default, the script tries to run the Docker-backed gate if Docker is reachable:

```bash
RUN_DOCKER_BACKED_GATE=1
```

If Docker is not reachable, the script reports:

```text
property_encryption_closeout_preflight: Docker-backed gate BLOCKED because Docker API is not reachable.
```

and continues when:

```bash
REQUIRE_DOCKER_BACKED_GATE=0
```

For final benchmark closeout on CI or a Docker-capable host, run:

```bash
REQUIRE_DOCKER_BACKED_GATE=1 scripts/property-encryption-closeout-preflight.sh
```

That mode fails if Docker is unavailable or if `scripts/property-encryption-backfill-gate.sh` fails.

## Verification

### Command

```bash
scripts/property-encryption-closeout-preflight.sh
```

### Result

Backend non-Docker evidence:

```text
Tests run: 75, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Frontend targeted evidence:

```text
Test Suites: 3 passed, 3 total
Tests: 10 passed, 10 total
```

Frontend lint:

```text
passed
```

Frontend production build:

```text
Compiled successfully.
```

Phase 5 registry-only preflight:

```text
expected events: 24
observed markers in specs: 24
missing_from_events_file_count: 0
stale_events_file_entries_count: 0
OK registry matches spec markers
```

Docker-backed PostgreSQL gate:

```text
property_encryption_closeout_preflight: Docker-backed gate BLOCKED because Docker API is not reachable.
property_encryption_closeout_preflight: continuing because REQUIRE_DOCKER_BACKED_GATE=0
```

### Interpretation

The local closeout preflight is green for all non-Docker evidence.

The remaining benchmark blocker is the Docker-backed PostgreSQL gate. It must be run on CI or another Docker-capable host with:

```bash
REQUIRE_DOCKER_BACKED_GATE=1 scripts/property-encryption-closeout-preflight.sh
```

## Remaining Work

- Run the preflight in `REQUIRE_DOCKER_BACKED_GATE=1` mode on a Docker-capable runner.
- Record the first Docker-backed green run in the final acceptance matrix.
- If the Docker-backed gate fails, fix the concrete PostgreSQL/Testcontainers issue and rerun the same script.
