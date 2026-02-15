# Phase 58 - Version History: Paging UX + Major-Only Toggle (Mocked Coverage)

Date: 2026-02-14

## Goal

Ensure the Version History dialog remains usable and regression-protected:

- Version history is fetched via the paged endpoint (no "load everything" behavior).
- Operators can toggle "Major versions only".
- Paging UX is present via an explicit "Load more" control.

This phase focuses on **automation coverage** (mocked Playwright) to keep the feature stable even when backend/Docker is unavailable.

## UX Summary

From the file browser context menu for a document:

1. Open `Version History`
2. View latest versions (newest first)
3. Use:
   - `Load more` to fetch older versions (paged)
   - `Major versions only` to switch to a major-only view

## API / Data Flow

Frontend calls:

- `GET /api/v1/documents/:id/versions/paged?page=<n>&size=<k>&majorOnly=<bool>`

The UI appends additional pages when `Load more` is used.

## Automation

Added a mocked E2E spec that:

- Boots the file browser from `/` (static build safe).
- Opens the document action menu -> `Version History`.
- Asserts:
  - initial page renders
  - `Load more` triggers a second page and appends older versions
  - enabling `Major versions only` switches to major-only results

### Code Touchpoints

- Version history UI:
  - `ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx`
- Mocked E2E:
  - `ecm-frontend/e2e/version-history-paging-major-only.mock.spec.ts`

