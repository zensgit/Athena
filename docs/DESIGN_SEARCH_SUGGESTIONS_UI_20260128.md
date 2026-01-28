# Design: Search Suggestions in Advanced Search (2026-01-28)

## Goals
- Surface backend autocomplete suggestions in the Advanced Search dialog.
- Keep UX responsive with light debounce and minimal API calls.

## Backend Notes
- Reuse existing endpoint: `GET /api/v1/search/suggestions?prefix=<text>&limit=<n>`.
- No backend changes required.

## Frontend Design
- Add suggestion state to `SearchDialog`.
- Fetch suggestions when `name` input length >= 2 and dialog is open.
- Use `Autocomplete` (freeSolo) for "Name contains" field to display suggestions.
- Show a small loading spinner while suggestions load.

## Trade-offs
- Debounce (250ms) reduces chatter but can delay fast typing slightly.
- Suggestions are opportunistic; failures are silent to avoid blocking search.

## Files
- `ecm-frontend/src/components/search/SearchDialog.tsx`
