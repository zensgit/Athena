# Phase 1 P102 Verification: E2E Target Guardrail (3000 vs 5500)

Date: 2026-02-12

## Validation Commands

1. Check dev target

```bash
./scripts/check-e2e-target.sh http://localhost:3000
```

Expected:
- `detected_mode=dev`
- exit `0`

2. Check static target

```bash
./scripts/check-e2e-target.sh http://localhost:5500
```

Expected:
- `detected_mode=static`
- exit `2`

3. Allow static (explicit override)

```bash
ALLOW_STATIC=1 ./scripts/check-e2e-target.sh http://localhost:5500
```

Expected:
- exit `0` with warnings (explicitly acknowledged)

## Suggested E2E Workflow

```bash
./scripts/check-e2e-target.sh "${ECM_UI_URL:-http://localhost:3000}"
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 ECM_API_URL=http://localhost:7700 npx playwright test --reporter=list
```

## Outcome

- Guardrail correctly detects dev vs static bundle targets and prevents accidental stale runs by default.
