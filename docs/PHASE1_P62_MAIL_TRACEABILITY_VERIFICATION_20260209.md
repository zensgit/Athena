# Phase 1 P62 - Mail Traceability: Per-Message Ingested Documents (Verification) - 2026-02-09

## What This Verifies

This verifies that an admin can:

1. From **Mail Automation > Recent Mail Activity**, open an **Ingested Documents** dialog for a processed message.
2. See all documents ingested for that message (account + folder + uid correlation), or a clear empty/error state.
3. Use the dialog actions to open the document or trigger "Find similar".

## Preconditions

- Stack is running (Keycloak + API + Frontend).
- You have an admin user (default: `admin/admin` in local dev).

## Rebuild / Restart (Local)

Rebuild and restart the local containers so the latest code is deployed:

```bash
bash scripts/restart-ecm.sh
```

Sanity checks:

```bash
curl -sS -o /dev/null -w "%{http_code}\n" http://localhost:7700/actuator/health
curl -sS -o /dev/null -w "%{http_code}\n" http://localhost:5500/
```

Expected:
- Both return `200`.

## Backend API Check (Optional, Direct)

1) Get an access token (writes to `tmp/admin.access_token`):

```bash
bash scripts/get-token.sh admin admin
```

2) Fetch mail diagnostics to obtain a `processedMail` id:

```bash
curl -fsS \
  -H "Authorization: Bearer $(cat tmp/admin.access_token)" \
  "http://localhost:7700/api/v1/integration/mail/diagnostics?limit=25" \
  | jq -r '.recentProcessed[0].id'
```

3) List all ingested documents for that processed mail id:

```bash
PROCESSED_ID="$(curl -fsS \
  -H "Authorization: Bearer $(cat tmp/admin.access_token)" \
  "http://localhost:7700/api/v1/integration/mail/diagnostics?limit=25" \
  | jq -r '.recentProcessed[0].id')"

curl -fsS \
  -H "Authorization: Bearer $(cat tmp/admin.access_token)" \
  "http://localhost:7700/api/v1/integration/mail/processed/${PROCESSED_ID}/documents?limit=200" \
  | jq -r '.[0] | {documentId,name,path,accountId,folder,uid}'
```

Expected:
- HTTP `200`
- Response is a JSON array (`[]` is valid if there are no matched docs).

Notes:
- This endpoint is admin-only.
- The response uses the same `MailDocumentDiagnosticItem` shape as diagnostics `recentDocuments`.
- Do not paste tokens into docs or issues.

## Frontend Verification (Playwright CLI)

Run the focused E2E test:

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/mail-automation.spec.ts --grep "processed item can show ingested documents dialog"
```

Expected:
- Test passes.
- If there are no processed messages in the environment, the test is skipped with a clear reason.

## Manual UI Spot-Check (Optional)

1. Open `http://localhost:5500/admin/mail`
2. In **Recent Mail Activity**, locate a processed message row.
3. Click the list icon button ("View ingested documents").
4. Confirm the dialog renders:
   - a table of documents, or
   - "No mail documents found for this message.", or
   - an error message if the API fails.

