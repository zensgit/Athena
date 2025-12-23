# Empty Content Cleanup Report

## Scope
- Query: `documents` joined to `nodes` where `file_size` is NULL/0 and `is_deleted=false`.
- Goal: soft-delete empty-content documents to reduce clutter and avoid preview errors.

## Execution
- Source list: `tmp/empty-content-documents.csv` (155 rows).
- Delete API: `DELETE /api/v1/nodes/{id}?permanent=false` (soft delete).
- Auth: Keycloak password grant for `admin` (token not stored in report).

## Results
- Processed: 155
- Success: 154
- Failed: 1 (HTTP 404; node already deleted)
- Remaining empty-content nodes: 0

## Artifacts
- `tmp/empty-content-documents.csv`
- `tmp/empty-content-delete-results.csv`
