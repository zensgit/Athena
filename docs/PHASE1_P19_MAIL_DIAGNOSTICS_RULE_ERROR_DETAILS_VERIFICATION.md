# Mail Automation P19 — Verification

Date: 2026-02-06

## Automated Tests
- `cd ecm-frontend && npm run lint`
  - Result: pass

## Manual Verification Checklist
1. Open Mail Automation -> Recent Mail Activity.
2. Ensure leaderboard shows at least one failed rule.
3. Click `Show details` on a rule.
4. Confirm recent error rows (time + summary) are shown.
5. Click copy icon on an error row and confirm success toast.
6. Apply diagnostics filters to remove that rule from leaderboard and confirm expanded details auto-collapse.
