# Phase272 验证记录：Preview Queue Declined Requeue Dry-run Preflight Reason Governance（2026-03-11）

## 1. 验证目标

1. dry-run API 能输出 preflight 诊断字段与 `PREFLIGHT_*` reason code。
2. dry-run CSV 导出包含 preflight 列与 reason breakdown。
3. 前端页面可显示 preflight 诊断。
4. mocked e2e 覆盖 preflight 展示与导出链路。

## 2. 执行命令与结果

### Backend（`ecm-core/`）

```bash
mvn -q -DskipTests compile
```

结果：通过。

```bash
mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest,PreviewQueueServiceTest test
```

结果：通过。

新增/增强断言：

1. `requeue/dry-run` 返回 `results[].preflight*` 字段与 `reasonBreakdown`。
2. 新增管理员用例：preflight declined 时 reason code 为 `PREFLIGHT_MIME_UNSUPPORTED`，且不会调用 `evaluateEnqueue(...)`。
3. `requeue/dry-run/export` CSV 头包含 preflight 列并含 preflight 内容。

### Frontend（`ecm-frontend/`）

```bash
npm run lint -- src/pages/PreviewDiagnosticsPage.tsx src/services/previewDiagnosticsService.ts e2e/admin-preview-diagnostics.mock.spec.ts
```

结果：通过。

```bash
npm run build
```

结果：通过（Compiled successfully）。

```bash
npx serve -s build -l 5511
ECM_UI_URL=http://localhost:5511 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium
```

结果：通过（1 passed）。

## 3. 结论

Phase272 已完成并验证：

1. queue declined requeue dry-run 已具备 preflight 级别的可解释跳过原因。
2. UI + CSV + 审计口径对齐。
3. 回归验证通过，未引入当前阶段可见回归。
