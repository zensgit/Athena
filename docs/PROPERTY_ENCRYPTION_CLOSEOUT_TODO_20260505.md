# Property Encryption Closeout And TODO

Date: 2026-05-05

## Current State

Branch evidence:

- Latest local commit: `409f836 feat(security): add property encryption rewrap job ledger`
- Working tree note: `.env` has pre-existing local changes and is intentionally excluded from this closeout.

Completed delivery:

- Backfill API-first workflow is implemented: dry-run, plan, run, cancel, async runner, stale-job recovery, and PostgreSQL-focused integration coverage.
- Property Encryption admin operations page is implemented: route, menu entry, service client, UI, unit tests, and mocked browser smoke registration.
- Rewrap backend workflow is implemented: dry-run, persisted planned-job ledger, plan/list/get, run/cancel, bounded async runner, JSONB candidate selection, and compare-and-set update.
- Protocol endpoint security-test expansion has covered Transfer Receiver, WOPI Host, CMIS AtomPub, and CMIS Browser patterns in prior security-test docs.

Verified evidence from latest rewrap-ledger slice:

- `PropertyEncryptionOperationsServiceTest,PropertyEncryptionOperationsControllerSecurityTest`: 52 tests passed after rewrap execution backend was added.
- `PropertyEncryptionBackfillPostgresIntegrationTest`: build success, two Docker/Testcontainers tests skipped locally because Docker was unavailable.
- `xmllint` on the rewrap changelog and master changelog: passed.
- `git diff --check`: passed.
- Frontend admin smoke was previously registered in the Phase 5 mocked gate and passed as a targeted Playwright smoke.

Known environment constraint:

- The repo `./mvnw` delegates to Docker and fails locally when Docker socket is unavailable.
- Use a temporary/local Maven binary with `.m2-cache/repository` for non-Docker unit tests, or run Docker-gated checks on CI or a Docker-capable host.

## TODO List

### P0: Docker-Backed Rewrap/Backfill Verification

Goal: convert the locally compiled PostgreSQL rewrap/backfill coverage into real Docker-backed evidence.

Required checks:

- Run `PropertyEncryptionBackfillPostgresIntegrationTest` on a Docker-capable runner.
- Confirm the backfill PostgreSQL path migrates plaintext encrypted properties into encrypted storage.
- Confirm the rewrap PostgreSQL path migrates existing `enc:v0:` payloads to active `enc:v1:` payloads without changing revealed plaintext.
- Record the first Docker-backed green result in a final closeout doc.

Expected blocker:

- Local host currently lacks a reachable Docker socket, so this must run on CI or a Docker-capable machine.

Estimated effort: `0.5-1 person-day`.

Risk buffer: add `1-2 person-days` if PostgreSQL/Testcontainers exposes a real migration, JSONB query, or concurrency issue.

### Completed: Rewrap Execution Backend

Goal: turn the planned rewrap ledger into a safe backend execution workflow.

Delivered changes:

- Added `POST /api/v1/admin/property-encryption/rewrap-jobs/{jobId}/run`.
- Added `POST /api/v1/admin/property-encryption/rewrap-jobs/{jobId}/cancel`.
- Added repository compare-and-set claim for `PLANNED -> RUNNING`.
- Added terminal update path for `SUCCEEDED`, `FAILED`, `CANCEL_REQUESTED`, and `CANCELLED`.
- Added JSONB candidate selection for encrypted properties whose payload key version is not the target key.
- For each candidate, reveal the old protected value, protect it with the target key, and update the node encrypted-property JSONB with compare-and-set semantics.
- Maintain execution counters: processed, rewrapped, skipped, failed.
- Keep error strings bounded and avoid logging plaintext values or key material.

Delivered tests:

- Successful rewrap execution updates counters and marks the job `SUCCEEDED`.
- Target/active key drift fails safely before mutation.
- CAS miss increments skipped count without corrupting data.
- Cancel requested before candidate processing marks `CANCELLED`.
- Non-admin users receive 403 for run/cancel endpoints.
- PostgreSQL rewrap integration test is present and compiles; Docker is required to execute it.

Status: implemented locally; pending Docker-backed PostgreSQL execution.

### P1: Rewrap Execution UI

Goal: expose rewrap execution only after the backend can safely mutate payloads.

