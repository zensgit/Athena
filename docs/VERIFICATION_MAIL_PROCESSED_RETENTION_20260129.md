# Verification: Processed Mail Retention (2026-01-29)

## Manual UI Flow
1. Go to `/admin/mail` → Recent Mail Activity → Processed Messages.
2. Verify retention status chips show Retention days + Expired count.
3. Click **Refresh Retention**.
4. If expired count > 0, click **Clean up expired** and confirm.
5. Expected:
   - Toast shows deleted count.
   - Retention expired count refreshes.

## API Check
- `GET /api/v1/integration/mail/processed/retention`
- `POST /api/v1/integration/mail/processed/cleanup`

## Automated Tests
- `cd ecm-core && mvn test` ✅
- `cd ecm-frontend && npm run lint` ✅
- `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 KEYCLOAK_URL=http://localhost:8180 ECM_E2E_USERNAME=admin ECM_E2E_PASSWORD=admin npx playwright test e2e/mail-automation.spec.ts` ✅ (2 passed)

## Playwright Retention Check
- Script: ad-hoc Playwright login + retention chip check (headless).
- Result: `Retention 90d`, `Expired 0`, cleanup not triggered.
