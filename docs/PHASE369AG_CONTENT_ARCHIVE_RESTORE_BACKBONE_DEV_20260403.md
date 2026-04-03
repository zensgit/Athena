# Phase369AG Content Archive/Restore Backbone

Date: 2026-04-03

## Goal

Introduce a first-class content archive domain that is separate from trash/recycle-bin semantics, with enough backend and frontend surface to archive, inspect, list, and restore archived nodes.

## Scope Delivered

### Backend archive domain

`Node` now carries explicit archive metadata:

- `archiveStatus` (`LIVE / ARCHIVED / RESTORING`)
- `archivedDate`
- `archivedBy`
- `archiveStoreTier` (`HOT / WARM / COLD / GLACIER`)

This is backed by Liquibase migration `055-add-node-archive-columns.xml`.

### Archive service and API

`ContentArchiveService` now provides:

- `archiveNode(nodeId, storageTier)`
- `restoreNode(nodeId)`
- `getArchiveStatus(nodeId)`
- `listArchivedNodes(pageable)`

Key decisions:

- archive is distinct from trash; it does not toggle `deleted`
- folder archive/restore propagates recursively to descendants by path scope
- archive uses normal node ACLs for the initial transition
- restore follows trash-like ownership semantics: archiver, owner, or admin

`ContentArchiveController` exposes:

- `POST /api/v1/nodes/{nodeId}/archive`
- `POST /api/v1/nodes/{nodeId}/restore`
- `GET /api/v1/nodes/{nodeId}/archive-status`
- `GET /api/v1/nodes/archived`

### Activity convergence

Archive lifecycle now participates in the existing collaboration stream:

- `node.archived`
- `node.restored`

This is done through a new public `ActivityEventListener.postNodeActivity(...)` helper so archive actions flow into:

- activity feed
- following feed
- follower notification inboxes

### Frontend operator surface

A new admin workbench now exists:

- `ContentArchivePage`
- route: `/admin/archive`
- menu entry: `Content Archive`

The page supports:

- archive-by-node-id with tier selection
- restore-by-node-id
- archive status lookup
- paged archived-node list
- open archived node in browser

## Deliberate Boundaries

This first version is a backbone, not a full cold-storage implementation.

It intentionally does **not** yet provide:

- external blob migration to a real cold-storage backend
- policy-driven scheduled archival
- browse/search exclusion for archived nodes
- archive policy authoring UI

The phase establishes the contract and operator surface first so those later slices have a stable foundation.
