# Phase 369BB: Tenant Activity/Notification Scope Verification

> **Date**: 2026-04-07

## Verification Plan

Use the commands below to validate the tenant-aware activity and notification
scope work after implementation.

### 1. Targeted backend tests

```bash
cd ecm-core
mvn test -Dtest=ActivityServiceTest,NotificationInboxServiceTest,ActivityControllerTest,ActivityEventListenerTest
```

### 2. Broader backend regression check

```bash
cd ecm-core
mvn test
```

### 3. Build/package sanity check

```bash
cd ecm-core
mvn clean package
```

## What To Confirm

- Activity feed methods only return items visible in the current tenant
  workspace when `TenantWorkspaceScopeService` reports a scoped tenant.
- Notification routing skips out-of-scope activities instead of creating inbox
  rows.
- Unread counts, mark-read, and mark-all-read behavior stay aligned with the
  same tenant visibility rules.
- `ActivityController` still serializes the activity feed contract expected by
  callers.
- `ActivityEventListener` still resolves activity inputs that feed the scoped
  visibility path when relevant.

## Expected Test Coverage

- `ActivityServiceTest`
  - global, user, site, following, and node feed visibility filtering
  - pagination behavior after filtering
- `NotificationInboxServiceTest`
  - routing skip for out-of-scope activity
  - inbox filtering
  - unread count and mark-read / mark-all-read scope checks
- `ActivityControllerTest`
  - feed endpoint contract remains intact
- `ActivityEventListenerTest`
  - only if event-to-site resolution affects scoped activity visibility inputs

## Notes

- The implementation should keep `TenantWorkspaceScopeService` as the single
  tenant-visibility decision point for these flows.
- If any test fails because of shared fixture assumptions, update the fixture to
  model the current tenant workspace rather than weakening the visibility rule.

