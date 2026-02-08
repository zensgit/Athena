# Development and Verification - Preview Unsupported Behavior (2026-02-08)

## Scope

This update completed the remaining `1+2+3` execution items:

1. Keep core E2E gates stable for `ui-smoke` target scenarios.
2. Keep `permissions-dialog` in core E2E gate and stable.
3. Fix and verify preview status behavior for unsupported files in Search and Advanced Search.

## Design / Fix Summary

### Problem

Unsupported preview failures (`application/octet-stream`) were shown as generic `Preview failed` and exposed retry actions that should be hidden for unsupported failures.

### Root cause

- Unsupported detection was too strict for category/reason variants.
- Frontend container on `:5500` uses `Dockerfile.prebuilt` (`COPY build/ ...`), so source changes require a local `npm run build` before `docker compose up --build`.

### Implementation

1. Hardened unsupported detection in `ecm-frontend/src/utils/previewStatusUtils.ts`:
   - Normalize separators and whitespace in failure reasons.
   - Recognize more unsupported phrases:
     - `unsupported media type`
     - `unsupported format`
   - Treat category variants containing `UNSUPPORTED` (for example `UNSUPPORTED_MEDIA_TYPE`) as unsupported.
2. Expanded unit tests in `ecm-frontend/src/utils/previewStatusUtils.test.ts`:
   - Added reason-variant coverage (hyphen/extra spaces/underscore variants).
   - Added category-variant coverage for `UNSUPPORTED_MEDIA_TYPE`.

## Verification

### Unit

- Command:
  - `npm test -- --watch=false --runTestsByPath src/utils/previewStatusUtils.test.ts`
- Result:
  - `9 passed`

### Build and runtime refresh (`:5500`)

- Commands:
  - `npm run build` (in `ecm-frontend/`)
  - `docker compose up -d --build ecm-frontend`
  - Health checks:
    - `curl http://localhost:5500/` => `200`
    - `curl http://localhost:7700/actuator/health` => `200`

### Playwright E2E (targeted + core gate)

- `search-preview-status`:
  - `npx playwright test e2e/search-preview-status.spec.ts --workers=1`
  - Result: `3 passed`
- `permissions-dialog`:
  - `npx playwright test e2e/permissions-dialog.spec.ts --workers=1`
  - Result: `1 passed`
- `ui-smoke` target scenarios:
  - `npx playwright test e2e/ui-smoke.spec.ts --workers=1 --grep 'UI smoke: PDF upload \+ search \+ version history \+ preview|UI search download failure shows error toast'`
  - Result: `2 passed`
- CI-equivalent core E2E list:
  - `npx playwright test --workers=1 e2e/browse-acl.spec.ts e2e/mfa-settings.spec.ts e2e/pdf-preview.spec.ts e2e/permissions-dialog.spec.ts e2e/permission-templates.spec.ts e2e/rules-manual-backfill-validation.spec.ts e2e/search-highlight.spec.ts e2e/search-preview-status.spec.ts e2e/search-sort-pagination.spec.ts e2e/search-view.spec.ts e2e/version-details.spec.ts e2e/version-share-download.spec.ts`
  - Result: `19 passed`

## Files changed in this cycle

- `ecm-frontend/src/utils/previewStatusUtils.ts`
- `ecm-frontend/src/utils/previewStatusUtils.test.ts`

## Notes

- Existing core gate coverage updates in `.github/workflows/ci.yml` and E2E scenario hardening in:
  - `ecm-frontend/e2e/ui-smoke.spec.ts`
  - `ecm-frontend/e2e/permissions-dialog.spec.ts`
  were preserved and validated in this run.
