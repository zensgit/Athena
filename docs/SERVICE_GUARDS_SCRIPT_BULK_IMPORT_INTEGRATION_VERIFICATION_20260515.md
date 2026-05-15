# Service Guards: Script + Bulk Import Integration Verification

Date: 2026-05-15

## Scope

This round continued the frontend service response-shape guard closeout line.
It hardened two small services that previously trusted typed API responses
directly:

- `scriptService`
- `bulkImportService`

Both slices preserve their existing public APIs, endpoint paths, request
payloads, and return types.

## Parallel Development Split

Codex implemented and verified the script slice in the main worktree:

- `ecm-frontend/src/services/scriptService.ts`
- `ecm-frontend/src/services/scriptService.test.ts`
- `docs/SCRIPT_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260515.md`

Claude implemented the bulk-import slice in an isolated worktree:

- Worktree: `.claude/worktrees/claude-bulk-import-service-guards`
- Branch: `worktree-claude-bulk-import-service-guards`
- Worktree commit: `a3af888 fix(bulk-import): guard service responses`
- Integrated commit: `c20e8bf fix(bulk-import): guard service responses`

Claude's local agent could write files but could not run `npm` or `git`
mutations. Codex reviewed the diff, reused the main worktree's `node_modules`
temporarily for verification, removed that symlink before staging, committed
the worktree, and cherry-picked the slice back to `main`.

## Backend Contract Checks

Script:

- Backend controller: `ScriptController`
- Mounts: `/api/scripts` and `/api/v1/scripts`
- Frontend relative paths remain `/scripts` and `/scripts/execute`
- Response shapes: `ScriptDefinitionDto[]`, `ScriptDefinitionDto`, and
  `ScriptExecutionResult`

Bulk import:

- Backend controller: `BulkImportController`
- Mounts: `/api/bulk-import` and `/api/v1/bulk-import`
- Frontend relative paths remain `/bulk-import` and `/bulk-import/{jobId}`
- Request shape preserved for multipart `FormData`: `files`, `relativePaths`,
  optional `targetFolderId`, and `conflictPolicy`
- Response shapes: `ImportJobDto` and Spring page envelope for
  `Page<ImportJobDto>`

## Guard Rules Added

Script:

- Rejects HTML fallback and malformed responses.
- Validates script definition strings, booleans, nullable strings, and `tags`.
- Validates execution result envelope while allowing `result` to be any value,
  including `null`, because the backend exposes it as `Object`.
- Leaves delete unguarded because the backend returns `204 No Content`.

Bulk import:

- Rejects HTML fallback and malformed responses across start/get/list/cancel.
- Validates `ImportJobStatus` closed union:
  `PENDING | RUNNING | COMPLETED | FAILED | CANCELED`.
- Validates `ConflictPolicy` closed union:
  `SKIP | RENAME | OVERWRITE`.
- Validates required string fields, nullable optional timestamp/path fields,
  finite numeric counters, and page envelope fields.
- Preserves default `conflictPolicy='SKIP'` and omitted `targetFolderId`
  behavior.

## Local Verification

Targeted service tests after integration:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/scriptService.test.ts src/services/bulkImportService.test.ts --watchAll=false
```

Result: PASS. 2 test suites, 31 tests, 0 failures.

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

Result: PASS. The build emitted the existing Node `fs.F_OK` deprecation
warning and CRA bundle-size advisory.

Remote CI after push:

```bash
gh run watch 25901772567 --exit-status --interval 30
```

Result: PASS. Run `25901772567` completed green for all seven jobs:

- Backend Verify
- Frontend Build & Test
- Phase C Security Verification
- Phase 5 Mocked Regression Gate
- Property Encryption Closeout Gate
- Acceptance Smoke (3 admin pages)
- Frontend E2E Core Gate

## Commits

- `4819252 fix(scripts): guard service responses`
- `c20e8bf fix(bulk-import): guard service responses`
- `a1970ad fix(scripts): satisfy production build guard typing`
- `263f3d3 docs(services): record script bulk import guard verification`

## Notes

The first `CI=true npm run build` after integration caught TypeScript `TS2775`
and cast-safety issues in the script guard. Jest and lint did not catch those
issues. The fix converted the assertion helper to a function declaration and
used an explicit `unknown` bridge for DTO casts.

The main worktree still has the pre-existing local `.env` modification. It was
not staged or changed by this round.
