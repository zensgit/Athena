# Design: Audit Export Feedback (2026-01-06)

## Goal
- Provide clearer UI feedback when audit export fails or returns no rows.

## Approach
- Parse error responses and surface the server message in the toast.
- Read `X-Audit-Export-Count` header and show an info toast when the export is empty.

## Impact
- Admins see actionable messages for invalid ranges or empty exports.
- Success toast remains for non-empty exports.

## Files
- ecm-frontend/src/pages/AdminDashboard.tsx
