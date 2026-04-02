# PHASE368ZEB: Preferences Import/Export Operator Surface

## Goal
Provide a productized preferences import/export surface without touching preview, rendition, search, or ops governance code paths.

## Scope
Backend:
- Add explicit export/import preference endpoints on `PeopleController`.
- Reuse `PreferenceService` validation and replacement semantics for imports.
- Keep export permission parity with existing preference read access.
- Keep import permission parity with existing writable preference mutations.

Frontend:
- Add export/download and import/replace actions in `PeopleDirectoryPage`.
- Add dedicated `peopleService` methods for export/import JSON contract.
- Disable import/export in filtered namespace mode to avoid overwriting hidden namespaces.

Tests:
- Add focused controller/service/frontend contract tests for export/import.
- Add security coverage for unauthorized preference import attempts.

## Design
### Backend contract
- `GET /api/v1/people/{username}/preferences/export` returns the full stored preference JSON map.
- `POST /api/v1/people/{username}/preferences/import` accepts a JSON body containing `preferences` and replaces the stored map after validation.
- `PreferenceService.exportPreferences(username)` returns a copy of the current preference map.
- `PreferenceService.importPreferences(username, preferences)` delegates to `replaceAll(...)` so the existing validation contract remains the source of truth.

### Frontend UX
- Export action downloads a prettified JSON file named with the username and date.
- Import action reuses the existing raw JSON editor dialog but re-labels it as import/replace.
- Filtered namespace mode disables import/export to avoid accidental overwrite of hidden namespaces.

### Safety notes
- Export is read-only and still requires the user to exist.
- Import is a write operation and must pass writable-user checks before mutation.
- Existing raw preference editing and single-entry edits remain unchanged.

## Files touched
- `ecm-core/src/main/java/com/ecm/core/controller/PeopleController.java`
- `ecm-core/src/main/java/com/ecm/core/service/PreferenceService.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerSecurityTest.java`
- `ecm-core/src/test/java/com/ecm/core/service/PreferenceServiceTest.java`
- `ecm-frontend/src/services/peopleService.ts`
- `ecm-frontend/src/pages/PeopleDirectoryPage.tsx`
- `ecm-frontend/src/services/peopleService.test.ts`
