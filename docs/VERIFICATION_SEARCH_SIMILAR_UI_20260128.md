# Verification: "More like this" in Search Results (2026-01-28)

## Frontend Lint
- Command: `cd ecm-frontend && npm run lint`
- Result: ✅ Passed

## Manual UI Check
- Run a search and open `Search Results`.
- For a document card, click **More like this**.
- Expected:
  - Results list swaps to similar documents.
  - Info banner shows the source document name.
  - Clicking **Back to results** restores the original search list.
- In document preview (open any result), open the menu and click **More like this**.
- Expected:
  - Navigates to Search Results with similar mode active.

## API Spot Check
- Command:
  - `GET http://localhost:7700/api/v1/search/similar/<documentId>?maxResults=20`
- Result:
  - ✅ HTTP 200 with similar result list (may be empty depending on index).
