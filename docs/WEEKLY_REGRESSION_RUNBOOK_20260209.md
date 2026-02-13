# Weekly Regression Runbook (2026-02-09)

This runbook defines a repeatable weekly regression for Athena ECM (search, preview, versions, permissions, audit, mail automation).

## Primary Entry Point

Use the repository verification runner:

- `scripts/verify.sh`

It consolidates logs under `tmp/` and can run:

- Docker restart (optional)
- API smoke tests
- Phase C security verification
- Frontend build
- WOPI verification (optional)
- Playwright E2E (Chromium)

## Recommended Weekly Run (Full)

```bash
./scripts/verify.sh
```

Expected:

- Summary shows `Verification PASSED`
- A timestamped report is written under `tmp/*_verify-report.md`

## Fast Local Loop (When Iterating)

Skip restart and WOPI, keep core API + E2E coverage:

```bash
./scripts/verify.sh --no-restart --skip-wopi
```

Or only API smoke tests:

```bash
./scripts/verify.sh --no-restart --smoke-only --skip-wopi
```

## Environment Variables (WOPI Step)

WOPI verification uses variables (configure via repo `.env`):

- `ECM_FRONTEND_URL`
- `ECM_API_URL`
- `KEYCLOAK_URL`
- `KEYCLOAK_REALM`
- `ECM_VERIFY_USER`
- `ECM_VERIFY_PASS`

Note:

- Do not commit secrets. Use local `.env` for developer machines.

## Artifacts / Logs

- `tmp/<timestamp>_verify.log` and per-step logs
- `tmp/<timestamp>_verify-report.md`
- `tmp/<timestamp>_verify-summary.json`
- Playwright artifacts under `ecm-frontend/test-results/` (on failure)

## Troubleshooting

### Docker stuck / disk pressure

If Docker becomes unresponsive or disk is close to full:

```bash
docker system df
docker system prune -af
```

If it still hangs, restart Docker Desktop.

