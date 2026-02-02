# Phase 8 P1 E2E Coverage - Development

## Summary
Added E2E coverage for the newly introduced Search, Version, Permissions, and Preview UX behaviors.

## Scope
- Search: preview status filters visibility and selection.
- Version history: confirmation dialog flow for download/restore.
- Permissions: inheritance path and Copy ACL affordance.
- Preview: queue preview action triggers processing notice.

## Implementation
- Added `e2e/search-preview-status.spec.ts`.
- Added `e2e/permissions-dialog.spec.ts`.
- Updated `e2e/pdf-preview.spec.ts` to exercise queue preview alert.
- Updated `e2e/version-share-download.spec.ts` for the new confirm dialog.

## Files Changed
- `ecm-frontend/e2e/search-preview-status.spec.ts`
- `ecm-frontend/e2e/permissions-dialog.spec.ts`
- `ecm-frontend/e2e/pdf-preview.spec.ts`
- `ecm-frontend/e2e/version-share-download.spec.ts`
