# Phase367ZZA Search Preview Projection Convergence

## Goal

Move Athena's search/index preview projection one step closer to rendition-backed semantics instead of raw `Document.preview*` passthrough.

## Why This Slice

Athena already had:

- rendition applicability
- rendition definition registry
- registry-backed operator surfaces

But search indexing still projected preview semantics directly from `Document.previewStatus`, which left one important drift:

- not-applicable sources could still look like `PENDING` in search/index semantics because the indexed field was missing

That mismatch made search facets and status filters less accurate than the live rendition API.

## Implementation

### 1. Added a dedicated search preview projection helper

New helper:

- `ecm-core/src/main/java/com/ecm/core/search/SearchPreviewProjection.java`

It provides a bounded bridge from `Document` to search-index preview semantics.

Current rules:

- `previewAvailable=true` projects to `READY`
- explicit unsupported failures project to `UNSUPPORTED`
- preview definitions that are registered-but-not-applicable project to `UNSUPPORTED`
- applicable but not yet created previews still project to missing status, preserving existing `PENDING` semantics

### 2. `NodeDocument` now indexes effective preview status

`NodeDocument.fromNode(...)` no longer copies raw preview fields directly.

It now uses:

- `SearchPreviewProjection.projectPreviewStatus(document)`
- `SearchPreviewProjection.projectPreviewFailureReason(document)`

That means search indexing now understands the distinction between:

- `pending`
- `unsupported / not applicable`

instead of collapsing both into the old "missing field" path.

## Files

- `ecm-core/src/main/java/com/ecm/core/search/SearchPreviewProjection.java`
- `ecm-core/src/main/java/com/ecm/core/search/NodeDocument.java`
- `ecm-core/src/test/java/com/ecm/core/search/NodeDocumentPreviewProjectionTest.java`

## Operator Impact

Search and facets are now better aligned with Athena's live rendition semantics:

- generic binary content stops looking like "maybe pending"
- unsupported content is indexed as unsupported
- pending remains reserved for applicable-but-not-yet-created previews

This is a small but important convergence step toward a rendition-backed search model.

## Residual Gap

This phase improves index projection, but Athena still has further search/rendition convergence work:

- more result/facet semantics can be driven from rendition-backed state instead of legacy preview fields
- the search layer still stores a simplified preview projection rather than full rendition-resource state
- lifecycle ownership is still shared with `Document.preview*`
