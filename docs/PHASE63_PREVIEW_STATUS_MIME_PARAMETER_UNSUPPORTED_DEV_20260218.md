# Phase 63: Preview Status Unsupported Matching for MIME Parameters - Development

## Date
2026-02-18

## Background
- In some legacy/indexed documents, `mimeType` may include parameters, e.g.:
  - `application/octet-stream; charset=binary`
- These records should be treated as preview `UNSUPPORTED`.
- Existing preview-status backend filters/facets matched unsupported MIME by exact term only, which can miss parameterized values.

## Goal
1. Ensure preview-status filtering/faceting consistently classifies parameterized unsupported MIME values as `UNSUPPORTED`.
2. Keep `FAILED` bucket clean (exclude unsupported legacy records).
3. Add regression coverage in backend Elasticsearch tests.

## Changes
1. Backend query helper hardening:
   - File: `ecm-core/src/main/java/com/ecm/core/search/PreviewStatusFilterHelper.java`
   - In unsupported-signal query:
     - keep exact `term` matching for unsupported MIME types
     - add `wildcard` matching with suffix pattern `mimeType + ";*"` for parameterized MIME values
     - add `match_phrase` for unsupported reason phrases (alongside existing `match`)

2. Backend integration test expansion:
   - File: `ecm-core/src/test/java/com/ecm/core/search/SearchAclElasticsearchTest.java`
   - Extended `fullTextSearchFiltersByPreviewStatus`:
     - add legacy `FAILED` doc with `application/octet-stream; charset=binary`
     - verify `FAILED` still excludes it and `UNSUPPORTED` includes it
   - Added new test:
     - `facetedSearchPreviewStatusFacetTreatsOctetStreamWithParametersAsUnsupported`
     - verifies preview-status facets count legacy parameterized MIME into `UNSUPPORTED` rather than `FAILED`

## Impact
- Search filters and facet counts are more stable for legacy/variant MIME values.
- Operator UI (Search/Advanced Search) receives more accurate `FAILED` vs `UNSUPPORTED` counts from backend facets.
