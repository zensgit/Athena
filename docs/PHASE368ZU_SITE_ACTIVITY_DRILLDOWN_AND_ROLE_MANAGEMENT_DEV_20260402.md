# Phase368ZU Site Activity Drill-Down And Role Management

## Goal

Turn the first-pass sites and activity surfaces into a more actionable operator workflow by adding:

- member role management directly in the site detail surface
- readable activity labels and summaries instead of raw activity type strings
- site and node drill-down actions from activity cards
- activity page filter and query-param state continuity

## Frontend scope

### SitesPage

- Added role editing for site members using the existing `siteService.updateMemberRole(...)` API.
- Added query-param site preselection through `?siteId=...` so activity-driven navigation can land on a selected site.
- Synced selected site back into the URL for stable reload/share behavior.
- Refreshed selected-site detail after approve/reject/withdraw flows so requests, roster, and recent activity stay coherent.
- Upgraded the `Recent Activity` panel to use shared activity presentation helpers and node drill-down actions.

### ActivityFeedPage

- Added activity type filter.
- Added query-param support for `scope`, `siteId`, and `type`, with URL sync after state changes.
- Rendered readable activity labels and summaries via shared helpers.
- Added `Open Site` and `Open Node` drill-down actions from feed cards.

### Shared util

- Added `siteActivityUtils.ts` for:
  - activity label formatting
  - activity summary formatting
  - filter matching
  - drill-down target generation

## Outcome

- Site activity is now operator-readable rather than exposing raw event keys.
- Activity cards now act as navigation surfaces instead of dead-end status rows.
- Site role changes are no longer backend-only capability; they are now directly manageable from the site detail panel.
