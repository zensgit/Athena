# Verification: Frontend Prebuilt Build Workflow (2026-02-10)

## Purpose

Verify that rebuilding `ecm-frontend/build/` and refreshing the `ecm-frontend` container resolves UI/E2E drift when `Dockerfile.prebuilt` is enabled via `docker-compose.override.yml`.

## Environment

- UI: `ECM_UI_URL=http://localhost:5500`
- API: `ECM_API_URL=http://localhost:7700`

## Steps

1. Rebuild prebuilt frontend assets and refresh the running container:

```bash
./scripts/rebuild-frontend-prebuilt.sh
```

2. Re-run the previously failing E2E:

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/mail-automation.spec.ts -g "reset OAuth state"
```

Result: **PASS** (`Mail automation can reset OAuth state (e2e safe)`).

3. Run the full frontend E2E suite:

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test
```

Result: **PASS** (`48 passed, 4 skipped`).

## Notes

- No OAuth credentials/tokens are written to the repo as part of this workflow.

