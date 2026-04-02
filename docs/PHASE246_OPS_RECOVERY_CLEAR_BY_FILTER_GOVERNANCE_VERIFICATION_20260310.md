# Phase 246 - Ops Recovery Dead-letter Clear-by-filter Governance (Verification)

Date: 2026-03-10  
Scope: `ecm-core`, `ecm-frontend`

## 1. Backend Verification

### 1.1 Controller security + behavior

Command:

```bash
cd ecm-core
mvn -q -Dtest=OpsRecoveryControllerSecurityTest test
```

Result: PASS

Validated:

- `/ops/recovery/clear-by-filter` is admin-gated
- mode `CLEAR_BY_FILTER` response payload is returned
- filter-targeted dead-letter entries are cleared (`jobState=CLEARED`)
- audit event `OPS_RECOVERY_CLEAR_BY_FILTER` logged

## 2. Frontend Static Gates

### 2.1 Lint

Command:

```bash
cd ecm-frontend
npm run -s lint -- src/pages/PreviewDiagnosticsPage.tsx src/services/opsRecoveryService.ts
```

Result: PASS

### 2.2 Build

Command:

```bash
cd ecm-frontend
npm run -s build
```

Result: PASS

## 3. Mocked E2E Verification

Command:

```bash
cd ecm-frontend
npx serve -s build -l 5500
# another shell
npx playwright test admin-preview-diagnostics.mock.spec.ts -g "Preview diagnostics renders failures and gates retry actions"
```

Result: PASS

Validated:

- reason-group `Clear DL` action triggers `/ops/recovery/clear-by-filter`
- request payload captures reason/category/retryable dimensions
- existing replay/clear-batch/export/dead-letter table flows remain passing in same scenario

## 4. Acceptance Checklist

- [x] Backend supports clear-by-filter governance path
- [x] Frontend exposes reason-group clear action
- [x] Recovery history filter model includes `CLEAR_BY_FILTER`
- [x] Backend + frontend + mocked e2e gates passed
