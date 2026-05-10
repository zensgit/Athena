# Site Invitation Resend UI — Design + Verification (2026-05-07)

## Context

Package B of a 3-package parallel slice. Package A is the backend (extending
`SiteInvitationDto` with five send-status fields and adding a per-invitation
resend endpoint); Package B is this frontend deliverable; Package C is the
acceptance smoke. The backend contract is treated as fixed and consumed in
parallel — no backend file is modified here.

### Backend contract consumed

`GET /api/v1/sites/{siteId}/invitations` — existing endpoint; rows now also
carry five new fields appended to the existing 12. Nullability is load-bearing:

| Field | Type | Nullable |
|---|---|---|
| `lastSendAttemptAt` | ISO-8601 UTC string | yes (null until first attempt) |
| `lastSendStatus` | `'SENT' \| 'FAILED'` | yes |
| `lastSendError` | string | yes (only populated on FAILED) |
| `sendAttemptCount` | integer | **NO** (defaults to 0) |
| `lastSentAt` | ISO-8601 UTC string | yes (null until first SENT) |

`POST /api/v1/sites/{siteId}/invitations/{invitationId}/resend` — new; returns
the refreshed `SiteInvitationDto` on 200; rejects with 400 / 401 / 403 / 404
under the documented conditions. The shared `api` client prepends `/api/v1`,
so the service-layer path mirrors the existing `siteInvitationService.ts`
style (`'/sites/${siteId}/invitations/${invitationId}/resend'`).

## Design

### Service layer (`siteInvitationService.ts`)

- `SiteInvitationDto` extended with the five fields above. Every nullable
  field is explicitly typed `| null`, mirroring the contract.
- `resendInvitation(siteId, invitationId)` posts the no-body request and
  returns the refreshed DTO.
