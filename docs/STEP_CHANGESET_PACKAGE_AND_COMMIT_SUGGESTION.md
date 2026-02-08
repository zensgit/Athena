# Step: Change Set Package and Commit Suggestion

## Scope Packaged
- Backend preview failure structured classification support.
- Frontend unsupported preview rendering and retry-control suppression.
- E2E stabilization for preview-status flow and login fallback handling.
- Local auth bootstrap compile fix to avoid webpack overlay interference.

## File Package Summary

### Backend (`ecm-core`)
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewFailureClassifier.java` (new)
- `ecm-core/src/main/java/com/ecm/core/dto/NodeDto.java`
- `ecm-core/src/main/java/com/ecm/core/search/SearchResult.java`
- `ecm-core/src/main/java/com/ecm/core/search/FullTextSearchService.java`
- `ecm-core/src/main/java/com/ecm/core/search/FacetedSearchService.java`
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewResult.java`
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewService.java`
- `ecm-core/src/test/java/com/ecm/core/preview/PreviewFailureClassifierTest.java` (new)

### Frontend (`ecm-frontend`)
- `ecm-frontend/src/types/index.ts`
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/utils/previewStatusUtils.ts`
- `ecm-frontend/src/utils/previewStatusUtils.test.ts`
- `ecm-frontend/src/pages/SearchResults.tsx`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- `ecm-frontend/src/components/browser/FileList.tsx`
- `ecm-frontend/src/components/dialogs/UploadDialog.tsx`
- `ecm-frontend/src/components/preview/DocumentPreview.tsx`
- `ecm-frontend/src/index.tsx`
- `ecm-frontend/e2e/search-preview-status.spec.ts`

### Docs
- `docs/STEP_PREVIEW_UNSUPPORTED_CLASSIFICATION_DESIGN.md`
- `docs/STEP_PREVIEW_UNSUPPORTED_CLASSIFICATION_VERIFICATION.md`

## Suggested Commit Split (Recommended)

1. Backend classification
- Suggested message:
  - `feat(core): add structured preview failure classification`
- Include:
  - backend classifier + DTO/search/preview result wiring + classifier unit test.

2. Frontend unsupported fallback and UI behavior
- Suggested message:
  - `feat(frontend): render unsupported preview state and suppress retry actions`
- Include:
  - preview status utility/type/service/UI changes.

3. E2E and local bootstrap stability
- Suggested message:
  - `test(frontend): stabilize search preview status e2e for local env`
- Include:
  - `search-preview-status.spec.ts`
  - `src/index.tsx` crypto fallback type-safe fix
  - docs for design/verification

## Suggested Commit Commands
```bash
# Commit 1
git add \
  ecm-core/src/main/java/com/ecm/core/preview/PreviewFailureClassifier.java \
  ecm-core/src/main/java/com/ecm/core/dto/NodeDto.java \
  ecm-core/src/main/java/com/ecm/core/search/SearchResult.java \
  ecm-core/src/main/java/com/ecm/core/search/FullTextSearchService.java \
  ecm-core/src/main/java/com/ecm/core/search/FacetedSearchService.java \
  ecm-core/src/main/java/com/ecm/core/preview/PreviewResult.java \
  ecm-core/src/main/java/com/ecm/core/preview/PreviewService.java \
  ecm-core/src/test/java/com/ecm/core/preview/PreviewFailureClassifierTest.java
git commit -m "feat(core): add structured preview failure classification"

# Commit 2
git add \
  ecm-frontend/src/types/index.ts \
  ecm-frontend/src/services/nodeService.ts \
  ecm-frontend/src/utils/previewStatusUtils.ts \
  ecm-frontend/src/utils/previewStatusUtils.test.ts \
  ecm-frontend/src/pages/SearchResults.tsx \
  ecm-frontend/src/pages/AdvancedSearchPage.tsx \
  ecm-frontend/src/components/browser/FileList.tsx \
  ecm-frontend/src/components/dialogs/UploadDialog.tsx \
  ecm-frontend/src/components/preview/DocumentPreview.tsx
git commit -m "feat(frontend): render unsupported preview state and suppress retry actions"

# Commit 3
git add \
  ecm-frontend/e2e/search-preview-status.spec.ts \
  ecm-frontend/src/index.tsx \
  docs/STEP_PREVIEW_UNSUPPORTED_CLASSIFICATION_DESIGN.md \
  docs/STEP_PREVIEW_UNSUPPORTED_CLASSIFICATION_VERIFICATION.md
git commit -m "test(frontend): stabilize search preview status e2e for local env"
```
