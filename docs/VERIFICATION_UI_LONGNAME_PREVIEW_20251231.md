# Verification: UI Long Name & Preview Error Actions (2025-12-31)

## Scope
- Long file name typography (3-line clamp + font scale) in list/grid and search results.
- Preview error actions (retry, server preview, open, download) in document preview.

## Environment
- UI: http://localhost:3000/browse/root (dev server with `REACT_APP_API_BASE_URL=http://localhost:7700`)
- Tooling: MCP Chrome DevTools
- Build: `npm run build` (local)

## Steps
1. Run frontend lint.
2. Run `npm run build` in `ecm-frontend`.
3. Open `http://localhost:3000/browse/root` and switch to list view.
4. Inspect the long-name folder card (`ui-long-name-20251231-...`) styles via DevTools.
5. Open search results (`/search-results`) for `ui-long-name` and verify the result card typography.
6. Open `ui-e2e-1767068532011.pdf` via the row actions > View to trigger preview error UI.

## Results
- Lint: PASS.
- UI load: PASS (root list view renders without errors).
- Long-name 3-line clamp (grid): PASS (`-webkit-line-clamp: 3`, `font-size: 12.8px`, `line-height: 13.44px`).
- Long-name 3-line clamp (search results): PASS (`-webkit-line-clamp: 3`, `font-size: 12.8px`, `line-height: 13.44px`).
- Long-name list view: PASS (`white-space: nowrap`, `text-overflow: ellipsis`, `overflow: hidden`).
- Preview failure actions: PASS (`Retry`, `Try server preview`, `Open file`, `Download` shown on error state).
- Search result path line wraps: PASS (`white-space: normal`, `overflow: visible`).
- Search result action buttons visible: PASS (`View` present on results; `Download` shown where applicable).
- Search result text preview: PASS (`ui-e2e-1766107704241.txt` opened preview overlay; content loaded via `/api/v1/nodes/{id}/content` 200).
- Search result download: PASS (Download triggered `/api/v1/nodes/{id}/content` 200).
- Search result PDF preview: PASS (`e2e-preview-1766303349184.pdf` opened preview overlay, page rendered).
- Search result PDF annotate toggle: PASS (`Annotate` -> `Annotating`, exit hint visible).

## Notes
- Preview failure reproduced with `ui-e2e-1767068532011.pdf` and error `Page tree root must be a dictionary`.
- Search dialog error (`An unexpected error occurred`) was reproduced when the dev server was not running (API calls to `/api/v1/search*` failed with connection refused); restarting `npm start` restored search results.
- Docker `compose build ecm-frontend` was interrupted after a long hang in `npm run build`; local build succeeded.
- Re-verified list and grid view styles on the dev server at `http://localhost:3000`.
- Verified PDF preview/annotate behavior from search results using `e2e-preview-1766303349184.pdf` (rendered page, annotate mode toggles).
