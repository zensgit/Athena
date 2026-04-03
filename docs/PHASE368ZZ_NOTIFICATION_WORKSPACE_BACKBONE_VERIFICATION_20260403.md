# Phase 368ZZ — Notification Workspace Backbone — Verification

> **Date**: 2026-04-03

---

## 1. Verification Matrix

| # | Claim | Status |
|---|-------|--------|
| 1 | Notification entity with userId, activityId FK, isRead, readAt, createdAt | PASS |
| 2 | routeActivityToFollowers creates notifications for user followers | PASS |
| 3 | routeActivityToFollowers creates notifications for site followers | PASS |
| 4 | routeActivityToFollowers excludes the actor | PASS |
| 5 | routeActivityToFollowers deduplicates across user/site/node | PASS |
| 6 | routeActivityToFollowers does nothing when no followers | PASS |
| 7 | getUnreadCount delegates to repository | PASS |
| 8 | markRead sets read=true and readAt | PASS |
| 9 | markRead rejects other user's notification | PASS |
| 10 | markAllRead delegates to repository | PASS |
| 11 | deleteNotification removes from repository | PASS |
| 12 | getFollowersOf reverse lookup added to FollowingService | PASS |
| 13 | ActivityService routes after postActivity | PASS |
| 14 | GET /notifications returns paginated inbox | PASS |
| 15 | GET /notifications/unread returns unread only | PASS |
| 16 | GET /notifications/unread-count returns count | PASS |
| 17 | PATCH /notifications/{id}/read marks as read | PASS |
| 18 | POST /notifications/mark-all-read bulk marks | PASS |
| 19 | DELETE /notifications/{id} deletes | PASS |
| 20 | Frontend notificationService has 6 methods | PASS |
| 21 | NotificationsPage with unread/all toggle | PASS |
| 22 | NotificationsPage unread badge | PASS |
| 23 | NotificationsPage mark-read + delete per item | PASS |
| 24 | NotificationsPage mark-all-read bulk action | PASS |
| 25 | Migration 050 creates notifications table | PASS |
| 26 | /notifications route + sidebar item | PASS |

## 2. Hot-File Constraint

Zero preview/rendition/search/ops-governance files modified.

## 3. Test Inventory

### NotificationInboxServiceTest.java — 10 tests

```
Routing (5):
  ✓ creates notifications for followers of the activity user
  ✓ creates notifications for followers of the activity site
  ✓ does not notify the actor about their own activity
  ✓ deduplicates across user/site/node followers
  ✓ does nothing when no followers

InboxOps (5):
  ✓ getUnreadCount delegates to repository
  ✓ markRead sets read=true and readAt
  ✓ markRead rejects other user's notification
  ✓ markAllRead delegates to repository
  ✓ deleteNotification removes from repository
```

## 4. Architecture

```
┌─────────────┐    ┌──────────────┐    ┌─────────────────────┐
│ Domain Event │ →  │ ActivityEvent│ →  │   ActivityService    │
│ (12 types)   │    │  Listener    │    │  postActivity()      │
└─────────────┘    └──────────────┘    └──────────┬──────────┘
                                                   │
                                       ┌───────────▼──────────┐
                                       │ NotificationInbox    │
                                       │ Service              │
                                       │ routeToFollowers()   │
                                       └──────────┬──────────┘
                                                   │
                         ┌─────────────────────────┼──────────────────┐
                         │                         │                  │
                ┌────────▼────────┐    ┌──────────▼─────────┐  ┌────▼────────┐
                │ User Followers  │    │ Site Followers     │  │ Node        │
                │ (FollowingSvc)  │    │ (FollowingSvc)     │  │ Followers   │
                └────────┬────────┘    └──────────┬─────────┘  └────┬────────┘
                         │                         │                  │
                         └─────────────────────────┼──────────────────┘
                                                   │
                                       ┌───────────▼──────────┐
                                       │  Notification Table  │
                                       │  (per recipient)     │
                                       └──────────────────────┘
```
