# Phase C Step 1 - Isolate Folder ACLs

## Goal
Ensure verification environments can disable inherited write permissions so non-privileged users (e.g., `viewer`) cannot create folders after inheritance is broken.

## Approach
1. After calling `POST /api/v1/security/nodes/{nodeId}/inherit-permissions?inherit=false`, immediately drop the `EVERYONE` `CREATE_CHILDREN` ACL using the existing `DELETE /api/v1/security/.../permissions` endpoint.
2. Keep the behavior surgical (only remove the `CREATE_CHILDREN` bit) to avoid altering other inherited grants that might be needed for read access.
3. Encode the workflow inside `scripts/verify-phase-c.py` so future end-to-end security runs remain deterministic.

## Implementation
- Added a helper segment inside `scripts/verify-phase-c.py` that executes the `DELETE` request above and records the outcome (`remove_everyone_create_children`).

## Notes
- This mirrors how an admin would manually remove the public ACL in the UI after breaking inheritance.
- No backend code change was required for this step.
