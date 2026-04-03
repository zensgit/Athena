# Phase369AI Archive Policy Backbone DEV

## Goal

Build a first real archive-policy control plane on top of the archive backbone:

- folder-scoped archive policy persistence
- operator dry-run and manual execute
- scheduled execution of enabled policies
- policy diagnostics inside the existing archive workspace

## Backend

### Archive policy domain

Added first-class policy storage:

- [ArchivePolicy.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/entity/ArchivePolicy.java)
- [ArchivePolicyRepository.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/repository/ArchivePolicyRepository.java)
- [056-create-archive-policies-table.xml](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/resources/db/changelog/changes/056-create-archive-policies-table.xml)

Policy shape:

- one policy per folder
- `enabled`
- `inactivityDays`
- `storageTier`
- `includeSubfolders`
- `maxCandidatesPerRun`
- execution diagnostics: `lastDryRunAt`, `lastExecutedAt`, `lastCandidateCount`, `lastArchivedNodeCount`, `lastError`

### Archive policy service

[ArchivePolicyService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/ArchivePolicyService.java) provides:

- `getPolicy`
- `listPolicies`
- `upsertPolicy`
- `deletePolicy`
- `dryRunPolicy`
- `executePolicy`
- `runScheduledPolicies`

Design choices:

- admin-only for all policy operations
- dry-run accepts an unsaved request payload so operators can preview before saving
- candidate selection uses `archiveStatus=LIVE` and inactivity cutoff
- nested descendants are collapsed so an old folder wins over its older descendants
- scheduled runs use `system:archive-policy` as actor

### Archive execution reuse

[ContentArchiveService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/ContentArchiveService.java) gained `archiveNodeByPolicy(...)` so policy execution reuses the same archive mutation path, including:

- recursive folder scope handling
- search index sync
- `node.archived` activity emission

### Scheduling

[ArchivePolicyScheduler.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/ArchivePolicyScheduler.java) adds a simple scheduled runner:

- cron: `ecm.archive.policy.cron`
- default: `0 15 3 * * *`

### Controller surface

[ContentArchiveController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/ContentArchiveController.java) now exposes:

- `GET /api/v1/folders/{folderId}/archive-policy`
- `PUT /api/v1/folders/{folderId}/archive-policy`
- `DELETE /api/v1/folders/{folderId}/archive-policy`
- `POST /api/v1/folders/{folderId}/archive-policy/dry-run`
- `POST /api/v1/folders/{folderId}/archive-policy/execute`
- `GET /api/v1/archive-policies`
- `POST /api/v1/archive-policies/run`

## Frontend

[contentArchiveService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/contentArchiveService.ts) now includes typed archive policy methods and DTOs.

[ContentArchivePage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/ContentArchivePage.tsx) now includes:

- policy form bound to folder id
- load/save/delete policy actions
- dry-run preview
- manual execute for one folder
- run-enabled-policies action
- policy list table
- dry-run diagnostics table

This stays in the existing archive route instead of introducing a new admin surface.

## Explicitly deferred

- policy inheritance between folders
- schedule windows per policy
- conflict remediation when restore collides with live names
- external cold-storage placement
- browse/search awareness of policy metadata
