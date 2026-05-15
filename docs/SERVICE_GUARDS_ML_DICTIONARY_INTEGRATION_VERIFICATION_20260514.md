# Service Guards: ML + Dictionary Integration Verification

Date: 2026-05-14

## Scope

This round continued the frontend service response-shape guard closeout line.
It hardened two small read/action services that still trusted typed API
responses directly:

- `mlService`
- `dictionaryService`

Both slices preserve their existing public APIs, endpoint paths, request
payloads, path encoding behavior, and return types.

## Parallel Development Split

Codex implemented and verified the ML slice in the main worktree:

- `ecm-frontend/src/services/mlService.ts`
- `ecm-frontend/src/services/mlService.test.ts`
- `docs/ML_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md`

Claude implemented the dictionary slice in an isolated worktree:

- Worktree: `.claude/worktrees/claude-dictionary-service-guards`
- Branch: `worktree-claude-dictionary-service-guards`
- Integrated commit: `5ab044b`

Codex reviewed Claude's diff against the backend controller and DTO records,
reran verification, updated the verification doc with real results, committed
the worktree, and cherry-picked it back to `main`.

## Backend Contract Checks

ML:

- Backend controller: `MLController`
- Mount: `/api/v1/ml`
- Frontend relative paths remain `/ml/health`, `/ml/classify*`, and `/ml/suggest-tags*`
- Response shapes: health map, `ClassificationResult`, and `List<String>`

Dictionary:

- Backend controller: `DictionaryController`
- Mounts: `/api/dictionary` and `/api/v1/dictionary`
- Frontend relative path remains `/dictionary`
- Response shapes: `TypeDefinitionDto`, `AspectDefinitionDto`,
  `PropertyDefinitionDto`, `ConstraintDefinitionDto`, and string lists

## Guard Rules Added

ML:

- Rejects HTML fallback and malformed responses.
- Validates health shape: `available`, `modelLoaded`, `modelVersion`, `status`.
- Validates classification `success`, nullable optional fields, and alternative
  categories.
- Validates tag suggestions as arrays of strings.

Dictionary:

- Rejects HTML fallback and malformed response bodies across all eight methods.
- Validates type/aspect definition shape, including nested property definitions.
- Validates `PropertyDataType` and `ConstraintType` against frontend closed unions.
- Validates constraint `parameters` as a plain object.
- Validates hierarchy and mandatory-aspect endpoints as arrays of strings.
- Preserves `encodeURIComponent(qualifiedName)` behavior.

## Verification

Targeted service tests after integration:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/mlService.test.ts src/services/dictionaryService.test.ts --watchAll=false
```

Result: PASS. 2 test suites, 34 tests, 0 failures.

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

Result: PASS. CRA emitted the existing bundle-size advisory.

Remote CI after push:

```bash
gh run watch 25900285879 --exit-status --interval 30
```

Result: PASS. Run `25900285879` completed green for all seven jobs:

- Backend Verify
- Frontend Build & Test
- Phase C Security Verification
- Phase 5 Mocked Regression Gate
- Frontend E2E Core Gate
- Acceptance Smoke (3 admin pages)
- Property Encryption Closeout Gate

## Commits

- `a11eccb fix(ml): guard service responses`
- `5ab044b fix(dictionary): guard service responses`
- `84f254c docs(services): record ml dictionary guard verification`

## Notes

The Claude split remained useful for parallel implementation, especially for
the larger dictionary DTO guard. Claude's session wrote files but could not run
`npm` or `git` commands. Codex temporarily reused the main worktree's
`node_modules` in the Claude worktree, removed the symlink before staging, and
performed final verification and integration.

The main worktree still has the pre-existing local `.env` modification. It was
not staged or changed by this round.
