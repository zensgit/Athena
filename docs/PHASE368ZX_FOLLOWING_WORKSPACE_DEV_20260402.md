# Phase368ZX Following Workspace

## Goal

Turn `Following` from a hidden feed scope into a small but real operator workspace by exposing:

- the current user's followed targets
- grouped subscription visibility
- direct jump-back to the followed user, site, or node
- inline unfollow actions

## Frontend scope

### ActivityFeedPage

- Reused the existing `Following` scope instead of creating a new page.
- Extended the `Following` scope load path to fetch:
  - personalized following feed
  - current subscription list
  in parallel.
- Added a `My Following` panel above the activity cards when `scope === 'following'`.
- Grouped subscriptions by target type:
  - `Sites`
  - `Users`
  - `Nodes`
- Added per-subscription actions:
  - `Open Site`
  - `Open User`
  - `Open Node`
  - `Unfollow`
- Reused the existing target filter so both:
  - activity cards
  - subscription groups
  respond to the same `ALL / SITE / NODE / USER` filter.

### Shared util

- Added `followingUtils.ts` for:
  - target-specific navigation link generation
  - target-kind filtering
  - grouped subscription presentation

## Outcome

- `Following` is now a real working surface rather than only a feed scope selector.
- Users can now see what they follow, jump back to those targets, and prune subscriptions without leaving the activity area.
- The `Sites + Activity + Following` line now has:
  - follow entry points
  - following feed
  - subscription management surface
