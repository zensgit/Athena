# Delivery Summary: ACL Counts + Role Guards (2026-01-11)

## Scope
- ACL-safe folder tree/stats counts for non-admin users.
- Admin-only access for system diagnostics and search index stats.
- UI gating for System Status entry.

## Changes
- Folder stats/tree counts now filter by READ permissions for non-admin users.
- Search index stats and system status endpoints now require admin role.
- System Status route/menu item now require admin in the UI.
- Added backend security tests and UI menu visibility assertions.

## Tests
- `mvn test -Dtest=FolderServiceStatsTreeAclTest,SearchControllerSecurityTest,SystemStatusControllerSecurityTest`
- `npm test -- --watchAll=false --testPathPattern=MainLayout.menu.test.tsx`
