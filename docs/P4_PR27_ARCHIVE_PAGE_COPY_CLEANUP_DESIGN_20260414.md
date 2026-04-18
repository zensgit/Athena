# P4 PR-27 Archive Page Copy Cleanup Design

## Goal

Apply the optional `Restore -> Reopen` UI copy cleanup only to the archive operator page, without changing the underlying backend contract or leaking the term into unrelated surfaces.

## Scope

`PR-27` covers:

- archive operator subtitle copy
- archive operator info-alert copy
- archive operator primary action label
- archived-nodes table row action label
- reopen success / failure toasts on the archive page
- thin frontend regression coverage for the page copy and restore contract

`PR-27` does not cover:

- backend API changes
- `contentArchiveService.restoreNode(...)` rename
- `POST /api/v1/nodes/{nodeId}/restore` rename
- `ArchiveStatus.RESTORING` enum/value changes
- trash-page restore copy
- browse/preview/search/trash reopen affordances

## Recommendation

Keep the repository/API term as `restore`.

Only soften the operator-facing archive-page copy to `Reopen`, exactly as documented in `PR-23`. That avoids creating a second archive mutation surface while still giving the archive page product language a softer tone.

## Why This Slice Is Safe

- the backend already stayed on the existing restore API during `PR-23`
- the archive page is the only approved surface for optional `Reopen` wording
- no search, browse, preview, or trash semantics change
- no state, routing, or data contract changes are required

## Files

Frontend production:

- `ecm-frontend/src/pages/ContentArchivePage.tsx`

Frontend tests:

- `ecm-frontend/src/pages/ContentArchivePage.test.tsx`
- `ecm-frontend/src/services/contentArchiveService.test.ts`

## Outcome

After `PR-27`, the archive operator page presents `Reopen` to users while the service and backend continue to use `restore` as the authoritative mutation contract.
