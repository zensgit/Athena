# PDF Preview Content Fetch Fix - Design

Date: 2025-12-22

## Goal
Ensure document preview fetches file content using the configured API base URL and shared auth handling, so PDF preview works when the frontend is served on a different port or origin.

## Problem
`DocumentPreview` used `fetch('/api/v1/nodes/{id}/content')`, which ignores `REACT_APP_API_URL`. In deployments where the frontend is not proxying `/api`, this call can hit the frontend origin and return HTML, causing PDF.js to fail with "Failed to load PDF".

## Approach
- Add `getBlob()` to `apiService` so binary downloads use the same base URL and auth interceptors as the rest of the app.
- Switch `DocumentPreview` to call `apiService.getBlob('/nodes/{id}/content')` and build the object URL from that blob.

## Files Updated
- `ecm-frontend/src/services/api.ts`
- `ecm-frontend/src/components/preview/DocumentPreview.tsx`

## Risks / Rollback
- Low risk; only affects preview content fetch.
- Rollback by restoring the previous `fetch` call and removing `getBlob()` if needed.
