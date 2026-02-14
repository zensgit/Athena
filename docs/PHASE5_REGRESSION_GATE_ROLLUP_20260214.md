# Phase 5 - Regression Gate Rollup (Mocked-First)

Date: 2026-02-14

## Goal

Provide a single CLI entrypoint to validate the Phase 5 admin UX slices without requiring the full backend stack.

## Command

```bash
bash scripts/phase5-regression.sh
```

## CI

This gate is also executed in GitHub Actions:

- Workflow: `.github/workflows/ci.yml`
- Job: `frontend_e2e_phase5_mocked` (Phase 5 Mocked Regression Gate)

## What It Runs

The gate is intentionally **mocked-first** and runs against a static build server (`:5500`):

- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
- `ecm-frontend/e2e/permissions-dialog-presets.mock.spec.ts`
- `ecm-frontend/e2e/admin-audit-filter-export.mock.spec.ts`
- `ecm-frontend/e2e/version-history-paging-major-only.mock.spec.ts`
- `ecm-frontend/e2e/search-suggestions-save-search.mock.spec.ts`
- `ecm-frontend/e2e/mail-automation-trigger-fetch.mock.spec.ts`
- `ecm-frontend/e2e/mail-automation-diagnostics-export.mock.spec.ts`
- `ecm-frontend/e2e/mail-automation-processed-management.mock.spec.ts`

## Behavior

The script:

1. Builds the frontend (`npm run build`)
2. Ensures a static server is reachable on `http://127.0.0.1:5500/` (starts one if needed)
3. Runs Playwright with:
   - `ECM_UI_URL` (default: `http://localhost:5500`)
   - `--project=chromium`
   - `--workers=1`

## Environment Overrides

- `ECM_UI_URL` (default `http://localhost:5500`)
- `PW_PROJECT` (default `chromium`)
- `PW_WORKERS` (default `1`)

Example:

```bash
ECM_UI_URL=http://localhost:5500 PW_WORKERS=2 bash scripts/phase5-regression.sh
```

## Notes

- This gate does not require Docker.
- Integration suites (full-stack) remain separate, since they depend on Keycloak/DB/Elasticsearch.
