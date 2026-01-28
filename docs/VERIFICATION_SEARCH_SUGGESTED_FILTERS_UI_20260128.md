# Verification: Suggested Filters in Search Results (2026-01-28)

## Frontend Lint
- Command: `cd ecm-frontend && npm run lint`
- Result: ✅ Passed

## Manual UI Check
- Run a search with a non-empty query.
- Expected:
  - Suggested filter chips appear above results.
  - Clicking a chip refines the results and adds corresponding facet chips.
  - Suggestions are hidden during "More like this" mode.

## API Spot Check
- Command:
  - `GET http://localhost:7700/api/v1/search/filters/suggested?q=invoice`
- Result:
  - ✅ HTTP 200 with suggested filter list (may vary by index).
