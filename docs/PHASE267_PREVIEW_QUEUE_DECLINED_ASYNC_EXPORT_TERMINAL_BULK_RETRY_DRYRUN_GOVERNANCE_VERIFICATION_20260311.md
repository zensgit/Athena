# Phase 267 - Preview Queue Declined Async Export Terminal Bulk Retry Dry-run Governance (Verification)

Date: 2026-03-11

## Verification checklist

1. backend dry-run endpoint/security/audit coverage passes;
2. frontend dry-run action and service integration pass lint/build;
3. mocked e2e validates `dry-run -> retry` flow and call propagation.

## Executed commands

### Backend tests

```bash
cd /Users/huazhou/Downloads/Github/Athena/ecm-core
mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest,PreviewQueueServiceTest test
```

Result: PASS

### Frontend lint

```bash
cd /Users/huazhou/Downloads/Github/Athena/ecm-frontend
npm run lint -- src/pages/PreviewDiagnosticsPage.tsx src/services/previewDiagnosticsService.ts e2e/admin-preview-diagnostics.mock.spec.ts
```

Result: PASS

### Frontend build

```bash
cd /Users/huazhou/Downloads/Github/Athena/ecm-frontend
npm run build
```

Result: PASS

### Playwright browser bootstrap (local one-time)

```bash
cd /Users/huazhou/Downloads/Github/Athena/ecm-frontend
npx playwright install chromium
```

Result: PASS

### Frontend mocked e2e

```bash
cd /Users/huazhou/Downloads/Github/Athena/ecm-frontend
npx serve -s build -l 5510
ECM_UI_URL=http://localhost:5510 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium
```

Result: PASS (`1 passed`)

## Validated outcomes

1. `retry-terminal/dry-run` endpoint enforces terminal-only governance and bounded sampling.
2. dry-run response exposes actionable metrics (`requested/retryable/skipped`) and candidate rows.
3. preview diagnostics UI supports operator precheck before bulk retry execution.
4. audit stream contains `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK_DRY_RUN` with status filter and counters.
