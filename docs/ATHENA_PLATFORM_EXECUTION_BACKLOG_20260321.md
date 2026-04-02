# Athena Platform Execution Backlog

## Date
- 2026-03-21

## Benchmark Position

### Can Athena surpass the benchmarks after this backlog?
- Yes, but only if "surpass" is defined against the benchmark scopes we actually care about, not against the entire historical surface area of Alfresco or Paperless-ngx.
- Athena can realistically surpass `alfresco-community-repo` on:
  - async task governance and operator control plane
  - preview failure observability and remediation
  - cross-domain export/download task transparency
  - pipeline-oriented automation visibility
- Athena can realistically surpass `paperless-ngx` on:
  - enterprise workflow breadth
  - permission-aware operational governance
  - ECM process/task/document integration
- Athena will not immediately surpass Alfresco in:
  - depth of configurable permission model
  - maturity of model-driven extension surface
  - breadth of legacy ecosystem and integrations
- Athena will not immediately surpass Paperless in:
  - out-of-box custom field and storage-path automation ergonomics
  - ingestion/product polish around consumer-style document intake
  - daily operator task ergonomics until Athena adds a persistent task ledger and acknowledge/dismiss semantics

## Practical Verdict
- If we complete `Phase361B -> Phase367`, Athena should be able to beat the benchmarked products in the selected target zone:
  - enterprise ECM + document processing + operational governance
- If we also complete `Phase368 -> Phase370`, Athena should have a credible "Alfresco governance + Paperless automation" story that is stronger than either benchmark within our intended product scope.
- If we want Athena to also win on operator detail and day-to-day admin sharpness, we need one extra follow-on slice after `Phase362`:
  - persistent operator task ledger
  - acknowledge/dismiss semantics
  - structured preflight warnings for artifact tasks such as batch download
- If we stop before `rendition resource model` and `search contract convergence`, Athena will remain feature-rich but structurally behind the benchmarks.

## Remaining Work Estimate

### Core platform convergence
- `35-50` effective dev days
- Covers:
  - `Phase361B`
  - `Phase362`
  - `Phase363`
  - `Phase364`
  - `Phase365`
  - `Phase366`
  - `Phase367`

### Benchmark-surpass extensions
- `12-22` effective dev days
- Covers:
  - `Phase368`
  - `Phase369`
  - `Phase370`

### Total
- `48-72` effective dev days
- Single-engineer pace: about `10-14` weeks
- Two parallel engineers with clean ownership split: about `5-8` weeks

## Current Completion View
- Unified async task control plane: about `40%`
- First-class rendition resource model: about `10%`
- Unified search contract and DSL: about `20%`
- Pipeline and permission productization: about `15%`
- Whole platform-convergence roadmap: about `25-30%`

## Execution Order
1. Finish async lifecycle contract.
2. Promote rendition to a first-class resource.
3. Collapse search into one query envelope and one consumer model.
4. Expose real pipeline and permission metadata.
5. Add the benchmark-surpass automation surfaces.

## Weekly Execution Plan

### Week 1
- Deliver `Phase361B` and `Phase362`.
- Goal:
  - unify `status/list/cancel/cleanup/download` affordances for async task centers
  - reduce AdminDashboard one-off task summary logic
- Detail-surpass follow-on:
  - if benchmark operator polish becomes immediate priority, seed a design slice for persistent task ledger and structured preflight after `Phase362`
- Output MD:
  - `docs/PHASE361B_ASYNC_TASK_LIFECYCLE_ACTION_CONTRACT_DEV_20260321.md`
  - `docs/PHASE361B_ASYNC_TASK_LIFECYCLE_ACTION_CONTRACT_VERIFICATION_20260321.md`
  - `docs/PHASE362_UNIFIED_ADMIN_TASK_CENTER_DEV_20260321.md`
  - `docs/PHASE362_UNIFIED_ADMIN_TASK_CENTER_VERIFICATION_20260321.md`
  - optional follow-on:
    - `docs/PHASE362A_OPERATOR_TASK_LEDGER_AND_PREFLIGHT_DEV_20260321.md`
    - `docs/PHASE362A_OPERATOR_TASK_LEDGER_AND_PREFLIGHT_VERIFICATION_20260321.md`

### Week 2-3
- Deliver `Phase363` and start `Phase364`.
- Goal:
  - add `RenditionResource` domain model and persistence
  - stop treating preview state as only `Document.preview*` fields
