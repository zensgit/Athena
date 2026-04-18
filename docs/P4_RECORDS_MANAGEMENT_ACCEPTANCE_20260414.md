# P4 Records Management Acceptance

## PR-16 Acceptance

- admin can declare a live non-working-copy document as a record
- declaration state is observable through `GET /api/v1/nodes/{nodeId}/record`
- declared record appears in `GET /api/v1/records`
- declared documents cannot be:
  - updated
  - checked out
  - versioned through normal version APIs
  - moved/copied/deleted through user mutation seams
  - trashed or auto-purged
- folders containing declared records cannot be moved/deleted/trashed through user mutation seams
- full backend regression remains green

## PR-17 Acceptance

- admin can declare a record from document preview UI
- declaration comment can be entered from the UI
- declared document shows a record badge in preview
- declared document shows read-only declaration details in preview
- front-end regression remains green
- front-end production build succeeds

## PR-18 Acceptance

- admin can create a file plan through RM API
- RM file plans are persisted as `FILE_PLAN` folders
- admin can create record categories under the seeded RM category root
- admin can assign a record category to a declared record
- generic category APIs cannot mutate RM record categories
- non-record nodes cannot be assigned RM record categories
- disposition schedules reject non-file-plan folders
- disposition execution only considers declared documents
- full backend regression remains green

## PR-19 Acceptance

- manual archive rejects declared records
- manual archive and restore reject file plans and nodes inside file-plan scope
- archive policy cannot be attached to file-plan folders
- archive policy candidate selection skips RM-governed nodes
- archive policy archive path honors legal holds
- folder copy rejects held source trees before recursive copy
- `GET /api/v1/records/summary` returns RM aggregate counts and breakdowns
- `GET /api/v1/records/audit` returns paged `RM_%` audit timeline entries
- RM file-plan / category / declaration mutations emit audit events
- full backend regression remains green

## PR-20 Acceptance

- admin can navigate to a dedicated RM page from the admin menu
- RM page renders summary counts from `GET /api/v1/records/summary`
- admin can browse and create file plans from the UI
- admin can browse and create RM record categories from the UI
- admin can browse declared records from the UI
- admin can assign a record category to a declared record from the UI
- RM audit timeline is visible from the UI and supports filter inputs
- full front-end regression remains green
- front-end production build succeeds

## PR-21A Acceptance

- trash restore rejects declared records
- trash restore rejects file plans and nodes inside file-plan scope
- trash restore rejects folders containing declared records
- `GET /api/v1/records/operations` returns governed bulk-import counts
- `GET /api/v1/records/operations` returns governed transfer-replication counts
- `GET /api/v1/records/operations` returns recent governed import and transfer jobs
- full backend regression remains green

## PR-21B Acceptance

- RM admin page renders governed import and transfer telemetry from `GET /api/v1/records/operations`
- RM admin page shows governed import and transfer counts
- RM admin page shows status breakdowns for governed imports and transfers
- RM admin page shows recent governed import jobs
- RM admin page shows recent governed transfer jobs
- operations telemetry failure does not block the rest of the RM admin page
- full front-end regression remains green
- front-end production build succeeds

## PR-21C Acceptance

- bulk import rejects file plan roots as generic import targets
- bulk import rejects folders inside file-plan scope as generic import targets
- bulk import does not delete RM-governed existing nodes during `OVERWRITE`
- transfer receiver rejects file plan and file-plan-scope target folders
- transfer receiver does not overwrite RM-governed existing targets during generic replication create paths
- loopback replication rejects RM-governed target folders before copy
- full backend regression remains green

## PR-22 Acceptance

- admin can undeclare a declared live document
- undeclare requires a non-empty reason
- undeclare is rejected for non-admin users
- undeclare is rejected for working copies, checked-out documents, trashed documents, archived documents, and undeclared documents
- undeclare is rejected when an active legal hold applies
- undeclare is rejected when the node is governed by a file plan
- successful undeclare removes RM declaration and record-category metadata
- successful undeclare does not change content, versions, ACL, parent, or path
- successful undeclare writes RM audit events and refreshes node state
- admin can trigger undeclare from preview and RM admin page
- full backend regression remains green
- full front-end regression remains green
- front-end production build succeeds

## PR-23 Acceptance

- restore continues to use `POST /api/v1/nodes/{nodeId}/restore`
- no new `reopen` backend endpoint is introduced
- restore is rejected for archived declared records
- restore is rejected for archived file plans and nodes governed by file plans
- restore is rejected for archived folders containing declared records
- restore is rejected for archived folders containing file-plan subtrees or file-plan-governed descendants
- restore is rejected when an active legal hold intersects the restore scope
- restore preflight validates the full archive scope before any node is mutated
- if UI copy is changed to `Reopen`, the action remains scoped to the archive admin page
- browse, preview, search, and trash surfaces do not gain reopen actions
- full backend regression remains green
- full front-end regression remains green if front-end copy changes are made
- front-end production build succeeds if front-end copy changes are made

## PR-24 Acceptance

- `GET /api/v1/records/operations` returns failed governed import count
- `GET /api/v1/records/operations` returns failed governed transfer count
- `GET /api/v1/records/operations` returns import governance-reason breakdown
- `GET /api/v1/records/operations` returns transfer governance-reason breakdown
- RM admin page renders a `Governance Health` section
- RM admin page highlights uncategorized and outside-file-plan record counts as attention signals
- RM admin page highlights failed governed import and transfer counts as attention signals
- RM admin page renders top import governance reasons
- RM admin page renders top transfer governance reasons
- full backend regression remains green
- full front-end regression remains green
- front-end production build succeeds

## PR-25 Acceptance

- admin can update file-plan description through RM API and RM admin UI
- file-plan rename and re-parent remain intentionally blocked in this slice
- admin can delete an empty file plan with no disposition schedule or archive-policy attachment
- file-plan delete remains non-recursive
- admin can update non-root record-category description through RM API and RM admin UI
- RM root category remains protected from edit and delete
- record-category rename and re-parent remain intentionally blocked in this slice
- admin can delete an unused leaf record category
- record-category delete is rejected for root, non-leaf, or assigned categories
- full backend regression remains green
- full front-end regression remains green
- front-end production build succeeds

## PR-26A Acceptance

