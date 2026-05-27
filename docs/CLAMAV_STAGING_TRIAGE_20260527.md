# ClamAV Staging Triage — Issue #19 (2026-05-27)

Read-only triage for GitHub issue **#19** ("Non-blocking staging alert: ClamAV container reports unhealthy"), follow-up to the validated staging deploy **#18** (`b7c7988`, host `23.254.236.11`). **No code/config change in this doc** — produced off-box (this dev box has no Docker daemon and no access to the staging host); all findings are from repository evidence, root-cause is ranked hypotheses for the owner to confirm from staging logs.

Disposition chosen by owner: **Acceptance (b) — virus scanning is optional for the staging acceptance scope; degraded mode documented and made explicit.**

## 1. It does NOT block the core path — confirmed reason

`athena-clamav-1` unhealthy cannot drag down `athena-ecm-core-1`:

- **No actuator health indicator is wired to ClamAV.** Sweep of `ecm-core/.../health/` and all `HealthIndicator` implementors found **zero** antivirus/ClamAV coupling. So `/actuator/health` reports `UP` independent of ClamAV state — exactly why #18's backend stayed UP. (We deliberately do **not** add an AV health indicator: that would couple `/actuator/health` to ClamAV and contradict the validated #18 baseline.)

➡️ #19's Boundary holds: do **not** reopen #18.

## 2. Load-bearing finding — current staging = silent unscanned uploads

This is the real issue, not an availability footnote:

- Docker profile defaults `ECM_ANTIVIRUS_ENABLED` to **`true`** (`docker-compose.yml:60`, `application-docker.yml:64`). So scanning is **enabled** on staging.
- The upload pipeline is **fail-open**: when AV is enabled but ClamAV is unavailable, `VirusScanProcessor` catches `AntivirusException` and **allows the upload** with only a `log.warn` (`VirusScanProcessor.java:120-130`; `AntivirusService.scan` throws on comms error at `:141-144`).

➡️ **Today on staging, uploads are accepted UNSCANNED, surfaced only as a warn line.** That is a *security posture*, currently implicit. The fix below makes it intentional and visible without changing the effective posture.

## 3. Disposition (Acceptance b) — make the degraded mode explicit

Set scanning **off** on staging so the pipeline SKIPS cleanly instead of silently failing open:

- Set `ECM_ANTIVIRUS_ENABLED=false` in the staging environment.
  - Note: `docker-compose.prod.yml` does **not** override this, so it inherits the base default `true`; the operator must set it explicitly in their env file / export at cutover.
  - With it false, `VirusScanProcessor` returns `SKIPPED` up front (`:41-47`) and `AntivirusService.scan` returns `skipped(...)` (`:101-104`) — a clean, logged SKIP, **no fail-open warn, no comms attempt**.
- Stop / do not start the `clamav` service in the staging compose set (removes the unhealthy container and the freshclam egress load).
- Document, in the staging acceptance record, that **staging accepts uploads without virus scanning by design**; full AV is a production-environment control.

Effective security posture is unchanged from today (uploads were already unscanned), but it becomes a deliberate, recorded decision rather than a silent warn.

## 4. Root-cause hypotheses (for the record — ranked; confirm from staging logs)

Not needed to close under (b), but recorded so a future "AV required" decision starts here. Off-box, so ranked hypotheses + the log line that discriminates each:

1. **freshclam cold-boot DB download > `start_period`.** `clamav/clamav:stable` downloads the full signature DB (hundreds of MB) on first boot; `clamd` does not answer the `clamdscan --ping 5` healthcheck until signatures load. `start_period: 120s` (`docker-compose.yml:325`) is frequently too short for a cold first boot. → `docker compose logs clamav` for `Database updated` / `daemon started` timing vs. 120s.
2. **clamd OOM loading signatures.** Loading the DB needs ~1–2 GB RSS; no `mem_limit`/`deploy.resources` is set on the clamav service. On a memory-tight host clamd is killed and the container flaps. → exit code `137` / host `dmesg` OOM-killer lines.
3. **freshclam mirror egress blocked/slow** from `23.254.236.11`. → freshclam errors / timeouts in the logs.

**Daemon-free config improvement worth considering (separate, gate-decided, NOT applied here):** bump `docker-compose.yml:325` `start_period: 120s` → `300s`/`600s`. Low-risk, posture-neutral, targets hypothesis #1. Relevant only if AV is later made required (Acceptance c); under (b) the container is stopped so it is moot.

**Noted, not opened — fail-open is global.** The same `VirusScanProcessor:120-130` fail-open path means that even in **production** (where AV is wanted on), a ClamAV outage silently allows unscanned uploads with only a warn. That is a real security-posture gap, but it is **out of #19's scope** and the product track is paused (Refresh 4). Flagged here for the record; a fail-closed / alert-on-AV-down slice is **not** opened off this issue — it waits for an operator signal or explicit authorization.

## 5. Owner receipt skeleton (redacted; fill from the staging host)

For the #19 close under (b). Share statuses/log excerpts only — no secret values.

```
ECM_ANTIVIRUS_ENABLED set to false on staging env: ___ (file/mechanism, no values)
ecm-core restarted; startup log shows "Antivirus service is disabled": ___
Upload smoke: file uploaded + downloadable, pipeline log shows AV SKIPPED (not fail-open warn): ___
clamav service stopped / not started: docker compose ps (no athena-clamav-1, or removed): ___
backend /actuator/health still UP after change: ___
Staging acceptance record updated: "staging accepts unscanned uploads by design": ___ (link)
```

(If owner instead later picks Acceptance c — AV required — capture the §4 discriminating log lines and the chosen remediation; that is a separate task, not this close.)

## 6. Verification (this triage)

- "No AV health indicator" — verified by sweep of `health/` + all `HealthIndicator` implementors (empty).
- Fail-open path — `VirusScanProcessor.java:120-130`; clean SKIP path — `:41-47` and `AntivirusService.java:101-104`.
- AV-enabled-by-default on docker profile — `docker-compose.yml:60`, `application-docker.yml:64` (base `application.yml:295` defaults `false`).
- Healthcheck / no mem_limit — `docker-compose.yml:320-325`, `307-319`.
- Off-box: no Docker daemon here, no access to `23.254.236.11`; root-cause is hypotheses, not a verdict. `.env` untouched; doc-only, uncommitted pending gate.
