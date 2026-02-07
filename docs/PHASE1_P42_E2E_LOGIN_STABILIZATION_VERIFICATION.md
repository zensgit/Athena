# Phase 1 P42 - E2E Login Stabilization Verification

## Environment

- Repo: `Athena`
- Frontend: `ecm-frontend/`
- Runner: Playwright (`chromium`)
- Date: 2026-02-07

## Commands and Results

### 1) P1 smoke full suite

Command:

```bash
cd ecm-frontend
npx playwright test e2e/p1-smoke.spec.ts --workers=1
```

Result:

- `4 passed`
- `0 failed`
- Duration: `~8.2s`

### 2) UI smoke full suite

Command:

```bash
cd ecm-frontend
npx playwright test e2e/ui-smoke.spec.ts --workers=1
```

Result:

- `10 passed`
- `0 failed`
- Duration: `~2.4m`

Observed key checkpoints:

- Browse/upload/search/copy/move/rules scenario passed.
- Mail automation actions passed.
- RBAC (editor/viewer) scenarios passed.
- Scheduled rule trigger flow passed.
- Antivirus EICAR rejection scenario passed.

### 3) Search preview status regression

Command:

```bash
cd ecm-frontend
npx playwright test e2e/search-preview-status.spec.ts --workers=1
```

Result:

- `3 passed`
- `0 failed`
- Duration: `~7.3s`

## Conclusion

The new token-first E2E login helper strategy removed Keycloak callback flakiness in smoke paths without regressing search preview workflows.

Current status for this round:

- `p1-smoke`: green
- `ui-smoke`: green
- `search-preview-status`: green
