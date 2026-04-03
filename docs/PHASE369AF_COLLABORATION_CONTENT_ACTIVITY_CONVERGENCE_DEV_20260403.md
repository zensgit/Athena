# Phase369AF Collaboration Content Activity Convergence

Date: 2026-04-03

## Goal

Complete the activity/notification convergence for site collaboration content so `Discussion`, `Blog`, and `Calendar` all participate consistently in the existing `Activity / Following / Notifications` pipeline.

## Scope Delivered

### Discussion activity integration

`DiscussionService` now emits site-scoped activities for topic and reply lifecycle actions:

- `discussion.topic.created`
- `discussion.topic.updated`
- `discussion.topic.deleted`
- `discussion.reply.created`
- `discussion.reply.updated`
- `discussion.reply.deleted`

These are posted through `ActivityEventListener.postSiteActivity(...)`, so they flow into:

- site activity feeds
- following-scoped activity feeds
- follower notification inboxes

### Calendar delete convergence

`CalendarService.deleteEvent(...)` now emits:

- `calendar.deleted`

This closes the previous gap where calendar only posted `created` and `updated`.

### Frontend activity wording

`siteActivityUtils.ts` now recognizes the new discussion/calendar activity types and renders stable, operator-readable labels and summaries instead of falling back to raw event codes.

## Design Notes

- This phase keeps the existing site-scoped collaboration model intact; no new routing or page surface was required.
- Discussion activities are intentionally posted directly from the service layer, matching the existing site/blog/calendar collaboration pattern rather than introducing a second event-dispatch path.
- The phase favors semantic completion of the collaboration activity contract over new UI scope.
