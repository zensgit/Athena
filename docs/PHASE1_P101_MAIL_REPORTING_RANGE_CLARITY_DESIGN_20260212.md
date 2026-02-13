# Phase 1 P101 Design: Mail Reporting Range Clarity

Date: 2026-02-12

## Background

- Mail reporting already supports `days` range filtering.
- When report payload is unavailable (`report == null`), empty-state text lacked filter context.
- This can look like stale or ambiguous data to operators.

## Goal

Make empty-state messaging explicit about currently selected reporting window and filters.

## Scope

- `ecm-frontend/src/pages/MailAutomationPage.tsx`
- `ecm-frontend/e2e/mail-automation.spec.ts`

## Implementation

1. Added reporting selection summary
- New computed label includes:
  - selected days window
  - selected account (or all accounts)
  - selected rule (or all rules)

2. Updated empty-state content
- Replaced generic message with:
  - `No report data available for the selected filters.`
  - context line: `Selected window: last X days • Account: ... • Rule: ...`

3. Added E2E coverage
- Route-stubbed report API failure scenario validates empty-state message plus selected range context rendering.

## Expected Outcome

- Operators can distinguish “no data for selected window” from stale rendering ambiguity.
