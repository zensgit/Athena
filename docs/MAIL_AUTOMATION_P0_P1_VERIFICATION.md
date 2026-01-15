# Mail Automation P0/P1 Verification

## Automated Tests
- `cd ecm-core && mvn -q -Dtest=MailRuleMatcherTest test`
  - Result: pass
- `cd ecm-core && mvn test`
  - Result: pass
- `cd ecm-frontend && npm run lint`
  - Result: pass
- `cd ecm-frontend && CI=true npm test`
  - Result: pass (4 suites, 11 tests)

## Manual Checks (Executed)
### GreenMail E2E (IMAP + SMTP)
- Start mail server: `docker compose up -d greenmail`
- Obtain admin token (Keycloak `unified-portal` client) and create mail account/rule via API.
- Send SMTP mail with a `.txt` attachment to `mailuser@local.test`.
- Trigger mail fetch and verify the document is ingested via node search.

Result: `verification:ok (attachment: mail-automation-verify-1768469436.txt, eml: mail-automation-verify-1768469436.eml)`

Notes:
- Mail fetcher runs without a user security context; folder assignment requires `CREATE_CHILDREN` permission.
- This check omitted `assignFolderId` to avoid permission failures. If you want folder targeting validated, grant create permission to the folder for the service user or add a system-user context in mail fetcher.
- Liquibase changeSet preconditions now skip existing mail tables; rebuild/restart `ecm-core` if you are rerunning in Docker.

Reference script (sanitized):
```bash
TOKEN=$(curl -s -X POST 'http://localhost:8180/realms/ecm/protocol/openid-connect/token' \
  -d 'grant_type=password' \
  -d 'client_id=unified-portal' \
  -d "username=${KEYCLOAK_USER}" \
  -d "password=${KEYCLOAK_PASSWORD}" | python3 -c 'import sys, json; print(json.load(sys.stdin)["access_token"])')

API_BASE="http://localhost:7700/api/v1"

ACCOUNT_ID=$(curl -s -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' \
  -d '{"name":"GreenMail-Local","host":"greenmail","port":3143,"username":"mailuser@local.test","password":"mailuser@local.test","security":"NONE","enabled":true,"pollIntervalMinutes":10}' \
  "${API_BASE}/integration/mail/accounts" | python3 -c 'import sys, json; print(json.load(sys.stdin)["id"])')

curl -s -H "Authorization: Bearer ${TOKEN}" -H 'Content-Type: application/json' \
  -d "{\"name\":\"gm-attachments\",\"accountId\":\"${ACCOUNT_ID}\",\"folder\":\"INBOX\",\"subjectFilter\":\"Athena E2E Mail Automation\",\"toFilter\":\"mailuser@local.test\",\"attachmentFilenameInclude\":\"mail-automation-verify.txt\",\"actionType\":\"EVERYTHING\",\"mailAction\":\"MARK_READ\"}" \
  "${API_BASE}/integration/mail/rules"

python3 - <<'PY'
import smtplib
from email.message import EmailMessage

msg = EmailMessage()
msg['Subject'] = 'Athena E2E Mail Automation'
msg['From'] = 'sender@example.com'
msg['To'] = 'mailuser@local.test'
msg.set_content('Mail automation verification message.')
msg.add_attachment('verification payload', filename='mail-automation-verify.txt', subtype='plain')

with smtplib.SMTP('localhost', 3025) as smtp:
    smtp.send_message(msg)
PY

curl -s -H "Authorization: Bearer ${TOKEN}" -X POST "${API_BASE}/integration/mail/fetch"
curl -s -H "Authorization: Bearer ${TOKEN}" "${API_BASE}/nodes/search?query=mail-automation-verify.txt"
curl -s -H "Authorization: Bearer ${TOKEN}" "${API_BASE}/nodes/search?query=Athena%20E2E%20Mail%20Automation.eml"
```
