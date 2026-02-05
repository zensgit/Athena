# Athena ECM Rollup Design (2026-02-05)

## Scope
This rollup captures the design intent for recent feature phases:
- Mail Automation reporting & diagnostics UX.
- Search explainability + highlight snippets.
- Permission template versioning, compare, and export.
- Preview retry visibility and bulk retry.
- Version history compare summary and preview retry actions.

## Mail Automation Reporting
- **Goal:** Give admins quick operational visibility into mail ingestion, with diagnostics and reporting.
- **UX:**
  - Mail Reporting panel shows account/rule/time filters with export-ready fields.
  - Diagnostics panel exposes folder list, dry-run diagnostics, last fetch summary.
  - Connection Summary provides health status, poll interval, OAuth connection state.
- **Behavior:**
  - Diagnostics can be triggered without ingesting mail (dry-run), surfacing skip reasons.
  - Reporting is bounded to recent activity and is exportable.

## Search Explainability & Highlights
- **Goal:** Provide transparency for search relevance while retaining ACL filtering.
- **UX:**
  - Result cards show highlight snippets where available.
  - Explainability metadata supports debugging relevance in admin workflows.
- **Behavior:**
  - Search responses include highlight fragments and optional explain payloads.

## Permission Template Versioning + Compare
- **Goal:** Provide auditability and safe change review for permission templates.
- **UX:**
  - Template History dialog lists versions with metadata.
  - Compare dialog shows change summary + diff table (added/removed/changed entries).
  - Export CSV allows offline review of diffs.
- **Behavior:**
  - Each save operation snapshots a version.
  - Versions are immutable and can be compared by id.

## Preview Retry Visibility + Bulk Retry
- **Goal:** Reduce preview failures and make retry state observable.
- **UX:**
  - Per-item retry status shows attempts + next retry time.
  - Bulk “Retry failed previews” action for current page.
- **Behavior:**
  - Retry requests enqueue preview generation and update status via queue state.

## Version Compare Summary + Preview Retry
- **Goal:** Make document version changes easier to scan and improve preview recovery.
- **UX:**
  - Version history dialog includes a comparison summary.
  - Search results expose per-file retry for previews.

## Cross-Cutting
- **Security:** ACL filtering preserved in search and browsing; admin roles can access reporting and template history.
- **Observability:** UI surfaces system status in mail automation and preview queues.
- **Extensibility:** Versioning and diff endpoints allow future tooling (audit exports, automated governance).
