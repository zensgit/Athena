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
- Property Encryption closeout preflight is implemented: one script runs all local non-Docker evidence and records Docker-backed gate availability.
- Property Encryption CI closeout gate is implemented: GitHub Actions now runs the preflight with `REQUIRE_DOCKER_BACKED_GATE=1` after backend and frontend gates.
- Final acceptance matrix is started in `docs/PROPERTY_ENCRYPTION_FINAL_ACCEPTANCE_MATRIX_20260505.md` with push evidence, CI run ID, gate criteria, and failure triage.
- Post-closeout async governance is implemented: backfill and rewrap jobs now appear in the shared Async Task Health Overview and Recent Async Tasks surfaces.
- The Property Encryption admin page now links directly to `/admin?asyncTaskDomain=propertyencryption`.
- The Property Encryption closeout preflight now includes the async-governance service, lifecycle, analytics-controller, and analytics-security contracts in its default non-Docker backend target.
- The Phase 5 mocked regression list now includes the async-governance overview fallback spec that pins `overview-required` for Property Encryption when the unified overview endpoint is unavailable.
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
- Property Encryption closeout preflight: backend non-Docker suite 75 tests passed, frontend targeted suite 10 tests passed, lint passed, production build compiled, Phase 5 registry matched 24/24, Docker-backed gate reported blocked by missing Docker socket.
- Property Encryption CI closeout gate wiring: shell syntax passed, workflow YAML parsed, reduced preflight passed locally, and Docker-backed gate fail-fast behavior was reproduced on this host.
- CI push evidence: `origin/main` advanced from `7d7639c` to `f5de379`; GitHub Actions run `25418055312` started for the closeout-gate commit.
- CI run `25418055312` failure triaged: backend failed on PostgreSQL JSONB/native-query cast plus timestamp/JSONB formatting assertions; frontend failed on a long operator-flow Jest timeout. Fixes are documented in `docs/PROPERTY_ENCRYPTION_CI_RUN_25418055312_FIXES_DESIGN_VERIFICATION_20260505.md`.
- CI run `25418484543` follow-up failure triaged: backend runner PostgreSQL does not provide `jsonb_object_length(jsonb)`; repository count now uses `CROSS JOIN LATERAL jsonb_each(...)`.
- CI run `25418606323` achieved the benchmark blocker: `Property Encryption Closeout Gate` passed with backend non-Docker 75/75, frontend targeted 10/10, production build, Phase 5 registry 24/24, and Docker-backed PostgreSQL gate 65/65.
- Async governance backend addendum: 65 targeted tests passed across `PropertyEncryptionAsyncTaskServiceTest`, async governance/lifecycle tests, and analytics controller/security tests.
- Async governance closeout-gate addendum: local non-Docker preflight now runs 140 backend tests, 10 frontend targeted tests, lint, and Phase 5 registry-only validation successfully.
- Async governance mocked E2E addendum: targeted Playwright specs passed for the Property Encryption bridge, Admin Dashboard task filtering/cancel action, and fallback `overview-required` behavior.
- Async governance one-command addendum gate: `scripts/property-encryption-async-governance-gate.sh` passed the full local path with backend 65/65, Jest 2/2, lint, production build, Phase 5 registry-only validation, and mocked E2E 3/3.

Known environment constraint:

- The repo `./mvnw` delegates to Docker and fails locally when Docker socket is unavailable.
- Use a temporary/local Maven binary with `.m2-cache/repository` for non-Docker unit tests, or run Docker-gated checks on CI or a Docker-capable host.

## TODO List

### P0: Docker-Backed Rewrap/Backfill Verification

Goal: convert the locally compiled PostgreSQL rewrap/backfill coverage into real Docker-backed evidence.

Required checks:

- Let the GitHub Actions `Property Encryption Closeout Gate` job run on a Docker-capable runner.
- Confirm `PropertyEncryptionBackfillPostgresIntegrationTest` passes instead of being locally blocked.
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

### Completed: CI Closeout Gate Wiring

Goal: convert local targeted evidence into final Docker-backed acceptance evidence.

Delivered changes:

- Added `Property Encryption Closeout Gate` to `.github/workflows/ci.yml`.
- The job runs after backend and frontend gates.
- The job installs Java and Node toolchains, installs frontend dependencies, and runs `scripts/property-encryption-closeout-preflight.sh`.
- The job sets `REQUIRE_DOCKER_BACKED_GATE=1`, making the PostgreSQL/Testcontainers gate mandatory on CI.
- `scripts/property-encryption-backfill-gate.sh` now prefers a local Maven binary before the Dockerized `ecm-core/./mvnw` wrapper.
- `scripts/property-encryption-closeout-preflight.sh` exports the resolved `MAVEN_BIN` into the Docker-backed child gate.

Delivered tests:

- Shell syntax checks passed for both property-encryption scripts.
- Workflow YAML parsed successfully.
- Reduced local preflight passed with 38 backend tests, 3 frontend service tests, and lint.
- Docker fail-fast behavior was reproduced locally with the expected missing-socket blocker.

Status: implemented locally; pending the first CI run with Docker available.

Remaining CI evidence: record the first green `Property Encryption Closeout Gate` run in the final acceptance matrix.

### Completed: Local Closeout Preflight

Goal: make the closeout evidence chain executable from one command and distinguish local non-Docker evidence from the Docker-backed PostgreSQL gate.

Delivered changes:

