# Phase 330 - Alfresco People Site Membership Request Moderation Queue

## Goal

Extend Athena's People Directory with a compact admin moderation queue for site membership requests stored in the existing lightweight preference-backed model.

## Scope

- add API support to list visible site membership requests across users
- add approve/reject actions that persist status and decision metadata
- add a compact moderation queue surface in `PeopleDirectoryPage`
- keep the implementation minimal and bounded to People-related files

## Backend Design

New moderation endpoints:

- `GET /api/v1/people/site-membership-requests`
- `POST /api/v1/people/{username}/site-membership-requests/{siteId}/approve`
- `POST /api/v1/people/{username}/site-membership-requests/{siteId}/reject`

Rules:

- moderation queue access is admin-only
- list endpoint supports simple filters:
  - `siteId`
  - `status`
  - `requester`
- approved/rejected requests preserve the original request payload and add:
  - `decisionBy`
  - `decisionAt`
  - `decisionComment`
- request list items returned from the per-user endpoint include `username` for moderation/deeplink use

## Frontend Design

`PeopleDirectoryPage` now includes a compact moderation queue for admins with:

- site/requester/status filters
- approve/reject actions for pending items
- pagination and refresh
- inline decision metadata display

The existing self-service membership-request panel remains below the moderation queue.

## Files

- `ecm-core/src/main/java/com/ecm/core/controller/PeopleController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerSecurityTest.java`
- `ecm-frontend/src/services/peopleService.ts`
- `ecm-frontend/src/pages/PeopleDirectoryPage.tsx`

## Result

Athena now goes beyond the self-service membership-request slice by giving admins a bounded moderation queue directly inside the People Directory.
