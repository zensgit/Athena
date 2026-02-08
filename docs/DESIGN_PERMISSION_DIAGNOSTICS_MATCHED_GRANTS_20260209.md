# Design: Permission Diagnostics Matched Grants (P64) (2026-02-09)

## Problem

1. The **Permissions** dialog exposes a "Permission diagnostics" panel, but it was hard to answer:
   - *Which* permission entries (ACL grants/denies) actually matched?
   - Were matches **Explicit** vs **Inherited**?
2. When an **admin** used "Diagnose as" to inspect a different user (e.g. `viewer`), the backend could incorrectly return `Reason ADMIN`, because role checks for a target user were short-circuiting on the *caller*'s authorities.

## Goals

1. In the UI, show a concise, readable explanation of **matched grants** for `ACL_ALLOW` / `ACL_DENY` decisions.
2. Ensure admin diagnostics reflect the **target user's** roles/authorities, not the caller.
3. Keep behavior backward-compatible for normal permission checks of the current user.

## Solution

### Backend: Target-User Role Evaluation

Update `SecurityService` so:

1. `hasRole(roleName, username)` and `hasPrivilege(privilege, username)` only short-circuit on the caller's JWT authorities when `username` is the **current** user.
2. `hasPermission(node, perm, username)` admin short-circuit is based on the **evaluated** user via `hasRole("ROLE_ADMIN", username)` (which now behaves correctly for non-current users).
3. `explainPermission(...)` uses `hasRole("ROLE_ADMIN", username)` to produce `Reason ADMIN` only when the *target* user is actually admin.

This enables correct admin "diagnose other users" behavior.

Implementation:

- `ecm-core/src/main/java/com/ecm/core/service/SecurityService.java`

### Frontend: Matched Grants Table

When diagnostics returns `reason` in `{ACL_ALLOW, ACL_DENY}`:

1. Compute a combined list from `allowedAuthorities` + `deniedAuthorities`.
2. For each authority, resolve the corresponding permission entry from the permission list currently displayed in the dialog:
   - Direct match: `entry.principal === authority`
   - Fallback group match: `entry.principal === "GROUP_" + authority`
3. Derive:
   - Match type: Allow / Deny (from diagnostics arrays)
   - Source: `Explicit` / `Inherited` / `Mixed` (from `entry.inheritance[permissionType]`)
   - Effective: `ALLOW` / `DENY` (from `entry.permissions[permissionType]`)
4. Render as a compact table under "Permission diagnostics".

Implementation:

- `ecm-frontend/src/components/dialogs/PermissionsDialog.tsx`

## API / Contract Changes

No API shape changes.

The UI relies on the existing `PermissionDecision` fields:

- `reason`
- `allowedAuthorities: string[]`
- `deniedAuthorities: string[]`
- `username`

## Risks / Notes

1. The "Matched grants" view is **diagnostic** and best-effort:
   - It uses the dialog's permission list as the source of inheritance markers.
2. This change intentionally prevents "admin caller" from being treated as admin when diagnosing another user.

## Verification

See:

- `docs/VERIFICATION_PERMISSION_DIAGNOSTICS_MATCHED_GRANTS_20260209.md`

