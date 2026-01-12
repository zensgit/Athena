# Design: Upload Dialog Auto-Close (2026-01-11)

## Context
- Upload dialog attempts to auto-close after a successful upload.
- The current auto-close path calls `handleClose` from a timeout callback.
- `handleClose` reads the `uploading` state from a stale render, so it can refuse to close even after uploads finish.

## Decision
- Introduce a reset helper that always closes the dialog and clears selected files.
- Use the reset helper for the success auto-close path.
- Keep the `uploading` guard for user-initiated closes.

## Implementation
- Add a `resetDialog` helper in `UploadDialog`.
- Use it in `handleClose` and the success auto-close timeout.

## Impact
- Upload dialog reliably closes after successful uploads.
- UI smoke E2E flow no longer flakes on a stuck upload modal.
