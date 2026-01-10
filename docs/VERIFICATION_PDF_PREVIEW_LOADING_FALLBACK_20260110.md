# Verification: PDF Preview Loading + Fallback UX (2026-01-10)

- `npm run build` (refresh prebuilt frontend assets).
- `docker compose up -d --build ecm-frontend`.
- `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/pdf-preview.spec.ts -g "PDF preview falls back to server render when client PDF fails" --project=chromium`
- Result: pass (fallback banner and actions visible).
