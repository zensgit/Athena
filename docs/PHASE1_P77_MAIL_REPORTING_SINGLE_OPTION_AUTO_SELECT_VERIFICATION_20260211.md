# Phase 1 (P77) - Mail Reporting Single-option Auto-select Verification (2026-02-11)

## Verification target

Confirm Account/Rule auto-selection in Mail Reporting when there is exactly one option, and ensure existing search/saved-search flows remain stable.

## Environment

- UI: local source dev server `http://localhost:3000`
- API: `http://localhost:7700`
- Auth: Keycloak (`admin/admin`)

## Commands

```bash
# from ecm-frontend/
npm start

# in a separate shell (same directory)
ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/mail-automation.spec.ts \
  -g "Mail reporting auto-selects single account and rule|Mail reporting defaults to last 30 days"

ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/search-fallback-criteria.spec.ts e2e/saved-search-import-export.spec.ts

npx eslint src/pages/MailAutomationPage.tsx
```

## Results

- `mail-automation.spec.ts` targeted run: **2 passed**
  - `Mail reporting auto-selects single account and rule`
  - `Mail reporting defaults to last 30 days`
- Search regression targeted run: **2 passed**
  - `search-fallback-criteria.spec.ts`
  - `saved-search-import-export.spec.ts`
- ESLint targeted file:
  - `src/pages/MailAutomationPage.tsx`: **pass**

## Notes

- A prior run against deployed frontend (`:5500`) showed stale behavior because that bundle was not rebuilt from latest source. Final verification for P77 is based on local source server (`:3000`), which reflects current code changes.
