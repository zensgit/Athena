# P2 PR-9 / PR-10 Acceptance Plan

## Date
- 2026-04-14

## Status
- completed

## PR-9 Smart Folder Completion

### Backend Acceptance
- smart folder can be created with valid `queryCriteria`
- smart folder update can change `queryCriteria` safely
- invalid smart-folder payload returns explicit `400`
- physical child creation under a smart folder is rejected
- converting a populated physical folder into a smart folder is rejected unless constraints are satisfied
- smart-folder contents execute through the stored query criteria and return stable paging totals

### Frontend Acceptance
- user can create a smart folder from a saved search or a dedicated folder dialog path
- created smart folder appears in folder listings with correct metadata
- opening the smart folder loads query-backed results instead of physical children

### PR-9A Acceptance Result
- backend acceptance met
- frontend acceptance deferred to `PR-9B`
- verified in this phase:
  - smart folder create/update rejects invalid `queryCriteria`
  - smart folder execution preserves search result ordering and total hits
  - physical child creation and move into smart folders are rejected
  - saved search can create a smart folder through `/api/v1/search/saved/{id}/smart-folder`
  - targeted backend tests and full backend suite remain green

### PR-9B Acceptance Result
- frontend acceptance met for the saved-search creation path
- verified in this phase:
  - user can create a smart folder from `SavedSearchesPage`
  - creation dialog captures name and optional description
  - successful creation navigates to `/browse/{folderId}`
  - targeted frontend tests pass
  - full frontend suite remains green

### PR-9C Acceptance Result
- frontend acceptance met for the generic create-dialog path
- verified in this phase:
  - user can create a smart folder from the main `CreateFolderDialog`
  - smart-folder create payload includes `description/isSmart/queryCriteria`
  - smart-folder path prefix defaults to the current folder path
  - targeted frontend tests pass
  - full frontend suite remains green

### Suggested Automated Coverage
- `FolderServiceSmartFolderTest`
- `FolderControllerSmartFolderTest`
- `SavedSearchControllerSmartFolderBridgeTest`
- frontend tests around create flow and smart-folder listing badge/state

### Manual Smoke
1. Save a search.
2. Create a smart folder from that saved search.
3. Open the smart folder and confirm results match the saved search.
4. Try to upload or create a physical child under the smart folder and confirm it is blocked.

## PR-10 Scheduled User Actions Hardening

### Backend Acceptance
- scheduled-rule create/update rejects invalid cron expressions
- scheduled-rule create/update rejects sub-minimum interval schedules
- manual trigger permission is enforced consistently
- non-admin rule authors cannot schedule outside their allowed folder scope

### Frontend Acceptance
- cron validation feedback is visible before save
- invalid scheduled-rule configuration is rejected with clear UI feedback
- manual trigger action respects permission boundaries

### PR-10 Acceptance Result
- backend acceptance met
- frontend acceptance met for authoring validation/state flow
- permission boundary remains unchanged and explicitly confirmed rather than expanded
- verified in this phase:
  - scheduled-rule create/update rejects invalid cron expressions
  - scheduled-rule create/update rejects sub-minimum interval schedules
  - scheduled-rule create/update rejects invalid batch limits
  - non-scheduled rules do not retain scheduled-only fields
  - cron preview endpoint and persistence path use the same validation semantics
  - `RulesPage` shows clear feedback for invalid scheduled configuration and strips scheduled fields when trigger type changes
  - targeted backend/frontend tests and full backend/frontend suites remain green

### Suggested Automated Coverage
- `ScheduledRuleRunnerTest`
- `RuleControllerScheduledRuleValidationTest`
- `RuleControllerFolderScopeSecurityTest`
- frontend tests for scheduled rule form validation and trigger visibility

### Manual Smoke
1. Create a scheduled rule with a valid cron expression.
2. Validate cron preview in the UI.
3. Trigger the rule manually as an authorized user.
4. Repeat as an unauthorized user and confirm access is denied.

## Command Baseline

```bash
cd ecm-core
./mvnw -B -Dstyle.color=never test
cd ../ecm-frontend
npm test -- --watch=false
```

## Merge Criteria
- no P2 feature may reintroduce P0/P1 correctness regressions
- targeted suites pass
- full backend suite remains green
- feature-specific frontend regression coverage is added where UI changes occur

## Final Status
- `PR-9 approve`
- `PR-10 approve`
- `P2` first wave close
