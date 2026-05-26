# Alfresco Parity Benchmark Refresh (2026-05-26)

## Verdict

Do not open an Alfresco-driven implementation slice by default.

The local Alfresco reference project is still useful as a maturity benchmark, but the old "catch up to Alfresco" backlog is stale. Athena has already closed many items that the January/March gap documents listed as missing, and the recent product-discovery line already concluded that auto-picking more product features should pause absent operator demand.

The right use of Alfresco now is:

1. **Compatibility discovery** when a customer or integration explicitly needs Alfresco-like protocol behavior.
2. **Sales/readiness checklist** when Athena is compared against Alfresco in a procurement conversation.
3. **Risk calibration** for architecture areas where Alfresco is deeper than Athena, especially protocol conformance and repository extension semantics.

The wrong use is to turn every remaining Alfresco difference into a feature queue. Most remaining deltas are not small operator workflow slices; they are protocol, storage, runtime, or architecture projects that need a buyer signal.

## Method

Read-only comparison against:

- Existing Athena Alfresco docs:
  - `docs/ALFRESCO_GAP_ANALYSIS_20260129.md`
  - `docs/ATHENA_VS_ALFRESCO_GAP_ANALYSIS_20260326.md`
  - `docs/ATHENA_SURPASS_ALFRESCO_DEVELOPMENT_PLAN_20260329.md`
- Current product posture:
  - `docs/PRODUCT_CAPABILITY_DISCOVERY_REFRESH4_20260526.md`
  - `docs/HANDOFF_HARDENING_20260526.md`
- Local Alfresco source:
  - `reference-projects/alfresco-community-repo/`
- Current Athena backend/frontend surfaces under `ecm-core/` and `ecm-frontend/`.
- Companion narrow deep-dive (service-contract level):
  - `docs/ALFRESCO_SERVICE_CONTRACT_PARITY_20260526.md` — method-level parity of the `com.ecm.core.alfresco.*` shim; finds it is unused, self-contained Alfresco-*shaped* scaffolding (4-way no-consumer verification), confirms CMIS is the real consumed interop contract, and that C4's stubs live in unconsumed code.

No tests were run for this benchmark. No production/test/frontend/config files were changed.

## Reference Baseline

Alfresco Community Repo exposes a broad repository kernel through service interfaces and protocol/integration test suites:

- Core repository services: `NodeService`, `ContentService`, `VersionService`, `PermissionService`, `SearchService`, `LockService`, `CheckOutCheckInService`, `RuleService`, `AuditService`, `TaggingService`, `RatingService`, `CategoryService`.
- Collaboration/services: `SiteService`, `PreferenceService`, `InvitationService`, `QuickShareService`, `DownloadService`.
- Protocol/extension surfaces: `AlfrescoCmisServiceImpl`, `WebDAVLockServiceImpl`, `ImapService`, importer/exporter services, transfer services, policy components.
- Integration coverage is a key differentiator: Alfresco TAS scenarios combine CMIS, WebDAV, FTP/IMAP, REST, sites, permissions, favorites, ratings, versions, tasks, and deletion semantics across protocols.

This means "Alfresco parity" should not be interpreted as endpoint count. The deeper benchmark is cross-protocol semantic consistency and long-running repository behavior.

## Athena Current Coverage

### Mostly closed vs older gap docs

