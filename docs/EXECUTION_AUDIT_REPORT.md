# Execution Audit Report (Scope 1/2/3/4)

## Scope Reviewed
- Roadmap + sprint docs: `docs/ECM_FEATURE_ROADMAP.md`, `docs/CLAUDE_EXECUTION_PLAN.md`
- Sprint reports: `docs/SPRINT_2_SCHEDULED_RULES_REPORT.md`, `docs/SPRINT_3_SECURITY_COMPLIANCE_REPORT.md`, `docs/SPRINT_4_REPORT.md`
- UI/verification docs: version detail validation + preview/long-name reports
- Code paths: search services, rules scheduling, audit export, preview UI

## Findings (Gaps / Follow-ups)
1) **Search permission filtering**
   - Sprint 2 search doc marks permission filtering TODO for Sprint 3.
   - Current `FullTextSearchService` / `FacetedSearchService` do not filter results by ACL.
   - Status: Resolved. Search results now filter to `PermissionType.READ`-authorized nodes.

2) **Audit export date range UI**
   - Sprint 3 compliance report notes TODO: add `from/to` range selection in UI.
   - `AdminDashboard` exports last 30 days only; no range input in UI.
   - Status: Resolved. From/To controls added and export URL wired to the range.

3) **PDF preview bottom spacing**
   - Preview dialog uses `calc(100dvh - toolbarHeight)` for container height and also constrains `DialogContent`.
   - Reported visual gap at bottom likely due to fixed height override instead of flex-driven layout.
   - Status: Resolved. Preview layout now uses flex fill with `100%` height content.

## Already Implemented (Confirmed)
- Scheduled rules backend (`ScheduledRuleRunner`, cron fields, UI) present.
- Antivirus service + pipeline scan (ClamAV) present; E2E skips when unavailable.
- Version detail DTO fields populated (`VersionDto`) with E2E validation docs present.
- Long filename handling in grid/search cards via clamp + font scaling.

## Verification
- Frontend E2E: `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test` (15 passed).
- Backend tests: `docker run --rm -v "$(pwd)":/workspace -v "$HOME/.m2":/root/.m2 -w /workspace/ecm-core maven:3-eclipse-temurin-17 mvn test` (17 tests, 0 failures).
- Backend verify: `docker run --rm -v "$(pwd)":/workspace -v "$HOME/.m2":/root/.m2 -w /workspace/ecm-core maven:3-eclipse-temurin-17 mvn verify` (17 tests, 0 failures).
