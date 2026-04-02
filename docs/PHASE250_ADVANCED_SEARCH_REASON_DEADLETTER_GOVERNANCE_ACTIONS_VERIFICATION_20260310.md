# Phase 250 - Advanced Search Reason-level Dead-letter Governance Actions (Verification)

Date: 2026-03-10  
Scope: `ecm-frontend`

## 1. Frontend Static Gates

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

### 2.1 Advanced search reason-scope workflow

Command:

```bash
cd ecm-frontend
npx playwright test e2e/advanced-search-preview-batch-scope.mock.spec.ts
```

Result: PASS

Validated:

- New reason-level buttons are visible and clickable.
- `replay-by-filter` and `clear-by-filter` calls are issued from Advanced Search.
- Payloads include expected reason/category/retryable/force values.

### 2.2 Regression guard for preview diagnostics

Command:

```bash
cd ecm-frontend
npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts -g "Preview diagnostics renders failures and gates retry actions"
```

Result: PASS

Validated:

- Preview diagnostics dry-run/replay/clear governance flow remains passing after Advanced Search changes.

## 3. Acceptance Checklist

- [x] Advanced Search supports reason-level dead-letter replay/clear actions
- [x] Mocked contracts validate endpoint payloads
- [x] Lint/build and e2e suites passed
