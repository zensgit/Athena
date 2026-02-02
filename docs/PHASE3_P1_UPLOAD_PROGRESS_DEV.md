# Phase 3 (P1) - Upload Progress + Retry UX (Development)

Date: 2026-02-01

## Summary
Improve upload feedback by wiring per-file progress callbacks, surfacing server error messages, and allowing retry of failed uploads without re-uploading successful files.

## Changes
- Forward upload progress from API to UI state for each file.
- Skip already-successful files on subsequent uploads.
- Add per-file retry control for failed uploads.
- Show backend error messages when available.

## Files Touched
- ecm-frontend/src/store/slices/nodeSlice.ts
- ecm-frontend/src/components/dialogs/UploadDialog.tsx
