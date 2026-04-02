# Phase 171 - Ops Dry-Run + Policy Rollback UX and Verification Hardening (Development)

## Date
2026-03-06

## Goal
- Continue the unified ops track by closing operator loop gaps:
  - add reason-scope dry-run in preview diagnostics UI before triggering batch recovery;
  - add policy rollback control in UI for rapid rollback to previous snapshot;
  - align mocked E2E API routes with migrated `ops/*` contracts;
  - add backend service-level tests for policy versioning and rollback semantics.

## Implemented

### 1) Preview diagnostics reason dry-run action
- File: `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- Added per-reason `Dry run` action in Backend Failure Summary table.
- Dry-run request uses:
  - `opsRecoveryService.dryRun({ mode: 'QUEUE_BY_REASON', domain: 'PREVIEW', reason, category, retryable, days, maxDocuments })`
- Result is surfaced as operator toast:
  - `matched / estimatedQueued / estimatedSkipped / estimatedFailed`.

### 2) Failure policy rollback action
- File: `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- Added `Rollback latest` button in Failure Policy Profiles panel.
- Rollback call:
  - `opsPolicyService.rollback('PREVIEW', { reason: 'ui_rollback_latest' })`
- On success:
  - refreshes policies and drafts;
  - refreshes policy version chip;
  - emits toast with `previousVersion -> rolledBackToVersion -> currentVersion`.

### 3) Mocked E2E alignment to ops APIs
- File: `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
- Replaced legacy policy mocks with:
  - `GET /api/v1/ops/policies`
  - `PUT /api/v1/ops/policies/{domain}`
  - `POST /api/v1/ops/policies/{domain}/rollback`
- Replaced legacy recovery mocks with:
  - `POST /api/v1/ops/recovery/replay-batch`
  - `POST /api/v1/ops/recovery/queue-by-reason`
  - `POST /api/v1/ops/recovery/dry-run`
- Adjusted mocked response payloads to `RecoveryBatchResult` and policy-domain contracts used by current UI.
- Extended scenario assertions:
  - dry-run toast for top reason;
  - rollback toast and rollback-call tracking.

### 4) Backend policy service test coverage
- File: `ecm-core/src/test/java/com/ecm/core/service/OpsPolicyServiceTest.java`
- Added tests for:
  - bootstrap snapshot initialization;
  - version increment + profile mutation on update;
  - rollback to previous snapshot when target omitted;
  - invalid target rollback rejection.
