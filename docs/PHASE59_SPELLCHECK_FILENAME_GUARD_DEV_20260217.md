# Phase 59 - Spellcheck Filename Guard (Dev) - 2026-02-17

## Background
- Search spellcheck suggestions are useful for natural-language typos.
- For exact filename/id-style queries (for example `e2e-preview-failure-1770563224443.bin`), spellcheck can produce noisy suggestions and extra API calls.

## Goal
- Skip spellcheck lookup for high-precision filename/id-style queries while keeping typo correction for natural-language queries.

## Scope
- Frontend only (`ecm-frontend`), no backend API contract changes.

## Implementation
1. Added spellcheck guard utility:
   - File: `ecm-frontend/src/utils/searchFallbackUtils.ts`
   - New export: `shouldSkipSpellcheckForQuery(query?: string): boolean`
   - Rules:
     - Skip when query is empty or too short (`<3`)
     - Skip path-like token (`/` or `\`)
     - Skip filename-like token (`name.ext`)
     - Skip high-precision id-like token already recognized by fallback governance
     - Skip long structured digit-heavy token

2. Wired guard into SearchResults spellcheck effect:
   - File: `ecm-frontend/src/pages/SearchResults.tsx`
   - Behavior:
     - For skipped queries, clear spellcheck UI state and do not call `/search/spellcheck`.
     - For normal typo queries, keep existing spellcheck behavior.

3. Added/extended tests:
   - Unit: `ecm-frontend/src/utils/searchFallbackUtils.test.ts`
     - Covers skip/non-skip query classification.
   - Mocked E2E: `ecm-frontend/e2e/search-suggestions-save-search.mock.spec.ts`
     - New scenario verifies filename-like query does not trigger spellcheck request or suggestion banner.

## Impact
- Reduces false-positive spell suggestions for exact filename searches.
- Reduces unnecessary spellcheck API traffic in precision-query scenarios.
- Keeps existing typo-correction UX for normal text queries.
