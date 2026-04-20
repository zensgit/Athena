# P5 PR-86 PR-83 Review And Name-Reuse Fix Development

Date: 2026-04-20

## Scope

This slice does two things:

- review the shipped `PR-83` runtime slice (`RM saved report preset foundation`) beyond CI status alone
- apply the smallest possible correctness fix for the one issue found during that review

It does not add a new API, migration, or frontend surface.

## Review Result

The `PR-83` backend foundation is broadly sound:

- controller remains admin-gated
- service remains owner-scoped
- migration is additive and self-contained
- no kernel, ACL, or search-index coupling was introduced

One real issue was found during review:

- soft-deleted presets could not safely reuse their original `(owner, name)` pair
- the service-level duplicate check only looked at `deleted = false`
- but the database unique constraint remained unconditional on `(owner, name)`
- result: create-after-delete would pass service validation and then fail at persistence time

## Fix Applied

Rather than reopening schema design midstream, this follow-up uses the smallest safe runtime fix:

- when a preset is soft-deleted, its stored `name` is rewritten to a tombstoned internal value
- the internal storage name preserves the original visible prefix and appends a deterministic deleted suffix using the preset id
- this frees the original user-facing name for reuse without changing the public API or requiring a new migration

Implementation details:

- `RmReportPresetService.delete(...)`
  - now rewrites `preset.name` before marking `deleted = true`
- helper added:
  - `toDeletedStorageName(String currentName, UUID id)`
- output is capped at the existing `varchar(200)` boundary

## Why This Fix

This approach was chosen because:

- it is additive and local to the existing delete path
- it avoids introducing a cross-environment partial unique index as an urgent hot follow-up
- there is no restore/undelete workflow for report presets today, so tombstoning the internal deleted-row name does not reopen a supported user path

## Files Changed

- `ecm-core/src/main/java/com/ecm/core/service/RmReportPresetService.java`
- `ecm-core/src/test/java/com/ecm/core/service/RmReportPresetServiceTest.java`

## Relationship To PR-83

`PR-83` remains the shipped foundation slice.

`PR-86` is a narrowly scoped follow-up fix discovered during milestone review. It does not change the surface area or product direction of `PR-83`; it only makes the advertised "reuse the same name after delete" behavior actually true.
