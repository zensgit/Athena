# SMTP Preset Defaults + Test SMTP UI — Design and Verification (2026-05-07)

Branch: `claude/smtp-ui` (worktree at `Athena-smtp-ui`)

## Context

This is Package B in a 3-package parallel slice. Package A is extending the
backend `MailProviderPreset` payload with three SMTP fields and adding a new
admin endpoint `POST /api/v1/admin/email/test-smtp` that sends a one-shot
test email through the application-level `spring.mail.*` transport. Package B
consumes that contract from the existing `MailAutomationPage`.

The backend contract is **fixed** by the brief and is being implemented in
parallel; this slice does not touch backend code (no `ecm-core/...` files
were modified). The shape we consume:

```ts
interface MailProviderPreset {
  id: 'ALIYUN_QIYE' | 'TENCENT_EXMAIL' | 'TENCENT_EXMAIL_OVERSEAS' | 'MAIL_263' | 'MAIL_263_OVERSEAS';
  label: string;
  imapHost: string;
  imapPort: number;
  imapSecurity: 'SSL' | 'STARTTLS' | 'NONE';
  // Newly added by Package A:
  smtpHost: string;
  smtpPort: number;
  smtpSecurity: 'SSL' | 'STARTTLS' | 'NONE';
}

// New admin endpoint (Package A):
// POST /api/v1/admin/email/test-smtp
// Body:    { to: string }
// Returns: { ok: boolean; message: string; smtpHost: string | null;
//            smtpPort: integer | null; fromAddress: string | null;
//            diagnostic: string | null }
```

## Design

### B1. Read-only SMTP defaults block under the IMAP fields

The mail account dialog already exposes a `Provider preset` dropdown that
fills the IMAP host/port/security fields. SMTP, by contrast, is
application-level configuration (`spring.mail.host`, `spring.mail.port`,
`spring.mail.properties.mail.smtp.<...>`) — not a per-account property — so
adding SMTP form inputs to the account form would mislead operators.

Instead, when a non-Custom preset is selected, an outlined `Alert` of
severity `info` renders just below the IMAP `Security` selector inside the
account dialog. It surfaces the preset's SMTP host, port, and security
algorithm in monospace, with copy that explains where to set them and
points the operator to the new Test SMTP control. The block is hidden
when `selectedProviderPresetId === ''` (Custom).

The block reads directly from the selected preset (`useMemo` on
`providerPresets` and `selectedProviderPresetId`); no third state slot,
no new form field. This avoids drift between what the dropdown applied
and what the info block displays.

### B2. Admin Test SMTP dialog

A small outlined `Test SMTP` button is added next to the existing
`Trigger Fetch` button in the page header (`Box display="flex"` row at
the top of the page). This mirrors how other Athena admin pages surface
single-purpose admin tools without nesting them inside record rows.

The dialog has:

- A single `Recipient email` `TextField`, autofocused.
- A `Send test` button gated by client-side validation
  (`trim().length > 0 && includes('@')`). The button is also disabled
  while a request is in flight, with a spinner in `startIcon`.
- A `Cancel` button that switches to `Close` after a successful send so
  the operator can read the success line and dismiss explicitly. We
  preferred an explicit close button over a 3-second auto-close: it
  avoids `setTimeout`/cleanup boilerplate and lets the operator dwell
  on the SMTP host/port confirmation.

Submission calls `mailAutomationService.testSmtp({ to })`. The handler
distinguishes:

- `response.ok === true` → green Alert: `"Sent! SMTP <host>:<port> from <from>"`.
  If any optional field is null or blank, the UI renders `configured host`,
  `default port`, or `configured sender` instead of leaking `null` into the
  success line.
- `response.ok === false` → red Alert with `response.message`. If
  `response.diagnostic` is non-null, it renders in a styled `<pre>` block
  beneath the message (max-height 200px, `whiteSpace: pre-wrap`,
  `wordBreak: break-word`).
- HTTP rejection → red Alert with `resolveErrorMessage(err, fallback)`,
  the same helper used by `OAuthCredentialAdminPage`. This honors the
  axios error envelope (`err.response.data.message`) and falls through
  to `Error.message` and a fallback string.

The frontend does NOT role-gate the button. The backend mounts
`/api/v1/admin/email/...` behind admin authorization; if the caller
lacks the role, the 401/403 response body's message surfaces in the
red Alert. A separate frontend role check would only create drift.

### B3. Phase 5 Mocked HTML-fallback shape guard

The Phase 5 Mocked CI gate serves the SPA's `index.html` with HTTP 200
for unmocked routes (see `feedback_phase5_mocked_html_fallback.md`).
A naive consumer would crash at `response.ok` against the parsed HTML
body. We defend in two layers:

1. **Service layer** (`mailAutomationService.testSmtp`): if the
   resolved value is not an object, or `typeof response.ok !== 'boolean'`,
   the service throws `new Error(TEST_SMTP_UNEXPECTED_RESPONSE_MESSAGE)`.
