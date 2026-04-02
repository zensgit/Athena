# Phase 264 - Preview Queue Declined Async Export Status Filter Governance (Verification)

Date: 2026-03-11

## Verification checklist

1. backend tests cover new declined async export audit hooks;
2. frontend status-filter task-center behavior passes lint/build;
3. mocked e2e validates status-filter propagation and governance actions.

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
npm run lint -- src/pages/PreviewDiagnosticsPage.tsx e2e/admin-preview-diagnostics.mock.spec.ts
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

1. backend declined async export lifecycle operations emit audit events with task id, status, and filter context;
2. queue declined async task center now supports status filtering in UI and API propagation;
3. cancel-active and cleanup behavior are status-aware and constrained as designed;
4. mocked e2e verifies status propagation across list/summary/cancel-active/cleanup and terminal filtered views.
