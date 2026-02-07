# Mail Automation P16 — Verification

Date: 2026-02-06

## Automated Tests
- `cd ecm-frontend && npm run lint`
  - Result: pass

## Manual Verification Checklist
1. Open Mail Automation -> Recent Mail Activity.
2. Ensure diagnostics includes at least one `ERROR` record for a rule.
3. Confirm `Rule error leaderboard` displays rule chips with error counts.
4. Click a rule chip.
5. Confirm filters are set to `Status = ERROR` and the selected `Rule`.
6. Click `View all errors` and confirm `Rule` filter is cleared while `Status = ERROR` remains.