- admin can rename a file plan through RM API
- admin can move a file plan through RM API to an allowed RM parent
- invalid RM parents are rejected before mutation
- file-plan rename repairs persisted `path` for the full subtree
- file-plan move stays on the authoritative subtree move path-repair chain
- file-plan rename triggers subtree search reindex so descendant paths do not remain stale
- RM audit logs record file-plan rename and move events
- frontend rename / move affordances remain intentionally deferred in this slice
- full backend regression remains green

## PR-26B Acceptance

- admin can rename a non-root record category through RM API
- admin can move a non-root record category through RM API within the RM tree
- RM root category remains protected from rename and move
- cycle creation is rejected before mutation
- descendant record-category paths are repaired recursively after rename and move
- declared records assigned to the affected category subtree get repaired:
  - `rm:recordCategoryName`
  - `rm:recordCategoryPath`
- `rm:recordCategoryId` remains stable for pure rename / move operations
- affected declared records are reindexed after commit so search does not keep stale category state
- frontend rename / move affordances remain intentionally deferred in this slice
- full backend regression remains green

## PR-26C Acceptance

- RM admin page exposes explicit `Rename` and `Move` actions for non-root record categories
- rename and move are performed through dedicated dialogs instead of the description-only edit form
- root category remains protected in the table
- move dialog does not offer the current category subtree or current parent as target parent options, and requires an explicit new-parent selection
- frontend uses the dedicated RM rename / move endpoints instead of generic category APIs
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-26D Acceptance

- RM admin page exposes explicit `Rename` and `Move` actions for file plans
- rename and move are performed through dedicated dialogs instead of the description-only edit form
- move dialog does not offer the current file plan, descendant file plans, or the current file-plan parent as target options
- move dialog requires an explicit new-parent selection before submit
- frontend uses the dedicated RM file-plan rename / move endpoints instead of generic folder APIs
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-27 Acceptance

- archive operator page uses `Reopen` copy for the operator-facing restore action
- archived-nodes table uses `Reopen` copy for the row action
- archive-page success and failure toasts use `Reopen` wording
- backend/service contract remains on the existing restore path and does not gain a new reopen API
- trash, browse, preview, search, and other non-archive surfaces do not gain `Reopen` wording through this slice
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-28 Acceptance

- RM admin page exposes declared-record quick filters for `All`, `Uncategorized`, and `Categorized`
- the declared-records area shows filtered vs total counts
- `Uncategorized Records` in governance health exposes a `Review queue` action
- `Review queue` switches the table into the uncategorized filter without any backend API change
- empty-state copy distinguishes between no declared records and no rows matching the current filter
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-29 Acceptance

- RM admin page exposes declared-record quick filtering for `Outside File Plan`
- `Outside File Plan` in governance health exposes a `Review coverage` action
- `Review coverage` switches the declared-records table into the outside-file-plan queue without any backend API change
- declared-records table shows file-plan coverage using existing visible file-plan data
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-30 Acceptance

- RM admin page exposes recent-governed-job quick filtering for import and transfer tables
- `Failed Governed Imports` in governance health exposes a `Review recent failures` action
- `Failed Governed Transfers` in governance health exposes a `Review recent failures` action
- review actions switch the corresponding recent-jobs table into a failed-only queue without any backend API change
- import and transfer empty-state copy distinguishes between no recent jobs and no rows matching the current filter
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-31 Acceptance

- RM admin page exposes governance-reason drilldown on top of the existing import and transfer reason breakdowns
- clicking an import governance reason filters the recent import-jobs table without any backend API change
- clicking a transfer governance reason filters the recent transfer-jobs table without any backend API change
- active reason selection is visible in the recent-jobs filter strip and can be cleared
- import and transfer tables keep their existing status filters while applying the selected governance reason locally
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-31B Acceptance

- selected import reason exposes a clear matched-jobs context message above the recent import-jobs table
- selected transfer reason exposes a clear matched-jobs context message above the recent transfer-jobs table
- selected reasons can be cleared from the context action without reloading telemetry
- recent import/transfer reason labels are visually highlighted for the selected reason
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-32 Acceptance

- RM admin page exposes exact-status drilldown on top of the existing import and transfer status breakdowns
- clicking an import status bucket filters the recent import-jobs table without any backend API change
- clicking a transfer status bucket filters the recent transfer-jobs table without any backend API change
- selected import/transfer status is visible in the recent-jobs filter strip and can be cleared
- exact-status selection coexists with existing reason drilldown while generic `All / Active / Failed` status chips reset the exact-status bucket locally
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-33 Acceptance

- RM admin page exposes a unified `Selected operations filters` summary when any import/transfer status or reason filter is active
- summary displays scoped import/transfer filter chips for currently active queue/status/reason conditions
- summary exposes `Clear import filters`, `Clear transfer filters`, and `Clear all filters`
- clearing by scope resets only the corresponding import or transfer filters without reloading telemetry
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-34 Acceptance

- RM admin page keeps the existing `Selected operations filters` title and clear actions
- scoped import/transfer summary copy is rendered as one compact label per scope instead of multiple fragmented summary chips
- single-condition scoped summaries preserve existing strings such as `Import: FAILED` and `Transfer: TARGET_OUTSIDE_FILE_PLAN`
- multi-condition scoped summaries use compact separator copy without changing the underlying filter semantics
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-35 Acceptance

- `Selected operations filters` exposes scope-level matched-count chips for active import/transfer filters
- matched-count chips show current `matched/total` for each active scope without any backend API change
- zero-match scoped filters surface `0/N` at the summary layer while keeping existing per-table empty-state behavior
- scoped clear actions continue to work without reloading telemetry
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-36 Acceptance

- zero-match recent import filters render a warning empty state instead of plain copy
- zero-match recent transfer filters render a warning empty state instead of plain copy
- each zero-match warning exposes a scope-specific recover CTA:
  - `Show all imports`
  - `Show all transfers`
- recover CTA clears only the corresponding scope filters without touching the other governed-operations scope
- zero-match warning continues to use existing telemetry only and introduces no backend API change
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-37 Acceptance

- RM admin page renders a `Declared Record Coverage Snapshot` card using existing summary and declared-record data only
- snapshot shows category coverage for:
  - categorized records
  - uncategorized records
- snapshot shows file-plan coverage for:
  - inside file plan
  - outside file plan
- RM admin page renders a `Governed Operations Snapshot` card using existing operations telemetry only
- snapshot shows import queue-health distribution for:
  - active imports
  - failed imports
  - other imports
