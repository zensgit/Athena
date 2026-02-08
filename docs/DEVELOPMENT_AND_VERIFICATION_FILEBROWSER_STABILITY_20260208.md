# Development and Verification - FileBrowser Stability (2026-02-08)

## Goal

Reduce blank/stale page risk in browse flow when folder children loading fails, and make failure recoverable in UI.

## Implementation

### 1) Prevent stale children data during folder switches/failures

Updated `ecm-frontend/src/store/slices/nodeSlice.ts`:

- In `fetchChildren.pending`:
  - clear `nodes`
  - reset `nodesTotal`
  - keep loading state true
- In `fetchChildren.rejected`:
  - clear `nodes`
  - reset `nodesTotal`
  - keep explicit error message

Effect:
- No more stale previous-folder list while a new folder load is in progress.
- On failure, UI state is deterministic (empty + error), not mixed old/new content.

### 2) Show explicit error + Retry entry in FileBrowser

Updated `ecm-frontend/src/pages/FileBrowser.tsx`:

- Reads `error` from node slice.
- Renders warning `Alert` with `Retry` action when load failed and not loading.
- `Retry` calls `loadNodeData()` directly.

Effect:
- User sees actionable feedback instead of perceived blank state.

### 3) Unit test coverage for reducer behavior

Added `ecm-frontend/src/store/slices/nodeSlice.test.ts`:

- verifies `fetchChildren.pending` clears stale nodes
- verifies `fetchChildren.rejected` clears nodes and keeps error

## Verification

### Unit tests

- Command:
  - `npm test -- --watch=false --runTestsByPath src/store/slices/nodeSlice.test.ts src/utils/previewStatusUtils.test.ts`
- Result:
  - `2 passed` suites
  - `11 passed` tests

### Build/deploy refresh

- Commands:
  - `npm run build` (`ecm-frontend`)
  - `docker compose up -d --build ecm-frontend`
- Health:
  - `http://localhost:5500/` => `200`
  - `http://localhost:7700/actuator/health` => `200`

### Playwright targeted regression

- Command:
  - `npx playwright test e2e/ui-smoke.spec.ts e2e/p1-smoke.spec.ts --workers=1 --grep 'UI smoke: browse \+ upload \+ search \+ copy/move \+ facets \+ delete \+ rules|P1 smoke: app error fallback can return to login'`
- Result:
  - `2 passed`

## Files changed (this stability increment)

- `ecm-frontend/src/store/slices/nodeSlice.ts`
- `ecm-frontend/src/pages/FileBrowser.tsx`
- `ecm-frontend/src/store/slices/nodeSlice.test.ts`
