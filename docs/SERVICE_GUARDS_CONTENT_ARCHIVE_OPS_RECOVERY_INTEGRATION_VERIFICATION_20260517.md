# Content Archive and Ops Recovery Service Guards: Integration Verification

Date: 2026-05-17

## Scope

This round continued the frontend service response-shape guard track with
two independent slices:

- Codex local slice: `contentArchiveService`.
- Claude parallel worktree slice: `opsRecoveryService` core JSON
  methods.

The intent is defensive hardening against HTML fallback or malformed API
responses that mocked frontend tests can otherwise miss. This round did
not change backend controllers, backend contracts, UI pages, endpoint
paths, payloads, default arguments, Blob/CSV download methods, or async
export task lifecycle methods.

`.env` was already modified before this work and was not touched,
staged, or committed.

## Parallel Development

Content archive was implemented directly on `main`:

- Commit: `d733b69 fix(content-archive): guard service responses`.
- Files:
  - `ecm-frontend/src/services/contentArchiveService.ts`
  - `ecm-frontend/src/services/contentArchiveService.test.ts`
  - `docs/CONTENT_ARCHIVE_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260517.md`

Ops recovery was implemented in a Claude worktree and then reviewed,
verified, documented, committed, and cherry-picked back to `main`:

- Worktree path:
  `/Users/chouhua/Downloads/Github/Athena/.claude/worktrees/claude-ops-recovery-core-service-guards`
- Claude worktree commit:
  `ecc18d0 fix(ops-recovery): guard core service responses`
- Main cherry-pick commit:
  `ea34ccf fix(ops-recovery): guard core service responses`
- Files:
  - `ecm-frontend/src/services/opsRecoveryService.ts`
  - `ecm-frontend/src/services/opsRecoveryService.core.test.ts`
  - `docs/OPS_RECOVERY_SERVICE_CORE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260517.md`

Claude produced a complete patch but could not run local npm commands or
stage/commit due its session permissions. Codex took over verification,
temporarily symlinked the main repo `node_modules` into the worktree,
ran the targeted test and lint, removed the symlink, corrected the
verification document, committed the worktree patch, and cherry-picked it
into `main`.

## Guard Coverage

`contentArchiveService` now guards JSON responses for:

- Archive/restore mutation responses.
- Archive status responses.
- Archived-node page envelopes and nested items.
- Archive policy get/upsert/list responses.
- Archive policy dry-run responses and nested candidates.
- Archive policy execute/run responses and nested batch results.

`deleteArchivePolicy` remains an intentional no-content call.

`opsRecoveryService` now guards only the first core JSON slice:

- `queueByReason`
- `queueByWindow`
- `replayBatch`
- `clearBatch`
- `clearByFilter`
- `replayByFilter`
- `dryRun`
- `getHistory`
- `getHistorySummary`
- `getHistorySummaryTrend`
- `getHistorySummaryCompare`
- `getHistorySummaryCompareBreakdown`
- `getHistorySummaryCompareActors`

Out of scope for this round:

- `previewDiagnosticsService`.
- `opsRecoveryService` Blob/CSV download methods.
- `opsRecoveryService` async export task lifecycle methods.
- Other still-unguarded frontend services.

## Verification

Targeted Jest for both slices:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/contentArchiveService.test.ts src/services/opsRecoveryService.core.test.ts --watchAll=false
```

Result:

```text
PASS src/services/contentArchiveService.test.ts
PASS src/services/opsRecoveryService.core.test.ts
Test Suites: 2 passed, 2 total
Tests:       43 passed, 43 total
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
- Build emitted a Node deprecation warning for `fs.F_OK`.
- Neither warning blocked the build.

Diff hygiene:

```bash
git diff --check
```

Result: PASS.

## CI Follow-Up

First pushed CI run:

- Run: `26008430735`
- Head: `421bb6b`
- Result: failed only in `Phase 5 Mocked Regression Gate`.

Passing jobs in that run:

- `Backend Verify`
- `Frontend Build & Test`
- `Phase C Security Verification`
- `Frontend E2E Core Gate`
- `Acceptance Smoke (3 admin pages)`
- `Property Encryption Closeout Gate`

Failure root cause:

- Spec: `e2e/admin-preview-diagnostics.mock.spec.ts`
- Failure: Playwright waited for
  `Recovery executed: queued=1, skipped=0, failed=0`.
- The app did not show the success toast because the new
  `opsRecoveryService` response guard rejected mocked
  `/api/v1/ops/recovery/queue-by-window` response items.
- The mocked `RecoveryBatchItemDto` entries were missing backend
  contract fields now enforced by the guard:
  `jobState` and `failureCategory`.

Fix:

- Updated the Phase 5 mocked ops recovery batch responses for
  `replay-batch`, `queue-by-reason`, and `queue-by-window` to include
  `jobState: 'QUEUED'` and `failureCategory: 'TEMPORARY'`.
- This keeps the guard strict and corrects the mocked backend contract
  instead of weakening runtime validation.

Local targeted E2E verification:

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium --workers=1
```

Result:

```text
1 passed (2.1m)
```

## Follow-Up

The next useful service-guard slices remain:

- `opsRecoveryService` async export task lifecycle methods.
- `previewDiagnosticsService`.
- Other medium-sized unguarded frontend services such as
  `contentModelService`, `propertyEncryptionService`, `peopleService`,
  `ruleService`, `workflowService`, `recordsManagementService`, and
  `nodeService`.

Recommended split for the next parallel round:

- Claude: `previewDiagnosticsService`, because it is large and can be
  isolated in a worktree.
- Codex: a smaller service such as `contentModelService` or
  `propertyEncryptionService`, with Codex retaining final review and
  integration responsibility.
