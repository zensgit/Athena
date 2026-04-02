# Phase368ZEA: Preferences Contract Consumption And Test Repair

## Context

`Phase368ZE` introduced `PreferenceService`, namespace filtering, namespace discovery, and validation rules for user preferences.

But that slice was not actually closed:

- `PeopleController` still handled bulk preference replacement directly instead of delegating to `PreferenceService.replaceAll(...)`.
- existing controller and security tests were broken after `PreferenceService` became a required dependency.
- the frontend `PeopleDirectoryPage` had started to evolve toward namespace-aware preference browsing, but the service contract consumption needed to be aligned and verified.

This phase closes those gaps rather than opening a new feature line.

## Goal

Make the preferences hardening work real at the contract and operator-surface level:

- bulk preference updates must flow through `PreferenceService`
- delete / clear paths should use the same service-level preference contract
- controller/security tests must be repaired
- frontend namespace filter and namespace list must be consumed through `peopleService`

## Implementation

### 1. `PeopleController` now routes write paths through `PreferenceService`

File:

- `ecm-core/src/main/java/com/ecm/core/controller/PeopleController.java`

Changed:

- `PUT /api/v1/people/{username}/preferences` now delegates to `preferenceService.replaceAll(...)`
- `DELETE /api/v1/people/{username}/preferences/{preferenceName}` now delegates to `preferenceService.deletePreference(...)`
- `DELETE /api/v1/people/{username}/preferences` now delegates to `preferenceService.clearAll(...)`

Response behavior remains stable:

- controller still returns `PeoplePreferencesDto`
- profile metadata is preserved via `requireUser(username)`
- the updated preference map is supplied through `PeoplePreferencesDto.fromFiltered(...)`

This removes the previous contract seam where single-entry upsert used the service but bulk replace still bypassed validation.

### 2. Controller tests were repaired and expanded

Files:

- `ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerSecurityTest.java`

Changed:

- added the missing `PreferenceService` mock dependency
- repaired existing preference controller tests so they stub service-layer calls instead of the old controller-local save path
- added focused coverage for:
  - namespace-filtered preference reads
  - namespace listing endpoint

This restores controller regression coverage after the service extraction.

### 3. Frontend service contract now consumes namespace-aware preferences

Files:

- `ecm-frontend/src/services/peopleService.ts`
- `ecm-frontend/src/services/peopleService.test.ts`

Changed:

- `getPreferences(username, filter?)` now accepts an optional namespace prefix
- added `getPreferenceNamespaces(username)`
- added focused service tests to verify the request contract

### 4. `PeopleDirectoryPage` now consumes namespace filtering as an operator surface

File:

- `ecm-frontend/src/pages/PeopleDirectoryPage.tsx`

Behavior now aligned with the backend contract:

- preferences are reloaded through `peopleService.getPreferences(selectedUsername, filter?)`
- distinct namespaces are loaded through `peopleService.getPreferenceNamespaces(selectedUsername)`
- preference entries are grouped by top-level namespace
- namespace chips allow operators to filter visible preference groups
- filtered mode keeps raw JSON editing disabled to avoid overwriting hidden namespaces
- save/delete flows trigger a targeted preference reload

## Why this slice matters

This phase is not a feature spike. It is closure work on a half-finished hardening slice.

The value is:

- `PreferenceService` is now the actual contract owner for bulk and single preference writes
- controller and security regression suites no longer break because of the extracted dependency
- frontend preference browsing now actually consumes the namespace-aware contract instead of ignoring it

## Follow-up

After this repair slice, the preferences line is much closer to a complete operator surface.

The next best Claude-owned continuation should be a true product enhancement, not more repair work. A reasonable next step is:

- preference preset templates
- preference import/export
- or another independent non-preview slice

The main Athena rollout can continue focusing on `preview / rendition / search / ops governance`.
