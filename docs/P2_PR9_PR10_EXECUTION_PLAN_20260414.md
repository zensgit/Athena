# P2 PR-9 / PR-10 Execution Plan

## Date
- 2026-04-14

## Status
- completed

## Context
- `P0A`, `P0B`, and `P1` are closed.
- `P2` should resume backlog work only where the hardened kernel can now safely carry the feature.
- Current code inspection shows:
  - `Scheduled User Actions` already has backend runner, cron validation, manual trigger API, and frontend editing hooks.
  - `Smart Folder` has entity fields and runtime execution in `FolderService`, but the feature is not yet a closed product path.

## Recommendation
- `PR-9`: `Smart Folder completion`
- `PR-10`: `Scheduled User Actions hardening`

## Why This Order
- `Smart Folder` is the larger remaining product gap on top of an already-present partial backend.
- `Scheduled User Actions` is already closer to “shippable enhancement” than “missing capability”.
- Running `PR-10` first would mostly polish an already-existing path while leaving the more visible partial feature unfinished.

## PR-9: Smart Folder Completion

### Goal
- Turn Athena’s partial smart-folder backend into a stable, API-backed feature path.

### Current State
- `Folder` already stores:
  - `isSmart`
  - `queryCriteria`
- `FolderService.getFolderContents(...)` already executes a smart-folder query through `FacetedSearchService`.
- `FolderController.createFolder(...)` accepts `isSmart/queryCriteria`.
- Frontend folder creation flow does not expose any smart-folder controls.
- Saved search APIs exist, but there is no bridge from saved search to smart folder creation.

### Main Gaps
- no smart-folder update API path
- no saved-search-to-smart-folder creation path
- no guard that prevents physical children from being added under a smart folder
- no tests that pin runtime behavior, paging semantics, and validation rules
- no frontend entry point for creating smart folders

### Proposed Scope

#### Backend
- extend `FolderService.updateFolder(...)` and `UpdateFolderRequest` to support:
  - `isSmart`
  - `queryCriteria`
- validate smart-folder constraints:
  - smart folder requires non-empty `queryCriteria`
  - smart folder cannot accept physical children
  - smart folder cannot be converted from/to incompatible state when physical children already exist
- improve smart-folder content execution:
  - preserve search ordering
  - preserve pageable totals
  - fail with explicit `400` on invalid criteria payloads instead of silent empty results where possible
- add a saved-search bridge in `SavedSearchService` / `SavedSearchController`:
  - create smart folder from a saved search definition

#### Frontend
- add smart-folder creation affordance in folder or saved-search UI
- minimum viable path:
  - create smart folder from `SavedSearchesPage`
  - optional smart-folder fields in `CreateFolderDialog`

### Primary Write Set
- `ecm-core/src/main/java/com/ecm/core/service/FolderService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/FolderController.java`
- `ecm-core/src/main/java/com/ecm/core/service/SavedSearchService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/SavedSearchController.java`
- `ecm-core/src/test/java/com/ecm/core/service/*Folder*Test.java`
- `ecm-core/src/test/java/com/ecm/core/controller/*Folder*Test.java`
- `ecm-frontend/src/components/dialogs/CreateFolderDialog.tsx`
- `ecm-frontend/src/pages/SavedSearchesPage.tsx`
- `ecm-frontend/src/services/nodeService.ts`

### Suggested Split
- `PR-9A` backend contract and validation
- `PR-9B` saved-search frontend create/bridge flow
- `PR-9C` generic folder-dialog authoring

### PR-9A Status
- completed
- delivered:
  - smart-folder validation and safe update path
  - physical-child guards in `FolderService` and `NodeService`
  - stable smart-folder query execution with preserved result ordering
  - saved-search-to-smart-folder backend bridge
  - targeted and full backend regression coverage
- deferred to `PR-9B`:
  - `SavedSearchesPage` action
  - optional `CreateFolderDialog` smart-folder controls
  - frontend contract usage and UI tests

### PR-9B Status
- completed
- delivered:
  - `SavedSearchesPage` smart-folder action and creation dialog
  - frontend `savedSearchService.createSmartFolder(...)` bridge
  - targeted frontend tests for service + page interaction
  - full frontend regression run after UI changes
