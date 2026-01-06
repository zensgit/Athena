# Verification: WOPI Sample Cleanup (2026-01-06)

## Commands
- `GET /api/v1/search?q=verify-wopi-sample.xlsx`
- `DELETE /api/v1/nodes/{id}` for each returned document id.
- `GET /api/v1/nodes/{id}` to confirm removal.

## Results
- One sample document removed (204).
- Two documents already missing (404 on delete and fetch), indicating prior removal.
- Ran `POST /api/v1/search/index/rebuild` (documentsIndexed: 961); search no longer returns `verify-wopi-sample.xlsx`.
