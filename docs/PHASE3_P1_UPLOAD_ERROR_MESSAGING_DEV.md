# Phase 3 (P1) - Upload Error Messaging (Development)

Date: 2026-02-01

## Summary
Surface backend pipeline errors (virus/size/processing failures) in the upload dialog so users see the actual cause instead of a generic failure message.

## Changes
- Parse upload error responses for `errors` map and render human-readable messages per file.

## Files Touched
- ecm-frontend/src/components/dialogs/UploadDialog.tsx
