# Mail Automation P26 — Rule-Focused Diagnostics Drawer

Date: 2026-02-06

## Goal
Provide an operator-focused side drawer to inspect diagnostics for a single rule with fast triage filters.

## Design
### Frontend
- Add right-side drawer entry from `Rule error leaderboard` (`Open drawer`).
- Drawer scope is current diagnostics window (`recentProcessed`) and selected `ruleId`.
- Drawer filters:
  - `Account`
  - `Status` (`ALL | PROCESSED | ERROR`)
  - `Time range` (`All time | Last 24 hours | Last 7 days | Last 30 days`)
- Drawer analytics:
  - Matched / Processed / Errors chips
  - Skip reason aggregation from error payload tokens
  - Recent failure samples list
- Add action `Apply To Main Filters`:
  - Push focused rule + drawer filters back to main diagnostics table filters
  - Sets diagnostics `ruleId`, optional `accountId`, optional `status`, and computed `processedFrom`

### Data/Behavior
- No backend API changes required for P26.
- Uses existing diagnostics payload fields (`ruleId`, `accountId`, `status`, `processedAt`, `errorMessage`).
- Keep behavior backward-compatible with existing diagnostics table and leaderboard.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
- `ecm-frontend/e2e/mail-automation.spec.ts`
