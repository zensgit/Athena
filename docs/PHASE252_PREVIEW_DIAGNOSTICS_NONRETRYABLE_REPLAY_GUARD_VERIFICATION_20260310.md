# Phase 252 - Preview Diagnostics Non-retryable Replay Guard (Verification)

Date: 2026-03-10  
Scope: `ecm-frontend`

## 1. Static Gates

### 1.1 Lint

Command:

```bash
cd ecm-frontend
npm run -s lint -- src/pages/AdvancedSearchPage.tsx src/pages/PreviewDiagnosticsPage.tsx e2e/advanced-search-preview-batch-scope.mock.spec.ts e2e/admin-preview-diagnostics.mock.spec.ts
```

Result: PASS

### 1.2 Build

Command:

```bash
cd ecm-frontend
npm run -s build
```

Result: PASS

## 2. Mocked E2E Verification

### 2.1 Preview Diagnostics governance flow

Command:

```bash
cd ecm-frontend
npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts -g "Preview diagnostics renders failures and gates retry actions"
```

Result: PASS

Validated:

- Non-retryable/unsupported reason row has disabled `Replay DL`.
- Non-retryable/unsupported reason row still supports `Clear DL`.
- clear-by-filter payload for unsupported reason uses `category=UNSUPPORTED`, `retryable=false`.
- replay-by-filter payloads remain retryable-only.

### 2.2 Advanced Search regression guard

Command:

```bash
cd ecm-frontend
npx playwright test e2e/advanced-search-preview-batch-scope.mock.spec.ts
```

Result: PASS (2 tests)

Validated:

- Advanced Search reason-level dead-letter governance remains stable after Preview Diagnostics replay-guard update.

## 3. Acceptance Checklist

- [x] Non-retryable replay action is blocked in Preview Diagnostics reason table.
- [x] Clear action remains available for non-retryable reasons.
- [x] Lint/build and mocked e2e regressions passed.
