# Phase 362: Unified Admin Task Center

## Date
- 2026-03-21

## Goal
- Start consuming the shared async lifecycle feed from the admin surface.
- Move `AdminDashboard` one step closer to a real cross-domain task center instead of separate summary islands.
- Keep the UI slice low-risk by focusing on recent tasks, shared affordances, and operator filters.

## Why
- Phase 361B added a shared backend contract for recent async tasks, but the admin UI still only showed health totals and domain-specific task centers.
- Athena’s strongest near-term benchmark overtake path is cross-domain operator governance, so the dashboard needs a first-class recent-task feed.
- This also reduces the coupling between admin UX and per-domain task response quirks.

## Implementation

### 1. Recent async task feed in AdminDashboard
- Updated [AdminDashboard.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/AdminDashboard.tsx) to call `GET /api/v1/analytics/async-governance/tasks`.
- Added a new `Recent Async Tasks` section after the health overview.
- The panel supports:
  - `maxItems`
  - `domain` filter
  - `status` filter
  - refresh and last-updated state

### 2. Shared lifecycle rendering
- The recent-task panel now renders:
  - domain label
  - status
  - task id
  - created / updated / finished timestamps
  - filename
  - action availability
- The UI uses the shared lifecycle affordances from the backend instead of hard-coding per-domain action rules.

### 3. Shared actions
- The panel wires the shared lifecycle actions into buttons for:
  - cancel
  - cleanup
  - download
- The frontend normalizes backend absolute-style API paths so they work correctly with the existing `apiService` base URL behavior.

## Result
- Athena now has both:
  - a cross-domain async health overview
  - a cross-domain recent-task operator surface
- This is the first UI slice where Athena starts to feel structurally ahead of benchmark products that keep task flows isolated by domain.

## Files
- [AdminDashboard.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/AdminDashboard.tsx)

## Next
- Phase 362A should deepen operator detail with persistent task ledger semantics and stronger preflight transparency.
- A later admin slice can replace more of the domain-specific task-center duplication once the shared lifecycle contract grows beyond recent tasks.
