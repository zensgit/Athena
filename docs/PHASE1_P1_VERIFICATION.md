# Phase 1 (P1) Verification

Date: 2026-01-31

## Automated Checks

Backend compile:
- `cd ecm-core && mvn -DskipTests compile`
- Result: ✅ Success

Frontend lint:
- `cd ecm-frontend && npm run lint`
- Result: ✅ Success

Frontend E2E (core smoke):
- `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test e2e/ui-smoke.spec.ts e2e/search-view.spec.ts e2e/version-details.spec.ts e2e/pdf-preview.spec.ts`
- Result: ✅ 16 passed (3.7m)

Frontend E2E (full suite):
- `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test`
- Result: ✅ 25 passed (6.1m)

Targeted reruns after fixes:
- `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test e2e/search-sort-pagination.spec.ts`
  - Result: ✅ 1 passed (26.1s)
- `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test e2e/ui-smoke.spec.ts -g "UI smoke: browse"`
  - Result: ✅ 1 passed (2.1m)
- `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test e2e/ui-smoke.spec.ts -g "Security Features"`
  - Result: ✅ 1 passed (5.2s)

Frontend E2E (P1 smoke):
- `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test e2e/p1-smoke.spec.ts`
- Result: ✅ 2 passed (14.4s)

## Manual / API Smoke (Suggested)
- Mail rule preview: open Mail Automation → Rules → Preview → ensure matched messages render.
- Spellcheck: run a search with a misspelled term → confirm “Did you mean” suggestions appear.
- Version label policy: switch `ECM_VERSION_LABEL_POLICY=calendar`, create a new version, confirm label format.
- Preview queue: `POST /api/v1/documents/{id}/preview/queue` and check preview status updates after background run.
- Permissions: verify explicit deny overrides inherited allow.
- Audit categories: set `ECM_AUDIT_DISABLED_CATEGORIES=MAIL` and confirm mail-related events are skipped.
