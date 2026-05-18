# Preview Async and People Service Guards: Integration Verification

Date: 2026-05-18

## Scope

This round continued the frontend service response-shape guard track with
two independent slices:

- Codex local slice: `previewDiagnosticsService` async export task and
  task-center helpers.
- Parallel worker slice: `peopleService`.

The intent is defensive hardening against HTML fallback or malformed API
responses that mocked frontend tests can otherwise miss. This round did
not change backend controllers, backend contracts, UI pages, endpoint
paths, payloads, Blob/download methods, CSV export methods, package
files, or migrations.

`.env` was already modified before this work and was not touched,
staged, or committed.

## Parallel Development

Preview async task guards were implemented directly on `main`:

- Commit: `209a6ab fix(preview-diagnostics): guard async task responses`.
- Files:
  - `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - `ecm-frontend/src/services/previewDiagnosticsService.async.test.ts`
  - `docs/PREVIEW_DIAGNOSTICS_ASYNC_TASK_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260518.md`

Claude was attempted for `peopleService`, but the local Claude CLI hit a
quota-limit message before producing usable changes. A Codex worker was
used as the parallel fallback:

- Commit: `7085d85 fix(people): guard service responses`.
- Files:
  - `ecm-frontend/src/services/peopleService.ts`
  - `ecm-frontend/src/services/peopleService.test.ts`
  - `docs/PEOPLE_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260518.md`

Codex retained final integration responsibility:

- reviewed the worker commit files
- reran combined targeted Jest
- reran lint and diff hygiene
- reran `CI=true npm run build`
- prepared this integration record

## Guard Coverage

`previewDiagnosticsService` now guards JSON responses for:

- rendition resources async export task start/list/summary/cleanup/cancel/get/retry
- rendition resources retry-terminal and retry-terminal dry-run responses
- queue diagnostics active cancellation responses
- queue declined async export task start/list/summary/cleanup/cancel/get/retry
- queue declined retry-terminal and retry-terminal dry-run responses
- queue declined requeue dry-run async export task start/list/summary/cleanup/cancel/get/retry
- queue declined requeue dry-run retry-terminal and retry-terminal dry-run responses

`peopleService` now guards JSON responses for:

- people search and user get
- user groups
- favorites
- preferences, namespaces, import/export, individual get/set/delete, and clear
- activities and sites
- favorite sites
- site membership requests and visible request pages
- membership create/update/approve/reject
- profile and preference updates

Out of scope for this round:

- Blob/download methods.
- CSV methods using `api.downloadFile`.
- Backend integration tests, because endpoint contracts were read but not
  changed.
- Browser/e2e updates, because no UI behavior changed.

## Verification

Combined targeted Jest:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/peopleService.test.ts src/services/previewDiagnosticsService.core.test.ts src/services/previewDiagnosticsService.async.test.ts --watchAll=false
```

Result:

```text
PASS src/services/peopleService.test.ts
PASS src/services/previewDiagnosticsService.async.test.ts
PASS src/services/previewDiagnosticsService.core.test.ts
Test Suites: 3 passed, 3 total
Tests:       80 passed, 80 total
```

Frontend lint:

```bash
cd ecm-frontend
npm run lint
```

Result: PASS.

Frontend CI build:

```bash
cd ecm-frontend
CI=true npm run build
```

Result: PASS.

Notes:

- Build emitted the existing CRA bundle-size advisory.
- Build emitted the existing Node deprecation warning for `fs.F_OK`.
- Neither warning blocked the build.

Diff hygiene:

```bash
git diff --check
```

Result: PASS.

## CI Follow-Up

Pushed CI run:

- Run: `26013638923`
- Head: `9a73249`
- Result: PASS

Passing jobs:

- `Backend Verify`
- `Frontend Build & Test`
- `Phase C Security Verification`
- `Acceptance Smoke (3 admin pages)`
- `Frontend E2E Core Gate`
- `Phase 5 Mocked Regression Gate`
- `Property Encryption Closeout Gate`

The CI-sensitive checks matched the local expectations:

- `Frontend Build & Test` covered the `CI=true` build path and service
  Jest suites.
- `Phase 5 Mocked Regression Gate` stayed green, so the guarded preview
  async methods preserved endpoint paths and mocked response contracts.
- Backend/security gates stayed green because this round was
  frontend-service-only.

## Follow-Up

Recommended next service-guard candidates:

- `ruleService`, preferably as a Codex local slice because it has many
  automation-specific DTOs.
- `workflowService`, as a larger isolated parallel slice.
- `recordsManagementService`, only after rechecking scheduled-report and
  saved-report mock contracts because it has recent product work.

Claude can still be used when quota is available, but it should stay in a
separate worktree and Codex should keep final review, build, CI, and doc
responsibility.
