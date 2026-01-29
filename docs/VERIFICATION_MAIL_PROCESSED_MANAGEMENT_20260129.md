# Verification: Processed Mail Management (2026-01-29)

## Manual UI Flow
1. Go to `/admin/mail` → Recent Mail Activity.
2. Apply filters:
   - Status = Error
   - Subject contains = some keyword
3. Expected:
   - Processed list updates to filtered results.
4. Select a few rows and click **Delete Selected**.
5. Expected:
   - Records removed from list.
   - Toast shows delete count.

## API Check
- `GET /api/v1/integration/mail/diagnostics?status=ERROR&subject=foo`
- `POST /api/v1/integration/mail/processed/bulk-delete` with `{ ids: [...] }`

## Frontend Lint
- Command: `cd ecm-frontend && npm run lint`
- Result: ✅ Passed
