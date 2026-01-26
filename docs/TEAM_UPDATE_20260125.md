# Team Update - 2026-01-25

## Summary
- Mail Automation now supports in-app test connection and fetch summary.
- Storage permission issues fixed (auto-correct on startup).
- Full Playwright E2E regression is green.

## What changed
- Backend:
  - Added `POST /api/v1/integration/mail/accounts/{id}/test`.
  - Manual fetch now returns a summary payload.
  - `mail_accounts.password` nullable for OAuth2; audit log columns added.
  - `ecm-core` entrypoint now fixes `/var/ecm/content` ownership on startup.
- Frontend:
  - “Test connection” button on Mail Automation page.
  - Fetch summary toast after manual trigger.
  - Web Crypto fallback (dev-only) for Keycloak login issues.
- E2E:
  - New `mail-automation.spec.ts` + UI smoke coverage.

## Verification
- Mail Automation E2E: PASS.
- Full Playwright regression: 21/21 PASS.

## Notes / Action Items
- If running locally with dev defaults, use `application-dev.yml` for `ddl-auto=update` and `jodconverter.local.enabled=false`.
- For existing volumes, one-time fix may still be required:
  - `docker exec -u 0 athena-ecm-core-1 chown -R app:app /var/ecm/content`
