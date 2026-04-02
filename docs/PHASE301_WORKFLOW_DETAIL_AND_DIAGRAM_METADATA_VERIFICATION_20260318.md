# Phase301 Workflow Detail and Diagram Metadata（验证记录）

## 1. 验证命令
```bash
cd ecm-core
mvn -q -Dtest=WorkflowControllerTest test
```

```bash
cd ecm-core
mvn -q -DskipTests compile
```

```bash
cd ecm-frontend
npx eslint src/services/workflowService.ts src/pages/TasksPage.tsx
```

```bash
cd ecm-frontend
npm run -s build
```

## 2. 结果
- `mvn -q -Dtest=WorkflowControllerTest test` 通过
- `mvn -q -DskipTests compile` 通过
- `npx eslint src/services/workflowService.ts src/pages/TasksPage.tsx` 通过
- `npm run -s build` 通过

## 3. 覆盖点
- `Task detail` 接口可返回当前任务、流程定义、流程变量、business key
- `Definition detail` 接口可返回 resource names、diagram/BPMN 元信息
- `Definition model` 接口可返回 BPMN XML
- `TasksPage` 可看到 selected task 的 detail / definition / history / XML 预览
