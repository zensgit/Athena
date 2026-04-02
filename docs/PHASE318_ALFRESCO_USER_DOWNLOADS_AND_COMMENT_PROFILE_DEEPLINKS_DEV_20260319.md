# Phase 318 - Alfresco User Downloads and Comment Profile Deeplinks Dev

Date: 2026-03-19

## Goal

Push Athena past raw admin parity by improving the end-user collaboration loop:

- make batch download tasks user-scoped in the file browser
- let users jump from comments and preview metadata directly into People Directory

## User-Scoped Batch Download Surface

Updated [FileBrowser.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/FileBrowser.tsx):

- batch download task listing now requests tasks filtered by the current user
- card title changed to `My Batch Download Tasks`
- owner chip added for clarity
- empty state now reflects user-scoped task history

This uses the existing owner filter support already available in [nodeService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/nodeService.ts).

## Comment and Preview Deeplinks

Updated [CommentSection.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/comments/CommentSection.tsx):

- comment author avatar is clickable
- comment author username is clickable
- both jump to `/people-directory?username=...`

Updated [DocumentPreview.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/preview/DocumentPreview.tsx):

- quick metadata actions now include lightweight profile navigation for creator / modifier / correspondent
- existing quick mention flow remains intact

## Outcome

Athena now gives users a tighter document-collaboration loop: see who authored a comment, jump to that profile, inspect their favorites and activity, then come back to the document without leaving the product’s collaboration context.