Required changes:

- Show rewrap planned-job ledger on the Property Encryption admin page.
- Add run/cancel controls after backend execution endpoints exist.
- Surface warnings for missing source key versions, malformed/unversioned values, and non-executable dry-run state.
- Add frontend service methods for run/cancel.
- Extend the mocked admin Playwright smoke to cover plan -> run -> cancel or terminal state.

Required tests:

- Unit test service methods for plan/list/get/run/cancel.
- Unit test page states for executable dry-run, blocked dry-run, planned job, running job, terminal success, terminal failure, and cancellation.
- Mocked Playwright test confirms the route mounts and actions call the expected endpoints.

Estimated effort: `1-2 person-days`.

### P1: CI Closeout

Goal: convert local targeted evidence into final Docker-backed acceptance evidence.

Required checks:

- Run frontend admin smoke.
- Run Phase 5 mocked regression gate.
- Record the first Docker-backed green result in a final closeout doc.

Expected blocker:

- Local host currently lacks a reachable Docker socket, so this must run on CI or a Docker-capable machine.

Estimated effort: `0.5-1 person-day`.

Risk buffer: add `1-2 person-days` if PostgreSQL/Testcontainers exposes a real migration, JSONB query, or concurrency issue.

### P2: Runtime Masking And Redaction Policy

Goal: close the product-facing gap around how encrypted properties render in generic viewers and editors.

Required changes:

- Audit generic node property viewers and editors for encrypted property behavior.
- Confirm protected payloads are never shown as raw `enc:...` strings unless explicitly intended for admin diagnostics.
- Document readable-vs-indexable behavior for encrypted properties.
- Add targeted UI or API acceptance checks if product policy requires masking assertions.

Required tests:

- Encrypted property values do not leak raw protected payloads through generic property display.
- Admin diagnostics show counts and state only, not plaintext or key material.
- Existing search/index behavior remains documented and unchanged unless explicitly revised.

Estimated effort: `1-2 person-days`.

## Remaining Development Estimate

Remaining work to reach Property Encryption benchmark closeout: about `2.5-4.5 person-days`, plus Docker issue buffer if PostgreSQL exposes real failures.

Recommended execution order:

1. Docker-backed rewrap/backfill verification.
2. Rewrap execution UI.
3. Runtime masking/redaction polish.
4. Final CI/acceptance matrix closeout.

Do not add UI run/cancel controls before backend rewrap execution exists. That would create a product promise the API cannot safely fulfill.

## Verification Commands

Backend targeted service and controller tests:

```bash
cd ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dtest=PropertyEncryptionOperationsServiceTest,PropertyEncryptionOperationsControllerSecurityTest \
  test
```

PostgreSQL integration compile/skip check:

```bash
cd ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dtest=PropertyEncryptionBackfillPostgresIntegrationTest \
  test
```

Changelog XML validation:

```bash
xmllint --noout \
  ecm-core/src/main/resources/db/changelog/changes/091-create-property-encryption-rewrap-jobs.xml \
  ecm-core/src/main/resources/db/changelog/db.changelog-master.xml
```

Whitespace check:

```bash
git diff --check
```

Docker-backed final gate:

```bash
bash scripts/property-encryption-backfill-gate.sh
```

Frontend mocked gate:

```bash
PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY=1 bash scripts/phase5-regression.sh
```

## Acceptance Criteria

Property Encryption can be considered benchmark-closeout ready when all of the following are true:

- Backfill workflow remains green on unit, controller, and PostgreSQL integration coverage.
- Rewrap execution can safely run from planned ledger to terminal state with PostgreSQL coverage.
- Admin UI exposes only backend-supported actions.
- Docker-backed backend gate has a recorded green run.
- Frontend mocked Phase 5 gate includes the property encryption admin route and remains green.
- Runtime masking/redaction behavior is documented and covered by targeted checks.
- No secrets, plaintext encrypted values, protected payloads, or key material are printed in logs, docs, API responses, or UI diagnostics.

## Assumptions

- This closeout document is a planning and tracking artifact; it does not replace the slice-specific design/verification docs.
- `.env` remains local-only and must not be staged with this work.
- Rewrap execution must stay API-first before UI actions are exposed.
- Docker-gated PostgreSQL verification is required before final benchmark closeout.
