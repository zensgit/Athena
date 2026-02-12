# Weekly Regression Update (2026-02-12)

This document consolidates the highest-signal regression commands to run weekly (or before merging).

The goal is to catch:
- search continuity regressions
- preview/versions regressions
- permission + audit regressions
- mail automation UI regressions

## Prerequisites

1. (Once per machine) Install Playwright browsers:

```bash
cd ecm-frontend
npm run e2e:install
```

2. Bring up a local stack (recommended):

```bash
bash scripts/restart-ecm.sh
```

Quick health checks:

```bash
curl -fsS http://localhost:7700/actuator/health
curl -sS -o /dev/null -w "%{http_code}\n" http://localhost:5500/
```

3. Optional: guard against running E2E against a stale prebuilt UI bundle.

```bash
# dev server (preferred for branch-accurate UI tests)
bash scripts/check-e2e-target.sh http://localhost:3000

# docker/prebuilt UI (allowed, but can be stale unless rebuilt)
ALLOW_STATIC=1 bash scripts/check-e2e-target.sh http://localhost:5500
```

## Backend (Targeted)

Run in a clean Maven container to avoid local toolchain drift:

```bash
docker run --rm -v "$(pwd)":/workspace -w /workspace/ecm-core maven:3-eclipse-temurin-17 \
  mvn -q test \
    -Dtest=MailFetcherServiceDiagnosticsTest,MailReportScheduledExportServiceTest,PermissionTemplateServiceDiffTest,PermissionTemplateDiffExportControllerTest,PreviewStatusFilterHelperTest
```

## Frontend (Unit/Lint)

```bash
cd ecm-frontend
npm test -- --watchAll=false
npm run lint
```

## Frontend E2E (Core Gate)

Environment:
- Docker UI: `ECM_UI_URL=http://localhost:5500`
- API: `ECM_API_URL=http://localhost:7700`

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 ECM_E2E_WORKERS=1 \
  npx playwright test --workers=1 \
    e2e/ui-smoke.spec.ts \
    e2e/browse-acl.spec.ts \
    e2e/pdf-preview.spec.ts \
    e2e/permissions-dialog.spec.ts \
    e2e/permission-templates.spec.ts \
    e2e/search-view.spec.ts \
    e2e/search-sort-pagination.spec.ts \
    e2e/search-preview-status.spec.ts \
    e2e/version-details.spec.ts \
    e2e/version-share-download.spec.ts
```

## Frontend E2E (Extended: Advanced Search + Fallback Governance)

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 ECM_E2E_WORKERS=1 \
  npx playwright test --workers=1 \
    e2e/advanced-search-fallback-governance.spec.ts \
    e2e/search-fallback-governance.spec.ts \
    e2e/search-dialog-active-criteria-summary.spec.ts \
    e2e/saved-search-load-prefill.spec.ts \
    e2e/search-snippet-enrichment.spec.ts \
    e2e/mail-automation.spec.ts \
    e2e/audit-node-filter.spec.ts
```

## One-Click (Smoke Only)

If you want a fast signal without E2E:

```bash
./scripts/verify.sh --no-restart --smoke-only --skip-build --skip-wopi
```

Notes:
- `docker-compose.yml` loads `.env.mail` via `env_file`. If you don't use mail automation locally, `.env.mail` can be empty. Guardrails now auto-create a safe placeholder when missing.
