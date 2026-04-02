# Phase272 开发设计：Preview Queue Declined Requeue Dry-run Preflight Reason Governance（2026-03-11）

## 1. 目标

在 Phase271 的 `reasonCode + reasonBreakdown + CSV` 基础上，进一步补齐 Stream C（能力预检）缺口：把 preflight 预检结果接入 `queue declined requeue dry-run`，让“为什么会跳过”不仅来自队列策略，也能覆盖 transformer/mime/route/pipeline 维度。

## 2. 后端设计

文件：`ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

1. 注入 `PreviewPreflightResolver`。
2. 在 `computeQueueDeclinedRequeueDryRun(...)` 中：
   - 对候选文档先计算 `PreflightDecision`。
   - 若 preflight declined，则直接产出 `SKIPPED`：
     - `reasonCode = PREFLIGHT_<skipReason>`
     - `message` 回显 preflight 拒绝原因
   - accepted/bypassed 时沿用 `evaluateEnqueue(...)` 估算逻辑。
3. dry-run item 增加 preflight 字段：
   - `preflightStatus`
   - `preflightSkipReason`
   - `preflightRoute`
   - `preflightPolicyProfile`
   - `preflightPipeline`
4. dry-run CSV 明细增加上述 preflight 列；保留 reason breakdown 段。
5. 新增辅助方法：
   - `evaluateQueueDeclinedPreflightDecision(...)`
   - `toPreflightSkipReasonCode(...)`

## 3. 前端设计

文件：
- `ecm-frontend/src/services/previewDiagnosticsService.ts`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

1. dry-run item 类型补齐 preflight 字段（与后端对齐）。
2. dry-run 结果表新增 `Preflight` 列：
   - 显示 status/skipReason/route/pipeline/profile，便于治理排查。
3. 与 Phase271 的导出能力叠加，确保 UI/CSV/审计三条线一致。

## 4. mocked e2e 设计

文件：`ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

1. `/queue/declined/requeue/dry-run` mock 返回 preflight 字段。
2. `/queue/declined/requeue/dry-run/export` mock CSV 增加 preflight 列。
3. 用例新增 preflight 文案断言，验证 UI 可见性与链路连通。

## 5. 对标价值（Alfresco+）

相比仅按失败状态/重试次数做 dry-run，本阶段提供：

1. 预检拒绝原因结构化（`PREFLIGHT_*`）进入 reason breakdown。
2. route/pipeline/policy profile 下钻，支持“为何不能入队”快速定位。
3. 导出与审计可复盘，满足运维治理闭环。
