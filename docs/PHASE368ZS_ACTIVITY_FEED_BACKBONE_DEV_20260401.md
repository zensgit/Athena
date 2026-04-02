# Phase 368ZS — Activity Feed Backbone

> **Scope**: Activity entity, event-driven posting, feed endpoints, frontend page
> **Date**: 2026-04-01

---

## 1. What Was Built

### Backend

**Activity entity** — `activities` table with: id, activityType, userId, siteId, nodeId, nodeName, summary (JSONB), postedAt.

**ActivityRepository** — paged queries by user, site, node, global; cleanup older than cutoff.

**ActivityService** — `postActivity()`, `getUserFeed()`, `getSiteFeed()`, `getGlobalFeed()`, `getNodeFeed()`, `cleanupOldActivities()` (daily @Scheduled, 90-day retention).

**ActivityEventListener** — `@Async @EventListener` for 8 event types:
- node.created, node.updated, node.deleted, node.moved
- node.locked, node.unlocked
- version.created
- comment.added

Auto-posts activities from existing domain events — zero changes to NodeService, VersionService, or CommentService.

**ActivityController** — 5 endpoints:

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/activities` | Global feed (paginated) |
| GET | `/api/activities/users/{userId}` | User feed |
| GET | `/api/activities/sites/{siteId}` | Site feed |
| GET | `/api/activities/nodes/{nodeId}` | Node feed |
| POST | `/api/activities` | Manual post (admin/internal) |

**DB Migration 048** — `activities` table with 5 indexes.

### Frontend

**activityService.ts** — 4 feed methods (global, user, site, node).

**ActivityFeedPage.tsx** — scope selector (My Activity / Global / By Site), paginated card list with:
- Activity type chip (color-coded: created=green, deleted=red, locked=warning)
- User + node name
- Timestamp
- Site filter input

**Routing** — `/activities` route in App.tsx, "Activity Feed" sidebar menu item.

## 2. Files Created

| File | Purpose |
|------|---------|
| `entity/Activity.java` | Activity entity |
| `repository/ActivityRepository.java` | Paged queries + cleanup |
| `service/ActivityService.java` | Feed logic + scheduled cleanup |
| `service/ActivityEventListener.java` | Auto-post from domain events |
| `controller/ActivityController.java` | REST endpoints + DTOs |
| `db/changelog/changes/048-create-activities-table.xml` | Migration |
| `services/activityService.ts` | Frontend API service |
| `pages/ActivityFeedPage.tsx` | Activity feed page |
| `test/service/ActivityServiceTest.java` | 6 service tests |
| `test/controller/ActivityControllerTest.java` | 4 controller tests |

## 3. Files Modified

| File | Change |
|------|--------|
| `App.tsx` | +ActivityFeedPage import, +`/activities` route |
| `MainLayout.tsx` | +"Activity Feed" sidebar menu item |
| `db/changelog/db.changelog-master.xml` | +048 |

## 4. NOT Modified

All preview/rendition/search/ops-governance files untouched. No changes to NodeService, VersionService, or CommentService — the event listener consumes existing events.
