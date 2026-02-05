# Athena ECM Release Notes (2026-02-05)

## Highlights
- Mail Automation reporting and diagnostics panels for operational visibility.
- Search explainability with highlight snippets to improve relevance transparency.
- Permission template history, compare, and CSV export for safe ACL governance.
- Preview retry status indicators and bulk retry for failed previews.
- Version history compare summary for quick change scanning.

## Admin & Security
- Permission templates now keep immutable version history.
- Compare view shows added/removed/changed entries with exportable CSV.
- ACL filtering and RBAC remain enforced across search and browsing.

## Search & Preview
- Search results show highlight snippets (where available).
- Preview queue status visible per item; bulk retry action provided.

## Mail Automation
- Reporting panel with account/rule/time filters.
- Diagnostics dry‑run supports folder listing and skip reasons.
- Connection status and polling health surfaced in UI.

## Verification
- Backend: `mvn test` (138 tests, BUILD SUCCESS)
- Frontend: Playwright E2E full suite (36 passed)
