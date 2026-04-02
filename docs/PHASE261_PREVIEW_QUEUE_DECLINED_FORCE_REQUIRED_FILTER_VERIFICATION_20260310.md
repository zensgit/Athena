# Phase 261 - Preview Queue Declined ForceRequired Filter & Category Breakdown (Verification)

Date: 2026-03-10

## Verification checklist

1. backend declined governance tests pass;
2. frontend lint/build pass after new filter + payload fields;
3. mocked e2e covers `forceRequired` propagation end-to-end.

## Executed commands

### Backend tests

```bash
cd /Users/huazhou/Downloads/Github/Athena/ecm-core
mvn -q -Dtest=PreviewQueueServiceTest,PreviewDiagnosticsControllerSecurityTest test
```

Result: PASS

Validated:
- declined endpoints accept `forceRequired`
- summary/export/action payloads include new force-required fields
- controller security and admin behavior remain correct

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
- declined category + query + forceRequired filters
- dry-run/requeue/clear/export all carry `forceRequired` query param
- UI dry-run summary displays `forceRequiredFilter=NO`
- response chips show category breakdown and force-required counters

## Note

An initial Playwright run failed with `ERR_CONNECTION_REFUSED` when `:5510` was not running; rerun with local static server passed.
