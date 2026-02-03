# Phase 18 - Permission Deny Precedence UX (Dev) - 2026-02-03

## Goal
Make deny precedence and conflict resolution visible in the Permissions dialog, and allow explicit allow/deny/clear actions per permission.

## Scope
- Show tri-state permission toggles (Allow → Deny → Clear).
- Surface a clear precedence note in the UI.
- Preserve backend deny precedence rules (no logic change required).

## Implementation Notes
- Permissions dialog now stores permission state as ALLOW/DENY.
- Clicking a permission cycles through Allow → Deny → Clear.
- Deny is rendered via an indeterminate checkbox (red) with explicit UI messaging.
- Copy ACL now outputs ALLOW/DENY sections per principal.

## Files Updated
- `ecm-frontend/src/components/dialogs/PermissionsDialog.tsx`
