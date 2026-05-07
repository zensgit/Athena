# OAuth Credential Revoke Capability UI - Design and Verification

Date: 2026-05-07

## Context

The OAuth credential admin inventory at `/admin/oauth-credentials` exposes
`Refresh Now`, `Require Reauth`, and `Provider Revoke` per row. Until this
slice the `Provider Revoke` button gated on a hard-coded
`provider === 'GOOGLE'` check plus a stored-token check
(`accessTokenStored || refreshTokenStored`). That decision tree was a partial
client-side replica of authoritative logic owned by the backend, which meant:

- Adding new providers (or new "no-op" cases such as env-managed-only credential
  rows) required edits in two places.
- The operator could not see *why* a row was disabled — the UI had no anchor
  for the backend's reason text.

The backend slice (Package A, fixed contract) appended two new fields to
`OAuthCredentialInventoryItem` returned by every OAuth credential admin
endpoint (`GET /admin/oauth-credentials`, plus the row returned by
`POST /require-reauth`, `POST /refresh-now`, `POST /revoke`):

```ts
providerRevokeSupported: boolean;
providerRevokeUnsupportedReason: string | null;
```

When `providerRevokeSupported` is true, `providerRevokeUnsupportedReason` is
null. When false, the reason is non-null and non-blank, sourced from the
backend's decision tree (provider != GOOGLE, env-managed-only,
no-locally-stored-token, etc.). The frontend treats this as the single source
of truth and does not re-derive the decision tree client-side.

This Package B slice consumes that contract and removes the legacy hard-coded
gating from the UI. The backend contract is fixed and consumed in parallel —
we depend on the JSON shape only, not on the backend implementation timeline.

## Design

### Capability-driven gating

The `Provider Revoke` button's `disabled` predicate is now:

```
revokeCredentialId === credential.id
  || refreshCredentialId === credential.id
  || reauthCredentialId === credential.id
  || !credential.providerRevokeSupported
```

The first three terms preserve the existing per-row in-flight guard against
overlapping action transitions. The fourth term replaces both the legacy
`credential.provider !== 'GOOGLE'` check and the legacy
`(!accessTokenStored && !refreshTokenStored)` check — both of which the
backend now subsumes in its `providerRevokeSupported` decision.

This means future provider expansion (e.g. adding Microsoft revoke support)
or new disable cases (e.g. policy-driven gating) become backend-only changes;
the frontend does not need to grow.

### Tooltip rationale

When the button is disabled because of `!credential.providerRevokeSupported`,
the operator should be able to read the backend reason without trial-and-error.
The button is wrapped in:

```tsx
<Tooltip
  title={
    !credential.providerRevokeSupported
      ? (credential.providerRevokeUnsupportedReason
        ?? 'Provider-side revoke is not supported for this credential.')
      : ''
  }
>
  <span
    data-testid={`provider-revoke-wrapper-${credential.id}`}
    aria-label={
      !credential.providerRevokeSupported
        ? (credential.providerRevokeUnsupportedReason
          ?? 'Provider-side revoke is not supported for this credential.')
        : undefined
    }
  >
    <Button disabled={...} ...>Provider Revoke</Button>
  </span>
</Tooltip>
```

Three notes on the shape:

- The wrapping `<span>` is required by MUI: a disabled `<button>` does not
  emit pointer events, so MUI Tooltip cannot fire on hover unless the child
  is a forward-event-capable element. Wrapping in a span is the documented
  MUI pattern.
- When the button is enabled (`providerRevokeSupported === true`), the
  Tooltip `title` is the empty string. MUI does not render the popper for
  an empty title, so an enabled button shows no tooltip — which is the
  desired behavior.
- The `aria-label` on the span mirrors the same backend reason text. This
  doubles as the test anchor (queryable via `findByLabelText`) and as
  assistive-technology copy when the disabled button receives focus, since
  the Tooltip popper itself only appears on hover. We do not introduce
  `userEvent` or hover-based testing-library queries — the existing test
  file uses `fireEvent` exclusively.
- The fallback string `'Provider-side revoke is not supported for this
  credential.'` is defensive: per contract the backend always supplies a
  non-blank reason when supported is false, but if a malformed response ever
  slips through, the UI does not crash and shows a sensible default.