| Area | Old gap claim | Current Athena evidence | Refresh judgment |
| --- | --- | --- | --- |
| Search totals/facets/suggestions | January P0 listed accurate totals, ES aggregations, suggestions as missing | `SearchController` exposes faceted/search/suggest/spellcheck paths; `FacetedSearchService` uses ES total hits, aggregations, highlights, suggestions, spellcheck fallback. Saved-search CSV export is also delivered. | Old P0 is stale; no feature slice from this row. |
| Version history | January P0 listed paginated history and major-only filter | `DocumentController` has version list, paged version list, version export, compare, download, revert; `VersionService` backs the flow. | Closed for product use. |
| Preview/rendition status | January P0 listed persisted status/failure reason | `RenditionResourceService`, `RenditionResourceController`, `PreviewDiagnosticsController`, queue/repair/failure/export paths exist. | Athena is stronger operationally than the old gap assumed. |
| Permission-set mapping | January P0 listed explicit mapping as missing | Security/permission services and recent controller-security work exist; bulk/share flows preserve permission contracts. | Product surface mostly closed; Alfresco-level deny/inheritance semantics require a separate customer-driven compatibility audit, not a default slice. |
| Audit query/export/toggles | January P0 listed filtered API/UI/export presets/category toggles | `AnalyticsController` has audit export, async export, presets, event types, retention, category list/update, cleanup. `AuditService` supports category settings. | Old P0 is stale. |
| Mail direct routing / preview export | January P0 listed email-to-node routing; product refresh later listed preview export | `MailFetcherService` has direct routing via `resolveRoutingTarget` / `buildDirectRoutingRule`; `MailAutomationController` has rule preview and one-shot preview export. | Old mail P0 is closed for Athena's mail-client automation model. |
| Content model/dictionary/aspects | March plan listed these as core missing items | `ContentModelController`, `DictionaryController`, aspect endpoints on `NodeController`, and CMIS secondary type mapping exist. | Closed enough for current product posture. |
| Lock / checkout / checkin | March plan listed enhanced lock + working-copy COCI | `LockService` has lock types, recursive/deep lock, batch lock/unlock, suspend/enable; `CheckOutCheckInService` uses working-copy documents and checkin/cancel flows. | Closed enough for product use; protocol conformance remains separate. |
| Sites/social/preferences | March plan listed sites, activity, following, preferences, invitation | `SiteController`, `SiteInvitationController`, `ActivityController`, `FavoriteController`, `FollowingController`, `PeopleController` preferences exist. | Broadly closed; UI polish should be demand-driven. |
| Ratings/tags/categories/templates/scripts/blog/calendar/discussion | March plan listed multiple collaboration gaps | Controllers/services now exist for ratings, tags, categories, templates, scripts, blog, calendar, discussion. | Endpoint presence is no longer the blocker. |
| Bulk/share/download/RM/legal hold | Not all were in old Alfresco docs, but were recent product gaps | Bulk site invitations, legal-hold bulk apply, bulk record declaration, bulk share-link creation, batch download governance are delivered. | Recent operator-shaped gaps have been exhausted. |

### Still meaningfully different from Alfresco

| Area | Alfresco depth | Athena current state | Recommendation |
| --- | --- | --- | --- |
| Alfresco Foundation API shim | Alfresco exposes dozens of Foundation API services through `ServiceRegistry` and public service interfaces. | Athena has a narrow local `com.ecm.core.alfresco.*` shim (`AlfrescoNodeService`, `AlfrescoContentService`, `AlfrescoSearchService`, `AlfrescoPermissionService`, registry/model types). It imports Athena types, not `org.alfresco.*`; whole-repo grep shows no real consumer outside the shim/old docs. `AlfrescoContentService` still has direct-stream/transform stubs. | Do not expand this into a 57-service compatibility layer. Treat it as inert scaffolding unless a drop-in-Alfresco buyer appears. If clarity is needed, document or delete the shim as a separate hygiene decision. |
| Cross-protocol conformance | Alfresco has mature CMIS/WebDAV/IMAP/FTP integration semantics and TAS scenarios that assert cross-protocol consistency. | Athena has CMIS Browser/AtomPub services, WebDAV support docs/code, WOPI/transfer sidecars, but no evidence that an Alfresco-like cross-protocol TAS suite has been run. | If compatibility matters, open a **read-only CMIS/WebDAV conformance discovery** first. Do not add protocol features blindly. |
| IMAP server | Alfresco exposes repository content as IMAP mailboxes. | Athena is an IMAP/OAuth mail client automation system: account setup, fetch, rules, diagnostics, replay, preview export, direct routing. It is not an IMAP server. | Defer. Only build if a customer explicitly needs repository-as-mailbox semantics. |
| Direct access / presigned URL | Alfresco exposes content access patterns through repository/protocol services; old Athena plans proposed `DirectAccessUrlService`. | Current code has no S3/MinIO client or `StorageAdapter`; content I/O is filesystem-backed NIO. ADR-001/ADR-003 explicitly defer storage abstraction and content-at-rest concerns. | Do not build presigned URLs until storage abstraction / object-storage direction is reopened. |
| Policy/behavior framework | Alfresco has policy components and deep repository behavior hooks. | Athena has application services and audit hooks, but no Alfresco-equivalent generalized behavior extension framework. | Architecture project only; requires extension/customer signal. |
| Repository package import/export | Alfresco has importer/exporter services and ACP-style repository movement. | Athena has bulk import, content archive, transfer/replication, downloads, and backups/runbooks; not an ACP-equivalent package format. | Defer unless migration/interchange is a sales requirement. |
| Multi-tenant physical isolation | Alfresco has tenant admin depth; Athena has tenant admin/path scope/quota. | ADR-001 accepted Option A: global content pool/path-scoped tenant isolation; ADR-003 deferred content-at-rest encryption. | Already decided; reopen only on the ADR criteria. |
| Production cutover | Alfresco benchmark does not replace production readiness. | Athena hardening has P0a/S1 complete and P0b templates/runbooks, but S2 rotation, A11 runtime, B1/B2 cutover, B3/B4 execution remain owner/ops. | Not a feature gap; follow `HANDOFF_HARDENING_20260526.md`. |

