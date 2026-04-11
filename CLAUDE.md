# Athena ECM — Claude Code Context

This file is automatically loaded by Claude Code on conversation start.

## Project Overview

Athena is a production-ready Enterprise Content Management system. Backend: Spring Boot 3.2 / Java 17 / PostgreSQL / Elasticsearch. Frontend: React 18 / TypeScript / MUI 5.

## Development Principles

- **Bridge existing capabilities, do not recreate Alfresco-shaped subsystems.** Check what exists before proposing new abstractions.
- Use Athena-native patterns: JSON/multipart (not XML), JPA/Spring conventions, path-based tenant scoping.
- Gradual generalization over big-bang refactors (e.g. NodeRelation alongside DocumentRelation).
- The user works with a "Codex" reviewer who does gate reviews on deliverables.
- User communicates in Chinese; code and docs in English.
- Prefers batched tasks with clear scope boundaries and parallel execution.

## Frozen Architecture Decisions

### Repository Identity
- Single source of truth: `RepositoryIdentityProvider` (`ecm.cmis.repository-id` default "athena")
- Never hardcode repository ID in CMIS or Transfer code

### Transfer Protocol
- JSON/multipart over HTTP — no XML manifest, no Redis locks
- DB-level mutual exclusion via `existsByDefinitionIdAndStatusIn()`
- Delta watermark: `lastSuccessfulSyncAt` set only on success
- v1 excludes: delete propagation, permission-delta sync, alien node handling

### Multi-Tenancy
- Path-based scoping via `TenantWorkspaceScopeService` — no AOP full interception
- Content storage is global (shared hash dedup) — per-tenant routing deferred (see ADR-001)
- Quota: dual-layer (preflight at controller + authoritative in ContentService)

### CMIS ACL Mapping
- READ → cmis:read
- WRITE, CREATE_CHILDREN, CHECKOUT, CHECKIN, CANCEL_CHECKOUT → cmis:write
- DELETE, DELETE_CHILDREN, CHANGE_PERMISSIONS, TAKE_OWNERSHIP, EXECUTE, APPROVE, REJECT → cmis:all

### Node Relations
- `NodeRelation` is the generalized entity; `DocumentRelation` is @Deprecated and delegates
- Data migrated via migration 071; both tables coexist

## Gap-Closure Roadmap Status (2026-04-11): COMPLETE

All items delivered in 24 commits, 115 files, ~11,500 lines.

### Transfer: COMPLETE
- Active-job DB fix, repository identity, node mappings, delta watermark
- Receiver-side idempotency (4-rule matrix), per-entry JSONB report (5000 cap)
- Loopback + HTTP client watermark + mapping support

### CMIS: COMPLETE (10 capabilities)
- Secondary types, version history, change log, ACL mapping, relationships
- Renditions, CONTAINS() + IN_TREE() query support
- Plus existing: object CRUD, content streams, checkout/checkin, type system

### Multi-Tenancy: COMPLETE
- Quota enforcement, security cache isolation, metrics endpoint

### Frontend: COMPLETE
- TenantAdminPage (quota display), TransferReplicationPage (per-entry report)
- CmisExplorerPage (/admin/cmis-explorer), TenantMetricsDashboardPage (/admin/tenant-metrics)

### Documentation
- `docs/BACKEND_DESIGN.md` — architecture (309 lines)
- `docs/BACKEND_VERIFICATION.md` — test matrix (236 lines, 28 classes, ~189 tests)
- `docs/adr/ADR-001-storage-routing-tenant-isolation.md` — storage routing (deferred)
- `docs/CHANGELOG-gap-closure.md` — full change summary

## Do NOT

- Introduce XML manifest / Redis distributed locks / AOP tenant interception
- Change the CMIS ACL mapping without re-opening the design discussion
- Implement delete propagation or alien node handling without explicit scope approval
- Add storage routing without resolving ADR-001 dedup tradeoff
