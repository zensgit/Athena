# Phase368ZT Site Activity Surface And Event Coverage

## Goal

Extend the existing activity backbone so site activity is both richer on the write side and directly consumable from the site operator surface.

## Backend scope

- Added explicit site-scoped activity helpers in `ActivityEventListener`:
  - `postSiteActivity(...)`
  - `postSiteMemberActivity(...)`
- Emitted site lifecycle activities from `SiteService`:
  - `site.created`
  - `site.updated`
  - `site.archived`
- Emitted site membership lifecycle activities from `SiteMembershipService`:
  - `site.membership.withdrawn`
  - `site.member.added`
  - `site.member.role_changed`
  - `site.member.removed`

## Frontend scope

- `SitesPage` now loads a compact site-scoped feed for the currently selected site and renders a `Recent Activity` panel in the site detail area.
- `SitesPage` adds an `Open Feed` action that opens the full activity screen with the current `siteId`.
- `ActivityFeedPage` now consumes `siteId` and optional `scope` from query params so the page can open directly into a site feed.
- The initial load is deferred until query-param preselection has been applied, preventing a stale first fetch.

## Outcome

- Site activity is no longer just a standalone global feed.
- Operators can inspect recent site changes from the same site detail surface they already use for membership and workspace actions.
- Site lifecycle and membership changes now generate explicit site activity records instead of relying only on generic repository events.
