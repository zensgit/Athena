# Mail Automation Local E2E Design

## Goals
- Provide a repeatable mail automation end-to-end flow without external mailbox dependencies.
- Keep validation self-contained for local/dev CI runs.
- Ensure cleanup so repeated runs do not pollute data or rules.

## Approach
- Add a local GreenMail service to docker-compose for SMTP/IMAP.
- Use a script to automate:
  - Admin token acquisition.
  - Folder + tag creation.
  - Mail account + rule setup (IMAP host `greenmail`, port `3143`, security `NONE`).
  - SMTP send (host `localhost`, port `3025`).
  - Manual fetch trigger.
  - Document + tag verification.
  - Cleanup of rule/account/tag/folder.

## Components
- `docker-compose.yml`
  - New `greenmail` service with SMTP (3025), IMAP (3143), API/UI (8085).
  - User provisioned via `GREENMAIL_USERS` (defaults to a local dummy account).
- `scripts/mail-e2e-local.sh`
  - Uses `curl`, `jq`, and `python3`.
  - Environment overrides for API, Keycloak, mail host/ports, and credentials.
  - `KEEP_ARTIFACTS=1` optional escape hatch for debugging.

## Data & Security
- Uses local-only dummy credentials (`mailuser@local.test`).
- No real external mailbox credentials are required or stored.

## Operational Notes
- The service name `greenmail` is resolvable from the app container on the `ecm-network`.
- SMTP is exposed on localhost for the script to send the test email.

## Non-Goals
- External provider validation (Outlook/Gmail) is out of scope for this flow.
- Production-grade mail account encryption is unchanged.
