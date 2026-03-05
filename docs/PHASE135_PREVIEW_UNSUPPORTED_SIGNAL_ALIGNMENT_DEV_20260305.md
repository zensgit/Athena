# Phase 135: Preview Unsupported Signal Alignment

## Date
2026-03-05

## Background
- Preview unsupported filtering already handles standard mime/reason signals.
- Legacy variants still appear in data (`unsupported_mime_type`, keyword-field-only mappings).

## Goals
1. Expand unsupported alias normalization.
2. Improve unsupported signal matching across analyzed/keyword fields.
3. Keep FAILED vs UNSUPPORTED split semantics stable.

## Changes
- `ecm-core/src/main/java/com/ecm/core/search/PreviewStatusFilterHelper.java`
  - added alias:
    - `UNSUPPORTED_MIME_TYPE -> UNSUPPORTED`
  - unsupported signal query now evaluates both field variants:
    - mime: `mimeType`, `mimeType.keyword`
    - reason: `previewFailureReason`, `previewFailureReason.keyword`
  - added wildcard reason hints for legacy tokenized variants:
    - `*unsupported_media_type*`
    - `*preview_not_supported*`
- `ecm-core/src/test/java/com/ecm/core/search/PreviewStatusFilterHelperTest.java`
  - expanded alias normalization coverage.
- `ecm-core/src/test/java/com/ecm/core/search/SearchAclElasticsearchTest.java`
  - added legacy reason sample (`unsupported_media_type`) in preview status filtering scenario.
  - expected unsupported hit count updated accordingly.

## Impact
- UNSUPPORTED filtering/faceting is more robust against legacy data variants and index mapping differences.
