# Phase 331 - Alfresco People Site Membership Moderation Queue

## Goal

Move Athena beyond self-service membership requests by adding an admin moderation queue that can review, approve, and reject visible site membership requests from one workspace.

## Scope

- keep the existing self-service lifecycle:
  - create
  - edit
  - withdraw
- add moderation APIs for admins:
  - list visible requests
  - approve
  - reject
- add moderation queue UI to `PeopleDirectoryPage`

## Backend Design

Athena continues using the lightweight preference-backed request model, but now layers moderation on top:

- `GET /api/v1/people/site-membership-requests`
- `POST /api/v1/people/{username}/site-membership-requests/{siteId}/approve`
- `POST /api/v1/people/{username}/site-membership-requests/{siteId}/reject`

Moderation logic persists:

- decision status
- decision actor
- decision timestamp
- optional decision comment

Authorization uses Athena's unified access-denied exception path so unauthorized moderation attempts resolve as standard `403` responses.

## Frontend Design

`PeopleDirectoryPage` now includes an admin-only `Moderation Queue` panel with:

- status filter
- site filter
- requester filter
- paging
- refresh
- inline `Approve` / `Reject` actions for pending requests

When a request is moderated:

- the moderation queue refreshes
- the currently opened profile refreshes if it belongs to the requester

## Files

- `ecm-core/src/main/java/com/ecm/core/controller/PeopleController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerSecurityTest.java`
- `ecm-frontend/src/services/peopleService.ts`
- `ecm-frontend/src/pages/PeopleDirectoryPage.tsx`

## Result

Athena now exceeds the prior self-service-only slice by giving administrators a dedicated moderation queue for collaboration access decisions, which is a more operationally complete surface than raw per-user preference editing.
