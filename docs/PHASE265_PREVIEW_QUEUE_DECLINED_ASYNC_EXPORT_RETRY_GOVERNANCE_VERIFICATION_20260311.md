# Phase 265 - Preview Queue Declined Async Export Retry Governance (Verification)

Date: 2026-03-11

## Verification checklist

1. backend retry endpoint behavior and security coverage pass;
2. frontend retry action integrates correctly in queue declined async task center;
3. mocked e2e validates retry route and UI flow.

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

### Frontend mocked e2e

```bash
cd /Users/huazhou/Downloads/Github/Athena/ecm-frontend
npx serve -s build -l 5510
ECM_UI_URL=http://localhost:5510 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium
```

Result: PASS (`1 passed`)

## Validated outcomes

1. queue declined async export retry endpoint enforces `404/409/terminal-retry` semantics;
2. retry action in UI is exposed only for `FAILED/CANCELLED` and creates a new async task;
3. mocked e2e verifies retry API call and retried task flow;
4. audit stream includes retry event with source/new task linkage and filter context.
