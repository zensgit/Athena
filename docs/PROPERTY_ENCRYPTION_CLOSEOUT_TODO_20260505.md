# Property Encryption Closeout And TODO

Date: 2026-05-05

## Current State

Branch evidence:

- Baseline commit before the frontend execution slice: `409f836 feat(security): add property encryption rewrap job ledger`
- Working tree note: `.env` has pre-existing local changes and is intentionally excluded from this closeout.

Completed delivery:

- Backfill API-first workflow is implemented: dry-run, plan, run, cancel, async runner, stale-job recovery, and PostgreSQL-focused integration coverage.
- Property Encryption admin operations page is implemented: route, menu entry, service client, UI, unit tests, and mocked browser smoke registration.
- Rewrap backend workflow is implemented: dry-run, persisted planned-job ledger, plan/list/get, run/cancel, bounded async runner, JSONB candidate selection, and compare-and-set update.
- Rewrap frontend execution workflow is implemented: service client methods, separate rewrap job table, plan/run/cancel controls, scoped tests, and mocked Playwright route coverage.
- Runtime protected-payload redaction is implemented: backend readable/indexable property maps, frontend property dialog display/save, search prefill, and search highlight snippets all guard against raw `enc:...` values.
- Runtime response masking is implemented: public/generic node property responses return `[encrypted]` for model-declared encrypted keys while internal trusted workflows keep readable projection.
- Protocol endpoint security-test expansion has covered Transfer Receiver, WOPI Host, CMIS AtomPub, and CMIS Browser patterns in prior security-test docs.

Verified evidence from latest rewrap-ledger slice:

- `PropertyEncryptionOperationsServiceTest,PropertyEncryptionOperationsControllerSecurityTest`: 52 tests passed after rewrap execution backend was added.
- `PropertyEncryptionBackfillPostgresIntegrationTest`: build success, two Docker/Testcontainers tests skipped locally because Docker was unavailable.
- `xmllint` on the rewrap changelog and master changelog: passed.
- `git diff --check`: passed.
- Frontend rewrap execution UI targeted Jest: 2 suites, 5 tests passed.
- Frontend lint: passed.
- Frontend production build: compiled successfully.
- Phase 5 registry-only preflight: 24 expected markers, 24 observed markers.
- Frontend admin mocked Playwright smoke: 1 test passed against a temporary local SPA server.
- Runtime redaction backend unit test: `NodePropertyEncryptionServiceTest`, 5 tests passed.
- Runtime redaction frontend unit test: `propertyRedactionUtils.test.ts`, 5 tests passed.
- Runtime response masking backend target suite: 23 tests passed across service, node/document/content-type controller, and search index projection coverage.

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

### Completed: Rewrap Execution UI

Goal: expose rewrap execution only after the backend can safely mutate payloads.

Delivered changes:

- Added rewrap job DTOs and service wrappers for plan, list, run, and cancel.
- Showed rewrap planned-job ledger on the Property Encryption admin page.
- Added plan, run, and cancel controls after backend execution endpoints existed.
- Kept backfill and rewrap job state separate to avoid counter and action ambiguity.
- Disabled cancel controls outside `PLANNED` and `RUNNING`; `CANCEL_REQUESTED` is no longer re-clickable.
- Surfaced warnings for missing source key versions, malformed/unversioned values, and non-executable dry-run state.
- Extended the mocked admin Playwright smoke to cover rewrap plan -> run -> cancel.

Delivered tests:

- Unit tested service methods for rewrap plan/list/run/cancel.
- Unit tested page load and backfill/rewrap action flows with table-scoped assertions.
- Mocked Playwright test confirms the route mounts and backfill/rewrap actions call the expected endpoints.

Status: implemented and locally verified.

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

### Completed: Runtime Protected-Payload Redaction

Goal: prevent raw protected payload strings from leaking through generic runtime views, search helpers, or editor save payloads.

Delivered changes:

