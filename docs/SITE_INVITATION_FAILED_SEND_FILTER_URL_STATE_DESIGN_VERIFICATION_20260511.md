# Site Invitation Failed-Send Filter URL State - Design and Verification

Date: 2026-05-11

## Context

The site invitation resend layer already exposes a failed-send filter chip on
`SiteInvitationsPage`. The remaining operator gap was URL persistence: after
selecting "Failed sends", a refresh or shared link returned to the default
"All" view.

This frontend-only follow-up makes the failed-send filter shareable through the
page query string.

## Design

`SiteInvitationsPage` now derives the send-status filter from the URL:

| Query string | View |
|---|---|
| no `sendStatus` parameter | all invitations |
| `?sendStatus=failed` | failed-send invitations only |
| any other `sendStatus` value | all invitations |

The implementation keeps one source of truth:

- `useSearchParams()` owns the filter state.
- Chip clicks update only the query parameter.
- Unknown values are normalized back to the all-invitations view.
- Existing query parameters are preserved when toggling the filter.
- The toggle uses `replace: true` to avoid polluting browser history with local
  table-filter changes.

No backend endpoint, DTO, migration, or service contract changed.

## Verification

### Local gates

| Gate | Command | Result |
|---|---|---|
| Targeted Jest | `CI=true npm test -- --runTestsByPath src/pages/SiteInvitationsPage.test.tsx --watchAll=false` | 1 suite, 14 tests, 0 failures |
| ESLint | `npm run lint` | clean |
| Frontend build | `CI=true npm run build` | compiled successfully; CRA bundle-size advisory remains informational |
| Whitespace | `git diff --check` | clean |

New test coverage:

- clicking `Failed sends` writes `?sendStatus=failed`.
- clicking `All` clears the query parameter.
- loading `/admin/sites/:siteId/invitations?sendStatus=failed` starts in the
  filtered view.

Notes:

- Targeted Jest still prints the existing React Router v7 future-flag warnings.
- Negative resend-path tests intentionally exercise `console.error`; those
  console lines are expected and the suite passes.
- The production build still prints the existing Node `fs.F_OK` deprecation
  warning from the CRA toolchain. This is separate from the GitHub Actions
  Node.js runtime warning fixed in `CI_NODE20_ACTIONS_MAINTENANCE_20260510.md`.

## Files Changed

- `ecm-frontend/src/pages/SiteInvitationsPage.tsx`
- `ecm-frontend/src/pages/SiteInvitationsPage.test.tsx`

## Remaining Work

- None for the failed-send filter. This closes the URL-state follow-up from
  `SITE_INVITATION_FAILED_SEND_FILTER_DESIGN_VERIFICATION_20260510.md`.