### Unchanged behavior

- The `handleRevoke` function — including the confirmation dialog text,
  success row replacement, and error reload — is byte-for-byte unchanged.
- The confirmation dialog text still references Google. That is intentional:
  the slice's contract describes the backend's *current* provider coverage,
  and the dialog wording is part of an unrelated UX decision (Package A's
  Google-only revoke implementation). When backend coverage expands beyond
  Google, the confirmation copy can be revisited as a separate change.
- The `Refresh Now` and `Require Reauth` buttons keep their existing disable
  predicates, which still depend on the older boolean fields
  (`refreshTokenStored`, `credentialKeyConfigured`, `connected`, etc.). Those
  predicates are not in scope for this slice.

## Verification

### Targeted frontend tests

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/pages/OAuthCredentialAdminPage.test.tsx \
  src/components/layout/MainLayout.menu.test.tsx \
  --watchAll=false
```

Result: 2 suites passed, 17 tests passed (16 pre-existing + 1 new
tooltip-reason assertion; two existing disabled-state tests rewritten to
exercise backend metadata instead of hard-coded provider/token values).

Test changes in `OAuthCredentialAdminPage.test.tsx`:

- Top-level `credential` fixture now includes
  `providerRevokeSupported: true, providerRevokeUnsupportedReason: null`.
- `Provider Revoke is enabled for GOOGLE rows with a stored token` — unchanged
  semantics; the new fixture defaults satisfy `providerRevokeSupported === true`.
- `Provider Revoke is disabled when backend reports providerRevokeSupported=false
  (regardless of provider)` — passes `provider: 'GOOGLE'` plus
  `providerRevokeSupported: false` to prove backend metadata wins over the
  legacy client-side gate.
- `Provider Revoke is disabled for non-GOOGLE rows when backend reports
  unsupported` — passes `provider: 'MICROSOFT'` plus
  `providerRevokeSupported: false` and the corresponding backend reason.
- `Provider Revoke surfaces the backend unsupported reason via tooltip wrapper
  aria-label` (new) — asserts that the wrapping span carries the backend
  reason as an `aria-label`, queryable via `findByLabelText`. This avoids
  hover-based testing-library queries (the rest of the file uses `fireEvent`)
  and `testing-library/no-node-access` lint hazards.
- `revokes OAuth token at the provider and replaces the row from the redacted
  response` — the mocked revoke response now also flips
  `providerRevokeSupported` to `false` with the env-managed-only reason
  string. The post-revoke disabled assertion still holds, but now via the
  capability-driven gate rather than the deprecated stored-token gate.
- `surfaces revoke provider errors and reloads inventory`,
  `does not revoke OAuth token when confirmation is cancelled`,
  `requires reauthorization by clearing stored token status`,
  `refreshes OAuth credential token status`, plus all menu and inventory
  tests are unchanged.

### Static gates

```bash
cd ecm-frontend
npm run lint
CI=true npm run build
```

Result: both pass. The CRA build emits only the existing bundle-size advisory
and the Node `fs.F_OK` deprecation warning, both pre-existing.

### Repository hygiene

```bash
git diff --check
```

Result: passes.

## Files Changed

- `ecm-frontend/src/services/oauthCredentialAdminService.ts`
- `ecm-frontend/src/pages/OAuthCredentialAdminPage.tsx`
- `ecm-frontend/src/pages/OAuthCredentialAdminPage.test.tsx`
- `docs/OAUTH_CREDENTIAL_REVOKE_CAPABILITY_UI_DESIGN_VERIFICATION_20260507.md`

## Remaining Work

- A live full-stack smoke remains useful after deployment because the targeted
  UI tests stub the service. The local integration gate is
  `scripts/oauth-credential-admin-preflight.sh`.
- The confirmation dialog copy still references Google. When backend revoke
  coverage expands to additional providers (a Package A follow-up), the
  dialog wording can be retuned to reference a generic "provider" — that is
  intentionally out of scope here, since changing the wording without backend
  coverage to match would mislead the operator.
- This slice does not introduce backend or contract changes. Package A owns
  the `OAuthCredentialInventoryItem.providerRevokeSupported` /
  `providerRevokeUnsupportedReason` decision tree.
