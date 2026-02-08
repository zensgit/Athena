# Phase 1 P56: E2E Core Gate Stabilization Verification

Date: 2026-02-08

## Scope
Verification for the E2E-only stabilization changes described in:
- `docs/PHASE1_P56_E2E_CORE_GATE_STABILIZATION_DESIGN_20260208.md`

Focus:
- CI core-gate Playwright suite compatibility
- `ui-smoke` PDF search/download robustness
- `localhost` IPv6 flake elimination for Playwright API calls

## Preconditions
- Full stack is running locally with:
  - UI: `http://localhost:5500`
  - API: `http://localhost:7700`
  - Keycloak: `http://localhost:8180`

## Commands + Results

### 1) Frontend Lint
From `ecm-frontend/`:
```bash
npm run lint
```
Result: ✅ PASS

### 2) Playwright (CI Core Gate Suite)
From `ecm-frontend/`:
```bash
npx playwright test --workers=1 \
  e2e/browse-acl.spec.ts \
  e2e/mfa-settings.spec.ts \
  e2e/pdf-preview.spec.ts \
  e2e/permissions-dialog.spec.ts \
  e2e/permission-templates.spec.ts \
  e2e/rules-manual-backfill-validation.spec.ts \
  e2e/search-highlight.spec.ts \
  e2e/search-preview-status.spec.ts \
  e2e/search-sort-pagination.spec.ts \
  e2e/search-view.spec.ts \
  e2e/version-details.spec.ts \
  e2e/version-share-download.spec.ts
```
Result: ✅ PASS (`19 passed`, ~`1.3m`)

### 3) Playwright (UI Smoke Core Gate Grep Subset)
From `ecm-frontend/`:
```bash
npx playwright test --workers=1 e2e/ui-smoke.spec.ts \
  --grep "UI smoke: PDF upload \\+ search \\+ version history \\+ preview|UI search download failure shows error toast"
```
Result: ✅ PASS (`2 passed`, ~`16s`)

## Notes
- These checks validate that the E2E suite no longer depends on `localhost` resolving to IPv4/IPv6 in a specific order for API readiness and direct API requests.
- The PDF search-card download assertion now uses a fallback strategy (Download button, View->More actions->Download, or API validation).

