# Requirements: Sharing & Collaboration (2025-12-28)

## Scope
Share links, collaboration flows, and permissions for external access.

## Requirements
1. **Share link management**
   - Create, revoke, and list share links per document.
   - Support expiry date/time and max access count (optional).

2. **Permission levels**
   - Read-only vs edit/annotate (if supported).
   - Clear UI indicator of permissions when opening shared links.

3. **Access logging**
   - Track share link usage (who, when, IP if available).
   - Include share access events in audit export.

4. **Share UX**
   - One-click copy link.
   - Warn when sharing sensitive docs (optional).
   - Show current share status in file properties panel.

## Acceptance Criteria (Draft)
- Users can revoke share links and see updated status immediately.
- Shared access is logged and retrievable.
- Permission level is clear to external viewers.
