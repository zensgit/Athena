# Phase 368ZZ — Notification Workspace Backbone

> **Scope**: Notification inbox entity, follower routing, inbox CRUD, frontend page
> **Date**: 2026-04-03

---

## 1. What Was Built

### Backend

**Notification entity** — `notifications` table: id, userId, activityId FK, isRead, readAt, createdAt.

**NotificationRepository** — inbox queries (all/unread by user, unread count, mark-all-read bulk update, cleanup).

**NotificationInboxService** — the core routing + inbox layer:

| Method | Description |
|--------|-------------|
| `routeActivityToFollowers(activity)` | Resolves followers of activity's user/site/node, creates Notification per recipient (excluding actor) |
| `getInbox(pageable)` | Current user's full inbox |
| `getUnread(pageable)` | Current user's unread only |
| `getUnreadCount()` | Count of unread notifications |
| `markRead(id)` | Mark single as read (owner check) |
| `markAllRead()` | Bulk mark all as read |
| `deleteNotification(id)` | Delete single (owner check) |
| `cleanupOldNotifications()` | @Scheduled daily 3:30 AM, 90-day retention |

**FollowingService** — added `getFollowersOf(targetType, targetId)` reverse lookup.

**FollowSubscriptionRepository** — added `findByTargetTypeAndTargetId()`.

**ActivityService** — wired `NotificationInboxService.routeActivityToFollowers()` after every `postActivity()` call.

**NotificationController** — 6 endpoints:

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/notifications` | Full inbox (paginated) |
| GET | `/api/notifications/unread` | Unread only |
| GET | `/api/notifications/unread-count` | `{count: N}` |
| PATCH | `/api/notifications/{id}/read` | Mark as read |
| POST | `/api/notifications/mark-all-read` | Bulk mark read |
| DELETE | `/api/notifications/{id}` | Delete notification |

**DB Migration 050** — `notifications` table with 3 indexes.

### Frontend

**notificationService.ts** — 6 methods: getInbox, getUnread, getUnreadCount, markRead, markAllRead, deleteNotification.

**NotificationsPage.tsx** — inbox page with:
- Unread/All toggle buttons
- Unread count badge in header
- "Mark All Read" bulk action
- Per-notification: activity type chip (color-coded), actor + node + site, timestamp, mark-read + delete buttons
- Unread highlight (left blue border + hover background)
- Pagination

**Routing** — `/notifications` route + sidebar menu item.

### Notification Flow

```
Domain Event (e.g. NodeCreatedEvent)
  → ActivityEventListener.onNodeCreated()
  → ActivityService.postActivity()
    → activityRepository.save(activity)
    → NotificationInboxService.routeActivityToFollowers(activity)
      → FollowingService.getFollowersOf(USER, userId)
      → FollowingService.getFollowersOf(SITE, siteId)
      → FollowingService.getFollowersOf(NODE, nodeId)
      → deduplicate, exclude actor
      → Notification saved per recipient
```

## 2. Files Created

| File | Purpose |
|------|---------|
| `entity/Notification.java` | Inbox entry entity |
| `repository/NotificationRepository.java` | Inbox queries |
| `service/NotificationInboxService.java` | Routing + inbox CRUD |
| `controller/NotificationController.java` | REST endpoints + DTO |
| `db/changelog/changes/050-create-notifications-table.xml` | Migration |
| `services/notificationService.ts` | Frontend API service |
| `pages/NotificationsPage.tsx` | Inbox page |
| `test/service/NotificationInboxServiceTest.java` | 10 focused tests |

## 3. Files Modified

| File | Change |
|------|--------|
| `repository/FollowSubscriptionRepository.java` | +`findByTargetTypeAndTargetId()` |
| `service/FollowingService.java` | +`getFollowersOf()` reverse lookup |
| `service/ActivityService.java` | +`@Lazy NotificationInboxService`; routes after save |
| `App.tsx` | +NotificationsPage import + `/notifications` route |
| `MainLayout.tsx` | +"Notifications" sidebar menu item |
| `db/changelog/db.changelog-master.xml` | +050 |

## 4. NOT Modified

All preview/rendition/search/ops-governance files untouched.
