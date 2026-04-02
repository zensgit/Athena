# Phase 245 - Ops Recovery Dead-letter Clear Batch Control-plane (Verification)

Date: 2026-03-10  
Scope: `ecm-core`, `ecm-frontend`

## 1. Backend Verification

### 1.1 Ops recovery security + behavior test

Command:

```bash
cd ecm-core
mvn -q -Dtest=OpsRecoveryControllerSecurityTest test
```

Result: PASS

Validated:

- `/ops/recovery/clear-batch` is admin-gated
- admin clear-batch response mode is `CLEAR_BATCH`
- per-item result includes `jobState= CLEARED`
- audit event `OPS_RECOVERY_CLEAR_BATCH` emitted

## 2. Frontend Static Gates

### 2.1 Lint

Command:

```bash
cd ecm-frontend
npm run -s lint -- src/pages/PreviewDiagnosticsPage.tsx src/services/opsRecoveryService.ts src/services/previewDiagnosticsService.ts
```

Result: PASS

### 2.2 Production build

Command:

```bash
cd ecm-frontend
npm run -s build
```

Result: PASS

## 3. Mocked E2E

Command:

```bash
cd ecm-frontend
npx serve -s build -l 5500
# another shell
npx playwright test admin-preview-diagnostics.mock.spec.ts -g "Preview diagnostics renders failures and gates retry actions"
```

Result: PASS

Validated:

- dead-letter `Clear Selected` and row-level clear hit `/ops/recovery/clear-batch`
- replay/export behavior remains working in the same scenario
- clear-batch call assertions pass

## 4. Acceptance Checklist

- [x] Ops recovery control-plane has clear-batch endpoint
- [x] Frontend switched clear flow to unified ops recovery API
- [x] History filter model supports `CLEAR_BATCH` mode/event
- [x] Backend + frontend + mocked e2e gates all pass
