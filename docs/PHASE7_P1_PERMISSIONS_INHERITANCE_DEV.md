# Phase 7 P1 Permissions Inheritance UX - Development

## Summary
Add an inheritance path visualization and ACL copy action to the permissions dialog.

## Scope
- Display the node path as an inheritance chain.
- Provide a "Copy ACL" action to export the permission matrix.

## Implementation
- Loaded node path during permission fetch.
- Rendered a chip-based path chain in the dialog header.
- Added a clipboard export of principals and granted permissions.

## Files Changed
- `ecm-frontend/src/components/dialogs/PermissionsDialog.tsx`
