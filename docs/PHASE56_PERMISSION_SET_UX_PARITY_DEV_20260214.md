# Phase 56 - Permission-Set UX Parity (Dev) - 2026-02-14

## Goal

Make Alfresco-style permission presets obvious during permissions triage:

- Coordinator
- Editor
- Contributor
- Consumer

So admins can quickly understand whether a principal is on a known preset or a custom mix.

## Scope / Non-Goals

In scope:

- UI-only improvements in the Permissions dialog.
- Works with existing backend permission set endpoints (no API changes).
- Adds Playwright mocked E2E coverage (no backend required).

Not in scope:

- Changing backend permission semantics or templates.
- Introducing new permission presets (we display what backend exposes).

## What Changed

### 1) Preset Legend (Help Text)

`PermissionsDialog` now shows a compact legend:

- "Permission presets (Alfresco-style)"
- Chips for each preset with tooltip:
  - description
  - included permissions

This provides in-context help without requiring a separate doc lookup.

### 2) "Effective preset" Column

The permissions grid now includes an **Effective preset** column per principal.

Behavior:

- If the principal's allowed permissions exactly match a preset:
  - show the preset name (green chip)
- Otherwise:
  - show `Custom` + `Closest: <Preset>`
  - tooltip shows:
    - preset description
    - missing permissions (what the preset would add)

### Matching Rule (Closest Preset)

To keep the output intuitive, we choose the **smallest preset that fully contains the principal's current allows**:

- find presets where `presetPermissions ⊇ allowedPermissions`
- choose the one with the fewest missing permissions (`presetPermissions - allowedPermissions`)

This avoids surprising results like suggesting `Consumer` for `READ+WRITE` (which is closer by symmetric-diff but not conceptually aligned).

### Data Sources / API Contract

We rely on existing endpoints (already used by the dialog):

- `GET /api/v1/security/permission-sets/metadata`
  - provides `label`, `description`, `order`, `permissions[]`
- `GET /api/v1/security/permission-sets`
  - provides a fallback mapping `{ [setName]: PermissionType[] }` if metadata is unavailable

The backend canonical reference remains `ecm-core/src/main/java/com/ecm/core/entity/PermissionSet.java`.

### UX Notes

- “Effective preset” is derived from the **current ALLOW grants** shown in the grid.
- If no preset fully contains the allowed set, we show `Custom` without a closest preset (tooltip shows the extra allowed permissions).
- The “Preset” dropdown (apply preset) is unchanged; “Effective preset” is informational and helps triage.

## Files Updated / Added

- `ecm-frontend/src/components/dialogs/PermissionsDialog.tsx`
- `ecm-frontend/e2e/permissions-dialog-presets.mock.spec.ts`

## Related

- Verification: `docs/PHASE56_PERMISSION_SET_UX_PARITY_VERIFICATION_20260214.md`
