# Mail Automation P26 — Verification

Date: 2026-02-06

## Automated Tests
- `cd ecm-frontend && npm run lint`
  - Result: pass
- `cd ecm-frontend && npx playwright test e2e/mail-automation.spec.ts -g "rule diagnostics drawer"`
  - Result: blocked by environment readiness
  - Failure detail: `API did not become ready: health status code=503`
  - Note: test setup failed before UI assertions; no drawer assertion regression observed from this run.

## Manual Verification Checklist
1. Open `/admin/mail#diagnostics`.
2. In `Rule error leaderboard`, click `Open drawer` on any rule.
3. Confirm drawer shows:
   - Rule identity
   - Account / Status / Time range filters
   - Matched / Processed / Errors chips
   - Skip reasons and recent failure samples
4. Change drawer filters and verify list/chips update.
5. Click `Apply To Main Filters`, verify main diagnostics filters are updated and table refreshes.
