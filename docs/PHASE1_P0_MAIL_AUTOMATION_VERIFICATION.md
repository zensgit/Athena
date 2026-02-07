# Mail Automation P0 — Verification

Date: 2026-02-06

## Automated Tests
- `cd ecm-frontend && npm run lint`
- `cd ecm-core && mvn -q -DskipTests package`

## Manual Verification Checklist
1. **Password masking (non-OAuth)**
   - Open Mail Automation → Accounts → Edit a non-OAuth account with a saved password.
   - Confirm password field shows `********` instead of empty.
   - Click Save without changing password.
   - Verify the account can still connect (Test Connection or Trigger Fetch).

2. **Password update**
   - Edit the same account and replace the masked value with a new password.
   - Save and confirm connection succeeds.

3. **OAuth token preservation**
   - Edit an OAuth-connected account and change a non-credential field (e.g., poll interval).
   - Save and confirm `OAuth connected` status remains true in Mail Automation.
