# Backend Preflight Helper (E1) — Design & Verification

Date: 2026-05-25
Track: E1 in `docs/NEXT_ENGINEERING_TRACK_DISCOVERY_20260525.md` (gate-approved, option (a) — provision Maven first, then implement).

## Problem

`ecm-core/mvnw` is a Docker launcher (it `exec docker run … maven:3.9 … mvn "$@"`). On a dev box without a reachable Docker daemon, backend `testCompile` / strict-Mockito / fixture-drift mistakes can't be verified locally and only surface after push — this session alone burned five CI rounds on exactly that class (`SearchResult.id` type, `@RequiredArgsConstructor` arity, abstract `new Node()`, Liquibase XML + unused stubs, `UnnecessaryStubbingException`).

## What shipped

- `scripts/backend-preflight.sh` — runs an `ecm-core` Maven goal locally **without Docker** when a host Maven exists, falling back clearly to `./mvnw` when it doesn't.
  - Maven resolution (first hit): `$MAVEN_BIN` → `/tmp/codex-maven/apache-maven-3.9.11/bin/mvn` → `/tmp/apache-maven-3.9.9/bin/mvn` → `mvn` on `PATH` → `ecm-core/mvnw` (Docker-backed, last).
  - Default goal `test-compile` (cheapest catch of the dominant failure class); arbitrary goals/options pass through (e.g. `-Dtest=Foo test`).
  - Always injects `-Dmaven.repo.local=.m2-cache/repository` and `-Dspring.profiles.active=test` (overridable via `MAVEN_REPO_LOCAL` / `SPRING_PROFILES_ACTIVE` env, and caller `-D` flags win by last-on-line).
  - When no host Maven is found, prints the actionable `export MAVEN_BIN=…` hint, then falls back to `./mvnw` and preserves its real error (e.g. the Docker-socket failure).

## Out of scope (per the E1 brief)

- Does not install Maven globally, does not auto-download, does not modify CI, does not replace `ecm-core/mvnw`, does not touch `.env`/YAML/docker-compose/production code.

## Precondition provisioned (gate option a)

This box had no host Maven (`MAVEN_BIN` unset, no `mvn` on PATH, no `/tmp` Maven; Java 17 present). Maven 3.9.9 was provisioned **without changing global env** — downloaded to `/tmp/apache-maven-3.9.9` (the path the precedent `scripts/property-encryption-closeout-preflight.sh` and this helper already look for). This is host setup, not a repo change.

## Verification

```
bash -n scripts/backend-preflight.sh ........................ syntax OK
scripts/backend-preflight.sh  (default test-compile) ........ BUILD SUCCESS, 11s
    → 571 main + 312 test sources compiled via /tmp Maven + .m2-cache (no Docker, no network)
scripts/backend-preflight.sh -Dtest=SavedSearchServiceCsvExportTest test
    → Tests run: 5, Failures: 0, Errors: 0 — BUILD SUCCESS, 11.8s (focused pass-through works)
git diff --check -- . ':!.env' ............................. clean
```

**Proof of value:** every backend CI failure this session was a `testCompile`/test mistake that this exact ~11s command would have caught locally before push (the focused run above is the very test whose `SearchResult.id` type mistake cost a CI round — it now passes locally).

## CI Follow-Up

`scripts/backend-preflight.sh` is a local-only dev helper — it is not wired into CI in this slice. The push carries the script + this doc only; CI runs unchanged (the `[skip ci]` doc commit follows after the code commit's CI is green).

```
Run id:        26406120662
Head SHA:      642f8a12
Conclusion:    success (7/7 — gh run view authority per feedback_gh_run_watch_unreliable)
URL:           https://github.com/zensgit/Athena/actions/runs/26406120662

Jobs (7/7 green — local-only helper, no CI impact as expected):
  ✓ Backend Verify
  ✓ Frontend Build & Test
  ✓ Phase C Security Verification
  ✓ Frontend E2E Core Gate
  ✓ Property Encryption Closeout Gate
  ✓ Phase 5 Mocked Regression Gate
  ✓ Acceptance Smoke (3 admin pages)
```
