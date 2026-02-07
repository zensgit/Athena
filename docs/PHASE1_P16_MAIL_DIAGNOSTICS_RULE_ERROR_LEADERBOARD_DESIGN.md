# Mail Automation P16 — Diagnostics Rule Error Leaderboard

Date: 2026-02-06

## Goal
Speed up diagnosis by surfacing top failing mail rules and allowing one-click filter jump to those failures.

## Design
- Build a leaderboard from `recentProcessed` diagnostics records.
- Include only `ERROR` records with non-empty `ruleId`.
- Group by `ruleId` and count failures, with latest failure timestamp as tie-breaker.
- Show top 8 rules in the diagnostics card.
- Add one-click action per rule to apply filters:
  - `Status = ERROR`
  - `Rule = clicked rule`
- Add `View all errors` action to clear rule filter while keeping `Status = ERROR`.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
