# Phase 1 P64 - Effective Permission Explanation UX (Design) - 2026-02-09

## Summary

Deliver an admin-facing “explain access” experience by improving the existing Permission Diagnostics panel to:

- Show which ACL entries actually matched (`allowedAuthorities` / `deniedAuthorities`)
- Mark whether each match is `Explicit` vs `Inherited` (or `Mixed`)
- Ensure “Diagnose as” evaluates the *target* user (not the admin caller) when determining `Reason ADMIN`

This aligns Athena’s permissions operability closer to Alfresco-style clarity for “why is access allowed/denied?”

## Implementation Notes

- Backend behavior fix:
  - Ensure role/privilege short-circuits only apply to the current user, so admin “diagnose other users” returns correct reasons.
- Frontend UX:
  - Render a compact “Matched grants” table for `ACL_ALLOW` / `ACL_DENY` decisions using the permission list currently shown in the dialog.

## References / Primary Docs

This phase is implemented and documented in:

- Design: `docs/DESIGN_PERMISSION_DIAGNOSTICS_MATCHED_GRANTS_20260209.md`
- Verification: `docs/VERIFICATION_PERMISSION_DIAGNOSTICS_MATCHED_GRANTS_20260209.md`

## Key Files

- Backend: `ecm-core/src/main/java/com/ecm/core/service/SecurityService.java`
- Frontend: `ecm-frontend/src/components/dialogs/PermissionsDialog.tsx`
- E2E: `ecm-frontend/e2e/permission-explanation.spec.ts`

