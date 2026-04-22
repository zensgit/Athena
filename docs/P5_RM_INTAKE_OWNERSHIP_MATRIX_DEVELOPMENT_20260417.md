# P5 RM Intake Ownership Matrix Development

## Scope

This document turns the `P4` closeout recommendations into an executable `P5` intake matrix.

It does not add runtime behavior. It defines:

- recommended owner lane
- supporting lanes
- readiness gate
- first executable slice
- first acceptance checkpoint

for each recommended `P5` RM direction.

## Matrix Fields

Each `P5` candidate is evaluated with the same handoff fields:

- `Direction`
- `Primary owner lane`
- `Supporting lanes`
- `Readiness gate`
- `First executable slice`
- `First acceptance checkpoint`

## Intake Matrix

| Direction | Primary owner lane | Supporting lanes | Readiness gate | First executable slice | First acceptance checkpoint |
| --- | --- | --- | --- | --- | --- |
| RM delivery workflows | Core backend integration/reporting | Frontend admin, product/ops, platform delivery | confirm who consumes RM exports, delivery channel, retention/security rules | saved RM report preset + scheduled export foundation | admin can save a preset and schedule one recurring RM export without creating a second evidence surface |
| RM search/index surfaces | Core backend search/indexing | Frontend browse/search, RM admin, product | agree authoritative record badge/index fields and acceptable browse/search hydration cost | indexed record badge and RM coverage projection for browse/search results | browse/search can show RM badge or coverage state from authoritative indexed/backend data without per-row ad hoc hydration |
| RM analytics productization | Core backend analytics | Frontend admin, product/ops | identify 1-2 operator questions not already answered by current cards | chart-ready aggregation for one operator question on top of shipped audit/report data | one curated chart/trend view ships with drilldown preserved to the existing `Records Audit` table |
| RM structure workflows | Core backend RM policy | Frontend admin, product/ops | decide workspace/system-root move policy, preflight semantics, and rollback guardrails | file-plan move orchestration foundation with preflight and explicit allowed targets | admin can preflight and complete an allowed broader-scope file-plan move without path drift, metadata drift, or policy bypass |

## Recommended Intake Order

### First intake wave

- RM search/index surfaces
- RM delivery workflows

These provide the strongest operator value without reopening already-hardened `P4` seams.

### Second intake wave

- RM structure workflows
- RM analytics productization

## Accepted Slices

The accepted `P5` runtime slices on top of this matrix are:

- `PR-76`
  - RM search/index record projection foundation
  - browse/search list visibility for authoritative record state
  - no new backend endpoint
- `PR-77`
  - RM advanced-search record projection consumption
  - advanced-search result-card visibility for authoritative record state
  - no new backend endpoint
- `PR-78`
  - RM record-only search filter foundation
  - end-to-end backend query semantics plus `SearchResults` and `AdvancedSearchPage` filter propagation
  - no new backend endpoint or migration
- `PR-79`
  - RM record-category-path facet and filter foundation
  - end-to-end search facet/filter consumption on `SearchResults`, `AdvancedSearchPage`, and the existing search prefill/saved-search chain
  - no new backend endpoint or migration
- `PR-80`
  - RM saved-search execution record projection fix
  - preserves `record / declared* / recordCategory*` on the `SavedSearchesPage -> executeSavedSearch -> SearchResults?savedSearchId=...` path
  - no new backend endpoint or migration
- `PR-89`
  - RM report preset save-as-preset UI consumption
  - first shipped frontend use of the `PR-83` preset foundation on existing RM report cards
  - no new backend endpoint or migration
- `PR-90`
  - RM report preset list and apply/export UI consumption
  - lists saved presets, applies them to `Records Audit`, and reuses existing CSV export routes
  - no new backend endpoint or migration
- `PR-91`
  - RM report preset edit/delete UI consumption
  - extends the same preset table with maintenance workflows while preserving existing CRUD semantics
  - no new backend endpoint or migration
- `PR-92`
  - RM report preset execute endpoint foundation
  - expands owned preset params and dispatches to existing RM report methods without introducing a second report engine
  - no new table or migration; scheduled delivery remains deferred
- `PR-93`
  - RM report preset scheduled delivery foundation
  - adds schedule metadata, CSV delivery upload, and execution ledger on top of the preset/execute path
  - backend-only; no frontend schedule UI yet
- `PR-96`
  - RM report preset scheduled delivery frontend service layer
  - adds typed schedule/deliver/execution methods and a kind guard that mirrors backend CSV-deliverable kinds
  - no new backend endpoint or migration
- `PR-97`
  - RM report preset schedule dialog component
  - adds load/save/deliver-now/history UI on top of the PR-96 service layer
  - component-only; page wiring remains separate
- `PR-98`
  - RM report preset schedule page wiring
  - adds the preset-row Schedule action and mounts the dialog on `RecordsManagementPage`
  - no new backend endpoint or migration
- `PR-99`
  - RM report preset delivery UI hardening
  - closes shipped follow-ups: summary-only presets stay audit-only in the table, and schedule chips/history refresh after save or manual delivery
  - no new backend endpoint or migration
- `PR-100`
  - RM report preset scheduled delivery mocked E2E
  - adds one browser-level mocked coverage slice across the shipped `PR-95/96/97/98/99` chain
  - no new backend endpoint or migration
- `PR-101`
  - RM report preset scheduled delivery full-stack smoke
  - adds one non-mocked browser/admin proof across the shipped `PR-95/96/97/98/99/100` chain against the current-code frontend/backend stack
  - no new backend endpoint or migration
- `PR-102`
  - RM report preset execution ledger UI polish
  - adds local execution filters, browse navigation to delivered evidence, and stronger schedule summary on top of the shipped scheduled-delivery dialog
  - no new backend endpoint or migration
- `PR-103`
  - RM report preset cross-preset execution ledger/filter/export backend foundation
  - adds owner-scoped ledger JSON and CSV routes, additive preset metadata on execution rows, and specification-based filtering
  - no new table or migration; frontend page-level ledger consumption remains deferred
- `PR-104`
  - RM report preset page-level execution ledger consumption
  - adds page-level preset delivery ledger filters, export reuse, and browse/apply actions on top of `PR-103`
  - no new backend endpoint or migration
- `PR-105`
  - RM report preset page-level execution ledger operator polish
  - adds active-filter summary and zero-match recovery on top of `PR-104`
  - no new backend endpoint or migration
- `PR-109`
  - RM report preset schedule metadata and health drilldown
  - adds additive schedule fields on the preset list plus page-level scheduled/due-now filters and health-card drilldown
  - no new backend endpoint or migration

These are still valid, but they should wait until policy semantics and operator demand are explicit.

## Ownership Guidance

The matrix deliberately uses owner lanes instead of individual names.

That keeps the handoff stable even if team assignment changes, while still making the next executor clear:

- search/index work should start from authoritative backend/index ownership, not frontend-only hydration
- delivery workflows should start from backend/reporting ownership, not ad hoc UI export code
- analytics productization should start from operator questions, not from adding more standalone cards
- structure workflows should start from RM policy ownership, not from generic folder UI seams

## Handoff Constraint

Any accepted `P5` intake should preserve the `P4` invariants:

- RM APIs remain authoritative
- `Records Audit` remains the primary evidence surface unless a new surface is explicitly justified
- new UI work should reuse shipped report/export and drilldown contracts whenever possible

## Intended Use

This matrix is meant to be the intake gate for the next capability phase.

`P5` work should be opened only after one row has:

- a confirmed owner lane
- a satisfied readiness gate
- an agreed first executable slice
- an explicit first acceptance checkpoint
