# Phase 326 - Alfresco People Site Request Withdraw Dev

Date: 2026-03-19

## Goal

Extend Athena's writable people workspace with a concrete collaboration action:

- allow the current user to withdraw recorded site membership requests
- keep the people workspace aligned with current-user self-service rather than read-only visibility

## Backend Delivery

Updated [PeopleController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/PeopleController.java):

- added `DELETE /api/v1/people/{username}/site-membership-requests/{siteId}`
- enforced self-or-admin write rules through the existing `requireWritableUser(...)`
- removes the matching request from the stored preference payload and persists the updated preference map

Updated [PeopleControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerTest.java):

- added coverage for successful request withdrawal

Updated [PeopleControllerSecurityTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerSecurityTest.java):

- added authenticated coverage for current-user withdrawal

## Frontend Delivery

Updated [peopleService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/peopleService.ts):

- added `withdrawSiteMembershipRequest(...)`
- aligned request timestamp field naming with backend `requestedAt`

Updated [PeopleDirectoryPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/PeopleDirectoryPage.tsx):

- site membership requests are now rendered as actionable list items instead of passive chips
- current-user profiles can withdraw a request directly from the people workspace
- successful withdrawal updates the in-page request list immediately

## Outcome

Athena's people workspace now supports another real current-user collaboration action, moving beyond profile/preferences editing into membership-request lifecycle management.
