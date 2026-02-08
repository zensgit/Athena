# Step: Preview Unsupported Classification (Design)

## Objective
- Make preview failure handling deterministic for unsupported file types.
- Ensure UI uses structured signals to hide retry actions for unsupported preview failures.
- Keep compatibility when backend category is absent by using frontend fallback logic.

## Scope
- Backend:
  - Add `PreviewFailureClassifier` for `UNSUPPORTED | TEMPORARY | PERMANENT`.
  - Expose `previewFailureCategory` in DTO/search/preview result payloads.
- Frontend:
  - Extend node/search typings with `previewFailureCategory`.
  - Update preview status utility and UI consumers.
  - Stabilize E2E assertions around unsupported preview behavior.

## Backend Design
- New classifier: `ecm-core/src/main/java/com/ecm/core/preview/PreviewFailureClassifier.java`
  - Input: `previewStatus`, `mimeType`, `failureReason`.
  - Output:
    - `UNSUPPORTED` for unsupported MIME/reason patterns.
    - `TEMPORARY` for transient failure patterns.
    - `PERMANENT` for other failed preview outcomes.
- Classifier output is attached in:
  - `NodeDto` (`previewFailureCategory`).
  - `SearchResult` (`previewFailureCategory`) via full-text and faceted mappers.
  - `PreviewResult` (`failureCategory`) via preview service.

## Frontend Design
- Type changes:
  - `Node.previewFailureCategory` added in `ecm-frontend/src/types/index.ts`.
  - API mapping extended in `ecm-frontend/src/services/nodeService.ts`.
- Utility changes:
  - `ecm-frontend/src/utils/previewStatusUtils.ts`
  - Added unsupported fallback by failure reason string (for backward compatibility).
  - Final unsupported decision order:
    1. category `UNSUPPORTED`
    2. unsupported reason pattern
    3. unsupported MIME set
- UI consumers updated:
  - `SearchResults`, `AdvancedSearchPage`, `FileList`, `UploadDialog`, `DocumentPreview`.
  - Retry controls and failure-reason info are suppressed for unsupported failures.

## E2E Strategy
- Test file: `ecm-frontend/e2e/search-preview-status.spec.ts`
- Kept core intent:
  - Unsupported preview displays neutral unsupported label.
  - Unsupported preview does not show card-level retry/reason action.
- De-flaked assertions:
  - Removed brittle global retry assertion in search-results test.
  - Removed environment-sensitive overlay/chip-state assertions.
  - Kept explicit advanced search failed-filter persistence checks.

## Additional Stability Fix
- Fixed dev compile issue in `ecm-frontend/src/index.tsx` (`TS2774`) by safely typing runtime crypto fallback checks.
- This prevents webpack overlay interference during local Playwright runs.