- snapshot shows transfer queue-health distribution for:
  - active transfers
  - failed transfers
  - other transfers
- slice introduces no backend API changes and does not change existing drilldown/filter semantics
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-38 Acceptance

- backend exposes `GET /api/v1/records/activity-timeline`
- activity timeline returns the last `N` days of RM activity using existing audit-log data only
- timeline points expose:
  - `declaredCount`
  - `undeclaredCount`
  - `categoryAssignedCount`
  - `governanceChangeCount`
  - `totalCount`
- RM admin page renders an `RM Activity Timeline` card using the new endpoint
- timeline card shows daily rows for recent RM activity without changing existing drilldown/filter semantics
- RM activity timeline load is isolated so the rest of the RM admin page remains usable if that endpoint fails
- targeted backend regression remains green
- full backend regression remains green
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-39 Acceptance

- backend exposes `GET /api/v1/records/activity-highlights`
- activity highlights compare the current RM activity window with the previous window using existing audit-log data only
- activity highlights expose:
  - `currentWindow`
  - `previousWindow`
  - `busiestDay`
- current and previous windows expose:
  - `fromDay`
  - `toDay`
  - `activeDayCount`
  - `declaredCount`
  - `undeclaredCount`
  - `categoryAssignedCount`
  - `governanceChangeCount`
  - `totalCount`
- RM admin page renders an `RM Activity Highlights` card using the new endpoint
- highlights card shows current-window metrics and previous-window comparison without changing existing timeline, queue, or coverage drilldown semantics
- RM activity highlights load is isolated so the rest of the RM admin page remains usable if that endpoint fails
- targeted backend regression remains green
- full backend regression remains green
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-40 Acceptance

- backend exposes `GET /api/v1/records/activity-breakdown`
- activity breakdown returns the last `N` days of RM activity grouped into contiguous `bucketDays` windows using existing audit-log data only
- breakdown buckets expose:
  - `label`
  - `fromDay`
  - `toDay`
  - `activeDayCount`
  - `declaredCount`
  - `undeclaredCount`
  - `categoryAssignedCount`
  - `governanceChangeCount`
  - `totalCount`
- RM admin page renders an `RM Activity Breakdown` card using the new endpoint
- breakdown card shows bucketed recent RM activity without changing existing highlights, timeline, queue, or coverage drilldown semantics
- RM activity breakdown load is isolated so the rest of the RM admin page remains usable if that endpoint fails
- targeted backend regression remains green
- full backend regression remains green
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-41 Acceptance

- `GET /api/v1/records/audit` accepts optional `to` query parameter as ISO DATE_TIME
- `to` is an inclusive upper bound: `eventTime <= to`
- `from` and `to` together form a closed `[from, to]` interval
- omitting `to` applies no upper bound — existing behavior unchanged
- omitting `from` applies no lower bound — existing behavior unchanged
- degenerate interval (`from == to`) returns events at exactly that instant
- existing admin-only access control is preserved
- no new endpoints, tables, or models introduced
- RM admin page can drill from:
  - `RM Activity Highlights`
  - `RM Activity Breakdown`
  - `RM Activity Timeline`
  into the existing `Records Audit` table
- drilldown pre-fills audit `From` / `To` range filters and reuses the existing audit load path
- active drilldown state is visible above `Records Audit` and can be cleared independently
- service test covers `to` propagation and closed-interval boundary behavior
- controller test covers `to` parameter binding via HTTP query params
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds
- full backend regression remains green

## PR-42 Acceptance

- `GET /api/v1/records/activity-contributors` returns top RM contributors within the requested window
- `days` defaults to 28 and is clamped to 7–90
- `limit` defaults to 5 and is clamped to 1–50
- window is closed from start-of-day of the oldest included day through 23:59:59 of today
- event classification reuses timeline/highlights/breakdown families: declared, undeclared, categoryAssigned, governanceChange
- unclassified RM events (e.g. `RM_RECORD_UNDECLARE_BLOCKED`) are not counted; users with only unclassified events are excluded
- null/blank audit username is surfaced as `username: null`, `label: "(System)"`
- contributors are ordered by totalCount desc, then label asc (case-insensitive)
- each contributor includes `declaredCount`, `undeclaredCount`, `categoryAssignedCount`, `governanceChangeCount`, `totalCount`, `lastEventTime`
- endpoint is admin-only, consistent with all existing RM analytics endpoints
- no new database tables or migrations
- 4 service tests and 2 controller tests added
- RM admin page renders an `RM Activity Contributors` card using the new endpoint
- each contributor row can drill into the existing `Records Audit` table using `username + from + to`
- contributor drilldown reuses the existing audit load path rather than adding a second evidence surface
- contributor load is isolated so the rest of the RM page remains usable if that endpoint fails
- targeted backend regression remains green
- full backend regression remains green
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-43 Acceptance

- `GET /api/v1/records/audit` accepts optional `family`
- `family` values are:
  - `DECLARED`
  - `UNDECLARED`
  - `CATEGORY_ASSIGNED`
  - `GOVERNANCE_CHANGE`
- invalid `family` values are rejected during enum binding with HTTP `400`
- `family` is optional; omitting it preserves current behavior (all `RM_%` events)
- `family` is additive with existing `eventType`, `username`, `from`, `to`, `page`, `size`
- when both `family` and `eventType` are provided, the result is the intersection
- conflicting `family + eventType` returns an empty page without repository access
- family mapping reuses `RM_TIMELINE_GOVERNANCE_EVENTS` and the existing RM analytics family model
- endpoint remains admin-only
- no new database tables or migrations
- no new RM audit endpoint
- `Records Audit` adds a `Family` filter on top of the existing audit load path
- `RM Activity Contributors` exposes family-level drilldown actions per contributor row
- contributor family drilldown pre-fills:
  - `family`
  - `username`
  - `from`
  - `to`
- contributor family drilldown reuses the existing `Records Audit` evidence table and banner instead of creating a second evidence surface
- targeted backend RM controller/service regression remains green
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-44 Acceptance

- backend exposes `GET /api/v1/records/activity-event-types`
- `days` defaults to 28 and is clamped to `7..90`
- `limit` defaults to 8 and is clamped to `1..20`
- the returned window is the recent closed range from the oldest included day at start-of-day through today at `23:59:59`
- source data is existing `RM_%` audit activity only
- aggregation is by exact `eventType`
- response rows expose:
  - `eventType`
  - `family`
  - `count`
  - `lastEventTime`
