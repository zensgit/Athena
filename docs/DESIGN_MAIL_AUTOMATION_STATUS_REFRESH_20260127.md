# Design: Mail Automation Status Refresh + Error Visibility (2026-01-27)

## Goals
- Let admins refresh account status without triggering a full fetch.
- Surface last fetch errors directly in the table (not only via tooltip hover).

## Approach
- Add a lightweight "Refresh Status" button that reloads accounts/rules/tags.
- Keep the existing `Trigger Fetch` flow unchanged.
- When the last fetch status is `ERROR`, show a short error summary under the status chip:
  - Truncated to a safe length for table layout.
  - Full error remains available via the chip tooltip.

## Implementation Notes
- `loadAll` now supports a silent mode:
  - Silent reload avoids the page-level loading spinner.
  - The refresh button shows its own spinner state.
- Silent reload returns a boolean so success toasts are not shown on failure.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`

## Risks / Trade-offs
- Silent refresh still reloads tags and rules, not only accounts.
- Error summaries are truncated; the tooltip is still the source of truth.

