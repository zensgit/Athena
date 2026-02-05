# Athena ECM Rollback & Impact Assessment (2026-02-05)

## Impact Summary
- **Mail Automation**: Adds reporting/diagnostics UX and service endpoints; no schema changes.
- **Search**: Adds highlight snippets/explainability helpers; preserves ACL filtering.
- **Permissions**: Adds permission template versioning (new table) and compare/export UI.
- **Preview**: Adds retry status visibility and bulk retry action; no data model changes.

## Data/Schema Changes
- **New table**: permission template version history (Liquibase `028-add-permission-template-versions.xml`).
- No destructive migrations.

## Backward Compatibility
- New endpoints are additive.
- UI changes are backward compatible with existing data.

## Rollback Plan
1. **Application rollback**: revert to prior build or git tag before `v2026.02.05`.
2. **DB rollback**: optional — new version table can remain without impact; if required, rollback Liquibase changeset `028` (drop table + indexes).
3. **Cache/Index**: no special reindex required; search highlights are additive.

## Risks & Mitigations
- **Risk**: CSV export relies on version compare data. 
  - **Mitigation**: E2E validates download; compare dialog guarded by version availability.
- **Risk**: Preview retry queue status may be stale if queue service unavailable.
  - **Mitigation**: UI shows last known status; retry endpoints remain safe.
- **Risk**: Mail reporting endpoints depend on processed mail data.
  - **Mitigation**: UI handles empty results; diagnostics still functional.

## Validation Gates
- Backend tests: `mvn test` (passed).
- Frontend E2E: `npx playwright test` (passed).
- Health checks: API/UI/Keycloak/WOPI(Search/Mail) all 200 with auth.