- rows are ordered by `count desc`, then `eventType asc`
- `family` uses best-effort classification:
  - `DECLARED`
  - `UNDECLARED`
  - `CATEGORY_ASSIGNED`
  - `GOVERNANCE_CHANGE`
  - `OTHER`
- endpoint remains admin-only
- no new tables or migrations
- RM admin page renders an `RM Activity Event Hotspots` card using the new endpoint
- each hotspot row can drill into the existing `Records Audit` table using `eventType + from + to`
- hotspot drilldown reuses the existing audit load path and banner instead of creating a second evidence surface
- hotspot load is isolated so the rest of the RM page remains usable if that endpoint fails
- targeted backend RM controller/service regression remains green
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-45 Acceptance

- backend exposes `GET /api/v1/records/activity-families`
- `days` defaults to 28 and is clamped to `7..90`
- the returned window is the recent closed range from the oldest included day at start-of-day through today at `23:59:59`
- source data is existing `RM_%` audit activity only
- aggregation is by activity family:
  - `DECLARED`
  - `UNDECLARED`
  - `CATEGORY_ASSIGNED`
  - `GOVERNANCE_CHANGE`
  - `OTHER`
- response exposes:
  - `days`
  - `totalCount`
  - `families[].family`
  - `families[].count`
  - `families[].lastEventTime`
- rows are ordered by `count desc`, then stable family rank
- endpoint remains admin-only
- no new tables or migrations
- `GET /api/v1/records/audit?family=OTHER` is supported and stays compatible with the rest of the RM family model
- RM admin page renders an `RM Activity Family Mix` card using the new endpoint
- each family row can drill into the existing `Records Audit` table using `family + from + to`
- family drilldown reuses the existing audit load path and banner instead of creating a second evidence surface
- family-mix load is isolated so the rest of the RM page remains usable if that endpoint fails
- audit `Family` filter now exposes `Other`
- targeted backend RM controller/service regression remains green
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-46 Acceptance

- backend exposes `GET /api/v1/records/activity-family-highlights`
- `windowDays` defaults to 7 and is clamped to `2..30`
- current window is the recent closed range from the oldest included day at start-of-day through today at `23:59:59`
- previous window is the immediately preceding closed window of equal size
- source data is existing `RM_%` audit activity only
- response exposes:
  - `windowDays`
  - `currentWindow.fromDay`
  - `currentWindow.toDay`
  - `previousWindow.fromDay`
  - `previousWindow.toDay`
  - `families[].family`
  - `families[].currentCount`
  - `families[].previousCount`
  - `families[].delta`
  - `families[].lastEventTime`
- family set stays aligned with the existing RM family model:
  - `DECLARED`
  - `UNDECLARED`
  - `CATEGORY_ASSIGNED`
  - `GOVERNANCE_CHANGE`
  - `OTHER`
- rows are ordered by strongest recent family presence, then current-window contribution, then stable family rank
- endpoint remains admin-only
- no new tables or migrations
- RM admin page renders an `RM Activity Family Highlights` card using the new endpoint
- each family row can drill into the existing `Records Audit` table for:
  - current window
  - previous window
- family highlight drilldown reuses the existing audit load path and banner instead of creating a second evidence surface
- family-highlights load is isolated so the rest of the RM page remains usable if that endpoint fails
- targeted backend RM controller/service regression remains green
- targeted frontend regression remains green
- full frontend regression remains green
- frontend production build succeeds

## PR-47 Acceptance

- backend exposes `GET /api/v1/records/activity-family-report`
- endpoint remains admin-only
- no new tables or migrations
- endpoint supports:
  - JSON by default
  - `format=csv` export on the same path
- source data remains existing `RM_%` audit activity only
- custom ranges use an explicit closed interval `[from, to]`
- `from` and `to` must be supplied together for custom ranges
- custom ranges over `90` days are rejected with `400`
- when `from` and `to` are omitted, the endpoint uses the recent closed `28`-day range from oldest included day at `00:00:00` through today at `23:59:59`
- previous window is the immediately preceding closed interval of equal duration
- `eventTypeLimit` defaults to `3` and is clamped to `1..10`
- `contributorLimit` defaults to `3` and is clamped to `1..10`
- JSON response exposes:
  - `currentWindow.from`
  - `currentWindow.to`
  - `previousWindow.from`
  - `previousWindow.to`
  - `eventTypeLimit`
  - `contributorLimit`
  - `currentTotalCount`
  - `previousTotalCount`
  - `families[]`
- each family row exposes:
  - `family`
  - `currentCount`
  - `previousCount`
  - `delta`
  - `lastEventTime`
  - `topEventTypes[]`
  - `topContributors[]`
- family classification stays aligned with existing RM analytics:
  - `DECLARED`
  - `UNDECLARED`
  - `CATEGORY_ASSIGNED`
  - `GOVERNANCE_CHANGE`
  - `OTHER`
- CSV export is a flattened one-row-per-family rendering of the same report DTO
- implementation reuses existing RM audit aggregate queries and does not add a second evidence surface
- targeted backend RM controller/service regression remains green

## PR-48 Acceptance

- backend exposes `GET /api/v1/records/activity-family-trend`
- endpoint remains admin-only
- no new tables or migrations
- source data remains existing `RM_%` audit activity only
- `days` defaults to `28` and is clamped to `7..90`
- `bucketDays` defaults to `7` and is clamped to `1..14`
- `bucketDays` is capped to `days`
- response exposes:
  - `days`
  - `bucketDays`
  - `buckets[]`
- each bucket exposes:
  - `label`
  - `fromDay`
  - `toDay`
  - `activeDayCount`
  - `totalCount`
  - `familyCounts[]`
- each family count row exposes:
  - `family`
  - `count`
- family classification stays aligned with the existing RM family model:
  - `DECLARED`
  - `UNDECLARED`
  - `CATEGORY_ASSIGNED`
  - `GOVERNANCE_CHANGE`
  - `OTHER`
- implementation reuses existing daily RM audit aggregation and does not add a second evidence surface
- targeted backend RM controller/service regression remains green

## PR-49 Acceptance

- backend exposes `GET /api/v1/records/activity-event-type-trend`
- endpoint remains admin-only
- no new tables or migrations
- source data remains existing `RM_%` audit activity only
- `days` defaults to `28` and is clamped to `7..90`
- `bucketDays` defaults to `7` and is clamped to `1..14`
- `bucketDays` is capped to `days`
- `limit` defaults to `8` and is clamped to `1..20`
- response exposes:
  - `days`
  - `bucketDays`
  - `limit`
  - `trackedEventTypes[]`
  - `buckets[]`
