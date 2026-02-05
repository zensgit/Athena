# Phase 50 - Version Compare Summary + Preview Retry (Verification)

## Automated Checks
- Frontend E2E: `ECM_E2E_SKIP_LOGIN=1 ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/search-preview-status.spec.ts`

## Manual Smoke Steps
1. Open a document's version history and click **Compare**.
2. Confirm the **Change Summary** chips appear.
3. In Search Results, locate a document with **Preview failed** status.
4. Click **Retry preview** and verify a toast indicates queuing.

## Results (2026-02-05)
- Targeted E2E: ✅ 2 passed (`search-preview-status.spec.ts`).
