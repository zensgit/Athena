# Phase 87: Search Exact-Match Mode Visibility

## Date
2026-02-21

## Background
- Filename-like precision queries intentionally skip spellcheck.
- UI did not explicitly explain why suggestions were absent, creating potential operator confusion.

## Goal
1. Surface explicit “exact filename mode” visibility in search results.
2. Keep spellcheck behavior unchanged while improving explainability.

## Changes

### 1) Precision query classifier
- File: `ecm-frontend/src/utils/searchFallbackUtils.ts`
- Added exported helper:
  - `isPrecisionFilenameLikeQuery(query)`
- Uses normalized token form (quotes/punctuation stripped) and filename/structured-ID heuristics.
- `shouldSkipSpellcheckForQuery` now reuses this classifier.

### 2) UI indicator
- File: `ecm-frontend/src/pages/SearchResults.tsx`
- Added info alert shown when precision mode is active:
  - title: `Exact filename mode active`
  - detail: `Spelling suggestions are skipped for precise filename-like queries.`
- Added test id:
  - `search-exact-match-mode-alert`

### 3) Regression coverage
- Files:
  - `ecm-frontend/src/utils/searchFallbackUtils.test.ts`
  - `ecm-frontend/e2e/search-suggestions-save-search.mock.spec.ts`
- Added cases for:
  - quoted/punctuated filename queries
  - precision-mode classifier behavior
  - UI alert visible for filename-like query and hidden for natural misspelling

## Impact
- No search API changes.
- Improved UX observability for exact-match spellcheck suppression.
