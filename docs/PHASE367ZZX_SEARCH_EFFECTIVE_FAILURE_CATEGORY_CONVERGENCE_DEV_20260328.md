# Phase367ZZX Search Effective Failure Category Convergence

## Goal

Converge search projection, search result building, and search-scope preview queue operations onto one shared effective preview failure category contract.

## Problem

Athena search had a remaining semantics split:

- `NodeDocument` indexed effective preview status and failure reason, but not failure category
- `FullTextSearchService` and `FacetedSearchService` rebuilt preview failure category on the fly
- `SearchController` `queue-failed` operations reclassified retryability from `previewStatus + previewFailureReason`, even when a search result already carried a stronger effective category

This meant a result could already be effectively `UNSUPPORTED`, but search-scope preview queue still treated it like a retryable `FAILED` row if the reason string was generic.

## Design

### Add effective failure category to search projection

Files:

- `ecm-core/src/main/java/com/ecm/core/search/SearchPreviewProjection.java`
- `ecm-core/src/main/java/com/ecm/core/search/NodeDocument.java`

Add:

- `SearchPreviewProjection.projectPreviewFailureCategory(Document document)`
- `NodeDocument.previewFailureCategory`

Projection now stores all three effective preview semantics together:

- `previewStatus`
- `previewFailureReason`
- `previewFailureCategory`

This keeps the index model aligned with the newer rendition-backed preview semantics instead of leaving category reconstruction to downstream consumers.

### Centralize effective failure category resolution

File:

- `ecm-core/src/main/java/com/ecm/core/search/PreviewStatusFilterHelper.java`

Add:

- `resolveEffectiveFailureCategory(previewStatus, mimeType, previewFailureReason, previewFailureCategory)`

Behavior:

1. prefer an explicit effective category when already present
2. otherwise derive from effective preview status and effective failure reason

This helper is now the shared contract for search-side category semantics.

### Make search result builders preserve projected category

Files:

- `ecm-core/src/main/java/com/ecm/core/search/FullTextSearchService.java`
- `ecm-core/src/main/java/com/ecm/core/search/FacetedSearchService.java`

Search result DTO construction now prefers the projected effective failure category instead of always reclassifying from scratch.

### Make search-scope preview queue trust effective category

File:

- `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`

`isRetryableFailed(...)` now uses `PreviewStatusFilterHelper.resolveEffectiveFailureCategory(...)`.

That means `queue-failed` and `queue-failed/dry-run` will no longer retry rows whose effective category is already `UNSUPPORTED` or otherwise non-retryable, even if their raw status remains `FAILED`.

## Result

Search projection, search result DTOs, and search-scope preview queue triage now share one effective failure category contract. Search no longer rebuilds retryability from stale partial signals after effective preview semantics have already been resolved upstream.
