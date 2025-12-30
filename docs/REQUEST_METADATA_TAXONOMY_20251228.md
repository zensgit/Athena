# Requirements: Metadata, Tags, Categories (2025-12-28)

## Scope
Tagging, categorization, and metadata editing workflows.

## Requirements
1. **Tag management**
   - Add/remove tags from list and detail views.
   - Suggest existing tags and allow quick creation.
   - Show tag usage counts in admin view.

2. **Category management**
   - Tree view for categories with drag/drop (optional).
   - Assign categories in batch to multiple documents.
   - Support default category per folder (optional).

3. **Metadata panel**
   - Inline edit for key metadata fields.
   - Validation with clear error messages.

4. **Search filters**
   - Faceted search by tags/categories with counts.
   - Filters persist when navigating back to list.

## Acceptance Criteria (Draft)
- Tags/categories can be edited quickly from file list and detail views.
- Batch tagging works without full page reload.
- Faceted search reflects tag/category updates in near real-time.
