# Phase368ZEA Verification

## Scope

Validated the preference contract repair across:

- `PeopleController`
- `PeopleControllerTest`
- `PeopleControllerSecurityTest`
- `peopleService`
- `PeopleDirectoryPage`

## Commands

### Patch / whitespace sanity

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/controller/PeopleController.java \
  ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerSecurityTest.java \
  ecm-frontend/src/services/peopleService.ts \
  ecm-frontend/src/services/peopleService.test.ts \
  ecm-frontend/src/pages/PeopleDirectoryPage.tsx
```

Result:

- passed

### Focused backend verification

```bash
cd ecm-core && mvn -q -Dtest=PreferenceServiceTest,PeopleControllerTest,PeopleControllerSecurityTest test
```

Result:

- passed

### Focused frontend lint

```bash
cd ecm-frontend && ./node_modules/.bin/eslint \
  src/services/peopleService.ts \
  src/services/peopleService.test.ts \
  src/pages/PeopleDirectoryPage.tsx
```

Result:

- passed

### Focused frontend service verification

```bash
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/services/peopleService.test.ts
```

Result:

- passed

### Frontend build verification

```bash
cd ecm-frontend && npm run -s build
```

Result:

- passed with unrelated pre-existing warnings in:
  - `ecm-frontend/src/components/share/ShareLinkManager.tsx`
  - `ecm-frontend/src/pages/AdminDashboard.tsx`

Those warnings were not introduced by this phase.

## Verified Outcome

- bulk preference updates now run through `PreferenceService.replaceAll(...)`
- single delete and clear-all preference paths now run through the same service layer
- `PeopleController` test suites no longer break after service extraction
- namespace-aware preference reads are exercised through controller coverage
- frontend `peopleService` and `PeopleDirectoryPage` now consume the namespace filter / namespace list contract
