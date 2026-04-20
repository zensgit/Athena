# P0B → P5 Startup Review And Healthcheck Fix Development

## Scope

This note records the follow-up review performed against Claude's startup report and the one runtime fix applied on top of it.

The review covered:

- Liquibase XML closure fixes in `077` and `078`
- JPA native-query JSONB operator fixes in `NodeRepository`
- multi-constructor Spring wiring fixes across the reported 10 beans
- current Docker runtime health, not just compile/startup logs

## Review Outcome

Claude's three reported startup blockers are present in the codebase as fixed:

- `077-create-legal-holds.xml` and `078-create-disposition-schedules.xml` both now end with `</databaseChangeLog>`
- `NodeRepository` now uses `jsonb_exists(...)` instead of the PostgreSQL `?` operator in native queries that also bind named parameters
- the reported Spring beans now expose an unambiguous injection path:
  - 8 beans use an explicit `@Autowired` full constructor plus a compatibility delegate
  - 2 search beans keep Lombok's `@RequiredArgsConstructor(onConstructor_ = @Autowired)` and a package-private delegate

## Additional Runtime Finding

The assembled stack still had one startup/runtime defect that the report did not capture:

- `ecm-frontend` was running but marked `unhealthy`
- container-local `wget http://localhost:80/` failed with `Connection refused`
- container-local `wget http://127.0.0.1:80/` succeeded immediately

This made the frontend healthcheck a false negative and also blocked clean `nginx` startup through `depends_on: condition: service_healthy`.

## Fix Applied

Updated `docker-compose.yml` healthchecks:

- `ecm-frontend`
  - from `http://localhost:80/`
  - to `http://127.0.0.1:80/`
- `nginx`
  - from `http://localhost:80/health`
  - to `http://127.0.0.1:80/health`

Also corrected one low-risk wording mismatch in Claude's report:

- the report previously said the 8 test-only delegates were all demoted to package-private
- current code shows a mixed state: most are package-private, while a few service delegates remain public
- the report text now reflects the actual mechanism: the `@Autowired` primary constructor is what removes Spring ambiguity

## Files Changed

- `docker-compose.yml`
- `docs/P0B_P5_STARTUP_VERIFICATION_AND_FIXES_20260418.md`

## Non-Goals

- no backlog feature work
- no migration changes
- no frontend or backend runtime code changes outside healthcheck configuration
- no backend or frontend application logic changes outside healthcheck configuration
