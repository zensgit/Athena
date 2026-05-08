# Mail Provider Setup: Aliyun / Tencent / 263 Enterprise Mailboxes

Date: 2026-05-07

## Context

Athena Mail Automation now ships preset metadata for Chinese enterprise mailboxes. The backend exposes the presets via an admin-only endpoint on the Mail Automation admin API, and the Mail Automation admin page renders a preset dropdown that pre-fills the IMAP host, port, and security fields when the operator picks a preset. The presets cover five provider variants:

- `ALIYUN_QIYE` — 阿里云企业邮箱
- `TENCENT_EXMAIL` — 腾讯企业邮箱（国内）
- `TENCENT_EXMAIL_OVERSEAS` — 腾讯企业邮箱（海外）
- `MAIL_263` — 263 企业邮箱（国内）
- `MAIL_263_OVERSEAS` — 263 企业邮箱（海外）

This document is the operator-facing companion to that work. It captures the provider-side prerequisites the operator must complete before an Athena mail account will succeed, and the manual smoke checklist to run after creating the account.

The preset metadata carries IMAP host / port / security defaults; the same `GET /api/v1/integration/mail/provider-presets` endpoint now also exposes the SMTP host / port / security defaults on each preset row (`smtpHost`, `smtpPort`, `smtpSecurity`), so operators can read the SMTP defaults straight from the admin UI's preset dropdown without consulting this document. The Mail Automation admin page renders a read-only SMTP defaults block under the preset dropdown when a preset is selected. SMTP values continue to be documented here as the canonical operator reference and as the cross-check for the values returned by the backend.

Athena Mail Automation v1 still does not send notification mail through the per-account SMTP credentials configured on a `MailAccount`. The SMTP defaults are exposed for two purposes: (a) so an operator wiring the application's outbound `spring.mail.*` settings against one of these providers can see the defaults next to the IMAP defaults, and (b) so the new admin Test SMTP control (see "Verifying SMTP after configuration" below) can be cross-checked against the canonical host/port/security combinations.

## Common prerequisites

These apply to all three providers regardless of preset choice:

- **Enable IMAP/SMTP at the provider admin console.** Most enterprise mailboxes ship with IMAP/SMTP disabled by default. The operator must turn the protocol on for the domain (or for the specific mailbox) before any client — including Athena — can connect. Per-provider steps are listed below.
- **Use the full email address as the IMAP username.** Athena passes whatever the operator enters in the `username` field straight to the provider's IMAP `LOGIN` / `AUTHENTICATE` exchange. All three providers expect the full email (`user@example.com`), not just the local part. Local-part-only logins will be rejected with `LOGIN failed` / `AUTHENTICATIONFAILED`.
- **Generate a client authorization code if the provider requires one.** Tencent and 263 both require a separate "客户端授权码" / "客户端安全密码" — the regular web-login password will be rejected by IMAP even when it works in the web UI. Aliyun also requires a "third-party client security password" for newly provisioned domains. Per-provider details are listed below; this doc never quotes any actual code or password value, only points at where the operator generates it.
- **Athena encrypts the IMAP password at rest.** The `MailAccount.password` field is annotated `@Convert(converter = EncryptedSecretConverter.class)`, so plaintext passwords are not persisted. The operator never has to inspect, decrypt, or rotate the encrypted form directly — they enter the plaintext in the admin form, the converter encrypts on write, and the IMAP fetch worker decrypts on read.
- **Pick the correct domestic / overseas preset for the Athena instance's egress.** Tencent and 263 expose distinct hostnames for international clients (`hwimap.exmail.qq.com`, `imapw.263.net`). The mailbox itself is identical; only the routing differs. Picking the wrong variant typically surfaces as long IMAP `IDLE` drops or intermittent `connection reset` events rather than outright failure.
- **Confirm the operator account has `ROLE_ADMIN`.** The presets endpoint and the account-create form both sit on the existing Mail Automation admin surface, and the backend gate is `@PreAuthorize("hasRole('ADMIN')")`. Non-admin requests receive `403 Forbidden`; that is a permission issue, not a preset-loading bug.

## Per-provider sections

For all five presets the IMAP security on port 993 is implicit TLS (SSL). The 143 / STARTTLS variants exist on every provider but are not the recommended Athena default and are not what the preset endpoint returns.

