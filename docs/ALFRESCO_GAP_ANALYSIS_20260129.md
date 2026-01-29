# Alfresco Benchmark Gap Analysis (2026-01-29)

This document compares Athena to Alfresco Community for the requested modules and proposes a phased improvement plan.

## Mail Automation
**Alfresco reference**
- IMAP server capabilities (mailbox list/rename/subscribe, IMAP home): `repository/src/main/java/org/alfresco/repo/imap/ImapService.java`
- Inbound email import to repository nodes: `repository/src/main/java/org/alfresco/service/cmr/email/EmailService.java`

**Athena current**
- IMAP client ingestion, rule-based processing, diagnostics + export, processed retention: `ecm-core/src/main/java/com/ecm/core/integration/mail/*`

**Gaps / recommendations**
- P0: Add “email-to-node” routing (address format like `nodeId@domain` or folder alias) for direct import.
- P1: Rule test/preview endpoint (simulate match without ingest) to reduce trial-and-error.
- P2: IMAP mailbox management (server-side) is a large scope; defer unless required.

## Search
**Alfresco reference**
- Multiple query languages and parameterization: `data-model/src/main/java/org/alfresco/service/cmr/search/SearchService.java`
- Rich search parameters (facets, filter queries, pivots, spellcheck, highlight, stats, ranges, timezone, trackTotalHits): `data-model/src/main/java/org/alfresco/service/cmr/search/SearchParameters.java`
- Term suggestions: `repository/src/main/java/org/alfresco/service/cmr/search/SuggesterService.java`

**Athena current**
- ES-backed full-text + advanced search, highlight, basic facets, smart search, similar-docs: `ecm-core/src/main/java/com/ecm/core/search/*`

**Gaps / recommendations**
- P0: Accurate total-hits from ES (currently using page size as total).
- P0: ES aggregations for facets/ranges instead of in-memory counting.
- P1: Spellcheck / “Did you mean?” via ES suggester.
- P1: Query templates / saved searches.
- P2: Multi-language query parsing / multiple query languages.

## Version Management
**Alfresco reference**
- Version store, history with paging, version children, label policies: `repository/src/main/java/org/alfresco/service/cmr/version/VersionService.java`

**Athena current**
- Major/minor versioning, revert, audit events, compare changes: `ecm-core/src/main/java/com/ecm/core/service/VersionService.java`

**Gaps / recommendations**
- P0: Paginated version history (skip/limit) and “major-only” filter.
- P1: Configurable version label policy (e.g., semantic / calendar).
- P2: Versioning for child nodes/collections.

## Preview / Rendition
**Alfresco reference**
- Rendition definitions, sync/async render, rendition discovery: `repository/src/main/java/org/alfresco/service/cmr/rendition/RenditionService.java`
- Thumbnail registry, failed thumbnail tracking, enable/disable thumbnails: `repository/src/main/java/org/alfresco/service/cmr/thumbnail/ThumbnailService.java`

**Athena current**
- On-demand preview + thumbnail generation (PDF/images/Office/CAD), caching: `ecm-core/src/main/java/com/ecm/core/preview/PreviewService.java`

**Gaps / recommendations**
- P0: Persist preview job status (ready/failed/processing) + failure reason.
- P1: Async preview generation queue with retry.
- P2: Thumbnail registry/definitions and re-render controls.

## Permissions
**Alfresco reference**
- Permission sets/roles, dynamic authorities, ACL inheritance: `data-model/src/main/java/org/alfresco/service/cmr/security/PermissionService.java`

**Athena current**
- Permission types, inheritance, dynamic authorities, effective permissions calc: `ecm-core/src/main/java/com/ecm/core/service/SecurityService.java`
- Alfresco compatibility adapter: `ecm-core/src/main/java/com/ecm/core/alfresco/AlfrescoPermissionService.java`

**Gaps / recommendations**
- P0: Explicit permission-set mapping (Coordinator/Editor/Contributor/Consumer) for UI + API consistency.
- P1: Permission “deny” precedence and conflict resolution rules (if needed).
- P2: Permission templates and bulk assignment.

## Audit
**Alfresco reference**
- Audit enable/disable, application-level audit toggles, query + clear by time/id: `repository/src/main/java/org/alfresco/service/cmr/audit/AuditService.java`

**Athena current**
- Event logging with export + retention: `ecm-core/src/main/java/com/ecm/core/service/AuditService.java`, `ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`

**Gaps / recommendations**
- P0: Filtered audit queries (user, eventType, time) on API/UI + export presets.
- P1: Audit category toggles (per event family).
- P2: Archive/rollover strategy for long-term audit storage.

## Proposed P0 Implementation Set (Phase 1)
1) Search: accurate total hits + ES aggregation facets/ranges + optional term suggestions.
2) Version: paginated history + “major-only” view.
3) Preview: persisted preview status (ready/failed/processing) + failure reason.
4) Permissions: permission-set mapping for roles (Coordinator/Editor/Contributor/Consumer).
5) Audit: filtered query + export presets for user/event/time.
6) Mail: email-to-node routing (nodeId@domain or folder alias).

---
Please confirm whether to proceed with the Phase 1 P0 set as listed, or adjust priorities/ordering.
