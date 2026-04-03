# Phase 369AA: Notification Entry Points And Triage Surface

## Summary

This phase turns the notification inbox from a standalone page into a navigable collaboration surface.

## Delivered

- Added a top-bar notification bell with unread badge in the shared layout.
- Added live unread-count refresh triggers for navigation, window focus, and inbox mutations.
- Added notification drill-down actions to related site, node, and activity feed surfaces.
- Added explicit notification entry points from the activity feed and selected-site activity panel.

## Frontend Changes

### Shared Layout

- `ecm-frontend/src/components/layout/MainLayout.tsx`
  - Added a notification bell icon in the app bar.
  - Added unread badge rendering for both the bell and the account-menu `Notifications` item.
  - Added unread-count refresh logic via `notificationService.getUnreadCount()`.
  - Added lightweight event-driven refresh using the `athena:notifications-changed` window event.

- `ecm-frontend/src/components/layout/MainLayout.menu.test.tsx`
  - Extended the layout test to verify the bell entry is visible for both admin and viewer roles.

### Notifications Inbox

- `ecm-frontend/src/pages/NotificationsPage.tsx`
  - Reworked inbox cards to use shared activity label/summary formatting.
  - Added triage actions per notification:
    - `Open Site`
    - `Open Node`
    - `Open Activity`
  - Marks notifications as read before navigation when opening a drill-down target.
  - Emits `athena:notifications-changed` after read, mark-all-read, and delete actions so the global badge stays in sync.

- `ecm-frontend/src/utils/notificationUtils.ts`
  - Added notification-to-activity adaptation helpers.
  - Reused `siteActivityUtils` formatting/link rules to avoid duplicating label and summary logic.

- `ecm-frontend/src/utils/notificationUtils.test.ts`
  - Added focused tests for formatting and link-target generation.

### Additional Entry Points

- `ecm-frontend/src/pages/ActivityFeedPage.tsx`
  - Added a direct `Notifications` entry-point button in the activity workspace header.

- `ecm-frontend/src/pages/SitesPage.tsx`
  - Added a direct `Notifications` entry-point button in the selected-site `Recent Activity` panel.

## Notes

- This phase intentionally keeps notification filtering server-side unchanged.
- Site-specific or node-specific notification views can be added later on top of the current inbox and drill-down foundation.
