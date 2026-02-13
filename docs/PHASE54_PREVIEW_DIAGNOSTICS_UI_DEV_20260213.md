# Phase 54 - Admin Preview Diagnostics UI (Dev) - 2026-02-13

## Goals

- Give admins a single place to triage **recent preview failures** (FAILED/UNSUPPORTED).
- Make the next action obvious:
  - open the item in Advanced Search
  - retry / force rebuild **only** when retryable (TEMPORARY / transient hints)

## Backend Contract (Existing)

- Admin sample endpoint:
  - `GET /api/v1/preview/diagnostics/failures?limit=50`
  - Auth: `ROLE_ADMIN`
  - Returns a list of:
    - `id`
    - `name`
    - `path`
    - `mimeType`
    - `previewStatus`
    - `previewFailureCategory`
    - `previewFailureReason`
    - `previewLastUpdated`
- Queue actions:
  - `POST /api/v1/documents/{id}/preview/queue?force=false` (retry)
  - `POST /api/v1/documents/{id}/preview/queue?force=true` (force rebuild)

## Frontend Changes

### Route + Access Control

- New admin-only route: `/admin/preview-diagnostics`
  - Guarded via `PrivateRoute requiredRoles={['ROLE_ADMIN']}`

### Navigation

- Added menu entry:
  - `MainLayout` -> Admin -> **Preview Diagnostics**

### Page UX

- Table of recent failure samples with:
  - quick filter (`name`, `path`, `mimeType`, status/category/reason)
  - limit selector (25/50/100/200)
  - summary chips:
    - total, retryable, permanent, unsupported
- Per-row actions:
  - open in Advanced Search (`/search?q=<name>`)
  - retry preview / force rebuild (disabled when not retryable)

### Retryability Rules

Uses shared logic in `utils/previewStatusUtils.ts`:

- UNSUPPORTED:
  - category contains `UNSUPPORTED`, or
  - reason contains “not supported/unsupported …”, or
  - mime type is known-unsupported (e.g. `application/octet-stream`)
- Retryable:
  - category is `TEMPORARY`, or
  - reason contains transient hints (timeout/502/503/504/etc)

## Files Added/Updated

- `ecm-frontend/src/App.tsx`
- `ecm-frontend/src/components/layout/MainLayout.tsx`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- `ecm-frontend/src/services/previewDiagnosticsService.ts`

