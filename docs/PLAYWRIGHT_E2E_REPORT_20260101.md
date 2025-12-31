# Playwright E2E Run (2026-01-01)

## Command
- cd ecm-frontend
- npm run e2e

## Environment
- UI base URL: http://localhost:5500 (ECM_UI_URL not set)
- Report output: ecm-frontend/playwright-report
- Artifacts: ecm-frontend/test-results (screenshots, videos, traces)

## Summary
- Total: 15
- Passed: 3
- Failed: 12
- Duration: 13.5m
- Warnings: NO_COLOR ignored due to FORCE_COLOR.

## Rerun (dev server on 3000)
- Commands:
  - `BROWSER=none PORT=3000 REACT_APP_API_BASE_URL=http://localhost:7700 npm start`
  - `ECM_UI_URL=http://localhost:3000 npm run e2e`
- Result: 10 passed, 5 failed.
- Failures: all five failed with `API did not become ready` (503 / ECONNRESET on `http://localhost:7700/actuator/health`).

## Targeted rerun (API ready)
- Command:
  - `ECM_UI_URL=http://localhost:3000 npx playwright test e2e/ui-smoke.spec.ts --grep "UI smoke: PDF upload|UI search download failure|RBAC smoke|Rule Automation"`
- Result: 5/5 passed once `/actuator/health` returned 200.
  - UI smoke: PDF upload + search + version history + preview: PASS
  - UI search download failure shows error toast: PASS
  - RBAC smoke (editor + viewer): PASS
  - Rule Automation auto-tag: PASS

## Full rerun (API ready, dev server restarted)
- Pre-checks:
  - `curl http://localhost:7700/actuator/health` => 200
  - `curl http://localhost:3000/` => 200 (dev server restarted)
- Command:
  - `ECM_UI_URL=http://localhost:3000 npm run e2e`
- Result: 10 passed, 5 failed (11.2m)

### Failures
- e2e/pdf-preview.spec.ts:240:5 - PDF preview falls back to server render when client PDF fails
  - Search result card for `e2e-preview-fallback-1767200777843.pdf` not found (60s timeout).
- e2e/search-sort-pagination.spec.ts:263:5 - Search sorting and pagination are consistent
  - Search index did not return 25 results for `e2epage1767200780303` (status=200 count=1).
- e2e/search-view.spec.ts:152:5 - Search results view opens preview for documents
  - Search index did not return `e2e-view-1767200764249.txt`.
- e2e/ui-smoke.spec.ts:360:5 - UI smoke: browse + upload + search + copy/move + facets + delete + rules
  - Folder not found after create: `ui-e2e-target-1767200907744`.
- e2e/ui-smoke.spec.ts:1093:5 - RBAC smoke: viewer cannot access rules or admin endpoints
  - API health returned 503 during `waitForApiReady`.

## Stabilization tweaks
- search-view: increased search index polling to 30 attempts (2s interval).
- pdf-preview: increased search index polling to 40 attempts (2s interval).
- ui-smoke: increased API readiness window to 120s; folder/document/search polling to 30 attempts.
- search-sort-pagination: increased search index polling to 90 attempts and added 200ms upload pacing.

## Full rerun after tweaks
- Pre-checks:
  - `curl http://localhost:7700/actuator/health` => 200
  - `curl http://localhost:3000/` => 200
- Command:
  - `ECM_UI_URL=http://localhost:3000 npm run e2e`
- Result: 15 passed, 0 failed (3.9m)
- AV note: ClamAV unavailable; EICAR upload skipped per test guard.

## Passed tests
- e2e/version-details.spec.ts: Version details: checkin metadata matches expectations
- e2e/ui-smoke.spec.ts: UI search download failure shows error toast
- e2e/ui-smoke.spec.ts: Rule Automation: auto-tag on document upload

## Failures
- e2e/pdf-preview.spec.ts:196:5 - PDF preview shows dialog and controls
  - Expected result card for e2e-preview-1767197099239.pdf within 60s; element not found.
- e2e/pdf-preview.spec.ts:240:5 - PDF preview falls back to server render when client PDF fails
  - Expected result card for e2e-preview-fallback-1767197163775.pdf within 60s; element not found.
- e2e/pdf-preview.spec.ts:280:5 - File browser view action opens preview
  - Actions button for e2e-file-browser-view-1767197227281.pdf not visible within 60s.
- e2e/search-sort-pagination.spec.ts:263:5 - Search sorting and pagination are consistent
- e2e/search-view.spec.ts:152:5 - Search results view opens preview for documents
- e2e/ui-smoke.spec.ts:360:5 - UI smoke: browse + upload + search + copy/move + facets + delete + rules
- e2e/ui-smoke.spec.ts:818:5 - UI smoke: PDF upload + search + version history + preview
- e2e/ui-smoke.spec.ts:993:5 - RBAC smoke: editor can access rules but not admin endpoints
- e2e/ui-smoke.spec.ts:1093:5 - RBAC smoke: viewer cannot access rules or admin endpoints
- e2e/ui-smoke.spec.ts:1301:5 - Scheduled Rules: CRUD + cron validation + UI configuration
- e2e/ui-smoke.spec.ts:1566:5 - Security Features: MFA guidance + Audit export + Retention
  - Retention section not found within 60s.
- e2e/ui-smoke.spec.ts:1642:5 - Antivirus: EICAR test file rejection + System status
  - Antivirus section heading not found within 60s; AV status enabled=true, available=false.

## Notes
- AV status reported unavailable after 30s wait; ClamAV may be starting or disabled.
- Several failures show missing search result cards for newly created PDFs; check search indexing and upload completion.

## How to inspect artifacts
- HTML report: `cd ecm-frontend && npx playwright show-report`
- Trace example:
  `npx playwright show-trace ecm-frontend/test-results/pdf-preview-PDF-preview-shows-dialog-and-controls-chromium/trace.zip`
