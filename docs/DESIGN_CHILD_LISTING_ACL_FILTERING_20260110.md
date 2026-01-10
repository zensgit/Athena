# Design: Child Listing ACL Filtering (2026-01-10)

## Context
- Folder and node child listing endpoints return all children without ACL filtering.
- For non-admin users, denied children can appear in listings and pagination can hide allowed items.

## Decision
- For non-admin requests, fetch all children with the requested sort, filter by READ permission, then paginate in memory.
- For admins, keep the existing paged repository query to avoid loading large child sets.
- Smart folder listings remain unchanged because search ACL filtering already applies.

## Implementation
- Add `NodeRepository.findByParentIdAndDeletedFalse(UUID, Sort)` to retrieve sorted child lists.
- Update `NodeService.getChildren` and `FolderService.getFolderContents` to filter by READ before pagination for non-admins.
- Add unit tests that assert ACL filtering happens before paging.

## Impact
- Child listings no longer leak denied nodes.
- Pagination reflects only permitted items for non-admins.
- Admin and smart-folder behavior remain unchanged.
