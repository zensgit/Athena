# Phase 52 - Preview Retry Status in Search (Verification)

## Automated Checks
- E2E: `ECM_E2E_SKIP_LOGIN=1 ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/search-preview-status.spec.ts`

## Manual Smoke Steps
1. Search for a document with **Preview failed** status.
2. Click **Retry failed previews**.
3. Confirm attempts/next retry details appear under the preview status chip.

## Results (2026-02-05)
- E2E: ✅ `search-preview-status.spec.ts` (2 passed, includes retry + attempts)