- **Phase 5 Mocked HTML-fallback shape guard.** When `api.post` resolves with
  the SPA `index.html` HTTP-200 fallback (Phase 5 Mocked harness routes that
  it doesn't recognise), the response is a string, not an object. The service
  rejects with a synthetic `Error` whose message is the exported
  `SITE_INVITATION_RESEND_UNEXPECTED_RESPONSE_MESSAGE` constant. The page's
  catch path then renders the synthetic message via `resolveErrorMessage`'s
  `Error.message` branch. The same guard is applied to `listInvitations`
  with an `Array.isArray` shape check — Phase 5 may now serve HTML for the
  list endpoint as well, and the page would otherwise crash on `.map` of a
  non-array.
- The synthetic message literal is exported so service / page / test all
  reference one constant. This avoids the drift trap called out in
  `feedback_contract_nullability_must_be_explicit.md`'s sibling pattern.

### Page UI (`SiteInvitationsPage.tsx`)

- New table column: **Send status**. A single cell, not three columns —
  matches the existing dense table style and keeps the table from breaking
  past the existing six-column rhythm.
- Inside that cell the `<SendStatusCell>` subcomponent renders, top to
  bottom:
  - A chip: `SENT` (success), `FAILED` (error), or `Not yet sent`
    (default + outlined) when `lastSendStatus` is null.
  - `last attempt: <formatted lastSendAttemptAt>` caption (`-` when null).
  - `last sent: <formatted lastSentAt>` caption — rendered **only** when
    `lastSentAt` is non-null. Distinguishes "successfully sent at X, then
    re-attempted and failed at Y" from "never successfully sent".
  - `attempts: <sendAttemptCount>` caption — always rendered (the field is
    non-nullable; 0 is shown as `0`, not `-`).
  - When `lastSendStatus === 'FAILED'`, the truncated `lastSendError`
    (≤80 chars + ellipsis) as an error-coloured caption underneath the
    chip, with the full message in a `<Tooltip>` for hover. The caption
    shape was picked over a tooltip-only because Testing Library's default
    queries find caption text directly, while tooltip text requires
    userEvent hover orchestration that no other test in this file uses.
- New per-row action: **Resend email**. A small outlined button with a
  `<Send>` icon, `aria-label={`Resend invitation to ${inviteeEmail}`}`.
  Always rendered for stable querying; **disabled** unless
  `status === 'PENDING'`, also disabled while a resend is in flight on
  that row (`resendingInvitationId === inv.id`). The Cancel icon button
  was likewise made always-rendered-but-conditionally-disabled, with its
  `aria-label` made row-unique, to match.
- Confirmation `<Dialog>` (replaces the previous `window.confirm` for
  Cancel with a structured MUI dialog only for Resend — Cancel still uses
  `window.confirm` to keep the diff scoped).
  - Title: `Resend invitation`.
  - Body: `Resend invitation email to <strong>{email}</strong>? The
    recipient will receive another copy of the invitation.`
  - Actions: `Cancel` and `Resend`. Both disabled while in flight.
  - Inline error `<Alert data-testid="resend-invitation-error">` inside
    the dialog — backend message via `resolveErrorMessage(err, fallback)`
    on rejection; synthetic shape-guard message on Phase 5 HTML fallback.
    Keeping the error inline (not a toast) means the operator sees it
    next to the Resend button without losing dialog context.
- On `lastSendStatus=SENT` the row is replaced from the redacted response —
  the same `setInvitations(current => current.map(item => item.id === updated.id ? updated : item))`
  pattern `OAuthCredentialAdminPage.handleRefreshNow` uses. Toast confirms
  the recipient and the dialog closes.
- On `lastSendStatus=FAILED` the request still completed at HTTP level, but
  SMTP dispatch failed. The page now treats that DTO as a failed send result:
  the row is still replaced, a failure toast is shown, the dialog remains
  open, and the inline dialog alert repeats the captured `lastSendError`.
  This avoids the misleading "Invitation re-sent" success toast for a 200
  response whose payload records `FAILED`.
- The create-invitation dialog applies the same DTO-level outcome check.
  A created invitation with `lastSendStatus=FAILED` is inserted into the
  table, but the toast says the invitation was created while email send
  failed; it no longer claims the email was sent.
- The catch/rejection path leaves the dialog open so the operator can read
  the message and either retry or cancel.
- `loadInvitations` now also funnels its error through `resolveErrorMessage`,
  so a Phase 5 HTML fallback on the list endpoint surfaces the synthetic
  message rather than the generic `'Failed to load invitations.'`.

### What is **not** in scope

- No bulk "Resend to all PENDING" action.
- No automatic retry / scheduled re-send.
- No per-recipient (multi-address) delivery tracking — backend tracks one
  send-status tuple per invitation row.

## Verification

### Targeted Jest suite

`ecm-frontend/src/pages/SiteInvitationsPage.test.tsx` — new file, 11 tests
across 4 `describe` blocks. All green.

```
Test Suites: 1 passed, 1 total
Tests:       11 passed, 11 total
Time:        ~4.2 s
```

Coverage:

- **Send status display (3 tests).** Asserts SENT chip + `last sent:`
  caption + attempt count for the SENT row; FAILED chip + truncated
  error caption (queried via `data-testid`) for the FAILED row;
  `Not yet sent` chip + `attempts: 0` for the never-attempted row.
- **Resend gating (2 tests).** PENDING rows enable the button (each
  located by row-unique `aria-label`); ACCEPTED / CANCELLED / EXPIRED /
  REJECTED rows disable it.
- **Invite create flow (1 test).** When `createInvitation` resolves with
  `lastSendStatus=FAILED`, the page does not show a success toast; it shows
  a failure toast, inserts the row, and renders the row-level failure
  caption from `lastSendError`.
- **Confirmation flow (5 tests).**
  1. Confirm-and-success: dialog opens, service called with
     `(siteId, invitationId)`, dialog closes, row updated (attempt count
     flips 0 → 1, SENT chip appears).
  2. DTO-level failure: `resendInvitation` resolves with
     `lastSendStatus=FAILED`; the page keeps the dialog open, shows the
     failure toast/inline alert, updates the row to FAILED, and does not
     show the success toast.
  3. Cancel-confirmation: dialog dismisses, service not called.
  4. Backend rejection: `{ response: { data: { message } } }` surfaces the
     backend message inside the dialog; page stays mounted.
  5. **Phase 5 HTML-fallback resilience.** Service rejects with a
     synthetic `Error(SITE_INVITATION_RESEND_UNEXPECTED_RESPONSE_MESSAGE)` —
     the exact same error the service-layer shape guard throws when
     `api.post` returns SPA HTML. The dialog renders the synthetic
     message; the page does not crash.

The mock factory re-exports `SITE_INVITATION_RESEND_UNEXPECTED_RESPONSE_MESSAGE`
explicitly because `jest.mock` replaces the entire module — without the
re-export the test's named import would be `undefined` and
`toContain(undefined)` would silently mis-pass.

Row lookups use `findAllByRole('row')` filtered by email substring rather
than `Element.closest('tr')`, keeping the suite outside the
`testing-library/no-node-access` lint surface.

### Lint

```
npm run lint   # eslint src --ext .ts,.tsx
# clean (no output)
```

### Production build

```
CI=true npm run build
# Compiled successfully.
```

### Whitespace

```
git -C /Users/chouhua/Downloads/Github/Athena-invitation-resend-ui diff --check
# clean
```

## Files Changed

Modified:

- `ecm-frontend/src/services/siteInvitationService.ts` — extended DTO,
  added `resendInvitation`, exported synthetic-message constant, applied
  shape guards to both `listInvitations` and `resendInvitation`.
- `ecm-frontend/src/pages/SiteInvitationsPage.tsx` — added `SendStatusCell`,
  Resend button + confirmation dialog, `resolveErrorMessage` helper,
  `lastSendError` truncation+tooltip, row-unique `aria-label`s.

Added:

- `ecm-frontend/src/pages/SiteInvitationsPage.test.tsx` — 9-test Jest suite.
- `docs/SITE_INVITATION_RESEND_UI_DESIGN_VERIFICATION_20260507.md` — this file.

## Remaining Work

- No bulk "Resend all PENDING" action (intentionally out of scope per
  task spec; acceptance smoke would have to be reshaped first).
- No per-recipient delivery tracking surface (backend only tracks one
  tuple per invitation row).
- Cancel still uses `window.confirm` rather than a structured dialog —
  left untouched to keep the diff scoped to the resend lane. Consider
  unifying in a follow-up.
