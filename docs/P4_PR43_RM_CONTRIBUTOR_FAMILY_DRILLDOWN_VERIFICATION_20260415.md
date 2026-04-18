# PR-43 RM Contributor Family Drilldown Verification

## Implemented

Backend:

- `GET /api/v1/records/audit` accepts optional `family`
- controller binds `family` as `RecordsManagementService.RmEventFamily`
- service applies family/eventType intersection before querying
- repository supports family-scoped `IN` filtering

Frontend:

- `Records Audit` includes a `Family` filter
- contributor family counters drill into the existing audit table
- drilldown reuses the existing banner, pagination, and audit load path

## Targeted Verification

Backend:

- command:
  - `cd ecm-core && ./mvnw -B -Dstyle.color=never test -Dtest=RecordsManagementServiceTest,RecordsManagementControllerTest`
- result:
  - `Tests run: 72, Failures: 0, Errors: 0, Skipped: 0`

Frontend:

- command:
  - `cd ecm-frontend && CI=true npm test -- --watchAll=false --runInBand src/pages/RecordsManagementPage.test.tsx src/services/recordsManagementService.test.ts`
- result:
  - `Test Suites: 2 passed`
  - `Tests: 58 passed`

## Wider Verification

Frontend:

- command:
  - `cd ecm-frontend && CI=true npm test -- --watchAll=false`
- result:
  - `Test Suites: 71 passed`
  - `Tests: 372 passed`

- command:
  - `cd ecm-frontend && npm run build`
- result:
  - build passed
  - remaining warnings are pre-existing:
    - `src/components/share/ShareLinkManager.tsx`: unused `BarChart`
    - `src/pages/AdminDashboard.tsx`: unused `FilterList`

Static:

- `git diff --check`
  - passed

## Full Backend Suite Note

`cd ecm-core && ./mvnw -B -Dstyle.color=never test` was re-run twice after the PR-43 changes.

Observed result:

- widespread unrelated `NoClassDefFoundError` / Mockito type-loading failures across many non-PR-43 test classes
- examples include:
  - `CorrespondentRepository`
  - `CategoryRepository`
  - `TrashService`
  - `RuleEngineService`
  - `TemplateDefinition$TemplateEngine`

The missing classes are present in `ecm-core/target/classes`, which points to a Docker-wrapper/runtime class-loading issue rather than a PR-43 slice regression.

Current PR-43 confidence therefore rests on:

- targeted backend RM tests green
- targeted frontend tests green
- full frontend regression green
- build green

## Review Notes

- `family` remains case-sensitive because it uses Spring enum binding
- `family` and `eventType` use `AND` semantics
- conflicting `family + eventType` returns `Page.empty(pageable)` and skips repository access
- `RM_RECORD_UNDECLARE_BLOCKED` remains intentionally outside all activity families, consistent with existing RM analytics
