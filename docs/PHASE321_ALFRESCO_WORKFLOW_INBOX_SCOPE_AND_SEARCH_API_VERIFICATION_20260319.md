# Phase 321 - Alfresco Workflow Inbox Scope and Search API Verification

Date: 2026-03-19

## Commands

- `cd ecm-core && mvn -q -Dtest=WorkflowControllerTest test`
- `cd ecm-core && mvn -q -DskipTests compile`

## Verification Notes

- `WorkflowController` exposes both the base and Alfresco-style alias routes for inbox/process browsing.
- `WorkflowControllerTest` covers scoped task summaries, query/business-key/assignee wiring, completed inbox metadata, and paged process listing.