- each tracked event type exposes:
  - `eventType`
  - `family`
  - `count`
  - `lastEventTime`
- each bucket exposes:
  - `label`
  - `fromDay`
  - `toDay`
  - `activeDayCount`
  - `totalCount`
  - `otherCount`
  - `eventTypeCounts[]`
- each bucket event-type count exposes:
  - `eventType`
  - `family`
  - `count`
- tracked event types are the current-window top-N exact RM event types
- bucket event counts are limited to the tracked top-N set
- `otherCount` preserves RM bucket activity outside the tracked set
- implementation reuses existing RM audit aggregate queries and does not add a second evidence surface
- targeted backend RM controller/service regression remains green

## PR-50 Acceptance

- backend exposes `GET /api/v1/records/activity-event-type-report`
- endpoint remains admin-only
- no new tables or migrations
- source data remains existing `RM_%` audit activity only
- endpoint supports:
  - JSON by default
  - `format=csv` export on the same path
- custom ranges use an explicit closed interval `[from, to]`
- `from` and `to` must be supplied together for custom ranges
- custom ranges over `90` days are rejected with `400`
- when `from` and `to` are omitted, the endpoint uses the recent closed `28`-day range from oldest included day at `00:00:00` through today at `23:59:59`
- previous window is the immediately preceding closed interval of equal duration
- `limit` defaults to `8` and is clamped to `1..20`
- JSON response exposes:
  - `currentWindow.from`
  - `currentWindow.to`
  - `previousWindow.from`
  - `previousWindow.to`
  - `limit`
  - `currentTotalCount`
  - `previousTotalCount`
  - `eventTypes[]`
- each event-type row exposes:
  - `eventType`
  - `family`
  - `currentCount`
  - `previousCount`
  - `delta`
  - `lastEventTime`
- family classification stays aligned with the existing RM family model:
  - `DECLARED`
  - `UNDECLARED`
  - `CATEGORY_ASSIGNED`
  - `GOVERNANCE_CHANGE`
  - `OTHER`
- CSV export is a flattened one-row-per-event-type rendering of the same report DTO
- implementation reuses existing RM audit aggregate queries and does not add a second evidence surface
- targeted backend RM controller/service regression remains green

## PR-51 Acceptance

- backend exposes `GET /api/v1/records/activity-contributor-report`
- endpoint remains admin-only
- no new tables or migrations
- source data remains existing `RM_%` audit activity only
- endpoint supports:
  - JSON by default
  - `format=csv` export on the same path
- custom ranges use an explicit closed interval `[from, to]`
- `from` and `to` must be supplied together for custom ranges
- custom ranges over `90` days are rejected with `400`
- when `from` and `to` are omitted, the endpoint uses the recent closed `28`-day range from oldest included day at `00:00:00` through today at `23:59:59`
- previous window is the immediately preceding closed interval of equal duration
- contributor `limit` defaults to `5` and is clamped to `1..50`
- `eventTypeLimit` defaults to `3` and is clamped to `1..10`
- JSON response exposes:
  - `currentWindow.from`
  - `currentWindow.to`
  - `previousWindow.from`
  - `previousWindow.to`
  - `limit`
  - `eventTypeLimit`
  - `currentTotalCount`
  - `previousTotalCount`
  - `contributors[]`
- each contributor row exposes:
  - `username`
  - `label`
  - `currentCount`
  - `previousCount`
  - `delta`
  - `lastEventTime`
  - `currentTopEventTypes[]`
- each nested current event-type row exposes:
  - `eventType`
  - `family`
  - `count`
  - `lastEventTime`
- blank usernames continue to surface as `(System)`
- contributor totals are full-window totals, while contributor rows remain top-N constrained
- CSV export is a flattened one-row-per-contributor rendering of the same report DTO
- implementation reuses existing RM audit aggregate queries and does not add a second evidence surface
- targeted backend RM controller/service regression remains green

## PR-52 Acceptance

- backend exposes `GET /api/v1/records/activity-contributor-family-report`
- endpoint remains admin-only
- no new tables or migrations
- source data remains existing `RM_%` audit activity only
- endpoint supports:
  - JSON by default
  - `format=csv` export on the same path
- custom ranges use an explicit closed interval `[from, to]`
- `from` and `to` must be supplied together for custom ranges
- custom ranges over `90` days are rejected with `400`
- when `from` and `to` are omitted, the endpoint uses the recent closed `28`-day range from oldest included day at `00:00:00` through today at `23:59:59`
- previous window is the immediately preceding closed interval of equal duration
- contributor `limit` defaults to `5` and is clamped to `1..50`
- JSON response exposes:
  - `currentWindow.from`
  - `currentWindow.to`
  - `previousWindow.from`
  - `previousWindow.to`
  - `limit`
  - `currentTotalCount`
  - `previousTotalCount`
  - `contributors[]`
- each contributor row exposes:
  - `username`
  - `label`
  - `currentCount`
  - `previousCount`
  - `delta`
  - `lastEventTime`
  - `families[]`
- each nested family row exposes:
  - `family`
  - `currentCount`
  - `previousCount`
  - `delta`
  - `lastEventTime`
- blank usernames continue to surface as `(System)`
- nested family classification stays aligned with the existing RM family model:
  - `DECLARED`
  - `UNDECLARED`
  - `CATEGORY_ASSIGNED`
  - `GOVERNANCE_CHANGE`
  - `OTHER`
- contributor totals are full-window totals, while contributor rows remain top-N constrained
- CSV export is a flattened one-row-per-contributor-family rendering of the same report DTO
- implementation reuses existing RM audit aggregate queries and does not add a second evidence surface
- targeted backend RM controller/service regression remains green

## PR-53 Acceptance

- backend exposes `GET /api/v1/records/activity-contributor-trend`
- endpoint remains admin-only
- no new tables or migrations
- source data remains existing `RM_%` audit activity only
- endpoint is JSON-only
- `days` defaults to `28` and is clamped to `7..90`
- `bucketDays` defaults to `7`, is clamped to `1..14`, and is capped at the effective `days`
- `limit` defaults to `5` and is clamped to `1..20`
- JSON response exposes:
  - `days`
  - `bucketDays`
  - `limit`
  - `trackedContributors[]`
  - `buckets[]`
