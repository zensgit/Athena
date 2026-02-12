# Phase 2 (Day 5) Verification: Mail Report Scheduled Export

Date: 2026-02-12

## What We Verify

1. Backend scheduled export service compiles and behaves correctly (skip vs success upload).
2. UI surfaces the scheduled export panel and does not require secrets to render.
3. API endpoints for schedule status and manual trigger respond for admin users.

## Automated Checks

### Backend (unit)

Runs a targeted test that validates:

- disabled schedule is skipped
- missing/invalid folder id is skipped
- enabled schedule uploads a CSV and logs an audit event

Command:

```bash
docker run --rm -v "$(pwd)":/workspace -w /workspace/ecm-core \
  maven:3-eclipse-temurin-17 mvn -q test -Dtest=MailReportScheduledExportServiceTest
```

Expected:

- Maven exits `0`
- Test class `MailReportScheduledExportServiceTest` passes

### Frontend (Playwright)

Validates the Mail Reporting card renders the "Scheduled export" section.

Prereq:

```bash
cd ecm-frontend
npm run e2e:install
```

Command:

```bash
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/mail-automation.spec.ts -g "Mail automation reporting panel renders"
```

Expected:

- Test passes and confirms "Scheduled export" is visible.

## API Smoke (Manual)

Prereq: obtain an admin access token using existing helper script.

```bash
./scripts/get-token.sh admin admin
TOKEN="$(cat tmp/admin.access_token)"
```

### Check schedule status

```bash
curl -fsS -H "Authorization: Bearer ${TOKEN}" \
  http://localhost:7700/api/v1/integration/mail/report/schedule | jq -r 'keys'
```

Expected keys:

- `enabled`, `cron`, `folderId`, `days`, `accountId`, `ruleId`, `lastExport`

### Trigger an export immediately

Note: if `enabled=false` or `folderId` is not configured, this returns a `SKIPPED` result (expected).

```bash
curl -fsS -X POST -H "Authorization: Bearer ${TOKEN}" \
  http://localhost:7700/api/v1/integration/mail/report/schedule/run | jq -r '{status,success,attempted,folderId,documentId,filename}'
```

Expected:

- JSON response includes `status` and does not leak secrets.

## Notes

- This verification does not require any Gmail/Outlook secrets.
- Scheduled execution is disabled by default and should be enabled explicitly via env/properties if desired.

