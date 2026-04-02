# Phase 251 - Advanced Search Non-retryable Dead-letter Clear (Verification)

Date: 2026-03-10  
Scope: `ecm-frontend`

## 1. Static Gates

### 1.1 Lint

Command:

```bash
cd ecm-frontend
npm run -s lint -- src/pages/AdvancedSearchPage.tsx e2e/advanced-search-preview-batch-scope.mock.spec.ts
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

### 2.1 Advanced Search scope + governance flow

Command:

```bash
cd ecm-frontend
npx playwright test e2e/advanced-search-preview-batch-scope.mock.spec.ts
```

Result: PASS (2 tests)

Validated:

- Non-retryable reason bucket is present in Advanced Search page-level preview issue summary.
- Non-retryable `Clear DL` action triggers `POST /api/v1/ops/recovery/clear-by-filter`.
- Payload includes `category=UNSUPPORTED` and `retryable=false`.
- Existing retryable reason replay/clear flows remain passing.

### 2.2 Preview diagnostics regression guard

Command:

```bash
cd ecm-frontend
npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts -g "Preview diagnostics renders failures and gates retry actions"
```

Result: PASS

Validated:

- Preview diagnostics dry-run/replay/clear governance path remains stable after Advanced Search updates.

## 3. Acceptance Checklist

- [x] Advanced Search supports non-retryable reason-level dead-letter clear action.
- [x] Action key uniqueness prevents cross-bucket busy-state collisions.
- [x] Lint/build + mocked e2e + regression guard passed.

## 4. Backend Contract Regression

Command:

```bash
cd ecm-core
mvn -q -Dtest=OpsRecoveryControllerSecurityTest test
```

Result: PASS

Validated:

- `clear-by-filter` accepts `category=UNSUPPORTED` + `retryable=false` contract.
- Admin endpoint security and response contract remain stable.
