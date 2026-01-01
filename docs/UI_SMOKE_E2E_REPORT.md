# UI Smoke E2E Report

## Scope
- Full UI smoke suite (`ecm-frontend/e2e/ui-smoke.spec.ts`).
- Targeted to the Dockerized UI (`ECM_UI_URL=http://localhost:5500`) and API (`ECM_API_URL=http://localhost:7700`).

## Command
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/ui-smoke.spec.ts
```

## Result
- 9 tests passed (3.6m).
- ClamAV unavailable at the time of run; antivirus test skipped per test logic after 30s wait.

## Notes
- Scheduled rule test created and removed a dedicated folder and verified auto-tagging.
- RBAC checks for editor/viewer passed.
