# Phase 253 - Preview Failure Ledger Lifecycle Reset (Verification)

Date: 2026-03-10

## 1. Backend verification

Command:

```bash
cd ecm-core
mvn -q -Dtest=PreviewQueueServiceTest,PreviewDiagnosticsControllerSecurityTest test
```

Result:

- PASSED
- confirms:
  - ledger endpoints are admin-gated and functional
  - batch reset clears ledger fields
  - queue-side terminal failures persist ledger counters/timestamps/reason/hash
  - stale ledger is auto-cleared when content hash changes before enqueue

Command:

```bash
cd ecm-core
mvn -q -Dtest=OpsRecoveryControllerSecurityTest test
```

Result:

- PASSED
- regression safety for ops-recovery governance path.

## 2. Frontend verification

Command:

```bash
cd ecm-frontend
npm run -s lint -- src/pages/PreviewDiagnosticsPage.tsx src/services/previewDiagnosticsService.ts e2e/admin-preview-diagnostics.mock.spec.ts
```

Result:

- PASSED

Command:

```bash
cd ecm-frontend
npm run -s build
```

Result:

- PASSED

Command:

```bash
cd ecm-frontend
npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts -g "Preview diagnostics renders failures and gates retry actions"
```

Result:

- PASSED (1 test)
- confirms:
  - failure ledger panel renders
  - `Reset Selected` triggers batch reset API with selected IDs
  - row-level `Reset` triggers single reset API by document id
  - days window propagates into ledger diagnostics request

## 3. Delivery notes

This phase closes Stream A baseline for persisted failure-ledger visibility and operator reset controls, while preserving existing dead-letter governance and retryability rules.

