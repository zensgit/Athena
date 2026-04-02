# Phase274 验证记录：Preview Queue Declined Requeue Dry-run Async Export Status Filter UI Governance（2026-03-12）

## 1. 验证目标

1. Requeue dry-run async task center 支持状态筛选并联动列表与汇总。
2. `cleanup` 可按状态筛选执行（验证 `status=COMPLETED`）。
3. `cancel-active` 非活跃筛选下回退为 `ALL`，保持接口契约一致。
4. mocked e2e 全链路通过，无回归。

## 2. 执行命令与结果

### Frontend（`ecm-frontend/`）

```bash
npm run lint -- src/pages/PreviewDiagnosticsPage.tsx e2e/admin-preview-diagnostics.mock.spec.ts
```

结果：通过。

```bash
npm run build
```

结果：通过（Compiled successfully）。

```bash
python3 -m http.server 5511 --directory build
ECM_UI_URL=http://localhost:5511 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium
```

结果：通过（1 passed）。

说明：e2e 运行脚本中使用了服务就绪探测后再启动 Playwright，避免静态服务未就绪导致的连接拒绝假失败。

## 3. 关键断言

1. 状态筛选切换到 `Completed` 后，仅显示 completed 任务。
2. cleanup 在 `Completed` 筛选下只清理完成态任务。
3. 切换到 `Cancelled` 后可见被取消任务。
4. 调用参数断言：
   - list/summary 命中 `ALL/COMPLETED/CANCELLED`
   - cleanup 命中 `COMPLETED`
   - cancel-active 命中 `ALL`（非活跃状态不透传）

## 4. 结论

Phase274 已完成并验证：

1. requeue dry-run async task center 已具备状态筛选治理能力。
2. 列表、统计、清理动作已按筛选状态一致联动。
3. mocked e2e 回归通过，当前阶段无可见回归。
