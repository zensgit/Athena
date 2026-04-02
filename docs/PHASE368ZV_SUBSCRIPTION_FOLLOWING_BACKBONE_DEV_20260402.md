# Phase368ZV Subscription Following Backbone

## Goal

Add a minimal follow/subscription backbone that turns the new sites and activity surfaces into a personalized stream:

- follow and unfollow users, sites, and nodes
- expose current-user follow state and subscription list
- add a personalized `following` activity feed
- make site following available directly from the existing `SitesPage` detail surface

## Backend scope

### Follow domain

- Added `FollowTargetType` with `USER`, `SITE`, and `NODE`.
- Added `FollowSubscription` entity and Liquibase migration `049-create-follow-subscriptions-table.xml`.
- Added `FollowSubscriptionRepository` for current-user follow lookups and idempotent existence checks.

### Following service and controller

- Added `FollowingService` to:
  - validate target existence before follow
  - normalize node ids
  - list current-user subscriptions
  - check follow state
  - follow and unfollow targets
  - derive grouped followed targets for downstream feed queries
- Added `FollowingController` with:
  - `GET /api/v1/followings`
  - `GET /api/v1/followings/check`
  - `POST /api/v1/followings`
  - `DELETE /api/v1/followings/{targetType}/{targetId}`

### Activity feed integration

- Extended `ActivityRepository` with `findFollowingFeed(...)` so a single paged query can merge:
  - followed users
  - followed sites
  - followed nodes
- Extended `ActivityService` with `getFollowingFeed(userId, pageable)`.
- Extended `ActivityController` with `GET /api/v1/activities/following`.

## Frontend scope

### ActivityFeedPage

- Added `Following` as a first-class feed scope.
- Reused the existing feed page rather than creating a separate following view.
- Preserved current query-param continuity, now including `scope=following`.

### SitesPage

- Added follow/unfollow toggle for the currently selected site.
- Loaded site follow state in parallel with member roster, membership requests, and recent site activity.
- Kept the site detail surface as the first operator entry point instead of scattering follow actions across unrelated pages.

### Services

- Added `followingService.ts` for list/check/follow/unfollow calls.
- Extended `activityService.ts` with `getFollowingFeed(...)`.

## Outcome

- Athena now has a real follow graph instead of only generic global/user/site feeds.
- `Activity Feed` can show a personalized scope based on followed entities.
- `SitesPage` now acts as both a site operator surface and a site subscription surface.
