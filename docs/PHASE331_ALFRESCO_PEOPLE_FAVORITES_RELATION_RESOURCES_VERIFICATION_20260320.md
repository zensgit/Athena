# Phase 331 Verification - Alfresco People Favorites Relation Resources

## Verified Commands

```bash
cd ecm-core && mvn -q -DskipTests compile
cd ecm-frontend && npx eslint --max-warnings=0 src/services/peopleService.ts
cd ecm-frontend && npm run -s build
```

## Verified Outcomes

- backend compile succeeds with the new favorites relation resources
- frontend service-layer lint passes for the new people favorites contracts
- frontend production build succeeds after wiring the new relation API

## Known Verification Gap

- `cd ecm-core && mvn -q -Dtest=WorkflowControllerTest,PeopleControllerTest,PeopleControllerSecurityTest test` is currently blocked by an existing test-runtime classpath issue in this workspace:
  - `NoClassDefFoundError: com/ecm/core/exception/ResourceNotFoundException`
  - `NoClassDefFoundError: com/ecm/core/repository/UserRepository`
- this occurs during test bootstrap/mock creation rather than inside the new people favorites assertions
