# Mail Automation P27 — Verification

Date: 2026-02-06

## Automated Tests
- `cd ecm-frontend && npm run lint`
  - Result: pass
- `cd ecm-core && mvn -DskipTests compile`
  - Result: pass
- `cd ecm-frontend && npx playwright test e2e/mail-automation.spec.ts -g "replay failed processed item"`
  - Result: blocked by environment readiness
  - Failure detail: `API did not become ready: health status code=503`
  - Note: test setup failed before replay UI assertions.

## Manual Verification Checklist
1. Open `/admin/mail#diagnostics`.
2. Ensure at least one processed row has `Status=ERROR`.
3. Click `Replay` in that row.
4. Confirm toast result appears (`processed/finished/skipped/failed`).
5. Confirm diagnostics table refreshes and row status/error text updates as expected.
6. Confirm backend audit contains `MAIL_PROCESSED_REPLAY` event.
