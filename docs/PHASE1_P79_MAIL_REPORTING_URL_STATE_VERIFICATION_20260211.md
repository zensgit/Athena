# Phase 1 (P79) - Mail Reporting URL State Persistence Verification (2026-02-11)

## Verification target

Confirm Mail Reporting filter state persists across URL/reload and does not regress existing search/saved-search behavior.

## Environment

- UI: `http://localhost:3000`
- API: `http://localhost:7700`
- Auth: `admin/admin`

## Commands

```bash
# from ecm-frontend/
npm start

npx eslint src/pages/MailAutomationPage.tsx

ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/mail-automation.spec.ts \
  -g "Mail reporting defaults to last 30 days|Mail reporting auto-selects single account and rule|Mail reporting days filter persists in URL"

ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/search-fallback-criteria.spec.ts e2e/saved-search-import-export.spec.ts
```

## Results

- ESLint (`MailAutomationPage.tsx`): **pass**
- Mail Reporting targeted E2E: **3 passed**
  - `Mail reporting auto-selects single account and rule`
  - `Mail reporting defaults to last 30 days`
  - `Mail reporting days filter persists in URL`
- Search regression targeted E2E: **2 passed**
  - `search-fallback-criteria.spec.ts`
  - `saved-search-import-export.spec.ts`

## Conclusion

Mail Reporting filter state is now URL-persistent and reload-safe, with no observed regressions in the targeted search/saved-search flows.
