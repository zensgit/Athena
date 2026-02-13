# Phase 1 (P78) - Playwright BaseURL Auto-detect Verification (2026-02-11)

## Verification target

Confirm Playwright selects local source frontend (`:3000`) automatically when `ECM_UI_URL` is not set.

## Environment

- Frontend dev server: `http://localhost:3000`
- API: `http://localhost:7700`
- No explicit `ECM_UI_URL` in command.

## Commands

```bash
# from ecm-frontend/
npm start

npx playwright test e2e/search-fallback-criteria.spec.ts --reporter=list
npx playwright test e2e/mail-automation.spec.ts -g "Mail reporting auto-selects single account and rule" --reporter=list
```

## Expected signal

Console should print:

```text
[playwright] ECM_UI_URL not set, using auto-detected baseURL: http://localhost:3000
```

## Results

- Auto-detect log observed in both test runs, selecting `http://localhost:3000`.
- `e2e/search-fallback-criteria.spec.ts`: **passed**.
- `e2e/mail-automation.spec.ts` (targeted case): **passed**.

## Conclusion

Default E2E target now aligns with active source dev server in local workflow, reducing stale-frontend verification errors.
