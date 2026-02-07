# Mail Automation P31 — Verification

Date: 2026-02-06

## Automated Validation
- `cd ecm-core && mvn -DskipTests compile`
  - Result: pass
- `cd ecm-frontend && npm run lint`
  - Result: pass

## Code Checks
- Controller audit fields and events confirmed:
  - `MAIL_DIAGNOSTICS_EXPORTED` includes request/sort/order and include flags.
  - `MAIL_RUNTIME_METRICS_VIEWED` event emitted on runtime metrics endpoint.
- Frontend permission handling confirmed:
  - `403` handling exists for runtime metrics loading and replay action.

## Manual Checklist
1. Login as admin; open `/admin/mail`.
2. Trigger runtime metrics refresh and verify no permission toast appears.
3. Trigger replay on one processed mail row and verify success/failure toast appears.
4. Login as non-admin user and repeat runtime/replay actions.
5. Confirm explicit permission-denied toast is shown for blocked actions.

