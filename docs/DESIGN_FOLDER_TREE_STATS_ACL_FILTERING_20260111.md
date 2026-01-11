# Design: Folder Tree + Stats ACL Filtering (2026-01-11)

## Context
- Folder tree nodes expose `childCount` based on total children, even for non-admin users.
- Folder stats count direct and descendant items without permission checks.
- These counts can leak the existence of denied documents or folders.

## Decision
- For non-admin users, filter direct and descendant counts by READ permission.
- For admins, keep existing behavior with full child visibility.
- Tree nodes should compute `childCount` from readable children for non-admins while preserving tree structure.

## Implementation
- Add a helper to load children and apply READ filtering for non-admin requests.
- Update `getFolderStats` to count direct and total items via the filtered helper.
- Update folder tree building to reuse the helper and avoid child count leakage.
- Add unit tests to verify non-admin counts in stats and tree responses.

## Impact
- Folder tree and stats counts align with user-visible content.
- Prevents ACL-based count leakage while keeping admin behavior intact.
