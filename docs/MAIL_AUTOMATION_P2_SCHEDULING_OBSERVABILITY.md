# Mail Automation P2: Scheduling, Observability, Permission Context

## Goals
- Respect per-account poll intervals while keeping a lightweight scheduler loop.
- Improve operational visibility with metrics and clearer run summaries.
- Ensure background mail ingestion runs with a predictable permission context.

## Scheduling
- Scheduler tick is configurable via `ecm.mail.fetcher.poll-interval-ms` (default: 60000).
- Each account respects `mail_accounts.poll_interval_minutes` (default: 10 when unset/invalid).
- Manual fetch (`/api/v1/integration/mail/fetch`) bypasses the interval guard.

## Permission Context
- When no authenticated user is present, mail fetch runs under a system identity.
- Default user: `ecm.mail.fetcher.run-as-user` (default: `admin`).
- This enables folder assignment and tagging during scheduled runs.

## Observability
Metrics (Micrometer):
- `mail_fetch_run_duration` (timer) – total run duration per scheduler tick.
- `mail_fetch_account_duration` (timer) – per-account processing duration.
- `mail_fetch_accounts_total` (counter) with tags:
  - `status`: `ok`, `error`, `skipped`
  - `reason`: `none`, `exception`, `poll_interval`
- `mail_fetch_messages_total` (counter) with tags:
  - `status`: `processed`, `skipped`, `error`
  - `reason`: `content`, `already_processed`, `no_rule`, `no_content`, `exception`

Logs:
- Run start/end includes attempted vs skipped account counts.
- Per-folder unread message counts remain logged.
