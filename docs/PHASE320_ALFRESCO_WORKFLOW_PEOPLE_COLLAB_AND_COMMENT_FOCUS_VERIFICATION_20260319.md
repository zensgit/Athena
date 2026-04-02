# Phase 320 - Alfresco Workflow People Collaboration and Comment Focus Verification

Date: 2026-03-19

## Commands

- `cd ecm-frontend && npx eslint --max-warnings=0 src/pages/FileBrowser.tsx src/pages/TasksPage.tsx src/pages/PeopleDirectoryPage.tsx src/components/preview/DocumentPreview.tsx src/components/comments/CommentSection.tsx`
- `cd ecm-frontend && npm run -s build`

## Verification Notes

- `TasksPage.tsx` compiles with People Directory deeplinks and assignee autocomplete.
- `PeopleDirectoryPage.tsx` -> `DocumentPreview.tsx` -> `CommentSection.tsx` now carries `initialCommentId` for discussion-focus handoff.
- `CommentSection.tsx` expands ancestor threads and scrolls to the target comment when a focused comment id is supplied.