### `ALIYUN_QIYE` — 阿里云企业邮箱

- **IMAP host:** `imap.qiye.aliyun.com` **Port:** `993` **Security:** `SSL`
- **SMTP host:** `smtp.qiye.aliyun.com` **Port:** `465` **Security:** `SSL`
  - Source: [help.aliyun.com 2937082 — IMAP/SMTP 配置](https://help.aliyun.com/zh/document_detail/2937082.html).
- **Provider admin steps to enable IMAP/SMTP:**
  1. Sign in to the Aliyun Mail (阿里邮箱) admin console as a domain administrator.
  2. Navigate to "安全管理" → "账号安全策略" (Security Management → Account Security Policy).
  3. Disable the "禁止使用三方客户端" (Prohibit third-party clients) restriction, or grant the "Allow third-party clients" permission to the target mailbox.
  4. Have the mailbox owner sign in to webmail and walk through "设置" → "账户与安全" → "账号安全" → "三方客户端安全密码" → "生成新密码" to enable the per-account third-party client password.
- **Username format:** Full email address (e.g. `user@example.com`). Local-part-only is rejected.
- **Authorization code:** Required for newly provisioned domains. Generate at webmail → 设置 → 账户与安全 → 账号安全 → 三方客户端安全密码 → 生成新密码. Use the generated value as the IMAP password in Athena, not the regular login password.
- **Source:** [help.aliyun.com 2937082 — IMAP/SMTP 配置](https://help.aliyun.com/zh/document_detail/2937082.html); supplementary auth-code reference [help.aliyun.com 444269 — 员工开启和使用三方客户端安全密码](https://help.aliyun.com/document_detail/444269.html).

### `TENCENT_EXMAIL` — 腾讯企业邮箱

- **IMAP host:** `imap.exmail.qq.com` **Port:** `993` **Security:** `SSL`
- **SMTP host:** `smtp.exmail.qq.com` **Port:** `465` **Security:** `SSL`
  - Source: [service.rtxmail.net/faq/119](https://service.rtxmail.net/faq/119.html) (cert-expired fallback verified via WeCom help center [open.work.weixin.qq.com/help2/pc/19886](https://open.work.weixin.qq.com/help2/pc/19886) and cnblogs corroboration).
- **Provider admin steps to enable IMAP/SMTP:**
  1. Sign in to the Tencent Exmail (腾讯企业邮箱) admin console as a domain administrator at `https://exmail.qq.com/`.
  2. Open "邮箱设置" → "客户端设置" (Mailbox Settings → Client Settings).
  3. Enable the IMAP/SMTP service for the domain or for the target mailbox.
  4. Have the mailbox owner sign in to webmail, open "设置" → "账户" → "客户端专用密码" / "安全登录", and generate a client-specific password.
- **Username format:** Full email address (e.g. `user@example.com`).
- **Authorization code:** Required. Tencent Exmail rejects the regular login password on IMAP when "安全登录" (secure login) is enabled — which is the default for most domains. Generate the client-specific password from webmail → 设置 → 账户 → 客户端专用密码 and use that value as the IMAP password in Athena.
- **Source:** [service.rtxmail.net/faq/119](https://service.rtxmail.net/faq/119.html). _Fallback note: the primary URL returned an expired-certificate error when fetched on 2026-05-07; the IMAP / SMTP host, port, and security values listed above were cross-checked against published Tencent client-configuration references including the WeCom help center [open.work.weixin.qq.com/help2/pc/19886](https://open.work.weixin.qq.com/help2/pc/19886) and the public Tencent service center [service.rtxmail.net/help/232](http://service.rtxmail.net/help/232.html). The operator should treat the official Tencent help URL as canonical once the certificate is rotated._

### `TENCENT_EXMAIL_OVERSEAS` — 腾讯企业邮箱（海外）

- **IMAP host:** `hwimap.exmail.qq.com` **Port:** `993` **Security:** `SSL`
- **SMTP host:** `hwsmtp.exmail.qq.com` **Port:** `465` **Security:** `SSL`
  - Source: [service.rtxmail.net/faq/119](https://service.rtxmail.net/faq/119.html) (cert-expired fallback verified via WeCom help center [open.work.weixin.qq.com/help2/pc/19886](https://open.work.weixin.qq.com/help2/pc/19886) and cnblogs corroboration).
- **Provider admin steps to enable IMAP/SMTP:** Same as `TENCENT_EXMAIL`. The overseas hostnames are a routing variant, not a separate product — domain-side enable / authorization-code generation is identical.
- **Username format:** Full email address (e.g. `user@example.com`).
- **Authorization code:** Required, same flow as `TENCENT_EXMAIL`.
- **When to use:** Pick the overseas preset when the Athena instance is hosted outside mainland China and the domestic `imap.exmail.qq.com` endpoint either resolves slowly or returns connection-reset errors during IMAP `IDLE`. The overseas hostnames terminate on Tencent's overseas points-of-presence.
- **Source:** [service.rtxmail.net/faq/119](https://service.rtxmail.net/faq/119.html). _Fallback note: same as `TENCENT_EXMAIL` — primary fetch failed on 2026-05-07, overseas host values cross-checked against the WeCom help center [open.work.weixin.qq.com/help2/pc/19886](https://open.work.weixin.qq.com/help2/pc/19886)._

### `MAIL_263` — 263 企业邮箱

- **IMAP host:** `imap.263.net` **Port:** `993` **Security:** `SSL`
- **SMTP host:** `smtp.263.net` **Port:** `465` **Security:** `SSL`
  - Source: [download.263.net helpcenter/client/970](https://download.263.net/263/helpcenter/client/20160603/970.html).
- **Provider admin steps to enable IMAP/SMTP:**
  1. Sign in to the 263 Enterprise Mail admin console at `https://mail.263.net/` as a domain administrator.
  2. Open the per-mailbox security settings ("账号安全" / Account Security).
  3. Confirm IMAP/SMTP access is allowed for the mailbox.
  4. Have the mailbox owner sign in to webmail and open "账号安全" → "客户端授权码" — by default this is "关闭" (off). Switch it to "开通" (on) and confirm. The system displays the client authorization code; the operator copies it and uses it as the IMAP password in Athena.
- **Username format:** Full email address (e.g. `user@example.com`).
- **Authorization code:** Required. 263 explicitly blocks third-party clients (Foxmail / Thunderbird / Athena / etc.) from logging in with the regular webmail password; only the client authorization code is accepted. Generate it at webmail → 账号安全 → 客户端授权码.
- **Source:** [download.263.net helpcenter/client/970](https://download.263.net/263/helpcenter/client/20160603/970.html).

### `MAIL_263_OVERSEAS` — 263 企业邮箱（海外）

- **IMAP host:** `imapw.263.net` **Port:** `993` **Security:** `SSL`
- **SMTP host:** `smtpw.263.net` **Port:** `465` **Security:** `SSL`
  - Source: [download.263.net helpcenter/client/970](https://download.263.net/263/helpcenter/client/20160603/970.html).
- **Provider admin steps to enable IMAP/SMTP:** Same as `MAIL_263`. The `*w.263.net` hostnames are routing variants for clients connecting from outside mainland China; the mailbox itself is provisioned identically.
- **Username format:** Full email address (e.g. `user@example.com`).
- **Authorization code:** Required, same flow as `MAIL_263`.
- **When to use:** Pick the overseas preset when the Athena instance is hosted outside mainland China and the domestic `imap.263.net` endpoint times out or shows degraded latency. The 263 documentation lists the `*w.263.net` hosts as the international-egress variant.
- **Source:** [download.263.net helpcenter/client/970](https://download.263.net/263/helpcenter/client/20160603/970.html).

## Manual smoke checklist

Run these checks against the live Athena instance after creating the mail account and saving the IMAP password (the encrypted-on-write converter handles persistence). Replace `{id}` with the UUID `MailAccount.id` returned from the create call. The endpoints listed already exist on `MailAutomationController` — this checklist references them, it does not create them.

1. **Test connection.**
   - Call: `POST /api/v1/integration/mail/accounts/{id}/test`
   - Expected: `200 OK`, response body shows the connection succeeded and `lastFetchError` on the inventory record is null.
   - Fail mode (4xx): the IMAP enable flag is most likely still off at the provider. Walk back through the provider admin steps in the per-provider section above.
   - Fail mode (5xx with TLS / SSL handshake error): `imapSecurity` is wrong for the chosen port. Confirm the preset value matches the table — port `993` should always be `SSL`, never `STARTTLS`.
   - Fail mode (auth failure): the operator typed the regular login password. Re-enter using the client authorization code generated in the per-provider auth-code step.
2. **List folders.**
   - Call: `GET /api/v1/integration/mail/accounts/{id}/folders`
   - Expected: `200 OK` with at least one folder, typically `INBOX`.
   - Fail mode (empty list): either the mailbox is genuinely empty, or the provider is exposing system folders behind an IMAP UTF-7 modified namespace prefix (e.g. some Chinese providers prefix Chinese folder names with sequences like `&XfJT0ZAB-` for IMAP UTF-7 encoding). If `INBOX` is missing entirely, treat that as a connectivity / permission issue and retry step 1; if `INBOX` is present and only the localized folders look truncated, the UTF-7 namespace is the likely cause and the rule engine will still process `INBOX` correctly.
3. **Manual fetch.**
   - Trigger one global fetch cycle from the Mail Automation admin page or call `POST /api/v1/integration/mail/fetch`; the fetch worker attempts enabled accounts according to the existing service rules.
   - Expected: `lastFetchAt` on the inventory list updates to a timestamp within the last few seconds, and `lastFetchError` is null.
   - Fail mode (`lastFetchError` populated): read the structured diagnostics at `GET /api/v1/integration/mail/diagnostics?accountId={id}`. The diagnostics endpoint returns recent processed-mail rows and mail-ingested document rows, including status, subject, folder, UID, rule/account names, and `errorMessage` for failed processed rows.

### After the smoke checklist passes

Once all three steps in the checklist return clean, the account is ready for the rule engine. The Mail Automation admin page exposes the per-account inventory row with `lastFetchAt`, `lastFetchError`, and the matched-rule count — that row is the canonical operator-facing health view going forward, and the smoke endpoints above are only re-run when something on that row goes red.

The diagnostics endpoint (`/api/v1/integration/mail/diagnostics?accountId={id}`) is safe to call at any time; it does not mutate state and does not consume the IMAP rate-limit budget. When in doubt, hit it first before re-running the test-connection call, because the test-connection call opens a fresh IMAP session against the provider and providers may count those toward connection-rate quotas.

## Troubleshooting

- **`IMAP-NO-LOGIN-DISABLED` / `LOGIN ... AUTHENTICATIONFAILED`.** Most likely cause: IMAP is not enabled at the provider, or the operator entered the local part of the address as the username instead of the full email. Re-check the provider admin steps in the per-provider section and re-enter the full email.
- **`SSLHandshakeException` / `PKIX path validation failed` / `unable to find valid certification path`.** TLS misconfiguration. Confirm `imapSecurity` matches the port — `993` is always `SSL`, `143` is `STARTTLS`. The preset endpoint always returns `993` / `SSL`; if the operator hand-edited the form to `143` they must also flip security to `STARTTLS`. If the provider has rotated certificates and Athena's truststore is stale, this also surfaces as PKIX path failure — refresh the JVM truststore in that case.
- **`Authentication failed for user@example.com`.** Tencent and 263 both reject the regular webmail login password on IMAP; Aliyun rejects it on newly provisioned domains. The operator must generate the client authorization code (Aliyun: 三方客户端安全密码; Tencent: 客户端专用密码; 263: 客户端授权码) and re-enter that as the IMAP password in Athena. Re-running the test-connection endpoint with the authorization code should immediately succeed.
- **Timeouts during `IDLE` from outside mainland China.** Switch from the domestic preset to the corresponding overseas preset (`TENCENT_EXMAIL_OVERSEAS` or `MAIL_263_OVERSEAS`). The overseas hostnames terminate on the provider's international-egress points-of-presence and avoid the cross-border latency / connection-reset symptoms that cause `IDLE` to drop.

### Confirming preset values against the live backend

The preset dropdown values rendered in the UI come from the backend presets endpoint Package A delivers. The operator can sanity-check that the values the dropdown shows are exactly the values listed in the per-provider sections above — they should match host-for-host, port-for-port, and security-for-security across all five preset ids for both IMAP and SMTP defaults. If the UI shows a different host for any preset, the truth is the backend response and this doc; surface the drift to the team that maintains the preset enum rather than working around it in the form.

## Verifying SMTP after configuration

Once the operator has configured `spring.mail.*` (host / port / username / password / SSL or STARTTLS) and `ecm.email.from-address` against one of the provider rows above, they can verify the configuration end-to-end without dispatching a real notification template by using the new admin Test SMTP control. The dialog lives on the Mail Automation admin page and submits a single recipient address; the backend opens a `JavaMailSender` connection using the live `spring.mail.*` settings and sends a fixed `[Athena] SMTP test` subject.

The full operator-side walkthrough — required env vars, click sequence, expected success/failure shapes, and a per-error troubleshooting FAQ — is captured in the runbook at [`SMTP_TEST_OPERATOR_RUNBOOK_20260507.md`](./SMTP_TEST_OPERATOR_RUNBOOK_20260507.md). For the live notification path that actually exercises a template (site invitation), see [`SITE_INVITATION_LIVE_SEND_CLOSEOUT_20260507.md`](./SITE_INVITATION_LIVE_SEND_CLOSEOUT_20260507.md).

## Out of scope

- **Outbound SMTP through per-account `MailAccount` credentials.** Athena Mail Automation v1 still does not send mail using the per-account IMAP credentials configured on a `MailAccount`. The application's outbound notification mail flows through `spring.mail.*` (a single application-level sender) — not through the per-account fields. The new Test SMTP admin control validates `spring.mail.*`, not per-account SMTP.
- **OAuth providers for Chinese mailboxes.** None of Aliyun / Tencent / 263 are wired through Athena's OAuth credential store in this slice. The OAuth credential reauth control on the admin page covers the existing OAuth providers only; vendor-specific OAuth for Chinese mailboxes will be revisited as a separate "vendor-specific OAuth enhancement" line if and when the providers expose stable OAuth endpoints to the partner program.
- **Gmail / Microsoft 365 / Outlook.** Not covered by this preset slice. Those providers are already addressed by the existing OAuth flows on the admin page and are out of scope for the IMAP-preset surface this doc accompanies.

## Appendix: error-to-cause quick reference

The following table summarizes the most common operator-visible failures, what the operator typically did wrong, and the fastest fix. It is a cross-reference for the troubleshooting section above; the troubleshooting prose is the authoritative copy.

| Symptom (visible in `lastFetchError` or test-connection response)             | Most likely cause                                                                            | Fastest fix                                                                                          |
| ------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| `LOGIN failed` / `AUTHENTICATIONFAILED` immediately on first try               | Username is the local part only, or IMAP is disabled at the provider                          | Re-enter username as full email; confirm IMAP enable at provider admin console                        |
| `LOGIN failed` after authorization code has been generated                     | Authorization code was copied with trailing whitespace, or the code was regenerated since     | Regenerate the code at the provider, paste fresh, retry                                               |
| `SSLHandshakeException` / `PKIX path validation failed`                        | `imapSecurity` does not match the port, or JVM truststore is stale                            | Use port `993` with `SSL` (the preset default); refresh JVM truststore if certs were rotated          |
| Test-connection succeeds, list-folders returns empty                           | Mailbox genuinely empty, OR localized folders hidden behind IMAP UTF-7 namespace prefix       | Confirm `INBOX` is present; if only Chinese folders missing, the rule engine will still see `INBOX`   |
| `lastFetchAt` does not advance and `lastFetchError` is null                    | Fetch worker not scheduled, or account is in a paused state                                   | Check the Mail Automation admin page for the per-account paused / active flag; check fetch scheduler  |
| `connection reset` / repeated `IDLE` drops                                     | Cross-border egress to Chinese provider is unstable                                           | Switch from domestic preset to the matching `*_OVERSEAS` preset                                       |
| `534 5.7.9 Application-specific password required` (or similar provider text) | Tencent / 263 secure-login is on; regular password used                                       | Generate the client authorization code at provider webmail and re-enter as the IMAP password          |

If the symptom does not match any row above, fall back to the diagnostics endpoint described in the smoke checklist. The diagnostics response carries recent processed-mail status and error-message rows, which are usually enough to narrow the cause to one of the rows above.