- Output MD:
  - `docs/PHASE363_RENDITION_RESOURCE_MODEL_DEV_20260321.md`
  - `docs/PHASE363_RENDITION_RESOURCE_MODEL_VERIFICATION_20260321.md`
  - `docs/PHASE364_RENDITION_RESOURCE_API_DEV_20260321.md`
  - `docs/PHASE364_RENDITION_RESOURCE_API_VERIFICATION_20260321.md`

### Week 4-5
- Deliver `Phase365` and `Phase366`.
- Goal:
  - collapse search advanced/context/stats/pivot into one query contract
  - remove page-specific search semantics drift
- Output MD:
  - `docs/PHASE365_UNIFIED_SEARCH_QUERY_ENVELOPE_DEV_20260321.md`
  - `docs/PHASE365_UNIFIED_SEARCH_QUERY_ENVELOPE_VERIFICATION_20260321.md`
  - `docs/PHASE366_SEARCH_CONSUMER_CONVERGENCE_DEV_20260321.md`
  - `docs/PHASE366_SEARCH_CONSUMER_CONVERGENCE_VERIFICATION_20260321.md`

### Week 6
- Deliver `Phase367`.
- Goal:
  - expose real upload pipeline registry and capability metadata
  - stop hardcoding processor status
  - expose permission model metadata instead of only enum-shaped APIs
- Output MD:
  - `docs/PHASE367_PIPELINE_PERMISSION_METADATA_REGISTRY_DEV_20260321.md`
  - `docs/PHASE367_PIPELINE_PERMISSION_METADATA_REGISTRY_VERIFICATION_20260321.md`

### Week 7-8
- Deliver benchmark-surpass surfaces `Phase368 -> Phase370`.
- Goal:
  - beat Paperless on automation depth while keeping Athena's ECM governance lead
- Output MD:
  - `docs/PHASE368_TYPED_CUSTOM_FIELDS_DEV_20260321.md`
  - `docs/PHASE368_TYPED_CUSTOM_FIELDS_VERIFICATION_20260321.md`
  - `docs/PHASE369_STORAGE_PATH_ROUTING_DEV_20260321.md`
  - `docs/PHASE369_STORAGE_PATH_ROUTING_VERIFICATION_20260321.md`
  - `docs/PHASE370_SHARE_LINK_POLICY_DOWNLOAD_DEV_20260321.md`
  - `docs/PHASE370_SHARE_LINK_POLICY_DOWNLOAD_VERIFICATION_20260321.md`

## Phase Breakdown

### Phase361B Async Task Lifecycle Action Contract
- Benchmark driver:
  - Alfresco `DownloadsImpl`
  - Paperless task ledger and task UX
- Goal:
  - unify action affordances across async centers
- Work:
  - shared DTO for `status/list/action links`
  - shared semantics for `cancel/download/cleanup`
  - consistent accepted/polling metadata
- Target files:
  - `ecm-core/src/main/java/com/ecm/core/asynctask/*`
  - `ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java`
  - `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`
  - `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`
  - `ecm-core/src/main/java/com/ecm/core/controller/BatchDownloadController.java`
  - `ecm-frontend/src/pages/AdminDashboard.tsx`
- Risk:
  - response compatibility drift

### Phase362 Unified Admin Task Center
- Benchmark driver:
  - Paperless system task center
  - Athena already has a better cross-domain foundation
- Goal:
  - one admin task center instead of per-domain summary islands
- Work:
  - unified filters by domain/status/risk
  - task action rendering from shared affordances
  - shared polling and empty/error states
- Target files:
  - `ecm-frontend/src/pages/AdminDashboard.tsx`
  - `ecm-frontend/src/services/*task*`
- Risk:
  - frontend state complexity if backend affordances are incomplete

### Phase362A Operator Task Ledger And Preflight
- Benchmark driver:
  - Paperless persistent task ledger and acknowledge/dismiss loop
  - Alfresco preflight strictness for download creation
- Goal:
  - move Athena from broad governance into sharper day-to-day operator ergonomics
- Work:
  - durable task ledger instead of only in-memory registries
  - acknowledge/dismiss semantics for completed or failed operator tasks
  - structured preflight warnings/errors for batch download and similar artifact jobs
- Target files:
  - `ecm-core/src/main/java/com/ecm/core/asynctask/*`
  - `ecm-core/src/main/java/com/ecm/core/controller/BatchDownloadController.java`
  - `ecm-core/src/main/java/com/ecm/core/service/BatchDownloadService.java`
  - `ecm-frontend/src/pages/AdminDashboard.tsx`
  - `ecm-frontend/src/pages/SystemStatusPage.tsx`
