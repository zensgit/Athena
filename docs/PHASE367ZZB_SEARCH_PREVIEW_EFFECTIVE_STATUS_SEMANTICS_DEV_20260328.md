# Phase 367ZZB: Search Preview Effective Status Semantics

## Goal
- Align search filter/result semantics with the new rendition-backed preview model.
- Stop treating preview-status-missing generic binaries as implicit `PENDING`.
- Normalize older indexed unsupported failures into `UNSUPPORTED` without requiring immediate reindex.

## Scope
- Backend search filter helper
- Backend full-text/faceted result mapping
- Search filter contract notes

## Design
- Extended `PreviewStatusFilterHelper` with effective preview status/failure resolution helpers.
- Tightened `PENDING` filter semantics to mean `previewStatus` missing and no unsupported signals.
- Expanded `UNSUPPORTED` filter semantics to include:
  - explicit `previewStatus=UNSUPPORTED`
  - `previewStatus=FAILED` with unsupported signals
  - missing `previewStatus` with unsupported mime/reason signals
- Updated `FullTextSearchService` and `FacetedSearchService` result mapping to emit effective preview status/failure reason/category instead of raw indexed values.
- Left old public filter names intact for compatibility; only the backend interpretation changed.

## Why This Matters
- Athena search results and preview triage now match rendition applicability more closely.
- Older documents with incomplete preview fields no longer leak into the wrong `PENDING` bucket.
- This reduces one of the remaining semantic gaps versus Alfresco-style rendition applicability/status behavior.
