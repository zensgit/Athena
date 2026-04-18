# P3 PR-15 Disposition Schedules Design

## Date
- 2026-04-14

## Status
- completed

## Scope
- backend-only first slice
- fixed ordered action chain:
  - `CUTOFF`
  - `ARCHIVE`
  - `DESTROY`
- admin-authored folder-level schedules
- auditable execution history
- legal-hold-aware destroy enforcement

## Why This Slice
- Athena already had archive policy and archive execution seams, but no first-class disposition schedule model.
- `PR-14` introduced the blocking control layer needed before automated destruction could be considered safe.
- A backend-only first slice is the lowest-risk way to add disposition without overcommitting to a full records-management surface.

## Delivered Model

### Persistence
- `disposition_schedules`
  - one schedule per folder
  - soft-delete capable
  - stores enablement, subtree scope, cutoff/archive/destroy age thresholds, archive tier, execution limits, and last run metadata
- `disposition_action_executions`
  - append-only execution history
  - action type, status, node snapshot, affected node count, actor, timestamp, details

### Service Layer
- `DispositionScheduleService`
  - admin-only authoring, dry-run, immediate execution, batch scheduled execution, and history listing
- `DispositionActionExecutorService`
  - isolates destroy execution and delegates to governance-safe node deletion
- `DispositionScheduleScheduler`
  - runs enabled schedules on cron
- `DispositionScheduleController`
  - admin API for schedule CRUD, dry-run, execute-now, list, and execution history

## Execution Semantics

### Candidate Selection
- candidates are folder-scoped
- `includeSubfolders=true` uses subtree path scanning
- tenant visibility is enforced before a node becomes eligible
- nested descendants are collapsed when an already-selected folder would subsume them
- `maxCandidatesPerAction` caps each action stage independently

### Ordered Actions
- `CUTOFF`
  - eligible when `lastModifiedDate` or `createdDate` is older than `cutoffAfterDays`
  - records a successful cutoff execution but does not mutate the node
- `ARCHIVE`
  - requires a prior successful cutoff aged by `archiveAfterCutoffDays`
  - delegates to `ContentArchiveService.archiveNodeByPolicy(...)`
- `DESTROY`
  - requires a prior successful archive aged by `destroyAfterArchiveDays`
  - only runs on `ARCHIVED` nodes
  - delegates to `NodeService.deleteNodeByGovernance(...)`

### Replay Safety
- execution history is loaded into an action ledger keyed by:
  - schedule
  - action type
  - node id
- a node is not re-selected for an action once a prior successful execution exists for that same action
- prerequisite stages must have completed before later stages become eligible

## Governance And Safety Rules

### Legal Hold
- destroy dry-run surfaces blocking hold names
- destroy execution converts `IllegalOperationException` into `BLOCKED` execution history
- actual destruction reuses `NodeService.deleteNodeByGovernance(...)`, which keeps the legal-hold guard centralized

### Archive Policy Conflict
- a folder cannot have both:
  - an enabled archive policy
  - an enabled disposition schedule
- the same conflict is checked from both sides:
  - `DispositionScheduleService`
  - `ArchivePolicyService`

### Folder Eligibility
- schedule authoring requires a live, undeleted folder
- scheduled batch execution skips deleted or archived folders

## API Surface
- `GET /api/v1/disposition-schedules`
- `GET /api/v1/folders/{folderId}/disposition-schedule`
- `PUT /api/v1/folders/{folderId}/disposition-schedule`
- `DELETE /api/v1/folders/{folderId}/disposition-schedule`
- `POST /api/v1/folders/{folderId}/disposition-schedule/dry-run`
- `POST /api/v1/folders/{folderId}/disposition-schedule/execute`
- `GET /api/v1/folders/{folderId}/disposition-schedule/executions`
- `POST /api/v1/disposition-schedules/run`

## Migration
- Liquibase `078-create-disposition-schedules.xml`
- adds:
  - `disposition_schedules`
  - `disposition_action_executions`
- indexes:
  - enabled schedules
  - schedule + execution time
  - schedule + action + status

## Deferred
- transfer handoff to remote/archive destinations
- frontend disposition authoring and reporting UI
- richer RM concepts such as multi-step configurable action graphs, cutoff events, file plans, and declaration semantics
- non-admin delegated governance roles
