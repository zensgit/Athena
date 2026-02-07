# Mail Automation P1 — Verification

Date: 2026-02-06

## Automated Tests
- `cd ecm-frontend && npm run lint`
- `cd ecm-core && mvn -q -DskipTests package`

## Manual Verification Checklist
1. **UTF-8 password retry**
   - Configure a non-OAuth IMAP account with a non-ASCII password.
   - Trigger Test Connection.
   - If the first login fails, confirm the retry succeeds (or error logs show the retry attempt).

2. **Processing scope labels**
   - Open Mail Automation → Rules → Edit/Create a rule.
   - Confirm the processing scope dropdown shows:
     - “Attachments only”
     - “Email (.eml) only”
     - “Email (.eml) + attachments”
   - Confirm helper text matches the selected option.
