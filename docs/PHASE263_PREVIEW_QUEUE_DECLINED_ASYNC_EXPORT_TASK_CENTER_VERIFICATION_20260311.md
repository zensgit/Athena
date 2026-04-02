# Phase 263 - Preview Queue Declined Async Export Task Center (Verification)

Date: 2026-03-11

## Verification checklist

1. backend security/controller coverage passes for declined async task APIs;
2. frontend service/page/e2e contract compiles and passes lint/build;
3. mocked e2e validates async task-center flows end-to-end.

## Executed commands

### Backend tests

```bash
cd /Users/huazhou/Downloads/Github/Athena/ecm-core
mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest,PreviewQueueServiceTest test
```

Result: PASS

Validated:
- declined async export endpoints are admin-protected;
- task lifecycle APIs (`start/list/summary/get/cancel/download`) are functional;
- cancel-active/cleanup governance paths behave as expected.

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

Validated flow:
- start declined async export task from current filter context;
- task list refresh and summary counters update;
- completed task download works with filename fallback;
- row cancel and bulk `Cancel Active` behave as expected;
- bulk `Cleanup` removes terminal tasks.

