# P0 Verification: Mail Automation UI, Bulk Metadata, Content Types

## Automated Checks
- Frontend unit test:
  - `npm test -- --runTestsByPath src/components/layout/MainLayout.menu.test.tsx --watchAll=false`
  - Result: PASS (2 tests)
- Frontend unit test (rerun):
  - `npm test -- --watchAll=false`
  - Result: PASS (4 suites, 11 tests)
- Frontend lint:
  - `npm run lint`
  - Result: SUCCESS
- Frontend lint (rerun):
  - `npm run lint`
  - Result: SUCCESS
- Frontend E2E (Playwright):
  - `ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test`
  - Result: 14 passed, 5 failed (29.1m)
  - Failures:
    - `e2e/ui-smoke.spec.ts:1092:5` RBAC smoke (viewer cannot access rules/admin endpoints) timeout waiting for login URL.
    - `e2e/ui-smoke.spec.ts:1197:5` Rule Automation auto-tag on upload (test timeout; request context disposed).
    - `e2e/ui-smoke.spec.ts:1300:5` Scheduled Rules CRUD + cron validation (test timeout).
    - `e2e/version-details.spec.ts:102:5` Version details checkin metadata (test timeout).
    - `e2e/version-share-download.spec.ts:181:5` Version history download + restore (navigation aborted timeout).
- Frontend E2E (targeted re-run of failed specs):
  - `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/ui-smoke.spec.ts -g "(RBAC smoke|Rule Automation|Scheduled Rules)"`
  - `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/version-details.spec.ts -g "Version details: checkin metadata matches expectations"`
  - `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/version-share-download.spec.ts -g "Version history actions: download + restore"`
  - Result: PASS (4 + 1 + 1 tests)
- Frontend E2E (full rerun):
  - `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test`
  - Result: 19 passed (10.3m)
- Backend tests:
  - `mvn test`
  - Result: PASS (96 tests)

## Manual/API Checks
- Backend container rebuild and restart:
  - `docker compose -p athena build ecm-core`
  - `docker compose -p athena up -d ecm-core`
- Keycloak admin token (unified portal client):
  - `POST http://localhost:8180/realms/ecm/protocol/openid-connect/token`
  - `client_id=unified-portal`, `grant_type=password`, `username=admin`, `password=admin`
- Bulk metadata API:
  - Created folder `ui-bulk-verify-1768298520` under root.
  - Uploaded docs: `ed98c8bc-70a0-494b-b5c1-081989a79f7f`, `a2d5dbb1-75a8-4c26-8317-9232f8b20f3a`.
  - `POST http://localhost:7700/api/v1/bulk/metadata` with tag `bulk-verify-tag`.
  - Result: `successCount=2`, tags verified via `GET /api/v1/nodes/{id}`.
- Content types API:
  - Created type `ecm:bulk-verify-1768298841` via `POST /api/v1/types`.
  - Applied to doc `ed98c8bc-70a0-494b-b5c1-081989a79f7f` using `POST /api/v1/types/nodes/{id}/apply?type=...`.
  - Result: response properties include `invoiceNumber=INV-1768298841`.

## Manual UI Checks
- Bulk metadata dialog:
  - Folder: `/Root/ui-p0-verify-20260113` (selected `ui-p0-verify-a.txt`, `ui-p0-verify-b.txt`).
  - Action: "Edit metadata for selected" → add tag `bulk-verify-tag` → Apply.
  - Result: toast `Updated 2 items`, network `POST /api/v1/bulk/metadata` 200.
- Content type apply:
  - Opened Properties for `ui-p0-verify-a.txt` → Edit.
  - Selected type `Bulk Verify 1768298841 (ecm:bulk-verify-1768298841)`.
  - Filled required field `Invoice Number = INV-UI-VERIFY-1`, Save.
  - Result: network `POST /api/v1/types/nodes/{id}/apply?type=ecm:bulk-verify-1768298841` 200, properties persisted.

## Manual Checks (Not Run)
- Full regression/E2E suites.

## Mail Automation E2E (Local GreenMail)
- Started GreenMail from docker-compose:
  - `docker compose up -d greenmail`
- End-to-end flow via local script:
  - `scripts/mail-e2e-local.sh`
- Result (run `MAIL_E2E_TS=1768368611`):
  - Folder `/Root/mail-e2e-1768368611` created and cleaned up
  - Tag `mail-e2e-tag-1768368611` applied to ingested attachment `mail-e2e-XXXXXX.txt.YtjLrRLlQU`
  - Document id `55ddfe8c-0b9c-4128-ada4-8da52ed39339` verified via `/api/v1/nodes/{id}`
- Cleanup:
  - Script removed mail rule/account/tag/folder automatically.

## Notes
- Cleanup after UI verification:
  - Cleared content type metadata/properties for `ui-p0-verify-a.txt` via `PATCH /api/v1/nodes/{id}`.
  - Deleted content type `ecm:bulk-verify-1768298841` via `DELETE /api/v1/types/{name}`.
  - Deleted folder `ui-bulk-verify-1768298520` (and its uploaded docs) via `DELETE /api/v1/folders/{id}?permanent=true&recursive=true`.
- GreenMail is now defined in `docker-compose.yml` for repeatable mail automation validation.
