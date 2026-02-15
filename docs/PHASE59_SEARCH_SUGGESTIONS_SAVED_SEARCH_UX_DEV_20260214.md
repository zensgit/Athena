# Phase 59 - Search: "Did You Mean" + Save Search Convenience (Mocked Coverage)

Date: 2026-02-14

## Goal

Protect two high-frequency search ergonomics features with CLI automation:

- Spellcheck suggestions surface as "Did you mean ..." and are clickable.
- Advanced Search criteria can be saved as a Saved Search with minimal friction.

This phase focuses on a **mocked Playwright E2E** so UI verification stays unblocked when Docker/backends are unavailable.

## UX Summary

### Spellcheck Suggestions

On `Search Results`, when a name query likely contains a typo:

- UI shows "Checking spelling suggestions…" (while loading).
- UI shows "Did you mean <suggestion>" with clickable suggestion buttons.
- Clicking a suggestion re-runs the search with the corrected query.

### Save Search

From `Advanced Search`:

- Fill at least one criterion (e.g., `Name contains`).
- Click `Save Search`
- Enter a name and click `Save` to create a Saved Search.

## API / Data Flow

- Search (simple full-text fast path):
  - `GET /api/v1/search?q=<query>&page=<n>&size=<k>[...]`
- Spellcheck:
  - `GET /api/v1/search/spellcheck?q=<query>&limit=<n>`
- Saved searches:
  - `GET /api/v1/search/saved`
  - `POST /api/v1/search/saved`

## Automation

Added a mocked spec that covers both:

1. Open Search dialog from the top bar search icon.
2. Fill `Name contains` with a misspelling.
3. Save the criteria as a new Saved Search.
4. Run the search and assert "Did you mean" is shown.
5. Click the suggestion and assert results update.

### Code Touchpoints

- Search results page (spellcheck UI):
  - `ecm-frontend/src/pages/SearchResults.tsx`
- Advanced Search dialog (Save Search flow):
  - `ecm-frontend/src/components/search/SearchDialog.tsx`
- Mocked E2E:
  - `ecm-frontend/e2e/search-suggestions-save-search.mock.spec.ts`

