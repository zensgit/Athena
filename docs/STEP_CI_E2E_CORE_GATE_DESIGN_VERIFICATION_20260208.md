# Step: CI Core E2E Gate (Design + Verification) (2026-02-08)

## Objective
- Add a deterministic frontend E2E gate in CI for core business paths.
- Ensure failure diagnostics are preserved (Playwright trace/screenshot/video + container logs).

## Workflow Change
- Updated file:
  - `.github/workflows/ci.yml`
- Added new job:
  - `frontend_e2e_core` (`needs: backend, frontend`)

## CI Job Design

## 1) Stack bootstrap for E2E
- Creates `.env` and `.env.mail` for CI.
- Starts required docker services and app containers:
  - infra: `postgres`, `redis`, `rabbitmq`, `minio`, `keycloak`, `postgres-keycloak`, `elasticsearch`, `ml-service`, `collabora`, `clamav`, `greenmail`
  - app: `ecm-core`, `ecm-frontend`
- Waits for readiness:
  - API `http://localhost:7700/actuator/health`
  - UI `http://localhost:5500/`
  - Keycloak discovery `http://localhost:8180/realms/ecm/.well-known/openid-configuration`

## 2) Core E2E gate list
- Runs these specs as required gate:
  - `e2e/browse-acl.spec.ts`
  - `e2e/mfa-settings.spec.ts`
  - `e2e/pdf-preview.spec.ts`
  - `e2e/permission-templates.spec.ts`
  - `e2e/rules-manual-backfill-validation.spec.ts`
  - `e2e/search-highlight.spec.ts`
  - `e2e/search-preview-status.spec.ts`
  - `e2e/search-sort-pagination.spec.ts`
  - `e2e/search-view.spec.ts`
  - `e2e/version-details.spec.ts`
  - `e2e/version-share-download.spec.ts`

## 3) Failure observability
- On failure, captures docker logs into `tmp/ci-e2e-logs`.
- Uploads artifacts:
  - `ecm-frontend/playwright-report`
  - `ecm-frontend/test-results`
  - `tmp/ci-e2e-logs`

## 4) Teardown
- Always executes `docker compose down -v`.

## Inclusion/Exclusion Rationale
- Included: tests that are deterministic in clean env and represent core features.
- Excluded from required gate in this step:
  - `mail-automation` (depends on preconfigured runtime mail account state)
  - `webhook-admin` (depends on `host.docker.internal` callback behavior)
  - `permissions-dialog` (currently unstable in clean run due list row discovery timing)

## Local Verification
- Ran the CI core list against local latest source target:

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 ECM_E2E_WORKERS=1 \
  npx playwright test --workers=1 \
    e2e/browse-acl.spec.ts \
    e2e/mfa-settings.spec.ts \
    e2e/pdf-preview.spec.ts \
    e2e/permission-templates.spec.ts \
    e2e/rules-manual-backfill-validation.spec.ts \
    e2e/search-highlight.spec.ts \
    e2e/search-preview-status.spec.ts \
    e2e/search-sort-pagination.spec.ts \
    e2e/search-view.spec.ts \
    e2e/version-details.spec.ts \
    e2e/version-share-download.spec.ts
```

- Result: `18 passed`, `0 failed`.

## Expected CI Outcome
- PRs touching frontend/backend integration now have a stable core E2E gate.
- On failures, diagnostics are available directly from uploaded artifacts.
