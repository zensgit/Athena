# Phase367ZZX Search Effective Failure Category Convergence Verification

## Scope

Verified:

- `ecm-core/src/main/java/com/ecm/core/search/PreviewStatusFilterHelper.java`
- `ecm-core/src/main/java/com/ecm/core/search/SearchPreviewProjection.java`
- `ecm-core/src/main/java/com/ecm/core/search/NodeDocument.java`
- `ecm-core/src/main/java/com/ecm/core/search/FullTextSearchService.java`
- `ecm-core/src/main/java/com/ecm/core/search/FacetedSearchService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`
- `ecm-core/src/test/java/com/ecm/core/search/PreviewStatusFilterHelperTest.java`
- `ecm-core/src/test/java/com/ecm/core/search/NodeDocumentPreviewProjectionTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`

## Commands

```bash
cd ecm-core && mvn -q -Dtest='NodeDocumentPreviewProjectionTest,PreviewStatusFilterHelperTest,SearchControllerTest#queueFailedPreviewsBySearchShouldReturnOk+queueFailedPreviewsBySearchShouldReturnDeclinedQueueStateWhenSkipped+queueFailedPreviewsBySearchSkipsUnsupportedEffectiveFailureCategory+dryRunQueueFailedPreviewsBySearchShouldReturnSamples' test
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/search/PreviewStatusFilterHelper.java \
  ecm-core/src/main/java/com/ecm/core/search/SearchPreviewProjection.java \
  ecm-core/src/main/java/com/ecm/core/search/NodeDocument.java \
  ecm-core/src/main/java/com/ecm/core/search/FullTextSearchService.java \
  ecm-core/src/main/java/com/ecm/core/search/FacetedSearchService.java \
  ecm-core/src/main/java/com/ecm/core/controller/SearchController.java \
  ecm-core/src/test/java/com/ecm/core/search/PreviewStatusFilterHelperTest.java \
  ecm-core/src/test/java/com/ecm/core/search/NodeDocumentPreviewProjectionTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java \
  docs/PHASE367ZZX_SEARCH_EFFECTIVE_FAILURE_CATEGORY_CONVERGENCE_DEV_20260328.md \
  docs/PHASE367ZZX_SEARCH_EFFECTIVE_FAILURE_CATEGORY_CONVERGENCE_VERIFICATION_20260328.md
```

## Result

Focused verification passed.

## Notes

- This phase intentionally targets search-side preview failure category semantics only.
- Acceptance covers projection, helper semantics, and the `queue-failed` admin search workflow together, because that is the first high-value consumer that materially depends on retryable vs non-retryable category decisions.
