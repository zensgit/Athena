# Phase 1 (P0) Verification

Date: 2026-01-30

## Backend
- `cd ecm-core && mvn -q -DskipTests compile`
  - Result: ✅ Passed
- `cd ecm-core && mvn test`
  - Result: ✅ Passed
- `cd ecm-core && set -a; source ../.env; source ../.env.mail; set +a; ECM_INGESTION_WATCH_FOLDER=../.local/import mvn -q -DskipTests -Dspring-boot.run.arguments=--server.port=8090 spring-boot:run`
  - Result: ✅ Started successfully with LibreOffice configured (`/Applications/LibreOffice.app/Contents`); stopped after startup.

## Frontend
- `cd ecm-frontend && npm run lint`
  - Result: ✅ Passed
- `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test e2e/mail-automation.spec.ts`
  - Result: ✅ Passed (2 tests)
- `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test e2e/ui-smoke.spec.ts e2e/search-view.spec.ts e2e/version-details.spec.ts e2e/pdf-preview.spec.ts`
  - Result: ✅ Passed (16 tests)
- `cd ecm-frontend && ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test e2e/ui-smoke.spec.ts -g "PDF upload + search + version history + preview|Security Features"`
  - Result: ✅ Passed (2 targeted reruns during stabilization)

## Notes
- Liquibase verification (Postgres `ecm_db`):
  - `select id, filename, dateexecuted from databasechangelog where id='023-add-preview-status-columns';`
  - `select column_name from information_schema.columns where table_name='documents' and column_name like 'preview_%';`
- Gmail OAuth status (user log confirmation):
  - Account `gmail-imap` successfully authenticates and fetches.
  - Poller skips already-processed UIDs (e.g., 12352/12358/12359) and respects the 10-minute interval.
- Manual validation recommended for:
  - Mail routing (nodeId + alias).
  - Audit filters + export presets.
  - Permissions preset apply.
  - Preview status transitions.
