# Requirements: PDF View/Edit/Annotation (2025-12-28)

## Background
Current UI behavior for PDF items:
- Context menu shows `View` and `Annotate`.
- Viewer banner indicates read-only with annotations available.
- No `Edit Online` option for PDFs.

## Requested/Expected Behavior
1. **Edit Online visibility**
   - Clarify whether PDFs should expose `Edit Online` in the context menu.
   - If not supported, ensure messaging is explicit (e.g., view + annotate only).

2. **Annotation vs Edit semantics**
   - Define whether `Annotate` is the only allowed modification path for PDFs.
   - If edits are allowed, specify where changes are stored (new version vs overwrite).

3. **Viewer UX**
   - Keep View/Annotate entry points consistent across search results and file browser.
   - Provide clear indicator of annotation mode and how to exit.

4. **Permissions**
   - Define roles allowed to annotate vs edit PDFs (admin/editor/viewer).

5. **Audit/Versioning**
   - If annotation writes back, ensure version history and audit logs capture:
     - who annotated
     - when
     - action type (annotation vs edit)

## Open Questions
- Should PDF editing use Collabora (Edit Online) or be annotation-only?
- If editing is enabled, is the output stored as a new version or a separate annotation layer?
- Should annotations be shared across users or stored per-user?

## Acceptance Criteria (Draft)
- PDF context menu clearly reflects supported actions.
- View + Annotate flows are stable in both list and search views.
- If editing is enabled, it is available for permitted roles and writes to version history.
