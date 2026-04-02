# Phase 205 - Search Scope Batch Executor Refactor - Development

## Date
2026-03-08

## Goal
- 对标 Alfresco 批处理动作编排思路，抽取可复用批执行器，统一成功/跳过/失败统计模型。
- 降低控制器内批量循环与异常分支重复逻辑，提升后续批任务并行开发速度。

## Implemented

### 1) Backend: generic batch executor
- Added `ecm-core/src/main/java/com/ecm/core/batch/BatchExecutor.java`
  - `BatchExecutor.run(...)` 统一处理：
    - input null-safe
    - item-level outcome 聚合（`SUCCEEDED/SKIPPED/FAILED`）
    - exception -> error mapper
    - run summary (`requested/processed/succeeded/skipped/failed/results`)

### 2) Backend: search-scope queue action refactor
- Updated `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`
  - `queueFailedPreviewsBySearch(...)` 改为通过 `BatchExecutor.run(...)` 执行每条匹配记录。
  - 新增 `queueSearchResultPreview(...)`，将单条记录的排队结果映射为统一 outcome DTO。
  - 失败场景统一走 error mapper，避免控制器层重复 try/catch 栈。

### 3) Tests
- Added `ecm-core/src/test/java/com/ecm/core/batch/BatchExecutorTest.java`
  - 覆盖聚合计数与异常映射路径。
- Updated `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerSecurityTest.java`
  - 将 `advancedSearch` 调用次数断言调整为 `atLeastOnce`，匹配分页扫描策略，避免脆弱断言。

## Impact
- 为后续 `ops recovery` 与 `preview diagnostics` 批操作提供统一执行内核。
- 批处理统计结构保持兼容现有前端展示字段，减少 API 变更风险。