- Risk:
  - if persistence lands before lifecycle contract convergence, Athena will duplicate task semantics instead of consolidating them

### Phase363 Rendition Resource Model
- Benchmark driver:
  - Alfresco rendition and thumbnail services
- Goal:
  - replace virtual/derived rendition semantics with a real resource model
- Work:
  - new entity/table for rendition resources
  - state model including `QUEUED/RUNNING/READY/FAILED/STALE/INVALIDATED`
  - migration from `Document.preview*` compatibility fields
- Target files:
  - `ecm-core/src/main/java/com/ecm/core/entity/Document.java`
  - `ecm-core/src/main/java/com/ecm/core/entity/PreviewStatus.java`
  - new `rendition` package
  - Liquibase changelog
- Risk:
  - dual-write compatibility period

### Phase364 Rendition Resource API
- Benchmark driver:
  - Alfresco rendition resource APIs
- Goal:
  - give preview/thumbnail first-class APIs
- Work:
  - `/api/v1/nodes/{nodeId}/renditions`
  - `/api/v1/nodes/{nodeId}/renditions/{renditionId}`
  - `requeue`, `invalidate`, diagnostics consumption
- Target files:
  - `ecm-core/src/main/java/com/ecm/core/controller/NodeController.java`
  - `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - `ecm-frontend/src/services/nodeService.ts`
  - preview-related frontend pages
- Risk:
  - relation API compatibility with existing consumers

### Phase365 Unified Search Query Envelope
- Benchmark driver:
  - Alfresco `SearchQuery` style request mapping
- Goal:
  - one request/response model for query, filters, stats, pivot, context, export
- Work:
  - normalized search DSL
  - request echo and explainability contract
  - adapter compatibility for current advanced endpoints
- Target files:
  - `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`
  - search DTO/service packages
- Risk:
  - breaking subtle UI assumptions if normalization is not explicit

### Phase366 Search Consumer Convergence
- Benchmark driver:
  - Athena frontend already over-consumes search semantics
- Goal:
  - one frontend query builder and one result contract
- Work:
  - unify URL state, saved search, stats, pivot, diagnostics trigger paths
  - remove duplicated preview-status-specific branching
- Target files:
  - `ecm-frontend/src/services/nodeService.ts`
  - `ecm-frontend/src/pages/SearchResults.tsx`
  - advanced search pages and store slices
- Risk:
  - UI regression due to large search page complexity

### Phase367 Pipeline And Permission Metadata Registry
- Benchmark driver:
  - Paperless plugin pipeline
  - Alfresco permission extensibility
- Goal:
  - expose real processor registry and permission metadata
- Work:
  - upload pipeline registry endpoint
  - processor capabilities and order exposure
  - permission model metadata API
- Target files:
  - `ecm-core/src/main/java/com/ecm/core/pipeline/*`
  - `ecm-core/src/main/java/com/ecm/core/controller/UploadController.java`
  - `ecm-core/src/main/java/com/ecm/core/controller/SecurityController.java`
  - `ecm-core/src/main/java/com/ecm/core/entity/Permission.java`
  - `ecm-core/src/main/java/com/ecm/core/entity/PermissionSet.java`
- Risk:
  - stopping halfway leaves metadata APIs half-static

### Phase368 Typed Custom Fields
- Benchmark driver:
  - Paperless custom fields
- Goal:
  - typed metadata fields with validation and search integration
- Work:
  - field definitions
  - field values
  - query support
  - UI editing and filtering
- Target files:
  - new custom field domain and search integration
- Risk:
  - search DSL needs to be stable first

### Phase369 Storage Path Routing
- Benchmark driver:
  - Paperless storage paths and workflow matching
- Goal:
  - route/assign archive storage path using rules and workflow signals
- Work:
  - path templates
  - test interface
  - batch backfill/rewrite
- Target files:
  - pipeline, workflow, storage services
- Risk:
  - clashes with rendition/storage migration if sequenced too early

### Phase370 Share Link And Policy Download
- Benchmark driver:
  - Alfresco download/share semantics
- Goal:
  - controlled share links and policy-aware download flows
- Work:
  - expiring links
  - download policy hooks
  - audit visibility
- Target files:
  - node/download/security services and related UI
- Risk:
  - security policy model should be at least partially metadata-driven first

## Immediate Next Slice
- Recommended now:
  - implement `Phase361B`
- Reason:
  - it is the shortest path to reducing controller duplication further
  - it unlocks a real unified admin task center
  - it improves current operations without waiting for the rendition migration
