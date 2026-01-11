# Design: Count-Based Endpoint Role Guards (2026-01-11)

## Context
- Some diagnostic/count endpoints returned global totals without role restrictions.
- The search index stats endpoint exposed total indexed documents.
- The system status endpoint returned database document counts and index stats to any authenticated user.

## Decision
- Restrict system diagnostics and index stats to admins.
- Align frontend routing/navigation so non-admin users cannot access the System Status page.

## Implementation
- Add `@PreAuthorize("hasRole('ADMIN')")` to `/api/v1/search/index/stats` and `/api/v1/system/status`.
- Update the UI route for `/status` to require the admin role.
- Hide the System Status menu item for non-admin users.
- Add security tests for the updated endpoints and a UI menu assertion for admin-only visibility.

## Impact
- Prevents global count leakage to non-admin users.
- Keeps diagnostics available for administrators only.
