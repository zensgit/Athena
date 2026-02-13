# Phase 3 Day 4: OCR-Driven Correspondent Enrichment (Design) — 2026-02-12

## Goal

When OCR successfully extracts text for a document, optionally perform a **best-effort correspondent auto-match**:
- Only if the document has **no correspondent already**.
- Only when explicitly enabled (feature flag).

This reuses the existing correspondent pattern matching logic already used in the upload pipeline.

## Feature Flag

Property:
- `ecm.ocr.enrich.correspondent.enabled` (default: `false`)

Environment variable (relaxed binding example):
- `ECM_OCR_ENRICH_CORRESPONDENT_ENABLED=true`

Default is `false` to avoid unexpected behavior changes in environments that enable OCR but do not want automatic enrichment.

## Behavior

On OCR success (`ocrStatus=READY`):
1. If `document.correspondent == null` and enrichment flag is enabled:
   - Run `CorrespondentService.matchCorrespondent(extractedText)`.
   - If a match is found, assign it: `document.setCorrespondent(match)`.
2. Persist the document (text content + metadata + correspondent).
3. Trigger search re-index (`SearchIndexService.updateDocument(document)`).

### Non-Goals / Safety

- Never overwrite a user-assigned correspondent.
- Best-effort only: matching errors must not fail OCR completion.

## Implementation

File:
- `ecm-core/src/main/java/com/ecm/core/ocr/OcrQueueService.java`

Changes:
- Inject `CorrespondentService`.
- Add `@Value` flag `ecm.ocr.enrich.correspondent.enabled`.
- In `extractAndPersist(...)`, after OCR text is produced:
  - if enabled and document has no correspondent, attempt match and set.

## Testing

Unit test:
- `ecm-core/src/test/java/com/ecm/core/ocr/OcrQueueServiceTest.java`

Added scenario:
- When enrichment enabled and `matchCorrespondent(...)` returns a `Correspondent`, OCR processing assigns it to the document.

