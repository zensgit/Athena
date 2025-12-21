# Search View Regression Report - 2025-12-20

## Scope
Validate that clicking "View" from search results opens the preview dialog (does not navigate into folder view).

## Test Command
- `npx playwright test e2e/search-view.spec.ts`

## Results
- ✅ 1/1 passed

## Notes
- Test creates a dedicated folder, uploads a TXT file, forces index, and validates preview dialog presence.
- Confirms URL remains on `/search-results` after clicking View.