- each tracked contributor row exposes:
  - `username`
  - `label`
  - `count`
  - `lastEventTime`
- each bucket exposes:
  - `label`
  - `fromDay`
  - `toDay`
  - `activeDayCount`
  - `totalCount`
  - `otherCount`
  - `contributorCounts[]`
- each bucket contributor row exposes:
  - `username`
  - `label`
  - `count`
- tracked contributors are the current-window top-N contributors by total RM activity
- bucket contributor counts are constrained to the tracked set
- `otherCount` preserves RM bucket activity outside the tracked contributor set
- blank usernames continue to surface as `(System)`
- implementation adds one additive repository query for daily contributor buckets and does not add a second evidence surface
- targeted backend RM controller/service regression remains green

## PR-54 Acceptance

- backend exposes `GET /api/v1/records/activity-contributor-highlights`
- endpoint remains admin-only
- no new tables or migrations
- no new repository queries
- source data remains existing `RM_%` audit activity only
- endpoint is JSON-only
- `windowDays` defaults to `7` and is clamped to `2..30`
- `limit` defaults to `5` and is clamped to `1..50`
- JSON response exposes:
  - `windowDays`
  - `limit`
  - `currentWindow`
  - `previousWindow`
  - `contributors[]`
- each contributor row exposes:
  - `username`
  - `label`
  - `currentCount`
  - `previousCount`
  - `delta`
  - `lastEventTime`
- current and previous windows are adjacent closed intervals of equal duration
- contributor rows are built from the union of current-window and previous-window contributors
- previous-only contributors are preserved when they still fall inside the top-N after sorting
- blank usernames continue to surface as `(System)`
- implementation reuses the existing contributor aggregation path and does not add a second evidence surface
- targeted backend RM controller/service regression remains green

## PR-55 Acceptance

- backend exposes `GET /api/v1/records/activity-contributor-family-highlights`
- endpoint remains admin-only
- no new tables or migrations
- no new repository queries
- source data remains existing `RM_%` audit activity only
- endpoint is JSON-only
- `windowDays` defaults to `7` and is clamped to `2..30`
- contributor `limit` defaults to `5` and is clamped to `1..50`
- JSON response exposes:
  - `windowDays`
  - `limit`
  - `currentWindow`
  - `previousWindow`
  - `contributors[]`
- each contributor row exposes:
  - `username`
  - `label`
  - `currentCount`
  - `previousCount`
  - `delta`
  - `lastEventTime`
  - `families[]`
- each nested family row exposes:
  - `family`
  - `currentCount`
  - `previousCount`
  - `delta`
  - `lastEventTime`
- current and previous windows are adjacent closed intervals of equal duration
- contributor rows are built from the union of current-window and previous-window contributors
- nested family rows are built from the union of current-window and previous-window families per contributor
- previous-only contributors are preserved when they still fall inside the top-N after sorting
- blank usernames continue to surface as `(System)`
- implementation reuses the existing contributor-family aggregation path and does not add a second evidence surface
- targeted backend RM controller/service regression remains green

## PR-56 Acceptance

- backend exposes `GET /api/v1/records/activity-contributor-event-type-report`
- endpoint remains admin-only
- no new tables or migrations
- no new repository queries
- source data remains existing `RM_%` audit activity only
- endpoint supports `json` and `csv`
- `from` and `to` remain optional together and continue to follow the existing closed-range comparison semantics
- contributor `limit` defaults to `5` and is clamped to `1..50`
- nested event-type `eventTypeLimit` defaults to `3` and is clamped to `1..10`
- JSON response exposes:
  - `currentWindow`
  - `previousWindow`
  - `limit`
  - `eventTypeLimit`
  - `currentTotalCount`
  - `previousTotalCount`
  - `contributors[]`
- each contributor row exposes:
  - `username`
  - `label`
  - `currentCount`
  - `previousCount`
  - `delta`
  - `lastEventTime`
  - `eventTypes[]`
- each nested event-type row exposes:
  - `eventType`
  - `family`
  - `currentCount`
  - `previousCount`
  - `delta`
  - `lastEventTime`
- contributor rows are built from the union of current-window and previous-window contributors
- nested event-type rows are built from the union of current-window and previous-window event types per contributor
- previous-only contributors are preserved when they still fall inside the top-N after sorting
- blank usernames continue to surface as `(System)`
- implementation reuses the existing contributor comparison path and does not add a second evidence surface
- targeted backend RM controller/service regression remains green

## PR-57 Acceptance

- backend exposes `GET /api/v1/records/activity-contributor-event-type-highlights`
- endpoint remains admin-only
- no new tables or migrations
- no new repository queries
- source data remains existing `RM_%` audit activity only
- endpoint is JSON-only
- `windowDays` defaults to `7` and is clamped to `2..30`
- contributor `limit` defaults to `5` and is clamped to `1..50`
- nested event-type `eventTypeLimit` defaults to `3` and is clamped to `1..10`
- JSON response exposes:
  - `windowDays`
  - `limit`
  - `eventTypeLimit`
  - `currentWindow`
  - `previousWindow`
  - `contributors[]`
- each contributor row exposes:
  - `username`
  - `label`
  - `currentCount`
  - `previousCount`
  - `delta`
  - `lastEventTime`
  - `eventTypes[]`
- each nested event-type row exposes:
  - `eventType`
  - `family`
  - `currentCount`
  - `previousCount`
  - `delta`
  - `lastEventTime`
- current and previous windows are adjacent closed intervals of equal duration
- contributor rows are built from the union of current-window and previous-window contributors
- nested event-type rows are built from the union of current-window and previous-window exact event types per contributor
- previous-only contributors are preserved when they still fall inside the top-N after sorting
- blank usernames continue to surface as `(System)`
- implementation reuses the existing contributor event-type merge path and does not add a second evidence surface
- targeted backend RM controller/service regression remains green

## PR-58 Acceptance

- backend exposes `GET /api/v1/records/activity-contributor-event-type-trend`
- endpoint remains admin-only
- no new tables or migrations
- no new repository queries
- source data remains existing `RM_%` audit activity only
- endpoint is JSON-only
- `days` defaults to `28` and is clamped to `7..90`
- `bucketDays` defaults to `7` and is clamped to `1..30`, then capped by effective `days`
- contributor `limit` defaults to `5` and is clamped to `1..20`
- nested event-type `eventTypeLimit` defaults to `10` and is clamped to `1..10`
- JSON response exposes:
  - `days`
  - `bucketDays`
  - `limit`
  - `eventTypeLimit`
  - `trackedContributors[]`
  - `buckets[]`
