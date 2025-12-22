# Preview Stability Step 3 Design

## Goal
Make preview failures recoverable and consistent across PDF and Office previews.

## Changes
- Standardized error UI with retry and download actions.
- Added user-visible message when server-rendered PDF preview is used.
- Added loading message when switching to server preview.

## Implementation Notes
- `DocumentPreview` renders a shared error block (`Retry` + `Download`) for all preview failures.
- `Retry` increments a reload key to re-fetch content/WOPI URL and reset preview state.
- Server-rendered PDF fallback shows a small banner: "Using server-rendered preview".
