# Phase367ZZR Search Preview Queue Local Override Convergence

## Goal

Make `queuePreview(...)` responses immediately affect ordinary search and advanced search preview triage semantics without waiting for a full page refresh.

## Problem

Phase367ZZP introduced a richer queue response carrying:

- `previewStatus`
- `previewFailureReason`
- `previewFailureCategory`
- retry metadata

But both search workspaces still had internal summary logic reading raw result payload fields:

- ordinary search `failedPreviewSummary`
- ordinary search `failedPreviewNodes`
- ordinary search `previewStatusCounts`
- advanced search `previewIssueScopeResults`
- advanced search `failedPreviewResults`
- advanced search `failedPreviewSummary`
- advanced search `nonRetryableFailedReasonSummary`
- advanced search batch queue local status persistence

This created a visible split:

- card-level preview chips updated after queue
- page-level counters and retry scopes still reflected stale raw node/result state

## Design

### Ordinary Search

File: `ecm-frontend/src/pages/SearchResults.tsx`

Add a local `previewAwareDisplayNodes` projection:

- keep folder rows unchanged
- for document rows, overlay queue-local `previewStatus / previewFailureReason / previewFailureCategory`

Then move the following consumers to the projection:

- `failedPreviewSummary`
- `failedPreviewNodes`
- `previewStatusCounts`
- reason-scoped retry target selection

Also stop using `searchFacets.previewStatus` for counts when current display rows already have queue-local overrides, because facet counts are stale until the next full search refresh.

### Advanced Search

File: `ecm-frontend/src/pages/AdvancedSearchPage.tsx`

Add a local `previewAwareDisplayResults` projection with the same overlay rule.

Move these consumers to the projection:

- `previewIssueScopeResults`
- `failedPreviewResults`
- `failedPreviewSummary`
- `nonRetryableFailedReasonSummary`

Also stop trusting `facets.previewStatus` for counts when current display results already have queue-local overrides.

Finally, update batch queue persistence so `previewQueueStatusById` stores:

- `previewStatus`
- `previewFailureReason`
- `previewFailureCategory`

for batch queue actions as well, not just single-row retries.

## Result

After this phase, queue-triggered preview changes become the local source of truth for:

- failed preview counters
- retryable failure scope
- per-reason retry buckets
- preview status counts
- advanced search preview triage filters

without requiring a fresh search request.
