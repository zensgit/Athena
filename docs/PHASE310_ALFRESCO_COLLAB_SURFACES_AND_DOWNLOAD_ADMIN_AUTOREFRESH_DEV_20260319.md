# Phase 310 - Alfresco Collaboration Surfaces and Download Admin Auto-refresh Dev

Date: 2026-03-19

## Goal

Expand collaboration and admin usability by adding:

- favorites-level preview and discussion entry points
- preview bootstrap props for seeded discussion flows
- people directory mentioned-comments visibility
- batch download admin auto-refresh and freshness status

## Favorites Collaboration Surface

Updated [FavoritesPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/FavoritesPage.tsx):

- document favorites now expose:
  - `Preview`
  - `Discuss`
  - `Inspect`
  - `Remove`
- folder favorites keep navigation-oriented actions and do not open document preview

`Discuss` now opens the document preview directly into the discussion context so favorites can act as a collaboration inbox rather than only a bookmark list.

## Preview Bootstrap Hooks

Updated [DocumentPreview.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/preview/DocumentPreview.tsx):

- added `initialCommentsOpen`
- added `initialCommentDraftText`
- opening a preview can now:
  - auto-open the comment panel
  - seed the draft composer
  - trigger scroll into the discussion region on first render

This creates a reusable handoff surface for any page that wants to deep-link into document collaboration.

## People Directory Mention Visibility

Updated [commentService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/commentService.ts):

- added `getMentionedComments(username, page, size)`

Updated [PeopleDirectoryPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/PeopleDirectoryPage.tsx):

- loads a small `mentioned-comments` sample together with profile/groups/favorites
- renders a `Mentioned Comments` panel in the profile workspace
- surfaces author, time, content, and mention chips

This moves People Directory closer to a collaboration hub rather than a passive identity lookup page.

## Download Admin Auto-refresh

Updated [AdminDashboard.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/AdminDashboard.tsx):

- added batch download admin auto-refresh toggle
- polls every 15 seconds when enabled
- records and renders `Last updated` status
- keeps the existing status filter while polling

This reduces manual refresh churn for operators managing async download workloads.

## Design Notes

- Auto-refresh stays opt-in to avoid surprising background traffic during heavy admin sessions.
- Preview bootstrap props are generic enough to support workflow, people, favorites, and future notification entry points.
- Mentioned-comments loading is intentionally sampled rather than fully paged in the profile surface to keep People Directory responsive.
