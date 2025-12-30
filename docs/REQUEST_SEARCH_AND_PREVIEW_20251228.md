# Requirements: Search + Preview Consistency (2025-12-28)

## Scope
Search results behavior, preview actions, and API correctness.

## Observed Issues (From Validation)
- Search results sometimes open folder view when the item is a file.
- `Preview not available` shown for supported PDFs when preview pipeline is healthy.
- API requests returning 404/400 for folders or uploads (e.g., `/folders/roots`, `/documents/upload`).

## Requirements
1. **Search result action correctness**
   - File results must open file preview (not folder view).
   - Folder results must open folder listing.

2. **Preview availability**
   - PDF preview should render when preview pipeline is healthy.
   - If preview fails, provide fallback messaging + Download button.

3. **API path stability**
   - Ensure endpoints use correct base path (avoid `/api/v1/v1`).
   - Folder roots endpoint should return 200 when authenticated.

4. **Error handling**
   - 4xx/5xx responses should show user-facing toast with short action hints.
   - Avoid silent failures that leave empty panels.

## Acceptance Criteria (Draft)
- File results open preview reliably across list and search views.
- PDF preview works without “not available” when services are healthy.
- Upload and folder-root endpoints respond consistently with correct base path.
