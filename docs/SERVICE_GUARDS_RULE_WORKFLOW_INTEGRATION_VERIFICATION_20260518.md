# Rule and Workflow Service Guards: Integration Verification

Date: 2026-05-18

## Scope

This round continued the frontend service response-shape guard track with
two independent service slices:

- Codex local slice: `ruleService`.
- Parallel fallback worker slice: `workflowService`.

The intent is defensive hardening against HTML fallback or malformed API
responses that mocked frontend tests may otherwise miss.

This round did not change backend controllers, backend contracts, endpoint
paths, request payloads, query params, Blob/download methods, void methods,
package files, migrations, or `.env`.

`.env` was already modified before this work and was not touched, staged, or
committed.

## Parallel Development

`ruleService` was implemented directly on `main`:

- Commit: `60709ac fix(rules): guard service responses`.
- Files:
  - `ecm-frontend/src/services/ruleService.ts`
  - `ecm-frontend/src/services/ruleService.test.ts`
  - `docs/RULE_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260518.md`

Claude Code CLI was attempted for `workflowService` in a separate worktree:

- Worktree: `/Users/chouhua/Downloads/Github/Athena-workflow-service-guards`
- Branch: `claude/workflow-service-guards-20260518`
- Result: unavailable because the CLI returned a quota-limit message.

A Codex worker was used as the parallel fallback in that same separate
worktree:

- Worker commit: `04fe505 fix(frontend): guard workflow service response shapes`.
- Cherry-picked to `main` as: `a2d7aa5 fix(frontend): guard workflow service response shapes`.
- Files:
  - `ecm-frontend/src/services/workflowService.ts`
  - `ecm-frontend/src/services/workflowService.test.ts`
  - `docs/WORKFLOW_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260518.md`

Codex retained final integration responsibility:

- reviewed the worker commit write set
- checked that workflow endpoint paths, payloads, Blob methods, and void
  methods were not changed
- cherry-picked only after worker targeted tests and lint had passed
- reran combined targeted Jest, lint, diff hygiene, and `CI=true` frontend build

## Guard Coverage

`ruleService` now guards JSON responses for:

- paged rule lists: all rules, my rules, search, folder-scoped rules
- rule entity reads/writes: get, create, update, enable, disable
- folder scoped reorder and dry-run
- manual rule execution ledger: execute, list, timeline, get run
- rule test, condition validation, cron validation
- templates, action definitions, stats, and audit timeline list

`workflowService` now guards JSON responses for:

- workflow definitions and definition detail/model
- approval start, start-form submit, and generic process start
- task inbox/my tasks and task detail
- process detail and process browser paging
- process tasks, task history, activities, variables, items, candidates, and
  involved actors
- task variables/items/involved actors
- start/task form models
- document workflow history

Out of scope and intentionally unchanged:

- `ruleService` CSV exports through `api.downloadFile`
- `workflowService` diagram Blob methods through `api.getBlob`
- void/write-only methods that do not consume JSON response bodies

## Verification

Combined targeted Jest:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/ruleService.test.ts src/services/workflowService.test.ts --watchAll=false
```

Result:

```text
PASS src/services/ruleService.test.ts
PASS src/services/workflowService.test.ts
Test Suites: 2 passed, 2 total
Tests:       14 passed, 14 total
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

- Run: `26015220038`
- Head: `c0922b0`
- Result: PASS

Passing jobs:

- `Backend Verify`
- `Frontend Build & Test`
- `Phase C Security Verification`
- `Phase 5 Mocked Regression Gate`
- `Frontend E2E Core Gate`
- `Property Encryption Closeout Gate`
- `Acceptance Smoke (3 admin pages)`

The CI-sensitive checks matched local expectations:

- `Frontend Build & Test` covered lint, type check, build, and frontend unit
  tests.
- `Phase 5 Mocked Regression Gate` stayed green, so mocked service contracts
  were preserved.
- `Frontend E2E Core Gate` stayed green, including preview/search regression.
- Backend/security/property-encryption gates stayed green because this round did
  not change backend code or migrations.

## Follow-Up

Recommended next service-guard candidates:

- `recordsManagementService`, after rechecking the recent scheduled-report and
  saved-report mock contracts.
- `mailAutomationService`, only if current mocked gates still rely on loose
  service responses.

Claude can be used for future slices when quota is available, but the safer
pattern remains: separate worktree, disjoint write set, targeted tests inside
the worker, and Codex final review/build/CI responsibility.
