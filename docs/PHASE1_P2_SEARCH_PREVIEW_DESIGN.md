# Search + Preview P2 — Match Explanation + Force Rebuild Controls

Date: 2026-02-06

## Context
Reference review:
- `reference-projects/alfresco-community-repo` re-thumbnail / re-render workflows inspired adding explicit rebuild actions.
- `reference-projects/paperless-ngx` surfaces highlight context more clearly for search results.

Athena already returns highlight snippets and matched fields but the summary does not explain *which* field produced the snippet. Preview retry exists but there was no batch force-rebuild option for failed previews.

## Goals
1. Improve search explanation by labeling highlight summaries with the matched field.
2. Provide a batch force-rebuild option for failed previews.

## Non-Goals
- Changing search scoring or query DSL.
- Adding new preview formats.

## Design
### 1) Search highlight summaries with field labels
- Build highlight summaries as `Field: snippet`.
- When multiple fields match, include up to two labeled snippets separated by `•`.

**Implementation**
- `SearchHighlightHelper` now formats summaries with field labels and supports multi-field summaries.

### 2) Batch force rebuild for failed previews
- Extend the existing “Retry failed previews” control with a “Force rebuild failed” button that queues previews with `force=true`.

**Implementation**
- `SearchResults` adds a second button to queue rebuilds for all failed items.

## Files Changed
- `ecm-core/src/main/java/com/ecm/core/search/SearchHighlightHelper.java`
- `ecm-frontend/src/pages/SearchResults.tsx`

