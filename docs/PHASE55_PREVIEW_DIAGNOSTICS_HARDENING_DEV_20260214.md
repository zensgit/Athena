# Phase 55 - Preview Diagnostics Hardening (Dev) - 2026-02-14

## Goals

- Reduce admin triage friction on preview failures:
  - fewer copy/paste steps
  - faster navigation from a failure sample to the underlying node
- Improve deep links so Advanced Search opens with the correct preview-status filter applied.

## Changes

### 1) Advanced Search Deep Link Includes `previewStatus`

Preview Diagnostics "Open in Advanced Search" now navigates to:

- `/search?q=<name>&previewStatus=FAILED|UNSUPPORTED`

Rationale:
- admins immediately see failure-scoped results (instead of a broad name search),
- aligns with Advanced Search UX (preview-status facet actions + retry/rebuild tooling).

### 2) Copy Document Id (Per Row)

New per-row action: **Copy document id**

- Uses `navigator.clipboard.writeText(item.id)`
- Shows toast:
  - success: `Document id copied`
  - failure: `Failed to copy document id`

### 3) Open Parent Folder (Per Row)

New per-row action: **Open parent folder**

Behavior:

1. Compute a parent folder path from `item.path` by removing the last segment.
   - Example: `/Root/Documents/foo/bar.pdf` -> `/Root/Documents/foo`
2. Resolve folder id:
   - `GET /api/v1/folders/path?path=<parentPath>`
3. Navigate to:
   - `/browse/<folderId>`

Guardrails:
- If `item.path` is missing/unparseable, the action is disabled.

### 4) Hook Lint Cleanup

`loadFailures` is memoized with `useCallback` to avoid stale closures and silence hooks lint warnings.

## Files Updated

- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

## Notes / Limitations

- Integration E2E still recommended (requires backend stack) to validate:
  - real diagnostics endpoint payloads
  - real folder-path resolution behavior
- Parent-folder navigation currently targets the folder (not auto-opening the document preview inside that folder).

