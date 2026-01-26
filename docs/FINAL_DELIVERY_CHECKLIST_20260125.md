# Final Delivery Checklist (2026-01-25)

## Scope Completed
- Mail Automation: connection test + fetch summary (API + UI)
- Mail Automation E2E coverage (standalone + UI smoke)
- Storage permission auto-fix on ecm-core startup
- Full Playwright regression green
- Docs: design, verification, ops notes, release notes, team update

## Code Changes
- Backend
  - Mail connection test + summary: `MailFetcherService`, `MailAutomationController`
  - Startup permission fix: `ecm-core/entrypoint.sh`, `ecm-core/Dockerfile`
  - DB updates: audit_log columns + mail_accounts.password nullable
- Frontend
  - Mail Automation UI: Test connection button + fetch summary toast
  - Keycloak Web Crypto fallback (dev-only)
- E2E
  - `ecm-frontend/e2e/mail-automation.spec.ts`
  - UI smoke update (`ui-smoke.spec.ts`)

## Verification
- Mail Automation E2E: PASS
- Full Playwright regression: 21/21 PASS

## Documentation
- Release notes: `docs/RELEASE_NOTES_20260125.md`
- Team update + templates: `docs/TEAM_UPDATE_20260125.md`
- Ops fix guide: `docs/CONTENT_STORAGE_PERMISSION_FIX_20260125.md`
- Verification docs updated

## Tag
- `release-20260125` pushed

## Notes
- Dev defaults moved to `application-dev.yml` (no production default change).
- Old volumes may still need one-time `chown` (see ops guide).
