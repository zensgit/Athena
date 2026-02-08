# Verification: Permission Diagnostics Matched Grants (P64) (2026-02-09)

## Prerequisites

- Docker Desktop running
- Athena services reachable:
  - UI: `http://localhost:5500/`
  - API: `http://localhost:7700/actuator/health` returns `200`

## Build / Restart (Local)

Rebuild and restart the two relevant containers:

```bash
bash scripts/restart-ecm.sh
```

Expected:

- Script prints `OK`
- `athena-ecm-core-1` becomes `(healthy)`

## Automated E2E (Playwright)

Run the focused regression test:

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/permission-explanation.spec.ts
```

Expected:

- `1 passed`
- In the Permissions dialog (diagnosing `viewer`), diagnostics shows:
  - `Reason ACL_ALLOW` (or `ACL_DENY` depending on test setup)
  - A "Matched grants" section including an `Inherited` marker

## Artifacts

On failure, Playwright writes:

- `ecm-frontend/test-results/**/trace.zip`
- `ecm-frontend/test-results/**/screenshot.png`

View a trace locally:

```bash
cd ecm-frontend
npx playwright show-trace test-results/**/trace.zip
```

