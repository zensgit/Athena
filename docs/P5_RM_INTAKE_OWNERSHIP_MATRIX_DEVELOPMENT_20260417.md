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
- `PR-110`
  - RM report preset schedule claim-before-upload hardening
  - advances `nextRunAt` through an atomic claim step before scheduled upload and skips already-claimed due presets
  - no new endpoint or migration
- `PR-111`
  - RM summary-only preset CSV and scheduled-delivery support
  - enables `ACTIVITY_FAMILY_HIGHLIGHTS` and `ACTIVITY_FAMILY_MIX` to participate in preset-row export, preset execute CSV, and scheduled CSV delivery by reusing the shipped family-report CSV semantics
  - no new endpoint, table, or migration
- `PR-112`
  - RM summary-only preset schedule/export mocked E2E coverage
  - updates the shipped mocked preset schedule flow so browser-level evidence matches the `PR-111` summary-only preset contract
  - no runtime endpoint, table, or migration change
- `PR-113`
  - RM summary-only preset full-stack/admin smoke
  - adds one non-mocked browser/admin proof across the shipped summary-only preset export and scheduled-delivery contract against the current-code frontend/backend stack
  - no new runtime endpoint, table, or migration change
- `PR-114`
  - RM preset delivery surface refresh and ledger full-stack proof
  - refreshes the parent preset/health/ledger surfaces after successful dialog save/deliver and extends the summary-only full-stack smoke into the page-level delivery ledger
  - no new runtime endpoint, table, or migration change
- `PR-115`
  - RM preset delivery ledger operator mocked browser coverage
  - extends the shipped mocked preset-delivery browser flow into scheduled-delivery health and cross-preset ledger filter/export/zero-match operator paths
  - no new runtime endpoint, table, or migration change
- `PR-116`
  - RM preset delivery ledger full-stack/admin smoke
  - extends the live preset schedule smoke into page-level ledger filter/export/zero-match operator actions against the current frontend/backend stack
  - no new runtime endpoint, table, or migration change
- `PR-117`
  - RM scheduled delivery health full-stack/admin smoke
  - extends the live preset schedule smoke into page-level scheduled-delivery telemetry and the `Scheduled presets` health drilldown against the current frontend/backend stack
  - no new runtime endpoint, table, or migration change
- `PR-118`
  - RM scheduled delivery health operator drilldowns
  - extends the health card into failed-delivery ledger drilldowns and adds browser-level regression coverage for `Due now` and recent-failure operator paths
  - no new runtime endpoint, table, or migration change
- `PR-119`
  - RM scheduled delivery health success-ledger full-stack/admin smoke
  - extends the live preset schedule smoke into the `Last 24h success` health drilldown and its page-level ledger evidence
  - no new runtime endpoint, table, or migration change
- `PR-120`
  - RM scheduled delivery health failure-ledger full-stack/admin smoke
  - extends the live preset schedule smoke into the `Last 24h failed` health drilldown and its page-level ledger/export evidence
  - no new runtime endpoint, table, or migration change
- `PR-121`
  - RM scheduled delivery health due-now full-stack/admin smoke
  - extends the live preset schedule smoke into the `Due now` health drilldown and adds page-level refresh hardening so preset/health/ledger delivery surfaces reload together
  - no new runtime endpoint, table, or migration change
- `PR-123`
  - RM preset delivery failure notification foundation
  - adds owner-scoped inbox notifications for failed scheduled preset deliveries by reusing the shipped activity + notification chain and records-management links
  - no new runtime endpoint, table, or migration change
- `PR-124`
  - RM preset delivery failure notification full-stack/admin smoke
  - adds a minimal admin trigger for due scheduled deliveries and proves that a real scheduled failure reaches the owner inbox and drills back into Records Management
  - no new table or migration change
- `PR-125`
  - RM preset delivery success notification full-stack/admin smoke
  - extends the direct owner inbox alerting lane so successful scheduled deliveries also surface through `/notifications` and can drill into the delivered node
  - no new table or migration change
- `PR-126`
  - RM preset delivery notification preferences
  - adds owner-scoped success/failure inbox alert toggles on top of the shipped People preferences map and makes scheduled delivery notifications honor those values before posting direct inbox entries
  - no new endpoint, table, or migration change
- `PR-127`
  - RM preset delivery notification preferences full-stack smoke
  - adds disabled success/failure notification preference smoke coverage that verifies scheduled execution still completes while matching `/notifications` rows stay absent
  - no runtime code, endpoint, table, or migration change
- `PR-128`
  - RM preset delivery notification publish hardening
  - isolates activity/notification publication failures from delivery execution ledger status so a successful CSV delivery cannot be converted into failed evidence by an inbox outage
  - no endpoint, table, or migration change
- `PR-129`
  - RM preset delivery admin trigger ops posture
  - documents, audits, and security-tests `POST /api/v1/records/report-presets/run-scheduled-deliveries` as an admin/ops trigger
  - no endpoint rename, table, or migration change
- `PR-130`
  - RM preset delivery notification acceptance gate
  - adds one repeatable script and one frontend npm script for the backend notification tests plus the four full-stack notification Playwright flows
  - no runtime endpoint, table, or migration change
- `PR-131`
  - RM preset delivery notification gate hardening
  - switches frontend acceptance selection to a stable Playwright title tag and expands backend defaults to cover activity/inbox notification materialization tests
  - no runtime endpoint, table, or migration change
- `PR-132`
  - RM preset delivery notification CI gate attachment
  - wires the hardened notification acceptance gate into the existing `frontend_e2e_core` CI stack after Keycloak realm readiness
  - no runtime endpoint, table, or migration change
- `PR-133`
  - RM preset delivery notification gate readiness hardening
  - adds bounded retry diagnostics around backend health, Keycloak discovery, and UI reachability checks in the CI-attached acceptance gate
  - no runtime endpoint, table, or migration change

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
