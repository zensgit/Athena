# Phase 367ZZE: Rendition Registry Dialog For Search And Browser Verification

## Verified
- Shared dialog loads node rendition definitions lazily and handles loading/error/empty states.
- `SearchResults` document cards expose a `Rendition Registry` action.
- `FileList` context menu exposes a `View Rendition Registry` action for documents.
- Available renditions with a content URL expose an `Open Rendition` action from the dialog.

## Commands
```bash
cd ecm-frontend && ./node_modules/.bin/eslint \
  src/components/dialogs/RenditionDefinitionDialog.tsx \
  src/pages/SearchResults.tsx \
  src/components/browser/FileList.tsx

cd ecm-frontend && npm run -s build
```

## Notes
- This slice intentionally reuses the existing rendition definition API and utility layer; no backend change was required.
- The dialog is operator-focused and does not replace low-level preview diagnostics surfaces.
