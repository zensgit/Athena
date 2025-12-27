# UI Verification - Actions Menu + PDF Preview

Date: 2025-12-25
Environment:
- Frontend: http://localhost:5500
- Login: admin/admin

## Steps
1. Logged in via Keycloak and landed at `/browse/root`.
2. Opened Actions menu on a PDF row (J0924032-02.pdf).
3. Clicked View to open PDF preview.
4. Verified preview toolbar and layout behavior.
5. Opened Actions menu on an Excel row (Workbook1.xlsx).

## Results
- PDF Actions menu shows: View, Download, Full Name, Properties, Permissions, Tags, Categories, Share, ML Suggestions, Version History, Copy, Move, Add to Favorites, Delete.
- PDF preview opened successfully; toolbar shows `Fit to height (55%)` and Annotate button.
- Preview canvas fills the dialog without the large bottom whitespace seen previously.
- Excel Actions menu shows both View and Edit Online.

## Notes
- I attempted to navigate to `Documents` via the tree to re-check long-name wrapping, but the tree click did not switch routes in this run. The long-name wrapping behavior was already verified in a prior report; re-check is optional.
