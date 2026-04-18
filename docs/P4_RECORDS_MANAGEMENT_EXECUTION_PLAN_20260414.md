# P4 Records Management Execution Plan

## Current Status

`PR-16`, `PR-17`, `PR-18`, `PR-19`, `PR-20`, `PR-21A`, `PR-21B`, `PR-21C`, `PR-22`, `PR-23`, `PR-24`, `PR-25`, `PR-26A`, and `PR-26B` are now complete:

- seeded `rm:record`
- added declaration API
- added immutability guardrails on user mutation seams
- added front-end record declaration dialog and preview badge/details UI
- added `FILE_PLAN` folder type
- added record-category foundation on top of existing `categories`
- tightened disposition eligibility to file plans + declared records
- blocked manual archive/restore on RM-governed content
- excluded RM-governed content from archive-policy scope
- added RM summary + audit endpoints on top of `audit_log`
- added legal-hold precheck to folder copy
- added dedicated RM admin page for summary, file plans, record categories, declared records, and audit
- added admin category assignment UI for declared records
- added dedicated admin route and menu entry for RM operations
- blocked trash restore for RM-governed content through the RM policy layer
- added RM operations telemetry for governed bulk import and transfer replication jobs
- added RM operations telemetry UI to the existing RM admin page
- isolated operations telemetry load so RM summary/file-plan/category/record surfaces remain usable if telemetry fails
- blocked bulk import create paths into file plans and file-plan-governed folders
- blocked transfer receiver and loopback replication create paths into RM-governed target folders
- blocked overwrite-on-create paths before RM-governed targets can be deleted or mutated
- added admin-only record undeclare workflow with required reason, legal-hold/file-plan blocks, audit, preview action, and RM admin action
- hardened archive restore to preflight the full archived scope before mutation
- blocked archive restore when the archived scope contains declared records, file plans, or file-plan-governed descendants
- blocked archive restore when the archived scope contains non-archived descendants, working copies, or checked-out documents
- kept archive reopen semantics on the existing restore API and required no frontend change
- enriched RM operations telemetry with failure counts and governance-reason breakdowns
- added `Governance Health` and `Top Governance Reasons` to the RM admin page
- added dedicated RM update/delete endpoints for file plans and record categories
- added safe file-plan/category edit-delete workflow to the RM admin page
- constrained edit semantics to description-only to avoid subtree path drift and declared-record metadata drift
- blocked file-plan delete unless the file plan is empty and unmanaged
- blocked record-category delete unless the category is a non-root unused leaf
- added dedicated RM rename / move endpoints for file plans
- repaired descendant file-plan subtree paths after rename
- added subtree reindex request handling for rename so search does not keep stale descendant paths
- kept file-plan rename / move backend-only so RM admins do not bypass the new contract through generic UI seams
- added dedicated RM rename / move endpoints for record categories
- repaired descendant record-category paths after rename and move
- repaired declared-record RM category metadata after category rename and move
- added after-commit batch node reindex for affected declared records
- kept record-category rename / move backend-only so RM admins do not bypass the hardened metadata-repair contract

## Recommended Next Slices
- optional deeper RM dashboards beyond the current queue drilldowns only if product needs charts or new backend slices
- optional contributor-to-event-family drilldown if operators need more than username + date-range evidence review

## Ordering Constraint

`PR-20` depends on `PR-19` and is complete. `PR-21A`, `PR-21B`, and `PR-21C` all build directly on the authoritative RM service layer and existing job tables. Any later RM follow-up should continue to use the RM APIs as the authoritative source instead of re-deriving governance state client-side.

`PR-23` followed the current RM policy layer instead of adding a second archive mutation surface. The repository term remains `restore`; any softer `reopen` label stays UI-only and should remain scoped to the archive operator page.

`PR-25` intentionally stopped short of rename/move semantics. `PR-26A` and `PR-26B` closed the backend half of that gap, `PR-26C` exposed record-category rename / move through dedicated RM dialogs, and `PR-26D` now does the same for file plans. Workspace/system-root file-plan targets remain intentionally deferred in the thin RM UI.

`PR-26` remains split. Current state:

