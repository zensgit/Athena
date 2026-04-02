# Phase 324 - Alfresco People Self-Service Profile and Preferences Dev

Date: 2026-03-19

## Goal

Close one of the largest remaining people/profile gaps against the Alfresco reference:

- make the current-user people workspace writable instead of read-only
- expose profile fields and raw preference payload updates through stable backend APIs
- let the current user manage favorites directly from the people workspace

## Backend Delivery

Updated [PeopleController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/PeopleController.java):

- added `PUT /api/v1/people/{username}/profile`
- added `PUT /api/v1/people/{username}/preferences`
- enforced self-or-admin write rules with `requireWritableUser`
- expanded `PeoplePreferencesDto` to include writable profile metadata such as display name, first/last name, phone, department, job title, and avatar URL

Updated [PeopleControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerTest.java):

- added writable profile update coverage
- added admin preference-map replacement coverage
- added forbidden-path coverage for cross-user profile edits
- stabilized activity assertions and favorite stubbing for the widened controller surface

Updated [PeopleControllerSecurityTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerSecurityTest.java):

- added authenticated self-profile update coverage

## Frontend Delivery

Updated [peopleService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/peopleService.ts):

- expanded the `PersonPreferences` contract with writable profile metadata
- added `updateProfile(...)`
- added `updatePreferences(...)`

Updated [PeopleDirectoryPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/PeopleDirectoryPage.tsx):

- added `Edit profile` dialog for current-user self-service updates
- added `Edit preferences` JSON dialog for raw preference payload editing
- surfaced richer profile/contact metadata in the preferences card
- added current-user favorite removal actions for document favorites and favorite sites

## Outcome

Athena's people workspace is no longer a passive directory-only view. Current users can now manage their own profile and preference payload directly in-product and curate favorites from the same workspace, which is a stronger collaboration loop than the read-only baseline.
