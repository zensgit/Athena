# Phase367ZZT Preview Diagnostics Failure Sample Rendition Convergence

## Goal

Make preview diagnostics failure samples and failure summaries prefer rendition-backed effective preview semantics instead of raw `Document.preview*` fields.

## Problem

`PreviewDiagnosticsController` still built admin failure samples via:

- `PreviewFailureSampleDto.from(document)`

which derived:

- `previewStatus`
- `previewFailureReason`
- `previewFailureCategory`

directly from `Document.previewStatus` and `PreviewFailureClassifier`.

That meant admin diagnostics could lag behind the newer rendition-backed semantics already used in:

- node DTO responses
- ordinary search preview projection
- document preview effective failure handling

## Design

### Controller helper

File: `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

Add `RenditionResourceService` to the controller and introduce:

- `toPreviewFailureSample(Document document)`

This helper:

1. calls `renditionResourceService.summarizeDocument(document)`
2. builds `PreviewFailureSampleDto` from rendition summary when `summary.document() == true`
3. falls back to raw `Document.preview*` only when rendition summary is unavailable

### Call-site convergence

Replace direct `PreviewFailureSampleDto.from(document)` usage in:

- `GET /api/v1/preview/diagnostics/failures`
- `POST /api/v1/preview/diagnostics/failures/queue-by-reason`
- `GET /api/v1/preview/diagnostics/failures/summary`

### DTO factory

`PreviewFailureSampleDto.from(...)` now accepts both:

- `Document`
- `RenditionResourceService.RenditionSummary`

and prefers:

- `previewStatus`
- `previewFailureReason`
- `previewFailureCategory`
- `previewLastUpdated`

from the rendition summary when it represents a real document-backed summary.

## Result

Admin failure diagnostics now align with the same rendition-backed preview semantics used elsewhere. Unsupported generic binaries and similar cases no longer depend on stale raw `Document.previewStatus` values when the rendition summary already has a more accurate effective state.