- `PR-26A`: complete, backend-only file-plan rename / move with subtree path repair and subtree reindex
- `PR-26B`: complete, backend-only record-category rename / move with recursive category-path repair, declared-record metadata repair, and affected-node reindex
- `PR-26C`: complete, thin RM admin UI for record-category rename / move on top of the hardened backend contract
- `PR-26D`: complete, thin RM admin UI for file-plan rename / move on top of the hardened backend contract, limited to alternate file-plan parents
- `PR-27`: complete, archive operator-page `Restore -> Reopen` UI copy cleanup while keeping the authoritative backend/service contract on `restore`
- `PR-28`: complete, RM dashboard drilldown for declared-record queues using front-end-only filters and uncategorized queue review
- `PR-29`: complete, RM file-plan coverage drilldown for declared records outside visible file-plan scope
- `PR-30`: complete, RM governed-operations failure drilldown for recent import/transfer jobs using existing telemetry only
- `PR-31`: complete, RM governed-operations reason drilldown for recent import/transfer jobs using existing telemetry only
- `PR-31B`: complete, RM governed-operations selected-reason UX polish with matched-job context and reason highlighting
- `PR-32`: complete, RM governed-operations exact-status drilldown for recent import/transfer jobs using existing telemetry only
- `PR-33`: complete, RM governed-operations selected-filters summary for import/transfer status and reason combinations
- `PR-34`: complete, RM governed-operations compact scoped-summary copy for active import/transfer filters
- `PR-35`: complete, RM governed-operations scoped matched-count signal for active import/transfer filters
- `PR-36`: complete, RM governed-operations zero-match warning and scoped recover CTA for import/transfer filters
- `PR-37`: complete, RM snapshot dashboard for declared-record coverage and governed-operations queue health using existing summary/telemetry data only
- `PR-38`: complete, RM activity timeline using new audit-backed daily aggregation and a non-blocking admin-page trend card
- `PR-39`: complete, RM activity highlights using new audit-backed comparison windows and a non-blocking admin-page highlights card
- `PR-40`: complete, RM activity breakdown using audit-backed bucket aggregation and a non-blocking admin-page trend-breakdown card
- `PR-41`: complete, full-stack RM activity audit drilldown — extended `GET /api/v1/records/audit` with optional `to` and wired highlights/breakdown/timeline cards into the existing `Records Audit` evidence table
- `PR-42`: complete, full-stack RM activity contributors — audit-backed top-contributor aggregation plus RM-page contributor card and username + date-range drilldown into the existing `Records Audit` table
- `PR-43`: complete, full-stack RM contributor event-family drilldown — extended `GET /api/v1/records/audit` with optional `family`, added `Family` filtering to `Records Audit`, and wired contributor family counters into the existing audit evidence table
- `PR-44`: complete, full-stack RM activity event hotspots — added exact event-type aggregation on top of existing RM audit data and wired hotspot rows into the existing `Records Audit` evidence table
- `PR-45`: complete, full-stack RM activity family mix — added family-level RM activity aggregation, completed `OTHER` family audit filtering, and wired family rows into the existing `Records Audit` evidence table
- `PR-46`: complete, full-stack RM activity family highlights — added current-vs-previous family window comparison and wired per-family current/previous drilldown into the existing `Records Audit` evidence table
- `PR-47`: complete, backend-only RM activity family report/export API — added closed-range JSON/CSV report export with current-vs-previous family totals plus per-family top event types and top contributors, without adding a second evidence surface or new repository queries
- `PR-48`: complete, backend-only RM activity family trend API — added bucketed family-distribution analytics over existing RM daily audit events, without adding frontend work, new queries, or a second evidence surface
- `PR-49`: complete, backend-only RM activity event-type trend API — added top-N tracked event-type buckets plus `otherCount` over existing RM daily audit events, without adding frontend work, new queries, or a second evidence surface
- `PR-50`: complete, backend-only RM activity event-type report/export API — added closed-range JSON/CSV report export for exact RM event types with current-vs-previous comparison, without adding frontend work, new queries, or a second evidence surface
- `PR-51`: complete, backend-only RM activity contributor report/export API — added closed-range JSON/CSV report export for contributors with current-vs-previous comparison and nested current-window top event types, without adding frontend work, new queries, or a second evidence surface
- `PR-52`: complete, backend-only RM activity contributor family report/export API — added closed-range JSON/CSV report export for contributors with current-vs-previous comparison and nested RM family breakdowns, without adding frontend work, new queries, or a second evidence surface
- `PR-53`: complete, backend-only RM activity contributor trend API — added tracked top-contributor buckets plus `otherCount` over recent RM daily audit activity, without adding frontend work or a second evidence surface
- `PR-54`: complete, backend-only RM activity contributor highlights API — added adjacent-window contributor comparison with signed deltas and `(System)` preservation, without adding frontend work, new queries, or a second evidence surface
- `PR-55`: complete, backend-only RM activity contributor family highlights API — added adjacent-window contributor comparison with nested RM family deltas and `(System)` preservation, without adding frontend work, new queries, or a second evidence surface
- `PR-56`: complete, backend-only RM activity contributor event-type report/export API — added closed-range JSON/CSV report export for contributors with nested current-vs-previous exact event-type breakdowns, without adding frontend work, new queries, or a second evidence surface
- `PR-57`: complete, backend-only RM activity contributor event-type highlights API — added adjacent-window contributor comparison with nested current-vs-previous exact event-type deltas and `(System)` preservation, without adding frontend work, new queries, or a second evidence surface
- `PR-58`: complete, backend-only RM activity contributor event-type trend API — added tracked top-contributor bucket analytics with nested exact event-type breakdowns plus `otherCount`, without adding frontend work, new queries, or a second evidence surface
- `PR-59`: complete, full-stack RM contributor event-type trend consumption — added a thin RM page card over the new trend API and routed nested contributor/event-type buttons back into the existing `Records Audit` evidence table
- `PR-60`: complete, full-stack RM contributor event-type highlights consumption — added a thin RM page card over the highlights API and routed nested current/previous contributor event-type actions back into the existing `Records Audit` evidence table
- `PR-61`: complete, thin RM contributor event-type report export UI — reused the existing report/export API to add current/previous CSV actions on the highlights card without adding a new backend surface
- `PR-62`: complete, backend-only RM contributor family trend API — added tracked top-contributor buckets with nested RM family breakdowns plus `otherCount`, without adding frontend work, new queries, or a second evidence surface
- `PR-63`: complete, thin RM contributor family trend consumption — added an RM page card over the new contributor-family trend API and routed nested contributor/family actions back into the existing `Records Audit` evidence table
- `PR-64`: complete, thin RM contributor family highlights consumption — added an RM page card over the contributor-family highlights API and routed nested current/previous contributor-family actions back into the existing `Records Audit` evidence table
- `PR-65`: complete, thin RM contributor family report export UI — reused the existing contributor-family report/export API to add current/previous CSV actions on the family-highlights card without adding a new backend surface
- `PR-66`: complete, thin RM activity family report export UI — reused the existing activity-family report/export API to add current/previous CSV actions on the family-highlights card without adding a new backend surface
- `PR-67`: complete, thin RM activity event-type report export UI — reused the existing activity-event-type report/export API to add current/previous CSV actions on the event-hotspots card without adding a new backend surface
- `PR-68`: complete, thin RM activity contributor report export UI — reused the existing activity-contributor report/export API to add current/previous CSV actions on the contributors card without adding a new backend surface
- `PR-69`: superseded duplicate planning artifact — no distinct product delta beyond `PR-68`; retained only for traceability after the duplicated contributor-export slice was merged into the authoritative `PR-68` entry
- `PR-70`: complete, thin RM activity family mix report export UI — reused the existing activity-family report/export API to add current/previous CSV actions on the family-mix card without adding a new backend surface
- `PR-71`: complete, thin RM activity timeline full-window drilldown — added a card-level shortcut that routes the full visible timeline range into the existing `Records Audit` evidence table without adding backend work or a second audit surface
- `PR-72`: complete, thin RM activity breakdown full-window drilldown — added a card-level shortcut that routes the full visible breakdown range into the existing `Records Audit` evidence table without adding backend work or a second audit surface
- `PR-73`: complete, docs-only P4 plan consistency cleanup — marked `PR-69` as a superseded duplicate and aligned the plan/acceptance ledger with the actual shipped contributor-export slice
- `PR-74`: complete, docs-only RM analytics consumption milestone consolidation — grouped `PR-59` through `PR-72` into one milestone view for implementation scope, acceptance ownership, and verification traceability
- `PR-75`: complete, docs-only P4 closeout and P5 handoff — recorded the closed `P4` RM capability bands, preserved shipped acceptance ownership, and moved future work into explicit `P5` handoff directions instead of extending the `P4` thin-slice ledger
