# Phase 1 P70 - Search Autocomplete Suggestions (Design) (2026-02-10)

## Goal
Make the `SearchResults` "Quick search by name..." field behave like a modern ECM search bar:

- Show name-based suggestions as the user types.
- Keep the existing debounced quick-search behavior (typing still triggers a search).
- Keep the UX safe/minimal: no new backend contracts, no breaking changes.

This is aligned with Alfresco-style auto-suggest search (`SuggesterService`) and Paperless "saved views" ergonomics (fast iteration on search inputs).

## Scope
- Frontend: `SearchResults` quick-search input becomes an MUI `Autocomplete` (freeSolo).
- Backend: no changes (reuse existing endpoint).

## API / Contract
Reuse existing Athena endpoint:

- `GET /api/v1/search/suggestions?prefix=<prefix>&limit=10`

Current implementation uses Elasticsearch `name` prefix search and returns distinct document names.

## UX Details
- Debounce:
  - Suggestions fetch is debounced (`250ms`) to avoid spamming API calls.
  - Existing quick-search debounce (`400ms`) remains unchanged.
- Query length guard:
  - Suggestions are only requested when `prefix.length >= 2`.
- Selection behavior:
  - Clicking a suggestion sets the input value; existing quick-search debounce then runs the search.

## Files
- Frontend:
  - `ecm-frontend/src/pages/SearchResults.tsx`
  - `ecm-frontend/src/services/nodeService.ts`
- E2E:
  - `ecm-frontend/e2e/search-autocomplete-suggestions.spec.ts`