- Added `scripts/property-encryption-closeout-preflight.sh`.
- Script runs backend non-Docker evidence, frontend targeted tests, lint, production build, Phase 5 registry-only preflight, and Docker availability checks.
- Script continues locally when Docker is unavailable and `REQUIRE_DOCKER_BACKED_GATE=0`.
- Script can be promoted to a final blocking gate with `REQUIRE_DOCKER_BACKED_GATE=1`.

Delivered tests:

- `scripts/property-encryption-closeout-preflight.sh` completed successfully on this host.
- Docker-backed PostgreSQL gate was explicitly marked blocked because Docker API was unavailable.

Status: implemented and locally verified.

### Completed: Async Governance Integration

Goal: make property-encryption backfill and rewrap jobs visible and actionable
from the shared async task governance control plane.

Delivered changes:

- Added a `propertyEncryption` async-governance provider that aggregates
  backfill and rewrap job ledgers.
- Normalized job statuses into the shared queued/running/completed/cancelled/failed buckets.
- Exposed cancel actions only for `PLANNED` and `RUNNING` jobs.
- Kept `CANCEL_REQUESTED` jobs active/running without exposing duplicate cancel.
- Added lifecycle aliases for `propertyencryption`, `property-encryption`,
  `property_encryption`, and `propertyencryptionjobs`.
- Added Admin Dashboard health overview and Recent Async Tasks support for
  Property Encryption.
- Added URL-state support for `/admin?asyncTaskDomain=propertyencryption`.
- Added an `Open in Async Governance` link from the Property Encryption admin page.

Delivered tests:

- Backend service/lifecycle/controller/security target: 65 tests passed.
- Frontend targeted Jest and production build passed.
- Mocked Playwright coverage passed for admin bridge, domain filtering, cancel
  action, unified overview rendering, and fallback `overview-required` behavior.

Status: implemented and locally verified; pending next pushed CI run.

### Completed: Async Governance Closeout Gate Addendum

Goal: ensure the property-encryption closeout gate does not miss the new
async-governance contracts.

Delivered changes:

- Added async-governance backend contract tests to
  `scripts/property-encryption-closeout-preflight.sh`.
- Added the fallback mocked E2E spec to `scripts/phase5-regression.sh`.
- Updated the final acceptance matrix with a 2026-05-12 post-closeout addendum.
- Added `scripts/property-encryption-async-governance-gate.sh` as the one-command
  local validation entry point for this addendum.

Delivered tests:

- Local closeout preflight non-Docker path: 140 backend tests, 10 frontend tests,
  lint, and Phase 5 registry-only validation passed.
- `admin-async-governance-overview-fallback.mock.spec.ts`: 1 Playwright test passed.
- Full local async-governance gate: backend 65/65, Jest 2/2, lint, production
  build, Phase 5 registry-only validation, and mocked E2E 3/3 passed.
- `git diff --check -- . ':!.env'`: passed.

Status: implemented and locally verified; Docker-backed CI evidence remains the
next-push responsibility.

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

Remaining work to refresh Property Encryption benchmark evidence after the
2026-05-12 async-governance addendum: about `0.25-0.5 person-day` if CI is
green, plus Docker issue buffer if PostgreSQL exposes real failures.

Recommended execution order:

1. Run `scripts/property-encryption-async-governance-gate.sh` locally before push.
2. Push the async-governance addendum bundle.
3. Observe `Backend Verify`, `Frontend Build & Test`, `Property Encryption Closeout Gate`, and `Phase 5 Mocked Regression Gate`.
4. If green, record the CI run in the final acceptance matrix addendum.
5. If red, fix the concrete backend/frontend/Docker/Testcontainers/PostgreSQL/mocked-E2E failure and rerun the same gate.

Current CI run to observe:

```text
https://github.com/zensgit/Athena/actions/runs/25418055312
```

Run `25418055312` final status: failed before the Property Encryption closeout job could execute.

Run `25418484543` final relevant status: backend failed on PostgreSQL JSONB entry count before closeout could execute.

Run `25418606323` Property Encryption status: `Property Encryption Closeout Gate` passed. Continue observing remaining non-property workflow jobs separately.

Do not broaden the UI beyond backend-supported execution semantics. The current UI is aligned to backend plan/run/cancel support and keeps unsafe jobs blocked by backend validation.

## Verification Commands

Async governance addendum one-command gate:

```bash
MAVEN_BIN=/tmp/codex-maven/apache-maven-3.9.11/bin/mvn \
scripts/property-encryption-async-governance-gate.sh
```

Async governance addendum lightweight gate:

```bash
MAVEN_BIN=/tmp/codex-maven/apache-maven-3.9.11/bin/mvn \
RUN_FRONTEND_BUILD=0 \
RUN_E2E=0 \
scripts/property-encryption-async-governance-gate.sh
```

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

Local closeout preflight:

```bash
scripts/property-encryption-closeout-preflight.sh
```

Docker-required final preflight:

```bash
REQUIRE_DOCKER_BACKED_GATE=1 scripts/property-encryption-closeout-preflight.sh
```

CI closeout job:

```text
.github/workflows/ci.yml -> Property Encryption Closeout Gate
```

## Acceptance Criteria

Property Encryption can be considered benchmark-closeout ready when all of the following are true:

- Backfill workflow remains green on unit, controller, and PostgreSQL integration coverage.
- Rewrap execution can safely run from planned ledger to terminal state with PostgreSQL coverage.
- Admin UI exposes only backend-supported actions.
- Docker-backed backend gate has a recorded green run.
- GitHub Actions `Property Encryption Closeout Gate` has a recorded green run.
- Local closeout preflight is green.
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
