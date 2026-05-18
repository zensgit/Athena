# Content Model, Property Encryption, and Preview Diagnostics Service Guards: Integration Verification

Date: 2026-05-18

## Scope

This round continued the frontend service response-shape guard track with
three bounded slices:

- Codex local slice: `contentModelService`.
- Codex local slice: `propertyEncryptionService`.
- Claude parallel worktree candidate reviewed and completed by Codex:
  `previewDiagnosticsService` core JSON methods.

The intent is defensive hardening against HTML fallback or malformed API
responses that mocked frontend tests can otherwise miss. This round did
not change backend controllers, backend contracts, UI pages, endpoint
paths, payloads, Blob/CSV download methods, async export task lifecycle
methods, package files, or migrations.

`.env` was already modified before this work and was not touched,
staged, or committed.

## Parallel Development

Content model was implemented directly on `main`:

- Commit: `372a1f8 fix(content-models): guard service responses`.
- Files:
  - `ecm-frontend/src/services/contentModelService.ts`
  - `ecm-frontend/src/services/contentModelService.test.ts`
  - `docs/CONTENT_MODEL_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260518.md`

Property encryption was implemented directly on `main` after the content
model slice:

- Commit: `0e83916 fix(property-encryption): guard service responses`.
- Files:
  - `ecm-frontend/src/services/propertyEncryptionService.ts`
  - `ecm-frontend/src/services/propertyEncryptionService.test.ts`
  - `docs/PROPERTY_ENCRYPTION_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260518.md`

Preview diagnostics was assigned to Claude in a separate worktree:

- Worktree path:
  `.claude/worktrees/claude-preview-diagnostics-core-service-guards`
- Claude status:
  budget exceeded before normal completion, documentation, or commit.
- Codex integration:
  - reviewed the candidate diff
  - verified it applied cleanly with `git apply --check`
  - imported the service patch and new test into `main`
  - ran targeted tests
  - removed an unused type warning that would break `CI=true npm run build`
  - added the design/verification document
  - committed the finalized slice
- Commit: `0838f62 fix(preview-diagnostics): guard core service responses`.
- Files:
  - `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - `ecm-frontend/src/services/previewDiagnosticsService.core.test.ts`
  - `docs/PREVIEW_DIAGNOSTICS_SERVICE_CORE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260518.md`

A final Codex fix was required after the production build caught a
TypeScript strict-cast issue that targeted Jest and ESLint did not catch:

- Commit: `a6621e5 fix(services): satisfy CI response guard casts`.
- Files:
  - `ecm-frontend/src/services/contentModelService.ts`
  - `ecm-frontend/src/services/propertyEncryptionService.ts`

## Guard Coverage

`contentModelService` now guards JSON responses for:

- model list/get/create/update/activate/deactivate
- type add/update
- aspect add/update
- property add-to-type/add-to-aspect
- constraint add

No-content delete methods remain unchanged.

`propertyEncryptionService` now guards JSON responses for:

- operations status
- encrypted definition list
- rewrap and backfill dry-runs
- rewrap and backfill job plan/list/run/cancel

`previewDiagnosticsService` now guards the core diagnostics JSON slice:

- failure samples, summaries, and ledgers
- ledger reset by id, batch, and filter
- rendition summaries and resource payloads
- queue summaries, declined queue summaries, requeue dry-runs, clear
  results, and reason-based queue batches
- CAD failover diagnostics
- transform traces
- failure policies
- rendition-prevention blocked/action/batch results
- dead-letter diagnostics, replay batches, and clear batches

Out of scope for this round:

- `previewDiagnosticsService` CSV/download methods.
- `previewDiagnosticsService` rendition-resource async export task
  lifecycle and task-center helpers.
- Backend integration tests, because endpoint contracts were read but not
  changed.
- Browser/e2e updates, because no UI behavior changed.

## Verification

Targeted Jest for all three slices:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/contentModelService.test.ts src/services/propertyEncryptionService.test.ts src/services/previewDiagnosticsService.core.test.ts --watchAll=false
```

Result:

```text
PASS src/services/previewDiagnosticsService.core.test.ts
PASS src/services/contentModelService.test.ts
PASS src/services/propertyEncryptionService.test.ts
Test Suites: 3 passed, 3 total
Tests:       85 passed, 85 total
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

Not pushed at document write time. Push `main` to trigger the repository
CI gate.

Expected CI-sensitive checks from this round:

- `Frontend Build & Test` should cover the `CI=true` build path that
  caught the strict-cast issue locally.
- `Phase 5 Mocked Regression Gate` should remain relevant because these
  service guards are designed to catch mocked endpoint shape drift.
- Backend and security gates should be unchanged because this round is
  frontend-service-only.

## Follow-Up

Recommended next slices:

- `previewDiagnosticsService` async export task lifecycle and task-center
  helpers, in a smaller dedicated pass.
- Remaining medium-sized unguarded frontend services such as
  `peopleService`, `ruleService`, `workflowService`,
  `recordsManagementService`, and `nodeService`.

Claude can continue to be useful for isolated large service guard slices,
but Codex should keep final integration responsibility: apply the patch
to main, run `CI=true npm run build`, check mocked-contract blast radius,
and avoid merging budget-exceeded or undocumented worktree state.
