# E2E Verification: PDF Search Result Download (2025-12-26)

## Scope
- Validate the updated PDF smoke test path:
  - Upload PDF
  - Search for PDF
  - Verify version history & preview
  - Download from search results

## Test Run
- Command: `npx playwright test e2e/ui-smoke.spec.ts -g "PDF upload"`
- Result: **PASS** (1 test, 13.1s)

## Notes
- The test now clicks **Download** from the search result card and waits for the `/api/v1/nodes/{id}/content` response.
- Upload dialog auto-close wait was replaced by a short delay to avoid flakiness; the subsequent row polling confirms upload completion.

