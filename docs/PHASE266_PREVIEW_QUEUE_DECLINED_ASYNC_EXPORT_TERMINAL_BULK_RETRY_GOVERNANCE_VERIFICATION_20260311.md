# Phase 266 - Preview Queue Declined Async Export Terminal Bulk Retry Governance (Verification)

Date: 2026-03-11

## Verification checklist

1. backend bulk retry endpoint/security/audit coverage passes;
2. frontend bulk retry button and service integration pass lint/build;
3. mocked e2e validates retry-terminal API wiring and retried task lifecycle visibility.

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

> Note: if `5510` is already occupied, `serve` will auto-pick another local port.  
> In this run it selected `55007`, and e2e was executed with `ECM_UI_URL=http://localhost:55007`.

## Validated outcomes

1. `retry-terminal` endpoint enforces terminal-only status governance and supports bounded bulk retry.
2. bulk retry response carries actionable aggregate/result-item metrics (`retried/skipped/failed`).
3. preview diagnostics UI bulk action correctly propagates status/limit and refreshes task center.
4. audit stream contains `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK` with status filter, counters, and task linkage.
