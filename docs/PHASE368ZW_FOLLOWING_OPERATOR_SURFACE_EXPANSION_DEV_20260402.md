# Phase368ZW Following Operator Surface Expansion

## Goal

Expand the new follow/subscription backbone beyond the initial site detail entry point so following becomes a real cross-surface operator capability:

- follow and unfollow users directly from the people workbench
- follow and unfollow nodes directly from file browser surfaces
- filter the activity feed by followed target kind

## Frontend scope

### PeopleDirectoryPage

- Added follow state for the selected profile using the existing `Following` backend contract.
- Loaded user follow state inside the same profile `Promise.all(...)` used for groups, favorites, comments, activities, and sites, so this did not introduce a second profile-fetch waterfall.
- Added `Follow user / Following user` action to the profile header.
- Suppressed self-following in the UI by hiding the action for the current user.

### FileList

- Added `followedNodeIds` state backed by `followingService.list()`.
- Added node follow/unfollow operator actions in:
  - list view icon column
  - grid card quick actions
  - context menu
- Reused the same `toggleNodeFollow(...)` path across all three entry points.

### ActivityFeedPage

- Added target-kind filtering with:
  - `All Targets`
  - `Sites`
  - `Nodes`
  - `Users`
- Extended query-param continuity to preserve `target=...` alongside `scope/siteId/type`.

### Shared util

- Extended `siteActivityUtils.ts` with target classification helpers so `ActivityFeedPage` can filter by:
  - site-target activity
  - node-target activity
  - user-target activity

## Outcome

- Following is no longer only a site-detail capability.
- Athena now supports a coherent follow workflow across:
  - people
  - browse/file list
  - activity feed
- The activity feed can now act as a personalized surface and a filtered operator surface at the same time.
