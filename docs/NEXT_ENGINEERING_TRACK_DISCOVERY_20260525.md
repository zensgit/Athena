# Next Engineering Track Discovery - 2026-05-25

Status: read-only discovery. No production, test, resource, frontend, workflow, or `.env` changes.

## Executive Recommendation

Default recommendation: **Engineering Track E1 - backend pre-CI local verification ergonomics**.

Reason: the product-capability queue is intentionally paused after Refresh 3, and recent CI history shows repeated avoidable Backend Verify failures from test compile / strict Mockito / fixture drift. The repo currently has no non-Docker backend Maven entrypoint on this dev box, so many Java issues are first caught only after push.

Do **not** start another product feature by default. If engineering work continues, fix the feedback loop first.

## Evidence

### Current repo state

- `HEAD == origin/main == b7fa818` when this discovery started.
- Working tree had only the pre-existing `M .env`.
- Product auto-picking was paused after `docs/PRODUCT_CAPABILITY_DISCOVERY_REFRESH3_20260525.md`; the C2 workflow bulk-reassign brief was archived as docs-only and not implemented.

### Local backend verification is Docker-gated

`ecm-core/mvnw` is not the standard Maven wrapper. It is a Docker launcher:

```sh
if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required to run this wrapper" >&2
  exit 1
fi

exec docker run --rm ... maven:3.9-eclipse-temurin-17 mvn "$@"
```

This host repeatedly cannot use that path because the Docker socket is unavailable:

```text
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock
```

This limitation is recorded across many 2026-05 verification docs, including saved-search CSV export, bulk share-link, mail preview export, backend response-contract slices, and sensitive-data logging remediation.

### Recent CI failures support the same diagnosis

Recent main runs show multiple first-run failures that CI caught before local backend verification could:

| Run | Head | Outcome | Root cause class |
|---|---|---|---|
| `26400394461` | `323ac39` | failure | `testCompile`: `SearchResult.id` is `String`, fixture passed `UUID` |
| `26393290710` | `bf62879` | failure | `@RequiredArgsConstructor` arity ripple into standalone controller test |
| `26379893132` | `2ec3347` | failure | test compile mismatch (`new Node()` vs mockable type) |
| `26360882665` | `0fbf8aa` | failure | Liquibase XML entity + Mockito unused stubs |
| `26358365652` | `5d374a3` | failure | Mockito `UnnecessaryStubbingException` on guard-path test |

These are not product regressions. They are feedback-loop defects: small Java/test mistakes escape local validation and consume a full CI cycle.

### Existing partial precedent

Some older focused scripts already support a `MAVEN_BIN` override or temporary Maven path:

- `scripts/property-encryption-closeout-preflight.sh` prefers `MAVEN_BIN`, `/tmp/apache-maven-3.9.9/bin/mvn`, `mvn`, then `./mvnw`.
- Several OAuth/property-encryption docs used `/tmp/codex-maven/apache-maven-3.9.11/bin/mvn` or `/tmp/apache-maven-3.9.9/bin/mvn`.

But there is no general repo-level backend preflight command for ordinary slices.

## Ranked Engineering Candidates

### E1. Backend pre-CI local verification ergonomics - recommended

Build a small backend verification helper that works without Docker when a host Maven binary is available, and falls back clearly when it is not.

Proposed scope:

- Add `scripts/backend-preflight.sh`.
- Resolve Maven in this order:
  1. `MAVEN_BIN` if executable.
  2. `/tmp/codex-maven/apache-maven-3.9.11/bin/mvn`.
  3. `/tmp/apache-maven-3.9.9/bin/mvn`.
  4. `mvn` on `PATH`.
  5. `ecm-core/./mvnw` as last fallback.
- Default command: `test-compile` or focused `-Dtest=... test` when supplied.
- Keep `-Dspring.profiles.active=test` and `-Dmaven.repo.local=ecm-core/.m2-cache/repository` unless explicitly overridden.
- Print a deterministic blocker if neither Maven nor Docker-backed wrapper can run.
- Add a short verification doc and update future slice templates to prefer this helper.

Out of scope:

- Do not replace the Docker-backed `ecm-core/mvnw` yet.
- Do not install Maven globally.
- Do not modify CI workflow unless discovery finds the helper belongs there later.
- Do not touch `.env`, docker-compose, application YAML, or production code.

Why first:

- Directly addresses the most common current failure pattern.
- Small, low-risk, script/doc only.
- Improves all future Java/backend slices, not one feature.

Open gate questions:

