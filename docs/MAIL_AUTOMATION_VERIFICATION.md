# Mail Automation Verification

Date: 2026-01-23 to 2026-01-24

## Scope
- Mail Automation account connectivity (Gmail IMAP OAuth2)
- Rule matching and ingestion pipeline
- Attachment ingestion verification

## Environment
- ECM API: http://localhost:7700
- Keycloak: http://localhost:8180
- Mail account: gmail-imap (Gmail IMAP, OAUTH2)
- Rule name: gmail-attachments

## Configuration (Current)
- Rule folder: INBOX
- Rule action_type: ATTACHMENTS_ONLY
- attachment_filename_include: *
- Filters: subject/from/to/body/max_age cleared
- Mail account poll interval: 10 minutes

## Verification Results
### 1) Connectivity
- IMAP OAuth2 connection verified after token refresh.
- Backend log confirms successful connection and fetch attempts.

### 2) Rule Execution (Metadata ingest)
- Confirmed mail rule execution and .eml ingestion when rule set to EVERYTHING in ECM-TEST folder.
- Example log evidence:
  - Email matched rule: gmail-attachments
  - Ingested email: Fwd_ e2e-_任意时间戳_.eml
  - Document created (node ID logged by backend)

### 3) Attachment Ingestion
- Verified on 2026-01-24 with INBOX unread attachment.
- Example log evidence:
  - Found 1 unread messages in gmail-imap (INBOX)
  - Email matched rule: gmail-attachments
  - Document upload successful: 泵.dwg -> e6b1928e-6b38-49a3-8962-63b8f1e3dad0
- Database evidence:
  - nodes.id = e6b1928e-6b38-49a3-8962-63b8f1e3dad0
  - nodes.name = 泵.dwg
  - created_date = 2026-01-24 14:28:49
- API evidence (admin token via unified-portal):
  - GET /api/v1/nodes/e6b1928e-6b38-49a3-8962-63b8f1e3dad0 returned 200
  - name = 泵.dwg, contentType = image/vnd.dwg, createdBy = admin

## Pending Items
- None

## Notes
- If needed for fast verification, temporarily lower poll interval to 1 minute and/or trigger a manual fetch.
- Ensure Gmail labels for testing are set to “Show in IMAP” when using non-INBOX folders.
