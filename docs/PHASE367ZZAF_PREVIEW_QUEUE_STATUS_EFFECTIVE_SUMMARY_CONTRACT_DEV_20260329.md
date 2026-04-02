# Phase367ZZAF Preview Queue Status Effective Summary Contract

## Goal

Turn `PreviewQueueService.PreviewQueueStatus` from a thin queue decision DTO into an effective preview summary contract that can be reused across queue service, controller fallback, and frontend consumers.

## Problem

Before this phase, `PreviewQueueStatus` only carried:

- `documentId`
- `previewStatus`
- `queued`
- `attempts`
- `nextAttemptAt`
- `message`

That left two concrete gaps:

1. real enqueue responses could still return the pre-queue status instead of the post-queue effective summary
2. controller consumers such as `DocumentController` still had to re-query rendition summary or otherwise lose `previewFailureReason / previewFailureCategory / previewLastUpdated`

This made queue semantics inconsistent with the richer rendition-backed preview contract already used elsewhere.

## Design

### Extend PreviewQueueStatus without breaking existing call sites

File:

- `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`

Extend `PreviewQueueStatus` with:

- `previewFailureReason`
- `previewFailureCategory`
- `previewLastUpdated`

Keep the original 6-argument constructor as a compatibility constructor delegating to the new full record shape. This avoids forcing broad mock/test churn in other slices.

### Centralize queue status construction

Add `buildQueueStatus(...)` so queue service status payloads always derive from the same effective semantics:

- `resolveEffectivePreviewStatusForEvaluation(document)`
- `resolveEffectiveFailureReasonForEvaluation(document)`
- `resolveEffectiveFailureCategoryForEvaluation(document)`
- `document.getPreviewLastUpdated()`

Behavior:

- declined / dry-run paths return the current effective summary
- actual queue paths return the post-queue effective summary
- already queued paths return the current document-backed queue summary

### Make enqueue return post-queue PROCESSING semantics

In both memory and redis queue backends:

- call `markProcessing(document)` first
- then build the queue status from the updated document

This fixes the previous drift where `enqueue()` returned the old failed/unsupported state even though the document had already been moved into `PROCESSING`.

### Let DocumentController consume queue contract as fallback

File:

- `ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java`

Update:

- `queuePreview(...)`
- `repairPreview(...)`

so they still prefer `RenditionResourceService.summarizeDocument(...)`, but if rendition summary is unavailable they now fall back to:

- `queueStatus.previewFailureReason()`
- `queueStatus.previewFailureCategory()`
- `queueStatus.previewLastUpdated()`

That makes the richer queue contract immediately visible on the existing frontend `nodeService.queuePreview(...)` path.

## Result

`PreviewQueueStatus` is no longer just a queue decision flag. It now carries effective preview summary semantics and can serve as a reliable fallback contract for queue-related operator flows even when an explicit rendition summary is not available.
