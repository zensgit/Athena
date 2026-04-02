# Phase303 Alfresco Workflow Process Relations and Diagram Binary（验证记录）

## 1. 验证命令
```bash
cd ecm-core
mvn -q -Dtest=WorkflowControllerTest test
```

```bash
cd ecm-frontend
npx eslint src/services/workflowService.ts src/pages/TasksPage.tsx
```

## 2. 结果
- `mvn -q -Dtest=WorkflowControllerTest test` 通过
- `npx eslint src/services/workflowService.ts src/pages/TasksPage.tsx` 通过

## 3. 覆盖点
- `definitions/{id}/diagram` 可返回二进制图像资源
- `processes/{id}` 可返回 process summary、lifecycle 和 variables
- `processes/{id}/tasks` 可返回当前流程下的任务列表
- `TasksPage` 可显示 process summary、current tasks、diagram preview、definition metadata、history、BPMN XML
