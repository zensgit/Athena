# PHASE1 P66 - Mail Reporting Default Range (30 Days) - Verification

## Environment
- UI: `http://localhost:5500`
- API: `http://localhost:7700`

## Automated Verification (Playwright)

Command:
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/mail-automation.spec.ts --grep "Mail reporting defaults to last 30 days"
```

Expected:
- The Mail Automation page renders.
- In **Mail Reporting**, the **Days** selector defaults to **Last 30 days**.

Result:
- ✅ Pass (after rebuilding `ecm-frontend` container so `http://localhost:5500` serves the updated bundle)
