# Q2b Discovery — scheduled writers tenant source-of-truth (2026-06-02)

Read-only discovery for the 4 `@Scheduled` content writers (Class B in the Q2 brief). **No code.**
Picks the tenant source-of-truth so a follow-up slice can scope their writes (quota + tenant) correctly.

## Per-writer findings

| Writer | `@Scheduled` | Iterates | Target folderId source | Tenant on entity? | Multi-tenant? | Natural source |
|---|---|---|---|---|---|---|
| `RmReportPresetDeliveryService` | `:287` cron | per-preset (DB) | `preset.deliveryFolderId` (admin-set per preset) | no | **YES** (per-preset folders) | derive-from-folder |
| `MailFetcherService` | `:122` fixedDelay | per-mail-account | `rule.assignFolderId` (per-rule) | no (`oauthTenantId` is OAuth, not ECM tenant) | **YES** (per-rule folders) | derive-from-folder |
| `MailReportScheduledExportService` | `:63` cron | one global run | config `ecm.mail.reporting.export.folder-id` | no | single config (restart to change) | config folder → derive |
| `DirectoryWatcherService` | `:72` fixedDelay | per-file in one watch dir | config `ecm.ingestion.target-folder-id` | no | **NO** (one dir/folder/tenant per deploy) | config folder → derive |

(Per-writer folder/line refs from the Explore inventory — **verify each before coding**.)

## Key gap — no folder→tenant reverse-lookup exists today (verified)

- `Tenant` has `tenantDomain` + `rootNodeId` (`Tenant.java:24,33`).
- **`TenantRepository` has NO `findByRootNodeId`** — only `findByTenantDomain…` + `findByDeletedFalseOrderBy…` (`TenantRepository.java:12,16`).
- `TenantWorkspaceScopeService` resolves the tenant root from `TenantContext` (the request thread), **not** by reverse-lookup (`:33`).
- So **every** "derive tenant from target folder" option needs a new primitive: walk the target folder up to its root node (or use its `path`), then map root→tenant. Athena's tenancy is **path-based** (CLAUDE.md), so a `path`-prefix match (folder under `tenant.rootPath`) or `findByRootNodeId(walk-to-root)` is the natural fit.

## Options (gate decision)

- **A. Unified derive-from-target-folder (recommended).** Every writer, before upload, resolves the tenant from its target folder (walk to root → tenant), sets `TenantContext`, uploads (quota now correct), `finally` clears. **One** mechanism for all 4; supports multi-tenant (per-preset / per-rule folders); fits path-based tenancy. The two "config-pinned" writers are just the degenerate case — their single config folder resolves to one tenant via the same lookup. Cost: add the folder→tenant primitive + decide the "folder not under any tenant root" fallback.
- **B. Per-writer mix.** derive-from-folder for the multi-tenant ones (RmReportPreset, MailFetcher); explicit config-pinned tenant for the single-config ones (MailReportScheduled, DirectoryWatcher). More config surface; two code paths.
- **C. Single-tenant-only.** Gate these features to single-tenant deployments (assert one tenant or a configured default), defer multi-tenant scheduled ingest/delivery. Simplest; drops multi-tenant support for these.

## Recommendation

**Option A.** It's a single primitive (folder→tenant reverse-lookup), matches Athena's path-based tenancy, and collapses the config-pinned writers into the same path. The one real design question is the **fallback when a target folder is not under any tenant root** (a system/global folder): treat as no-tenant system write (no quota) **vs** reject the scheduled write. **That fallback is the gate's call.**

A secondary check the implementer must make: a target folder could be **stale/deleted** (admin-set `deliveryFolderId` / config id pointing at a removed folder) — the reverse-lookup must handle "folder not found" distinctly from "found but no tenant".

## Scope boundary

Q2b = the folder→tenant reverse-lookup primitive + applying "resolve-tenant-from-target-folder → set/clear `TenantContext`" at the 4 scheduled writers' upload points. **Not** a snapshot (there is no request tenant). Implementation is a follow-up slice once the gate picks A/B/C and the not-under-any-tenant fallback.
