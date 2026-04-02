# Phase 320 - Alfresco Workflow People Collaboration and Comment Focus Dev

Date: 2026-03-19

## Goal

Push Athena beyond raw workflow parity by making workflow identities actionable and by preserving comment context across People Directory to document preview handoff.

## Workflow People Collaboration

Updated [TasksPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/TasksPage.tsx):

- task assignee, process starter, reviewer, and approver identities now deep-link into People Directory
- task assignment dialog now uses People Directory-backed autocomplete instead of a raw username-only text box
- workflow summary/history surfaces now treat participants as collaboration objects rather than inert labels

## Comment Focus Handoff

Updated:

- [PeopleDirectoryPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/PeopleDirectoryPage.tsx)
- [DocumentPreview.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/preview/DocumentPreview.tsx)
- [CommentSection.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/comments/CommentSection.tsx)

Changes:

- authored/mentioned comment `Discuss` actions now pass a target comment id into preview
- document preview now opens the discussion panel when a target comment id is supplied
- comment section expands ancestor threads, scrolls to the target comment, and visually highlights it

## Outcome

Athena now provides a tighter workflow-to-people-to-discussion loop than the baseline reference. Users can move from workflow assignee/starter identities into profile context, then back into the exact discussion thread tied to authored or mentioned comments.
