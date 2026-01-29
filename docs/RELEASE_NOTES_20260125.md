# Release Notes (2026-01-25)

## Highlights
- Mail Automation now supports in-app “Test connection” and richer fetch summaries.
- Full E2E regression green; storage permissions auto-corrected on container startup.

## Features
- Mail Automation connection test endpoint and UI action.
- Manual fetch now returns processing summary (accounts, matched, processed, errors, duration).
- New E2E coverage for Mail Automation (standalone + UI smoke).

## Fixes
- Automatically correct `/var/ecm/content` ownership on `ecm-core` startup to prevent upload failures.
- Allow `mail_accounts.password` to be nullable for OAuth2 accounts.
- Add audit log fields: `client_ip`, `user_agent`, `metadata`.
- Keycloak login fallback when Web Crypto is unavailable (dev-only via `REACT_APP_INSECURE_CRYPTO_OK`).

## Configuration
- Dev-only defaults moved to `application-dev.yml`:
  - `spring.jpa.hibernate.ddl-auto=update`
  - `jodconverter.local.enabled=false`

## Verification
- Mail Automation E2E: PASS.
- Full Playwright regression: 21/21 PASS.

## Documentation
- Added storage permission fix guide and updated handover/installation notes.
- Added Mail Automation design + verification docs.

## 2026-01-29 Addendum
- Processed mail retention policy + cleanup (scheduled/manual) with UI controls.
- New retention configuration: `ECM_MAIL_PROCESSED_RETENTION_DAYS` (default 90).
- Mail Automation Playwright verification pass recorded.
