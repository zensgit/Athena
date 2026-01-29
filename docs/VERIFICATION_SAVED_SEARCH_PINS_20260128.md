# Verification: Saved Search Pins (2026-01-28)

## Backend API Check
- Create a saved search, then call pin endpoint:
  - `PATCH /api/v1/search/saved/{id}/pin` with `{ "pinned": true }`
- Expected:
  - Response includes `"pinned": true`.
  - `GET /api/v1/search/saved` returns pinned items first.
- Result: ✅ Passed after rebuilding/restarting `ecm-core` container (pin + unpin succeeded).

## Frontend Lint
- Command: `cd ecm-frontend && npm run lint`
- Result: ✅ Passed

## Playwright (Search flow)
- Command:
  - `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test e2e/ui-smoke.spec.ts -g "browse \\+ upload \\+ search"`
- Result: ✅ 1 passed (~1.3m)

## Manual UI Check
- Go to `Saved Searches`.
- Pin a saved search (star icon).
- Expected:
  - Pin icon fills.
  - Pinned search appears in Admin Dashboard under "Pinned Saved Searches".
- Click the Play button in the dashboard list.
- Expected:
  - Search results run and open in `/search-results`.
- Click unpin in dashboard or Saved Searches.
- Expected:
  - Pinned item removed from dashboard list.
  - Pin state persists after reload or login on another device.