2. **Page handler** (`handleSubmitTestSmtp`): re-applies the same shape
   check after `await`. This matters for tests that mock the service
   directly and bypass the service-level guard, and is cheap defense in
   depth at runtime.

The synthetic message is exported from the service module so tests can
assert it without string drift:

```ts
export const TEST_SMTP_UNEXPECTED_RESPONSE_MESSAGE =
  'Test SMTP endpoint returned an unexpected response. Mocked CI gate may not cover it; runtime configuration may be missing.';
```

`listProviderPresets()` already had this protection at the page layer
(`Array.isArray(presets) ? presets : []`). For symmetry, the service now
also returns `[]` when the response isn't an array, without changing
page behavior — the existing "malformed preset response degrades to
Custom" test still pins the user-facing outcome.

## Verification

### Targeted Jest tests

```bash
cd /Users/chouhua/Downloads/Github/Athena-smtp-ui/ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/pages/MailAutomationPage.presets.test.tsx \
  src/pages/MailAutomationPage.testSmtp.test.tsx \
  --watchAll=false
```

Result after Codex integration review: `Test Suites: 2 passed, 2 total. Tests: 15 passed, 15 total.`

`MailAutomationPage.presets.test.tsx` (existing, extended):

- All 6 prior assertions still hold (preset fetch, three preset selections
  fill IMAP fields, manual edits persist, fetch failure degrades to
  Custom, malformed response degrades to Custom).
- New: a preset selection reveals the read-only SMTP defaults block with
  the fixture's `smtpHost` / `smtpPort` / `smtpSecurity`.
- New: switching back to Custom hides the SMTP defaults block.

`MailAutomationPage.testSmtp.test.tsx` (new):

- Dialog opens via the header `Test SMTP` button; `Send test` is
  disabled until a recipient containing `@` is entered.
- `ok=true` resolution renders a success Alert containing
  `smtpHost:smtpPort` and the from-address; the action footer shows a
  `Close` button.
- `ok=true` with nullable SMTP metadata renders stable fallback labels
  instead of `null:null`.
- `ok=false` resolution with a `diagnostic` renders a red Alert with
  `message` and a `<pre>` diagnostic block.
- Service rejection with an axios-style error envelope surfaces the
  backend `message` via `resolveErrorMessage`.
- Service resolves `null` (Phase 5 HTML fallback) → page does not
  crash, red Alert contains `TEST_SMTP_UNEXPECTED_RESPONSE_MESSAGE`,
  and the page heading text is still in the DOM.

### Lint

```bash
npm run lint
```

Result: clean (no errors, no warnings on the changed files).

### Production build

```bash
CI=true npm run build
```

Result: `Compiled successfully.` (CRA bundle-size advisory is unchanged
and unrelated to this slice).

### Whitespace

```bash
git -C /Users/chouhua/Downloads/Github/Athena-smtp-ui diff --check
```

Result: clean.

## Files Changed

- `ecm-frontend/src/services/mailAutomationService.ts` — extended
  `MailProviderPreset` with `smtpHost` / `smtpPort` / `smtpSecurity`,
  added `MailTransportSecurity`, `EmailTestSmtpRequest`,
  `EmailTestSmtpResponse`, `TEST_SMTP_UNEXPECTED_RESPONSE_MESSAGE`,
  `testSmtp(...)` method, and a defensive shape guard inside
  `listProviderPresets()`.
- `ecm-frontend/src/pages/MailAutomationPage.tsx` — header `Test SMTP`
  button, `resolveErrorMessage` helper, Test SMTP dialog state and
  handlers, `selectedProviderPreset` `useMemo`, read-only SMTP defaults
  `Alert` block in the account dialog, and the new dialog markup with
  success/error/diagnostic surfaces.
- `ecm-frontend/src/pages/MailAutomationPage.presets.test.tsx` —
  extended `PRESET_FIXTURE` with the new SMTP fields; added two tests
  covering the SMTP defaults block visibility.
- `ecm-frontend/src/pages/MailAutomationPage.testSmtp.test.tsx` — new
  test file (5 tests) covering the Test SMTP dialog flow, including
  the Phase 5 HTML-fallback resilience.
- `docs/SMTP_PRESET_AND_TEST_UI_DESIGN_VERIFICATION_20260507.md` — this
  document.

No backend (`ecm-core/...`) files, `.env`, or top-level meta files were
touched.

## Remaining Work

- Package A has been integrated on `main` together with this UI slice.
  End-to-end verification of `Test SMTP` against a real SMTP transport
  remains an operator/runtime activity because it depends on deployment
  SMTP credentials.
- A Phase 5 Mocked harness route for
  `/admin/email/test-smtp` should be added in a follow-up so the new
  surface is covered without relying on the synthetic shape-guard
  message in CI; the synthetic message remains a runtime safety net.
- The Test SMTP dialog could grow a "From override" field if Athena
  later supports per-tenant SMTP identities; today the from address is
  whatever `spring.mail.username` or `mail.from` resolves to, and is
  reported back to the operator via the `fromAddress` response field.
