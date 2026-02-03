# Phase 31 Preview Diagnostics Details (2026-02-03)

## Goal
Expose clearer preview failure context and queue retry details inside the preview dialog.

## Changes
- Preview failure alert now includes the failure reason when available.
- Added preview queue metadata (attempts, next retry time) and server preview errors beneath the alert.

## Files
- `ecm-frontend/src/components/preview/DocumentPreview.tsx`