- intentionally deferred:
  - generic `nodeService.createFolder(...)` smart payload support
  - `CreateFolderDialog` smart-folder authoring

### PR-9C Status
- completed
- delivered:
  - generic `CreateFolderDialog` smart-folder authoring
  - `nodeSlice.createFolder` and `nodeService.createFolder(...)` support for `description/isSmart/queryCriteria`
  - `FolderResponse -> Node` preservation of `smart/queryCriteria`
  - targeted frontend tests for dialog and service payloads
- intentionally deferred:
  - dedicated folder edit/settings UI
  - broader browse/list API exposure of smart-folder metadata

### PR-9 Overall Status
- completed
- backend and frontend minimum viable smart-folder path is now closed for both saved-search and generic create-dialog entry points

## PR-10: Scheduled User Actions Hardening

### Goal
- Treat scheduled rules as a hardened enhancement rather than a greenfield feature.

### Current State
- `AutomationRule` already stores `cronExpression`, `timezone`, `nextRunAt`, and batch limits.
- `ScheduledRuleRunner` already:
  - polls due rules
  - validates cron
  - supports manual trigger
  - audits batch execution
- `RuleController` already exposes:
  - `validate-cron`
  - `trigger`
- `RulesPage` and `ruleService` already expose cron validation and scheduled-rule fields.

### Main Gaps
- no tighter non-admin scoping for scheduled rules by owned/managed folder
- no explicit minimum interval guard in backend
- no dedicated acceptance coverage for scheduled-rule authoring UX and permission boundaries
- no documented product decision on whether non-admin users may manually trigger their own scheduled rules

### Proposed Scope
- add minimum cron interval enforcement in backend
- define and enforce authoring/trigger permission boundaries
- add API-visible validation for invalid schedule shapes
- add tests around:
  - creation/update of scheduled rules
  - manual trigger auth
  - scope-folder ownership/management constraints

### Sidecar Audit Summary
- already present and should not be rebuilt:
  - cron validation + next-run preview
  - scheduled runner execution chain
  - folder-scope reorder and dry-run
  - base visibility filtering in service layer
- next minimal implementation should focus on:
  - stricter scheduled-field validation and minimum interval enforcement
  - tighter UI state for scheduled vs non-scheduled rules
  - targeted controller/service/page coverage instead of broad refactor

### Primary Write Set
- `ecm-core/src/main/java/com/ecm/core/service/RuleEngineService.java`
- `ecm-core/src/main/java/com/ecm/core/service/ScheduledRuleRunner.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RuleController.java`
- `ecm-core/src/test/java/com/ecm/core/service/ScheduledRuleRunnerTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/*Rule*Test.java`
- `ecm-frontend/src/pages/RulesPage.tsx`
- `ecm-frontend/src/services/ruleService.ts`

### PR-10 Status
- completed
- delivered:
  - shared scheduled-rule validation utility for cron, timezone, minimum interval, and batch size
  - backend create/update normalization that clears scheduled-only fields from non-scheduled rules
  - consistent cron preview and `nextRunAt` computation across persistence and runtime
  - `RulesPage` trigger-state normalization and stronger scheduled-form validation
  - targeted backend/controller/frontend coverage plus full regression runs
- explicitly unchanged:
  - manual trigger remains admin-gated
  - runner architecture and folder-scope APIs were not rebuilt

## Parallelization Guidance
- `Claude Code CLI` can be used as a read-only sidecar for bounded analysis if needed.
- Do not let `Claude CLI` and the main implementation edit the same write set simultaneously.
- Safe parallel pattern:
  - main line: `PR-9A` backend changes
  - sidecar: `PR-9B` frontend surface audit or `PR-10` permission-gap analysis

## Gate
- `PR-9` closes only when smart folders are both creatable and safely constrained.
- `PR-10` closes only when scheduled rules have clear permission and validation boundaries.

## Overall Status
- `PR-9`: completed
- `PR-10`: completed
- `P2` first wave (`Smart Folder completion` + `Scheduled Rule hardening`) is complete
