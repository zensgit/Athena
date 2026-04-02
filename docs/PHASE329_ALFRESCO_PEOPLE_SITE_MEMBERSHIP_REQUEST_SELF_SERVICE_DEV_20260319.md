# Phase 329 - Alfresco People Site Membership Request Self Service

## Goal

Extend Athena's People Directory from read/withdraw-only membership requests to full self-service request creation and editing, matching the next useful Alfresco collaboration action slice.

## Scope

- add API support for:
  - create membership request
  - update membership request
  - existing list and withdraw stay unchanged
- wire request management into `PeopleDirectoryPage`
- keep the implementation lightweight by storing the request payload inside `preferences.siteMembershipRequests`

## Backend Design

New endpoints:

- `POST /api/v1/people/{username}/site-membership-requests`
- `PUT /api/v1/people/{username}/site-membership-requests/{siteId}`

Rules:

- only current user or admin-managed profile can write
- `siteId` is required
- create rejects duplicates
- update requires path/body `siteId` consistency
- `requestedAt` is preserved on edit when already present
- `status` defaults to `PENDING`

## Frontend Design

`PeopleDirectoryPage` now includes:

- `New request` action
- per-request `Edit` action
- existing `Withdraw` action
- a shared dialog form for:
  - site id
  - site title
  - role
  - message

This keeps all membership-request work inside the directory workspace instead of forcing raw preference editing.

## Files

- `ecm-core/src/main/java/com/ecm/core/controller/PeopleController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerSecurityTest.java`
- `ecm-frontend/src/services/peopleService.ts`
- `ecm-frontend/src/pages/PeopleDirectoryPage.tsx`

## Result

Athena now exceeds the previous request-withdraw-only slice by supporting the full create/edit/withdraw lifecycle directly in the People Directory.
