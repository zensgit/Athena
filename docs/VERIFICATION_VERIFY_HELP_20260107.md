# Verification: verify.sh Help Output (2026-01-07)

- Command: `bash scripts/verify.sh --help`
- Result: help text printed as expected.
- Output log: `tmp/20260107_115216_verify-help.txt`

```
Usage: scripts/verify.sh [--no-restart] [--smoke-only] [--skip-build] [--wopi-only] [--skip-wopi] [--wopi-cleanup] [--wopi-query=<query>]
  --no-restart  Skip docker-compose restart (services must be running)
  --smoke-only  Only run API smoke tests, skip E2E tests
  --skip-build  Skip frontend build step
  --wopi-only   Only run WOPI verification (skip other steps)
  --skip-wopi   Skip WOPI verification step
  --wopi-cleanup  Remove auto-uploaded WOPI sample after verification
  --wopi-query=<query>  Search query to find WOPI document
  --wopi-query <query>  Search query to find WOPI document
```
