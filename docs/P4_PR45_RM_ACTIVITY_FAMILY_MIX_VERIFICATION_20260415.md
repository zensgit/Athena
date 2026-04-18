# PR-45 RM Activity Family Mix Verification

## Implemented

Backend:

- added `GET /api/v1/records/activity-families`
- aggregate exact `RM_%` audit rows into family buckets
- added `listAudit(..., family=OTHER, ...)` support so the family model is fully drillable

Frontend:

- added `RM Activity Family Mix` card
- wired family rows into the existing `Records Audit` table
- added `Other` to the audit family filter
- kept family-mix load isolated from the rest of the RM page

## Targeted Verification

Backend:

- command:
  - `cd ecm-core && ./mvnw -B -Dstyle.color=never test -Dtest=RecordsManagementServiceTest,RecordsManagementControllerTest`
- result:
  - `Tests run: 84, Failures: 0, Errors: 0, Skipped: 0`

Frontend:

- command:
  - `cd ecm-frontend && CI=true npm test -- --watchAll=false --runInBand src/pages/RecordsManagementPage.test.tsx src/services/recordsManagementService.test.ts`
- result:
  - `Test Suites: 2 passed`
  - `Tests: 66 passed`

## Wider Verification

Frontend:

- command:
  - `cd ecm-frontend && CI=true npm test -- --watchAll=false`
- result:
  - `Test Suites: 71 passed`
  - `Tests: 380 passed`

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

The full backend suite was not re-run for PR-45.

Reason:

- the Docker Maven wrapper still shows repeated non-PR-slice class-loading / Mockito instability on broad full-suite runs
- the same wrapper issue was already documented during `PR-43` and kept unchanged in `PR-44`
- targeted RM controller/service coverage for the PR-45 surface is green

## Verification Conclusion

`PR-45 approve`.

The slice is complete as delivered:

- new backend RM analytics endpoint for family-level activity mix
- complete RM audit family model including `OTHER`
- new RM admin-page family-mix card
- family drilldown reuses the existing audit evidence surface
- targeted backend regression passed
- targeted and full frontend regression passed
- frontend production build succeeded