## Reconciliation With Older Athena/Alfresco Documents

The January and March docs should be treated as historical planning artifacts, not active backlog.

| Old document / claim | Refresh status |
| --- | --- |
| `ALFRESCO_GAP_ANALYSIS_20260129.md` Phase 1 P0 set | Mostly stale. Search, version, preview, audit, mail routing are now implemented; permissions need only targeted compatibility review if required. |
| `ATHENA_VS_ALFRESCO_GAP_ANALYSIS_20260326.md` 26-feature backlog | Many rows are now implemented or partially superseded by later slices. Remaining rows are architecture/protocol projects, not small product slices. |
| `ATHENA_SURPASS_ALFRESCO_DEVELOPMENT_PLAN_20260329.md` "追平并超越" plan | Useful as historical ambition, not executable current plan. It predates CMIS work, hardening, recent bulk/export slices, and product pause decisions. |
| DirectAccessUrlService plan | Still not implemented, but now blocked by an explicit storage abstraction decision; not a standalone quick feature. |
| IMAP server plan | Still not implemented; it is a different product model from Athena's mail ingestion automation. |
| CMIS "missing" claim | Stale. CMIS Browser/AtomPub surfaces now exist. The open question is conformance depth, not existence. |
| Alfresco compatibility shim | Real but narrow/inert. Its `ContentService` stubs justify the existing readiness C4 caveat, but no current runtime path consumes the shim as an Alfresco drop-in layer. |

## Current Best Next Options

### Option A — Archive this refresh and stop (recommended)

Commit this read-only benchmark and keep the product line paused. Use it as a gate document when someone proposes "more Alfresco parity" work.

Why: the strongest current signal is not "build more features"; it is "avoid using an old Alfresco checklist as a stale backlog."

### Option B — Protocol compatibility discovery

Open a read-only discovery only if the business needs Alfresco-compatible integrations:

- Scope: CMIS Browser/AtomPub, WebDAV, WOPI, transfer endpoints, auth model, and the smallest TAS-like cross-protocol smoke plan.
- Output: no code; a matrix of supported selectors/actions, missing protocol semantics, and conformance-risk tests.
- Explicitly out of scope: implementing OpenCMIS SPI, FTP, IMAP server, or new storage backend.

This is the most defensible technical follow-up if a customer asks "can we use existing Alfresco clients/tools?"

### Option C — Sales/demo parity checklist

Build a customer-facing matrix from this evidence:

- "Athena has it"
- "Athena has a different model"
- "Athena intentionally defers it"
- "Requires owner/ops cutover"

This is useful for procurement or demos, but it should not drive engineering by itself.

## Recommendation Framework

| Candidate | Value | Risk | Trigger needed | Verdict |
| --- | --- | --- | --- | --- |
| Stop after this refresh | High discipline value | Low | None | Recommended default |
| CMIS/WebDAV conformance discovery | High if integrations matter | Medium | Customer/integration asks for Alfresco client compatibility | Keep as first technical follow-up |
| Alfresco Foundation API shim cleanup | Hygiene only | Low-medium | Owner wants dead-code clarity | Optional; document-or-delete, not feature work |
| IMAP server | Narrow / niche | High | Explicit repository-as-mailbox requirement | Defer |
| Presigned/direct-access URL | Useful only with object-storage abstraction | Medium-high | StorageAdapter/object storage direction reopened | Defer |
| Policy/behavior framework | Architecture extensibility | High | Extension partner/customer signal | Defer |
| ACP/package import-export | Migration/interchange | Medium-high | Alfresco migration/export requirement | Defer |

## Boundaries

- No production, test, frontend, schema, config, or script changes in this refresh.
- No claim of full CMIS/WebDAV conformance.
- No claim that Athena "is Alfresco"; Athena has deliberately different product surfaces in mail automation, preview diagnostics, RM workflows, and hardening posture.
- No automatic feature implementation should be launched from this document without a gate-approved brief.
- No owner/ops hardening item is marked done here. Production cutover remains governed by `docs/HANDOFF_HARDENING_20260526.md`.

## Verification

Read-only checks performed:

- Re-read old Alfresco gap/plan docs and current product/hardening docs.
- Grepped Alfresco reference services and TAS test surfaces under `reference-projects/alfresco-community-repo/`.
- Grepped Athena current controllers/services for the old gap areas: search, version, preview/rendition, permissions, audit, mail, CMIS, WebDAV, tenant, content model, sharing, sites, workflow, RM, bulk operations.

Expected worktree impact:

- One new documentation file: `docs/ALFRESCO_PARITY_BENCHMARK_REFRESH_20260526.md`.
- No code/config/schema/test/frontend changes.
