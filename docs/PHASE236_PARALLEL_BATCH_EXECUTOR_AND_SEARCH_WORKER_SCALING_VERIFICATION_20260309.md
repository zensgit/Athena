# Phase 236 - Parallel Batch Executor and Search Worker Scaling - Verification

## Date
2026-03-09

## Scope
- 验证通用并发批处理执行器与 Search preview queue worker 扩展的正确性与回归稳定性。
- 验证前端契约改动（workerCount）不引入 UI 回归。

## Commands and results

1. Backend focused tests
```bash
cd ecm-core
mvn -q -Dtest=BatchExecutorTest,SearchControllerTest,SearchControllerSecurityTest test
```
- Result: PASS

2. Backend compile
```bash
cd ecm-core
mvn -q -DskipTests compile
```
- Result: PASS

3. Frontend lint (targeted)
```bash
cd ecm-frontend
npm run -s lint -- src/services/nodeService.ts src/pages/AdvancedSearchPage.tsx
```
- Result: PASS

4. Frontend build
```bash
cd ecm-frontend
npm run -s build
```
- Result: PASS

5. Mocked E2E regression (preview/search async task-center)
```bash
cd /Users/huazhou/Downloads/Github/Athena
bash scripts/phase235-preview-async-task-center-mock-e2e.sh
```
- Result: PASS (`3 passed`)

## Verified outcomes
- `BatchExecutor.runParallel(...)`:
  - preserves ordered output
  - aggregates succeeded/skipped/failed accurately
  - maps handler exceptions to failed payloads
- `POST /api/v1/search/preview/queue-failed`:
  - default worker count is `4`
  - oversized worker request is clamped to `16`
  - response exposes effective `workerCount`
- Frontend advanced-search all-matched preview operations remain stable with explicit workerCount payload.