- Backend recursively redacts `enc:...` payload-looking strings from readable property maps before response projection overlays model-declared encrypted values.
- Backend recursively redacts `enc:...` payload-looking strings from indexable property maps.
- Frontend property display formats raw protected payloads as `[encrypted]`.
- Frontend generic property editor disables redacted custom-property values and skips them during save so `[encrypted]` is not written back as literal data.
- Frontend search prefill omits protected payload values from property filters.
- Frontend search highlights replace inline protected payload text with `[encrypted]`.

Delivered tests:

- Backend unit coverage for readable and indexable protected-payload redaction.
- Frontend utility coverage for detection, recursive value redaction, display formatting, and highlight text redaction.

Status: implemented and locally verified.

### Completed: Runtime Model-Property Response Masking

Goal: make public/generic API projections mask model-declared encrypted properties by default.

Delivered changes:

- Added `NodePropertyEncryptionService.resolveResponseProperties(...)`.
- Public/generic response projection returns `[encrypted]` for model-declared encrypted keys.
- Public `NodeDto` mappers in node, document, and content-type controllers use response projection.
- Alfresco-compatible property response mapper uses response projection.
- Internal trusted workflows preserve `resolveReadableProperties(...)` for copy, checkout/checkin, metadata comparison, and compatibility mutations.
- Search subtree/child reindex paths now apply `resolveIndexableProperties(...)` before saving `NodeDocument`.

Delivered tests:

- Unit coverage for response masking without crypto reveal.
- Unit coverage for legacy plaintext encrypted-model keys being masked.
- MockMvc coverage for node, document, and content-type public response DTOs.
- Search index coverage that subtree reindex stores indexable properties instead of raw properties.

Status: implemented and locally verified.

## Remaining Development Estimate

Remaining work to reach Property Encryption benchmark closeout: about `0.5-1.5 person-days`, plus Docker issue buffer if PostgreSQL exposes real failures.

Recommended execution order:

1. Docker-backed rewrap/backfill verification.
2. Final CI/acceptance matrix closeout.

Do not broaden the UI beyond backend-supported execution semantics. The current UI is aligned to backend plan/run/cancel support and keeps unsafe jobs blocked by backend validation.

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

Frontend rewrap execution UI targeted tests:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/propertyEncryptionService.test.ts \
  src/pages/PropertyEncryptionOperationsPage.test.tsx \
  --watchAll=false
```

Frontend rewrap execution UI build checks:

```bash
cd ecm-frontend
npm run lint
CI=true npm run build
```

Frontend mocked browser smoke:

```bash
cd ecm-frontend
ECM_UI_URL=http://127.0.0.1:5500 \
  npx playwright test e2e/admin-property-encryption.mock.spec.ts \
  --project=chromium \
  --workers=1
```

Runtime redaction backend test:

```bash
cd ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dtest=NodePropertyEncryptionServiceTest \
  test
```

Runtime redaction frontend test:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/utils/propertyRedactionUtils.test.ts \
  --watchAll=false
```

Runtime response masking backend target suite:

```bash
cd ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dtest=NodePropertyEncryptionServiceTest,NodeControllerAspectTest,DocumentControllerCheckoutTest,ContentTypeControllerPreviewSemanticsTest,SearchIndexServiceSubtreeReindexTest \
  test
```

## Acceptance Criteria

Property Encryption can be considered benchmark-closeout ready when all of the following are true:

- Backfill workflow remains green on unit, controller, and PostgreSQL integration coverage.
- Rewrap execution can safely run from planned ledger to terminal state with PostgreSQL coverage.
- Admin UI exposes only backend-supported actions.
- Docker-backed backend gate has a recorded green run.
- Frontend mocked Phase 5 gate includes the property encryption admin route and remains green.
- Runtime protected-payload redaction behavior is documented and covered by targeted checks.
- Runtime model-property masking is implemented as default-masked response projection.
- No protected payloads, key material, or admin-operation plaintext values are printed in logs, docs, API responses, or UI diagnostics.
- Model-declared encrypted property plaintext remains available only to explicit trusted/internal readable workflows.

## Assumptions

- This closeout document is a planning and tracking artifact; it does not replace the slice-specific design/verification docs.
- `.env` remains local-only and must not be staged with this work.
- Rewrap execution must stay API-first before UI actions are exposed.
- Docker-gated PostgreSQL verification is required before final benchmark closeout.
