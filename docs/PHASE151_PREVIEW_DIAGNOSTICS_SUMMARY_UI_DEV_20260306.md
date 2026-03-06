# Phase 151: Preview Diagnostics Summary UI (Development)

## Date
2026-03-06

## Goal
- Surface backend summary confidence and top failure reasons directly in Preview Diagnostics page to reduce operator guesswork.

## Scope
- Frontend only (`ecm-frontend`):
  - Add summary API models/call in diagnostics service
  - Render summary panel in diagnostics page

## Design
1. Add `getFailureSummary(sampleLimit)` to `previewDiagnosticsService`.
2. Load list + summary in parallel with `Promise.all(...)`.
3. Add a new UI block above diagnostics table:
   - Confidence chip (`HIGH`/`LOW`)
   - Sample coverage chip (`sampled/total`)
   - Warning alert when sample is truncated
   - Status and category count chips
   - Top reason table with reason/category/retryable/count
4. Keep existing per-item actions unchanged:
   - Copy id
   - Open parent folder
   - Open in Advanced Search
   - Retry / Force rebuild (retryable only)

## Changed Files
- `ecm-frontend/src/services/previewDiagnosticsService.ts`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

## Compatibility
- Backward compatible:
  - Existing list loading path still works.
  - New summary panel is additive and does not alter existing action semantics.
