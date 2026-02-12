# Phase 2 Day 3 (P0) - Preview Retry Per-Item Actions (Design)

Date: 2026-02-12

## Goal

When a document preview fails (but is retryable), admins should be able to:
- retry preview generation for a single result, directly from the result card/row
- force-rebuild a single preview (bypass caches / rebuild even if a prior attempt exists)

This is required in both:
- Search results (`/search-results`)
- Advanced Search results (`/search`)

## Scope

- Frontend: `ecm-frontend`
- API: existing preview queue endpoint (no backend changes required)
- Automation: extend existing Playwright E2E around preview status

Out of scope:
- Changing preview generation semantics or adding new preview engine capabilities
- Adding new preview statuses beyond what the API already returns

## UX / Behavior

### Search Results (`/search-results`)

On each **document** result card:
- If `previewStatus=FAILED` and the failure is **not** UNSUPPORTED:
  - show `Retry preview` (calls queue endpoint with `force=false`)
  - show `Force rebuild preview` (calls queue endpoint with `force=true`)
- If `previewStatus=UNSUPPORTED` (or `FAILED` where failureCategory indicates UNSUPPORTED):
  - show a neutral “Preview unsupported” status
  - do not show retry/rebuild actions

### Advanced Search (`/search`)

On each **document** result row:
- Same visibility rules as `/search-results`
- Same actions:
  - `Retry preview`
  - `Force rebuild preview`

### Feedback

After click:
- show toast:
  - success: `Preview queued`
  - info: `Preview already up to date` (when server returns `queued=false`)
  - error: `Failed to queue preview`
- store `attempts` + `nextAttemptAt` from the queue response (when present) and surface it as detail alongside the status chip.

## API Contract

Use existing endpoint:

`POST /api/v1/documents/{documentId}/preview/queue?force={true|false}`

Expected response includes:
- `queued` (boolean)
- `attempts` (optional number)
- `nextAttemptAt` (optional ISO timestamp)

## Implementation Notes

### Result Action Wiring

Both pages already had a shared retry handler that calls `nodeService.queuePreview(id, force)`.
This change adds a **per-item force rebuild** action that calls the same handler with `force=true`.

### Visibility Rules

For correct behavior and test stability, actions are gated by:
- `previewStatus === FAILED`
- AND `!unsupported` based on failure metadata (`failureCategory`, mimeType, failureReason)

This prevents showing actions for file types that are categorically unsupported.

## Files

- `ecm-frontend/src/pages/SearchResults.tsx`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`

Automation:
- `ecm-frontend/e2e/search-preview-status.spec.ts`

