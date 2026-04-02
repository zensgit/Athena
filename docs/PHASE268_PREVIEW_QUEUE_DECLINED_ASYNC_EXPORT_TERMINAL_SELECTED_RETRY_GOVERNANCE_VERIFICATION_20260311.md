# Phase 268 - Preview Queue Declined Async Export Terminal Selected-Retry Governance (Verification)

Date: 2026-03-11

## Verification checklist

1. backend selected-retry endpoint/security/audit coverage passes;
2. frontend dry-run candidate selection and selected-retry action pass lint/build;
3. mocked e2e validates `dry-run -> retry selected -> retry terminal` flow.

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

1. `retry-terminal/by-task-ids` endpoint accepts explicit source task ids and retries only eligible terminal tasks.
2. selected-retry response/audit expose actionable governance metrics and source/new task linkage.
3. UI supports operator-controlled retry scope via dry-run candidate selection.
4. mocked e2e assertions cover dry-run call, selected-retry call, and downstream task center lifecycle.
