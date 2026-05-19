# Records and Mail Service Guards: Integration Verification

Date: 2026-05-18

## Scope

This round continued the frontend service response-shape guard track with two
parallel slices:

- Codex local slice: `recordsManagementService`.
- Claude Code CLI slice: `mailAutomationService`.

The purpose is to stop HTTP-200 HTML fallback or malformed JSON from being
treated as valid DTOs by frontend services. This round did not change backend
controllers, backend contracts, endpoint paths, request payloads, query params,
Blob/download methods, void methods, package files, migrations, pages, e2e
tests, or `.env`.

`.env` was already modified before this work and was not touched, staged, or
committed.

## Parallel Development

`recordsManagementService` was implemented directly on `main`:

- Files:
  - `ecm-frontend/src/services/recordsManagementService.ts`
  - `ecm-frontend/src/services/recordsManagementService.test.ts`

`mailAutomationService` was delegated to Claude Code CLI in a separate
worktree:

- Worktree:
  `/Users/chouhua/Downloads/Github/Athena-mail-automation-service-guards`
- Branch: `claude/mail-automation-service-guards-20260518`
- Worker commit: `a77391a fix(frontend): guard mail automation service responses`

Codex retained final integration responsibility:

- reviewed Claude's changed files and tests
- cherry-picked the worker commit into the main worktree without keeping the
  staged state
- reran combined targeted Jest, lint, diff hygiene, and production build

## Guard Coverage

`recordsManagementService` now guards JSON responses for:

- declared record lists and record readbacks
- summary, audit pages, operations telemetry, activity timeline/highlight
  dashboard responses
- report preset list/create/update responses
- report preset schedule status, delivery execution, execution history, and
  execution ledger pages
- file plan and record category lists and mutation readbacks

Download and no-body methods remain intentionally unchanged:

- CSV exports through `api.downloadFile`
- delete methods and `undeclareRecord`

`mailAutomationService` now guards JSON responses for:

- account CRUD, OAuth reset, connection test, OAuth authorize URL, and folders
- rule CRUD and rule preview
- diagnostics, reports, report schedule, scheduled report execution
- processed-mail bulk delete, replay, document readbacks, retention cleanup
- runtime metrics, fetch summary, fetch trigger, debug fetch
- provider presets and SMTP test result

`mailAutomationService` keeps two sentinels:

- `MAIL_AUTOMATION_UNEXPECTED_RESPONSE_MESSAGE` for general JSON methods.
- `TEST_SMTP_UNEXPECTED_RESPONSE_MESSAGE` for the existing test-SMTP UI path.

## Verification

Combined targeted Jest:

```bash
cd ecm-frontend
npm test -- --runTestsByPath \
  src/services/mailAutomationService.test.ts \
  src/services/recordsManagementService.test.ts \
  src/pages/RecordsManagementPage.test.tsx \
  src/components/records/ScheduleReportPresetDialog.test.tsx \
  --watchAll=false
```

Result:

```text
Test Suites: 4 passed, 4 total
Tests:       148 passed, 148 total
```

Post-build service regression check:

```bash
cd ecm-frontend
npm test -- --runTestsByPath \
  src/services/mailAutomationService.test.ts \
  src/services/recordsManagementService.test.ts \
  --watchAll=false
```

Result:

```text
Test Suites: 2 passed, 2 total
Tests:       59 passed, 59 total
```

Frontend lint:

```bash
cd ecm-frontend
npm run lint
```

Result: PASS.

Frontend production build:

```bash
cd ecm-frontend
CI=true npm run build
```

Result: PASS.

Notes:

- Build still emits the existing Node `fs.F_OK` deprecation warning from the
  CRA toolchain.
- Build still emits the existing CRA bundle-size advisory.
- Neither warning blocks the build.

Diff hygiene:

```bash
git diff --check -- . ':!.env'
```

Result: PASS.

## CI Follow-Up

Pushed CI run:

- Run: `26078498817`
- Head: `52a507a`
- Result: PASS

Passing jobs:

- `Frontend Build & Test`
- `Backend Verify`
- `Phase C Security Verification`
- `Phase 5 Mocked Regression Gate`
- `Frontend E2E Core Gate`
- `Property Encryption Closeout Gate`
- `Acceptance Smoke (3 admin pages)`

The CI-sensitive checks matched local expectations:

- `Frontend Build & Test` covered lint, type check, build, and frontend unit
  tests (the combined targeted Jest of 148 ran locally before push).
- `Phase 5 Mocked Regression Gate` stayed green, so mocked service contracts
  were preserved.
- `Frontend E2E Core Gate` and `Acceptance Smoke (3 admin pages)` stayed
  green, including the records/mail admin surfaces.
- Backend/security/property-encryption gates stayed green because this round
  did not change backend code or migrations.

## Remaining Work

Recommended next service-guard candidates:

- `nodeService`, split into smaller slices rather than one large rewrite.
- `opsRecoveryService` async-export tail, because the core service is already
  guarded but some async export methods remain direct generic responses.
- `bulkOperationService` and `tagService` as small lower-risk follow-ups.

Property Encryption Docker-backed closeout evidence remains a separate
verification-only track and was not part of this frontend service-guard round.
