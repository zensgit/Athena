# Design: Folder-Scoped Search Default (2026-02-10)

## Problem
From a folder browse route (`/browse/:nodeId`), users expect Advanced Search to default to searching within the current folder. In practice, the search dialog could open before `currentNode` finished loading, which resulted in:

- Missing scope chip in the Advanced Search dialog (`Scope: This folder`)
- Searches running across the entire repository instead of the current folder
- Slower/flakier E2E due to larger result sets (notably in `ui-smoke`)

## Goal
Make “search within this folder” the default when opening Advanced Search from a folder page, even if the folder entity has not been loaded yet.

## Solution
In `MainLayout` search handler:

1. Prefer the real UUID from `currentNode.id` when `currentNode.nodeType === 'FOLDER'`.
2. If `currentNode` is not ready, fall back to the route param `nodeId` when it looks like a UUID and is not the `root` alias.
3. Dispatch `setSearchPrefill({ folderId, includeChildren: true })` before opening the dialog.

This ensures the Search dialog renders the scope chip immediately and the server-side query uses `folderId`/`includeChildren` filters.

## Scope / Non-goals
- No backend changes.
- Root alias (`/browse/root`) is intentionally not forced into scoped search by this change.

## Files
- `ecm-frontend/src/components/layout/MainLayout.tsx`

## Testing
- E2E: `ecm-frontend/e2e/folder-scoped-search.spec.ts`
- E2E (regression/stability): `ecm-frontend/e2e/ui-smoke.spec.ts` (browse+upload+search flow)