- each bucket exposes:
  - `label`
  - `fromDay`
  - `toDay`
  - `activeDayCount`
  - `totalCount`
  - `otherCount`
  - `contributorCounts[]`
- each contributor row exposes:
  - `username`
  - `label`
  - `count`
  - `eventTypes[]`
- each nested event-type row exposes:
  - `eventType`
  - `family`
  - `count`
- tracked contributors are derived from the same full-window top contributor path as `activity-contributor-trend`
- bucket aggregation reuses the existing daily contributor + event-type audit query
- blank usernames continue to surface as `(System)`
- targeted backend RM controller/service regression remains green

## PR-59 Acceptance

- frontend exposes a new `RM Contributor Event-Type Trend` card on `RecordsManagementPage`
- no backend contract changes
- no new tables or migrations
- no new evidence surface beyond the existing `Records Audit` table
- card consumes `GET /api/v1/records/activity-contributor-event-type-trend`
- card remains independently loaded so failures do not block other RM surfaces
- each bucket shows:
  - `label`
  - `totalCount`
  - `activeDayCount`
  - `otherCount`
  - tracked contributors
  - nested event-type actions
- clicking a nested event-type action drills into existing audit with:
  - `username`
  - `eventType`
  - closed-range `from/to` from the selected bucket
- targeted frontend page/service regression remains green
- production build remains green except for the repo's pre-existing two eslint warnings

## PR-60 Acceptance

- frontend exposes a new `RM Contributor Event-Type Highlights` card on `RecordsManagementPage`
- no backend contract changes
- no new tables or migrations
- no new evidence surface beyond the existing `Records Audit` table
- card consumes `GET /api/v1/records/activity-contributor-event-type-highlights`
- card remains independently loaded so failures do not block other RM surfaces
- card shows:
  - current / previous highlight windows
  - contributor-level current / previous totals
  - signed deltas
  - nested exact event-type rows
  - current / previous audit drilldown actions
- clicking a nested current/previous action drills into existing audit with:
  - `username`
  - `eventType`
  - closed-range `from/to` from the selected current or previous highlight window
- targeted frontend page/service regression remains green
- production build remains green except for the repo's pre-existing two eslint warnings

## PR-61 Acceptance

- frontend exposes `Export current CSV` and `Export previous CSV` actions on `RM Contributor Event-Type Highlights`
- no backend contract changes
- no new tables or migrations
- no new evidence surface beyond the existing report/export API and `Records Audit`
- export actions reuse `GET /api/v1/records/activity-contributor-event-type-report`
- current export sends:
  - current-window closed-range `from/to`
  - current `limit`
  - current `eventTypeLimit`
  - `format=csv`
- previous export sends:
  - previous-window closed-range `from/to`
  - current `limit`
  - current `eventTypeLimit`
  - `format=csv`
- targeted frontend service regression remains green
- targeted frontend page regression remains green
- production build remains green except for the repo's pre-existing two eslint warnings

## PR-62 Acceptance

- backend exposes `GET /api/v1/records/activity-contributor-family-trend`
- no frontend change
- no new tables or migrations
- no new evidence surface beyond existing RM analytics endpoints
- endpoint returns:
  - `days`
  - `bucketDays`
  - `limit`
  - `trackedContributors[]`
  - `buckets[]`
- each bucket returns:
  - `label`
  - `fromDay`
  - `toDay`
  - `activeDayCount`
  - `totalCount`
  - `otherCount`
  - `contributorCounts[]`
- each contributor row returns:
  - `username`
  - `label`
  - `count`
  - `families[]`
- each nested family row returns:
  - `family`
  - `count`
- tracked contributors are derived from the same full-window contributor ranking used by existing contributor trend endpoints
- targeted backend RM controller/service regression remains green

## PR-63 Acceptance

- frontend exposes a new `RM Contributor Family Trend` card on `RecordsManagementPage`
- no backend contract changes
- no new tables or migrations
- no new evidence surface beyond the existing `Records Audit` table
- card consumes `GET /api/v1/records/activity-contributor-family-trend`
- card remains independently loaded so failures do not block other RM surfaces
- each bucket shows:
  - `label`
  - `totalCount`
  - `activeDayCount`
  - `otherCount`
  - tracked contributors
  - nested family actions
- clicking a nested family action drills into existing audit with:
  - `username`
  - `family`
  - closed-range `from/to` from the selected bucket
- targeted frontend page/service regression remains green
- production build remains green except for the repo's pre-existing two eslint warnings

## PR-64 Acceptance

- frontend exposes a new `RM Contributor Family Highlights` card on `RecordsManagementPage`
- no backend contract changes
- no new tables or migrations
- no new evidence surface beyond the existing `Records Audit` table
- card consumes `GET /api/v1/records/activity-contributor-family-highlights`
- card remains independently loaded so failures do not block other RM surfaces
- card shows:
  - current / previous highlight windows
  - contributor-level current / previous totals
  - signed deltas
  - nested RM family rows
  - current / previous audit drilldown actions
- clicking a nested current/previous action drills into existing audit with:
  - `username`
  - `family`
  - closed-range `from/to` from the selected current or previous highlight window
- targeted frontend page/service regression remains green
- production build remains green except for the repo's pre-existing two eslint warnings

## PR-65 Acceptance

- frontend exposes `Export current CSV` and `Export previous CSV` actions on `RM Contributor Family Highlights`
- no backend contract changes
- no new tables or migrations
- no new evidence surface beyond the existing report/export API and `Records Audit`
- export actions reuse `GET /api/v1/records/activity-contributor-family-report`
- current export sends:
  - current-window closed-range `from/to`
  - current `limit`
  - `format=csv`
- previous export sends:
  - previous-window closed-range `from/to`
  - current `limit`
  - `format=csv`
- targeted frontend service regression remains green
- targeted frontend page regression remains green
- production build remains green except for the repo's pre-existing two eslint warnings

## PR-66 Acceptance

- frontend exposes `Export current CSV` and `Export previous CSV` actions on `RM Activity Family Highlights`
- no backend contract changes
- no new tables or migrations
- no new evidence surface beyond the existing report/export API and `Records Audit`
- export actions reuse `GET /api/v1/records/activity-family-report`
- current export sends:
  - current-window closed-range `from/to`
  - `format=csv`
