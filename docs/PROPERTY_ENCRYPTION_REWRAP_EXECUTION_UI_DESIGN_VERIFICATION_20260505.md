# Property Encryption Rewrap Execution UI Design Verification

Date: 2026-05-05

## Context

The backend now supports executable property-encryption rewrap jobs: plan, list, get, run, cancel, async execution, and terminal ledger counters.

Before this slice, the admin page still exposed rewrap as dry-run only and only rendered a backfill job ledger. This created a frontend/backend gap: the new backend execution endpoints existed but had no admin UI consumer.

## Design

### Service Layer

Extended the frontend property-encryption service with the rewrap execution contract:

- shared `PropertyEncryptionJobStatus`
- `PropertyEncryptionRewrapJobDto`
- `planRewrapJob(targetKeyVersion?)`
- `listRewrapJobs(limit?)`
- `runRewrapJob(jobId, batchSize?)`
- `cancelRewrapJob(jobId)`

The service methods use the backend admin endpoints under `/admin/property-encryption/rewrap-jobs`.

### Admin Page

Updated `PropertyEncryptionOperationsPage` to keep backfill and rewrap jobs separate:

- `backfillJobs` renders the existing backfill ledger
- `rewrapJobs` renders a new rewrap ledger
- initial load fetches status, encrypted definitions, backfill jobs, and rewrap jobs together
- refresh actions are split: `Refresh Backfill Jobs` and `Refresh Rewrap Jobs`

Rewrap admin actions:

- `Plan Rewrap Job`
- `Run Rewrap`
- `Cancel Rewrap`

Cancel controls are intentionally enabled only for `PLANNED` and `RUNNING` jobs. `CANCEL_REQUESTED` is treated as already in the cancellation path and cannot be clicked repeatedly from the UI.

The UI no longer says rewrap execution is unavailable. It now explains the backend safety rule: rewrap execution requires the target key to match the active backend key, and unsafe jobs fail before mutating node payloads.

### Mocked Browser Coverage

Updated `e2e/admin-property-encryption.mock.spec.ts` to model independent backfill and rewrap ledgers.

The mock route now covers:

- `GET /rewrap-jobs`
- `POST /rewrap-jobs/plan`
- `POST /rewrap-jobs/{id}/run`
- `POST /rewrap-jobs/{id}/cancel`

Backfill and rewrap mocks use separate mutable state because the DTO counters differ: backfill uses `migratedValueCount`; rewrap uses `rewrappedValueCount`.

## Verification

### Frontend Unit Tests

Command:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/propertyEncryptionService.test.ts \
  src/pages/PropertyEncryptionOperationsPage.test.tsx \
  --watchAll=false
```

Result:

```text
Test Suites: 2 passed, 2 total
Tests: 5 passed, 5 total
```

Coverage added:

- rewrap service plan/list/run/cancel endpoint wrappers
- page initial load calls `listRewrapJobs(10)`
- page can dry-run, plan, run, and cancel rewrap jobs
- cancel buttons become disabled after `CANCEL_REQUESTED`
- page assertions are scoped to the backfill and rewrap tables separately
- button labels are operation-specific to avoid backfill/rewrap ambiguity

### Lint

Command:

```bash
cd ecm-frontend
npm run lint
```

Result: passed.

### Production Build

Command:

```bash
cd ecm-frontend
CI=true npm run build
```

Result:

```text
Compiled successfully.
```

Build note: Create React App still reports the existing bundle-size warning after successful compilation.

### Phase 5 Registry Preflight

Command:

```bash
PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY=1 bash scripts/phase5-regression.sh
```

Result:

```text
expected events: 24
observed markers in specs: 24
missing_from_events_file_count: 0
stale_events_file_entries_count: 0
OK registry matches spec markers
registry-only mode complete
```

### Mocked Playwright Smoke

Command:

```bash
cd ecm-frontend
ECM_UI_URL=http://127.0.0.1:5500 \
  npx playwright test e2e/admin-property-encryption.mock.spec.ts \
  --project=chromium \
  --workers=1
```

Result:

```text
1 passed
```

Environment note: the command used a temporary local SPA server serving `ecm-frontend/build` on `127.0.0.1:5500`.

## Remaining Work

Remaining work to close the Property Encryption benchmark after this slice: about `1.5-3 person-days`, plus Docker issue buffer if the PostgreSQL gate exposes real failures.

Recommended order:

1. Run Docker-backed PostgreSQL property-encryption gate.
2. Close runtime masking/redaction policy.
3. Update final acceptance matrix after the first Docker-backed green run.

No frontend execution action is now intentionally blocked by missing backend endpoints. The remaining blocker is environment-backed verification, not frontend/backend capability mismatch.
