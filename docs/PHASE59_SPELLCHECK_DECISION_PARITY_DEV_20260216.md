# Phase 59 - Spellcheck Decision Parity (Reference-Informed) - Development

## Date
2026-02-16

## Reference Comparison
- Source reviewed:
  - `reference-projects/alfresco-community-repo/repository/src/main/java/org/alfresco/repo/search/impl/solr/SpellCheckDecisionManager.java`
- Borrowed decision intent:
  - when current query returns zero hits, spellcheck should act as a stronger “search instead” decision.
  - when current query already has hits, spellcheck remains a softer “did you mean” suggestion.

## Athena Implementation
- Updated `ecm-frontend/src/pages/SearchResults.tsx`
  - `displayTotal > 0` -> label: `Did you mean`
  - `displayTotal === 0` -> label: `Search instead for`
  - click behavior is unchanged (still reruns search with selected suggestion).

## Updated E2E
- `ecm-frontend/e2e/search-suggestions-save-search.mock.spec.ts`
- `ecm-frontend/e2e/search-suggestions-save-search.integration.spec.ts`
- `ecm-frontend/e2e/p1-smoke.spec.ts`

All updated tests now accept either spellcheck header:
- `Did you mean`
- `Search instead for`
