# Mail Automation Local E2E Verification

## Mail Automation E2E
- Start GreenMail:
  - `docker compose up -d greenmail`
- Run automation script:
  - `scripts/mail-e2e-local.sh`
- Result (run `MAIL_E2E_TS=1768368611`):
  - Folder `/Root/mail-e2e-1768368611` created and removed
  - Tag `mail-e2e-tag-1768368611` applied to attachment
  - Document id `55ddfe8c-0b9c-4128-ada4-8da52ed39339` verified
- Cleanup:
  - Script removed mail rule/account/tag/folder
  - GreenMail container stopped after validation

## Targeted Playwright Re-run
- UI smoke (previous failures):
  - `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/ui-smoke.spec.ts -g "(RBAC smoke|Rule Automation|Scheduled Rules)"`
  - Result: PASS (4 tests)
- Version details:
  - `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/version-details.spec.ts -g "Version details: checkin metadata matches expectations"`
  - Result: PASS (1 test)
- Version history download/restore:
  - `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/version-share-download.spec.ts -g "Version history actions: download + restore"`
  - Result: PASS (1 test)

## Full Playwright Suite
- `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test`
- Result: 19 passed (10.3m)
