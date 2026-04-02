# Phase271 验证记录：Preview Queue Declined Requeue Dry-run Reason Breakdown + CSV Governance（2026-03-11）

## 1. 验证范围

1. 后端可编译，且 `requeue dry-run`/`dry-run export` 安全与行为测试通过。
2. 前端类型、页面交互、mocked e2e 联动通过。
3. 新增导出与 reason breakdown 在 UI 和 CSV 中均可见。

## 2. 执行命令与结果

### 2.1 Backend

在 `ecm-core/` 执行：

```bash
mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest,PreviewQueueServiceTest test
```

结果：通过（`BUILD SUCCESS`）。

```bash
mvn -q -DskipTests compile
```

结果：通过（修复了 `PreviewDiagnosticsController` 中缺失的 dry-run 计算/导出/审计方法导致的编译阻塞）。

### 2.2 Frontend

在 `ecm-frontend/` 执行：

```bash
npm run lint -- src/pages/PreviewDiagnosticsPage.tsx src/services/previewDiagnosticsService.ts e2e/admin-preview-diagnostics.mock.spec.ts
```

结果：通过。

```bash
npm run build
```

结果：通过（`Compiled successfully`）。

```bash
npx serve -s build -l 5510
ECM_UI_URL=http://localhost:5510 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium
```

结果：通过（`1 passed`）。

## 3. 核心断言（新增能力）

1. `POST /queue/declined/requeue/dry-run` 响应包含：
   - `results[].reasonCode`
   - `reasonBreakdown[]`
2. `GET /queue/declined/requeue/dry-run/export`：
   - 返回 `text/csv;charset=UTF-8`
   - 包含明细头：`...reasonCode...`
   - 包含 breakdown 区：`reasonCode,outcome,count`
3. 审计事件新增/增强：
   - `PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN`（带 `reasonBreakdown=`）
   - `PREVIEW_QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORTED`
4. 前端新增按钮：
   - `Export Requeue Dry-run CSV` 点击后成功提示
5. mocked e2e 覆盖：
   - dry-run reason breakdown 渲染
   - dry-run export 调用参数与成功路径

## 4. 结论

Phase271 交付通过：后端编译与安全测试通过，前端 lint/build/mock e2e 通过，dry-run 诊断从“仅计数”升级为“可解释 + 可导出 + 可审计”的治理闭环。
