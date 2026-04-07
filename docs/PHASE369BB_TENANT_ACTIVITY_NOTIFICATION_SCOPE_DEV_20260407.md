# Phase 369BB: Tenant Activity/Notification Scope

> **Date**: 2026-04-07

## Goal

Add tenant-aware visibility filtering to the activity and notification surfaces
so workspace-scoped tenants only see activity and inbox content that belongs to
their current tenant workspace. The intended scope is to centralize the
visibility decision in `TenantWorkspaceScopeService` and reuse it from the
activity feed and notification inbox code paths.

## Likely Implementation Scope

### ActivityService

- Apply tenant filtering to the feed methods that currently read directly from
  `ActivityRepository`:
  - `getUserFeed(...)`
  - `getSiteFeed(...)`
  - `getFollowingFeed(...)`
  - `getGlobalFeed(...)`
  - `getNodeFeed(...)`
- Reuse `TenantWorkspaceScopeService.resolveCurrentTenantRootPath()` and
  `TenantWorkspaceScopeService.isActivityVisible(...)` instead of duplicating
  workspace-path checks.
- Preserve the existing pagination behavior after filtering so callers still
  receive stable `Page<Activity>` results.

### NotificationInboxService

- Gate notification routing with tenant visibility before creating inbox rows.
- Filter inbox reads so only notifications whose backing activity is visible in
  the current tenant workspace are returned.
- Keep unread-count and read/write operations tenant-aware:
  - `getUnreadCount()`
  - `markAllRead()`
  - `markRead(...)`
  - `deleteNotification(...)`
- Continue using `TenantWorkspaceScopeService` as the single visibility source
  for notification filtering.

### TenantWorkspaceScopeService

- Treat this service as the shared visibility boundary for activity and
  notification data-plane checks.
- No additional scope-service behavior is expected unless a test reveals a
  missing visibility edge case.

## Test Surface

Focused backend coverage should center on the services and the HTTP layer that
exposes them:

- `ActivityServiceTest`
- `NotificationInboxServiceTest`
- `ActivityControllerTest`
- `ActivityEventListenerTest` if event-to-activity mapping needs confirmation
  for tenant-scoped visibility inputs

## Scope Boundaries

- No frontend changes are expected in this phase.
- No schema or Liquibase changes are expected.
- No changes to search, preview, rendition, ops recovery, or unrelated tenant
  admin flows are part of this scope.
- Keep the implementation limited to the activity/notification visibility path
  and the tests that exercise it.