- previous export sends:
  - previous-window closed-range `from/to`
  - `format=csv`
- targeted frontend service regression remains green
- targeted frontend page regression remains green
- production build remains green except for the repo's pre-existing two eslint warnings

## PR-67 Acceptance

- frontend exposes `Export current CSV` and `Export previous CSV` actions on `RM Activity Event Hotspots`
- no backend contract changes
- no new tables or migrations
- no new evidence surface beyond the existing report/export API and `Records Audit`
- export actions reuse `GET /api/v1/records/activity-event-type-report`
- current export sends:
  - current-window closed-range `from/to` derived from the existing rolling hotspots horizon
  - current `limit`
  - `format=csv`
- previous export sends:
  - previous-window closed-range `from/to` derived from the immediately preceding equal-length horizon
  - current `limit`
  - `format=csv`
- targeted frontend service regression remains green
- targeted frontend page regression remains green
- production build remains green except for the repo's pre-existing two eslint warnings

## PR-68 Acceptance

- frontend exposes `Export current CSV` and `Export previous CSV` actions on `RM Activity Contributors`
- no backend contract changes
- no new tables or migrations
- no new evidence surface beyond the existing report/export API and `Records Audit`
- export actions reuse `GET /api/v1/records/activity-contributor-report`
- current export sends:
  - current-window closed-range `from/to` derived from the existing rolling contributors horizon
  - current `limit`
  - `format=csv`
- previous export sends:
  - previous-window closed-range `from/to` derived from the immediately preceding equal-length horizon
  - current `limit`
  - `format=csv`
- targeted frontend service regression remains green
- targeted frontend page regression remains green
- production build remains green except for the repo's pre-existing two eslint warnings

## PR-69 Acceptance

- no separate acceptance gate
- `PR-69` is a superseded duplicate planning artifact
- the shipped activity-contributor export behavior is tracked under `PR-68`
- no distinct runtime, API, or UI delta exists beyond `PR-68`

## PR-70 Acceptance

- frontend exposes `Export current CSV` and `Export previous CSV` actions on `RM Activity Family Mix`
- no backend contract changes
- no new tables or migrations
- no new evidence surface beyond the existing report/export API and `Records Audit`
- export actions reuse `GET /api/v1/records/activity-family-report`
- current export sends:
  - current-window closed-range `from/to` derived from the existing rolling family-mix horizon
  - `format=csv`
- previous export sends:
  - previous-window closed-range `from/to` derived from the immediately preceding equal-length horizon
  - `format=csv`
- targeted frontend page regression remains green
- production build remains green except for the repo's pre-existing two eslint warnings

## PR-71 Acceptance

- frontend exposes `Review full timeline audit` on `RM Activity Timeline`
- no backend contract changes
- no new tables or migrations
- no new evidence surface beyond the existing `Records Audit` table
- clicking the shortcut drills into audit with:
  - `from` = first visible timeline day at `00:00:00`
  - `to` = last visible timeline day at `23:59:59`
  - empty `family`, `eventType`, and `username`
- row-level day drilldown remains unchanged
- targeted frontend page regression remains green
- production build remains green except for the repo's pre-existing two eslint warnings

## PR-72 Acceptance

- frontend exposes `Review full breakdown audit` on `RM Activity Breakdown`
- no backend contract changes
- no new tables or migrations
- no new evidence surface beyond the existing `Records Audit` table
- clicking the shortcut drills into audit with:
  - `from` = first visible bucket `fromDay` at `00:00:00`
  - `to` = last visible bucket `toDay` at `23:59:59`
  - empty `family`, `eventType`, and `username`
- row-level bucket drilldown remains unchanged
- targeted frontend page regression remains green
- production build remains green except for the repo's pre-existing two eslint warnings

## PR-73 Acceptance

- plan and acceptance docs identify `PR-69` as a superseded duplicate planning artifact
- `PR-68` remains the authoritative acceptance record for the shipped activity-contributor export UI
- no backend or frontend runtime behavior changes
- no new tests are required because this slice is documentation-only

## PR-74 Acceptance

- milestone docs consolidate `PR-59` through `PR-72` into a single traceable implementation/verification view
- acceptance ownership remains unchanged for the shipped runtime slices
- `PR-69` remains explicitly marked as a superseded duplicate planning artifact
- no backend or frontend runtime behavior changes
- no new tests are required because this slice is documentation-only

## PR-75 Acceptance

- `P4` is explicitly documented as closed from a planning and acceptance perspective
- the shipped `P4` runtime scope is summarized into authoritative capability bands
- future work is redirected into explicit `P5` handoff directions instead of new `P4` runtime slices
- shipped acceptance ownership remains unchanged for all existing `P4` runtime entries
- no backend or frontend runtime behavior changes
- no new tests are required because this slice is documentation-only

## Deferred Acceptance

These are explicitly not part of `PR-16` / `PR-17` / `PR-18` / `PR-19` / `PR-20` / `PR-21A` / `PR-21B` / `PR-21C` / `PR-22` / `PR-23` / `PR-24` / `PR-25` / `PR-26A` / `PR-26B` / `PR-26C` / `PR-26D` / `PR-27` / `PR-28` / `PR-29` / `PR-30` / `PR-31` / `PR-31B` / `PR-32` / `PR-33` / `PR-34` / `PR-35` / `PR-36` / `PR-37` / `PR-38` / `PR-39` / `PR-40` / `PR-41` / `PR-42` / `PR-43` / `PR-44` / `PR-45` / `PR-46` / `PR-47` / `PR-48` / `PR-49` / `PR-50` / `PR-51` / `PR-52` / `PR-53` / `PR-54` / `PR-55` / `PR-56` / `PR-57` / `PR-58` / `PR-59` / `PR-60` / `PR-61` / `PR-62` / `PR-63` / `PR-64` / `PR-65` / `PR-66` / `PR-67` / `PR-68` / `PR-69` / `PR-70` / `PR-71` / `PR-72` / `PR-73` / `PR-74` / `PR-75`:

- admin browse-page hydration of record badges via a dedicated RM list/index
- workspace/system-root target selection for file-plan move in the thin RM page
- deeper RM charts or trend visualizations beyond current summary, health, queue drilldowns, audit, telemetry, and coverage drilldowns
