# P5 PR-81 CI/E2E Fix Batch Review And Readiness Gate Fix

Date: 2026-04-20

## Scope

This follow-up covered formal review of the recent CI/E2E stabilization batch and one workflow hardening fix found during review.

Reviewed commits:

- `8236a8e` `fix(ci): remove unused imports + fix Phase C timeout`
- `50d7b33` `fix(ci): Phase C health-check wrong port 8080 vs 7700`
- `ddde667` `fix: /checkin 500 + pdf-preview close scoping`
- `b89cc29` `fix(checkin): preserve checkinDocument() call`
- `e2913e2` `fix(e2e): preview/search regression gate tests`
- `5c47ec3` `fix(e2e): advanced-search strict-mode match`
- `3d82170` `docs: CI post-push fixes report`
- `2d37fca` `docs: E2E regression gate bugfix report`

## Review Conclusion

No blocking correctness issue was found in the reported runtime fixes:

- the `/checkin` try/catch remains narrowly scoped to the legacy "file upload without active checkout" path
- the advanced-search and preview/search Playwright updates mostly correct wrong mocked endpoints, strict-mode selector ambiguity, and eventual-consistency timing
- the earlier healthcheck `127.0.0.1` correction and `073` backfill `nodes` join remain valid

## Follow-up Finding

One medium CI issue was confirmed in `.github/workflows/ci.yml`:

- three backend readiness loops were treating any `/actuator/health` payload containing `"status"` as ready
- that allowed CI to proceed on `DOWN` or `OUT_OF_SERVICE` responses as long as Spring returned JSON

## Fix Applied

All three readiness gates were tightened from:

- `curl -s ... | grep -q "\"status\""`

to:

- `curl -fs ... | python3 -c '... json.load(sys.stdin).get("status") == "UP" ...'`

This restores the intended contract:

- the backend must answer with an HTTP success status
- the Spring actuator health payload must be explicitly `UP`
- readiness is checked via JSON parsing rather than a brittle string match

## Files Changed In This Follow-up

- [`.github/workflows/ci.yml`](</Users/chouhua/Downloads/Github/Athena/.github/workflows/ci.yml>)

## Residual Risk

This follow-up does not rerun the entire GitHub Actions matrix locally. It hardens the readiness contract statically, but final confirmation still depends on the next CI run.
