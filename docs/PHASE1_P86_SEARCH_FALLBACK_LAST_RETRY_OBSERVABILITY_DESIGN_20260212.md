# Phase 1 P86 - Search Fallback Last Retry Observability (Design) - 2026-02-12

## Background
- Search fallback banner already shows:
  - fallback notice,
  - hide action,
  - auto-retry progress and next retry delay.
- It does not show when the last retry was actually fired, which weakens troubleshooting during indexing lag.

## Scope
- Frontend-only observability enhancement.
- Applies to both:
  - `SearchResults`
  - `AdvancedSearchPage`

## Changes
1. Track fallback last retry time
- Files:
  - `ecm-frontend/src/pages/SearchResults.tsx`
  - `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- Added state:
  - `fallbackLastRetryAt: Date | null`
- Set timestamp when retry is triggered:
  - manual `Retry` click
  - scheduled auto-retry timer callback

2. Reset retry timestamp when fallback is dismissed/cleared
- Files:
  - `ecm-frontend/src/pages/SearchResults.tsx`
  - `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- Clear `fallbackLastRetryAt` when:
  - fallback is hidden by user (`Hide previous results`)
  - fallback is no longer active for current criteria

3. Expose timestamp in fallback banner
- Files:
  - `ecm-frontend/src/pages/SearchResults.tsx`
  - `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- Banner now appends:
  - `Last retry: <localized datetime>.`

4. Extend fallback governance E2E assertions
- Files:
  - `ecm-frontend/e2e/search-fallback-governance.spec.ts`
  - `ecm-frontend/e2e/advanced-search-fallback-governance.spec.ts`
- Added checks to ensure fallback alert contains `Last retry:` after retry loop starts.

## Risk and Mitigation
- Risk: localized timestamp formatting may vary by runtime locale.
  - Mitigation: tests assert stable prefix (`Last retry:`), not exact datetime text.

## Rollback
- Remove `fallbackLastRetryAt` state and banner text in both pages.
- Revert the two E2E assertions for `Last retry:`.
