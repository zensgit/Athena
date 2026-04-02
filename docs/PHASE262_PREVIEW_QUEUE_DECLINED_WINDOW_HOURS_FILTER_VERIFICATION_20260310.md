# Phase 262 - Preview Queue Declined WindowHours Filter (Verification)

Date: 2026-03-10

## Verification checklist

1. backend declined governance tests pass with `windowHours` filter coverage;
2. frontend lint/build pass after new filter wiring and contract updates;
3. mocked e2e verifies `windowHours` propagation and declined-window behavior.

## Executed commands

### Backend tests

```bash
cd /Users/huazhou/Downloads/Github/Athena/ecm-core
mvn -q -Dtest=PreviewQueueServiceTest,PreviewDiagnosticsControllerSecurityTest test
```

Result: PASS

Validated:
- declined endpoints accept `windowHours`;
- summary/export/requeue/dry-run/clear include `windowHoursFilter`;
- audit text includes `windowHours`;
- security/admin behavior remains correct.

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
- declined filter UI supports `Any/1h/6h/24h/7d`;
- summary/export/requeue/dry-run/clear all carry `windowHours` query param;
- dry-run summary echoes `windowHours`;
- mock backend applies `declinedAt` time-window filtering correctly.
