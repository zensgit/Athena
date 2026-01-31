# Phase 1 P1 - Preview Queue UI + Status (Development)

Date: 2026-01-31

## Goal
Expose preview queue controls and status in the document preview UI, and mark preview jobs as processing when enqueued.

## Backend Changes
- `PreviewQueueService` now marks preview status as `PROCESSING` when a job is enqueued, clearing any prior failure reason.
- `NodeDto` now includes preview status/failure metadata so the UI can display it.

## Frontend Changes
- Document preview header shows preview status (READY/FAILED/PROCESSING) with optional failure tooltip.
- Added menu actions to queue preview and force preview rebuild.

## Files Touched
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`
- `ecm-core/src/main/java/com/ecm/core/dto/NodeDto.java`
- `ecm-frontend/src/types/index.ts`
- `ecm-frontend/src/components/preview/DocumentPreview.tsx`
