# Phase 308 - Alfresco Comment Mention Surfaces Dev

Date: 2026-03-19

## Goal

Push collaboration surfaces beyond basic mentions by adding:

- document-preview discussion entry points
- quick mention chips in preview
- external draft seeding into the comment composer
- people directory mention-copy affordances

## Comment Composer Integration

Updated [CommentSection.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/comments/CommentSection.tsx):

- added `initialDraftText`
- added `draftVersion`
- comment composer can now accept an externally seeded draft without breaking:
  - reply flow
  - inline mention suggestion lookup
  - safe mention rendering
  - comment search

This creates a reusable integration point for other collaboration surfaces to hand off mention drafts into the document thread.

## Document Preview Collaboration Surface

Updated [DocumentPreview.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/preview/DocumentPreview.tsx):

- renamed the top-level comments action to `Discuss / Hide discussion`
- added quick mention chips for:
  - creator
  - modifier
  - correspondent
- added `Jump to discussion`
- quick mention action now:
  - seeds `@username `
  - opens the discussion panel
  - scrolls into the comments region
  - best-effort copies the mention text to clipboard

This moves the preview surface closer to a collaboration-first document experience rather than treating comments as a detached side feature.

## People Directory Mention Utility

Updated [PeopleDirectoryPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/PeopleDirectoryPage.tsx):

- added `@username` chips in search results
- added `@username` chip plus `Copy mention` action in profile view
- kept mention export lightweight by using clipboard copy rather than forcing a modal or route handoff

## Design Notes

- Draft seeding uses a version token so repeated quick-mention clicks still refresh the compose value.
- Mention entry points are intentionally additive and do not change the underlying backend comment APIs.
- The preview mention target set is metadata-driven today; future iterations can expand this to assignees, recent commenters, and workflow actors.
