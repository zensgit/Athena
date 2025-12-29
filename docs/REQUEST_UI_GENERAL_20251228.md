# Requirements: UI Layout + Preview UX (2025-12-28)

## Scope
UI polish items observed during verification and user feedback.

## Layout & Navigation
1. **Left/right spacing**
   - Reduce excessive whitespace between sidebar and main content in wide screens.
   - Keep a balanced gutter (consistent with list/grid cards).
2. **Resizable sidebar**
   - Sidebar width should be adjustable by drag.
   - Persist width per user/session.
3. **Auto-hide behavior**
   - If auto-hide is enabled, clicking a node should collapse the sidebar.
   - Provide clear toggle and persist state.
4. **Breadcrumb clarity**
   - Remove duplicate root levels (avoid `Root > Root > ...`).

## Listing & Naming
1. **Long file names in grid**
   - Allow 2–3 line wrap with ellipsis.
   - Optional: reduce font size when wrapping to 3 lines.
   - Always show full name on hover tooltip.
2. **List view consistency**
   - Ensure filename column does not truncate too aggressively.
   - Provide full-name tooltip for long names.

## Preview Experience
1. **PDF viewer height**
   - Minimize unused bottom whitespace in preview view.
   - Default to “fit to height” on open (or remember last fit mode).
2. **View action consistency**
   - `View` from file browser and search results should open the same preview.
   - For PDFs, avoid “Preview not available” when the PDF preview pipeline is healthy.
3. **Unsupported file types**
   - Show a clear message with next actions (Download / Open externally).

## Permissions & Actions
1. **Edit vs Annotate**
   - PDF editing requirements are tracked in `docs/REQUEST_UI_PDF_EDIT_ANNOTATION_20251228.md`.
   - Ensure action visibility aligns with roles (admin/editor/viewer).

## Acceptance Criteria (Draft)
- Sidebar and main content spacing feel balanced at 1440px+ width.
- Long file names are readable and discoverable.
- PDF preview uses available height with minimal blank area.
- `View` works consistently across list and search views.
