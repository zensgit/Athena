# Phase 6 P1 Mail Automation - Real Backend Integration Verification

## Date
2026-02-15

## Environment
- UI: `http://localhost`
- API: `http://localhost:7700`
- Keycloak: `http://localhost:8180`

## Automated Test

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost ECM_API_URL=http://localhost:7700 npx playwright test \
  e2e/mail-automation-phase6-p1.spec.ts \
  --project=chromium --workers=1
```

or (one-command wrapper):

```bash
ECM_UI_URL=http://localhost bash scripts/phase6-mail-automation-integration-smoke.sh
```

## Verified Scope
- Mail Automation page loads under real auth/session flow.
- Phase 6 P1 UI sections render under real backend data:
  - Account health summary
  - Fetch diagnostics summary block
  - Processed status quick-filter chips
- Preview dialog can be opened and `Run Preview` controls render when preview-capable rule exists.

## Result
- PASS (2026-02-15): `1 passed (6.2s)`

## Notes
- The integration test auto-seeds a temporary account if the environment has zero mail accounts, then cleans it up at the end.
- If no preview-capable rule exists, preview-dialog deep assertions are skipped while the rest of the integration checks still pass.
