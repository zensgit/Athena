# Verification: Mail Rule Enable/Disable (2026-01-29)

## Manual UI Flow
1. Go to `/admin/mail`.
2. Create a new rule (Enabled checked by default).
3. Confirm the rule shows “Enabled” in the list.
4. Toggle the checkbox in the list to disable the rule.
5. Expected:
   - Status chip switches to Disabled.
   - Subsequent mail fetch skips the rule.
6. Toggle back to Enabled.

## Backend Sanity
- Verify `enabled` persisted:
  - `GET /api/v1/integration/mail/rules` includes `enabled` flag.
  - Result: ✅ Endpoint reachable; no rules returned in current environment.
- Create/update rule via API:
  - Result: ⚠️ Subsequent create attempt returned 401 (token expired). Re-run with fresh admin token if needed.

## Frontend Lint
- Command: `cd ecm-frontend && npm run lint`
- Result: ✅ Passed
