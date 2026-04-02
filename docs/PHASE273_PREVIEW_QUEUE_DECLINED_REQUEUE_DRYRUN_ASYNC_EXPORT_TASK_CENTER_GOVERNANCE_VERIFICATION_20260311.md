# Phase273 验证记录：Preview Queue Declined Requeue Dry-run Async Export Task Center Governance（2026-03-11）

## 1. 验证目标

1. 后端异步导出治理 API（start/list/summary/get/cancel/download/cleanup/cancel-active）可用且受管理员权限保护。
2. start 去重命中时可复用活跃任务并返回 dedup 响应字段。
3. 前端 Task Center 可执行刷新、下载、取消、清理治理动作。
4. mocked e2e 覆盖端到端交互链路。

## 2. 执行命令与结果

### Backend（`ecm-core/`）

```bash
mvn -q -DskipTests compile
```

结果：通过。

```bash
mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest test
```

结果：通过。

```bash
mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest,PreviewQueueServiceTest test
```

结果：通过。

重点覆盖：

1. requeue dry-run async export 全量端点的 admin 权限校验。
2. start 第二次同过滤命中 dedup（返回复用 taskId）。
3. list/summary/get/download/cancel-active/cleanup 行为正确。
4. 异步任务到 terminal 状态后可下载 CSV。

### Frontend（`ecm-frontend/`）

```bash
npm run lint -- src/services/previewDiagnosticsService.ts src/pages/PreviewDiagnosticsPage.tsx e2e/admin-preview-diagnostics.mock.spec.ts
```

结果：通过。

```bash
npm run build
```

结果：通过（Compiled successfully）。

```bash
npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium
```

结果：通过（1 passed）。

重点覆盖：

1. 页面可启动 requeue dry-run async export。
2. Task Center 汇总与任务列表刷新正常。
3. 行级 cancel/download 与批量 cancel-active/cleanup 正常。
4. mocked 请求参数与过滤上下文一致。

## 3. 结论

Phase273 已完成并验证：

1. requeue dry-run 导出具备异步任务中心治理能力。
2. start 去重减少重复导出任务，提升并行稳定性。
3. 后端测试、前端 lint/build、mocked e2e 均通过，当前阶段无可见回归。
