# Design: E2E Browse ACL Coverage (2026-01-10)

## Context
- Browsing folder contents must not surface nodes without READ permission.
- Backend now filters folder/node children for non-admins before paging.

## Decision
- Add a Playwright scenario that creates a folder/document, denies READ for EVERYONE on the document, and verifies a viewer cannot see it in the browse list.

## Approach
- Use admin token to create a folder and upload a document.
- Set READ allow on the folder for EVERYONE and explicit READ deny on the document.
- Login as viewer and navigate to the folder in list view.
- Assert the document name is not present and the empty-state message is visible.

## Files
- `ecm-frontend/e2e/browse-acl.spec.ts`