- Should the helper default to `test-compile` or full `test`?
- Should it auto-download Maven into `/tmp/codex-maven` when missing, or only use existing binaries? Recommendation: **do not auto-download in v1**; keep it deterministic and no-network by default.
- Should it be wired into any existing preflight scripts immediately? Recommendation: **no**; ship as standalone first.

### E2. CI/E2E gate startup and timing observability

The CI workflow has repeated stack startup snippets across Acceptance Smoke, Frontend E2E Core, and Phase C:

- `docker compose up -d ...`
- service health polling with fixed timeouts
- backend `/actuator/health` wait
- frontend reachability wait

E2E is currently green, but failures are expensive and sometimes opaque. A small track could extract shared startup/wait logic into a script that emits timing summaries and common diagnostics.

Proposed scope:

- Add `scripts/ci-stack-start.sh` or similar.
- Keep current workflow behavior but centralize wait logic.
- Emit per-service startup durations and final health snapshot.
- Keep upload artifacts unchanged.

Why not first:

- Recent E2E gates are green.
- Workflow refactors carry risk because they touch the CI critical path.
- The pain is real but less immediate than backend pre-CI feedback.

### E3. E2E inventory and ownership map

There are 79 top-level E2E spec files. The CI core gate runs a curated subset plus preview/search regression and Phase 5 mocked gates. A doc-only inventory could map each spec to owner/gate/status to reduce accidental expansion.

Proposed scope:

- Read-only inventory doc.
- Map specs into: CI core, Phase 5 mocked, acceptance smoke, local-only, historical/unknown.
- Identify duplicate or stale specs without deleting anything.

Why not first:

- Useful documentation, but it does not by itself reduce failures.
- It can become inventory work without immediate operational gain.

### E4. Runtime observability follow-up

There are existing focused observability surfaces:

- Mail runtime metrics (`/api/v1/integration/mail/runtime-metrics`).
- Tenant metrics.
- Property encryption diagnostics.
- System status / ML health.

A broader observability track could unify these, but that is product/ops design rather than an obvious next slice.

Why not now:

- No incident or operator signal points to a specific missing metric.
- Risk of broad dashboard work without a concrete failure mode.

## Recommendation Matrix

| Option | Recommendation | Rationale |
|---|---|---|
| Pause | Acceptable | Product queue is weak; no urgent engineering bug is open. |
| E1 backend pre-CI helper | **Best next track** | Small, immediate leverage, explains recent CI churn. |
| E2 CI/E2E observability | Second | Useful but touches CI critical path. |
| E3 E2E inventory | Third | Low risk, lower immediate payoff. |
| E4 runtime observability | Defer | Needs incident/operator signal. |

## Proposed Worker Brief for E1

If the gate accepts E1, use this as the implementation brief:

Scope:

- Add `scripts/backend-preflight.sh`.
- Add `docs/BACKEND_PREFLIGHT_HELPER_DESIGN_VERIFICATION_20260525.md`.
- Do not modify production Java, frontend, CI workflow, `.env`, YAML, docker-compose, or `ecm-core/mvnw`.

Required behavior:

- `scripts/backend-preflight.sh test-compile` runs `mvn -B -Dstyle.color=never -Dspring.profiles.active=test test-compile` from `ecm-core`.
- `scripts/backend-preflight.sh -Dtest=SavedSearchServiceCsvExportTest test` passes through arbitrary Maven goals/options.
- Maven resolution order: `MAVEN_BIN`, known `/tmp` Maven paths, `mvn` on `PATH`, then `ecm-core/./mvnw`.
- If the final fallback fails because Docker socket is missing, preserve the actual error and add a one-line hint explaining how to set `MAVEN_BIN`.
- Shell script must pass `bash -n`.

Tests / verification:

- `bash -n scripts/backend-preflight.sh`.
- Dry-run or resolver test if implemented as shell-only flags.
- On this host, expected outcome may still be Docker-blocked if no local Maven binary exists; document that honestly.
- `git diff --check -- . ':!.env'`.

Commit cadence:

1. `chore(core): add backend preflight helper`
2. `docs(core): record backend preflight helper verification`
3. Push and monitor CI.
4. If 7/7 green, append CI follow-up and commit `[skip ci]`.

## Explicit Non-Goals

- No product feature implementation.
- No C2 workflow bulk reassign implementation unless the user explicitly reopens it.
- No CI workflow rewrite in the first E1 slice.
- No Maven global installation.
- No changes to secrets, `.env`, or local machine configuration.
