# Phase302 Alfresco Async Batch Download and Collaboration Surfaces（验证记录）

## 1. 验证命令
```bash
cd ecm-core
mvn -q -Dtest=BatchDownloadControllerTest,WorkflowControllerTest test
```

```bash
cd ecm-core
mvn -q -DskipTests compile
```

```bash
cd ecm-frontend
npx eslint src/pages/FileBrowser.tsx src/services/nodeService.ts src/components/preview/DocumentPreview.tsx src/components/dialogs/StartWorkflowDialog.tsx src/components/comments/CommentSection.tsx src/services/peopleService.ts src/services/workflowService.ts src/pages/TasksPage.tsx
```

```bash
cd ecm-frontend
npm run -s build
```

## 2. 结果
- `mvn -q -Dtest=BatchDownloadControllerTest,WorkflowControllerTest test` 通过
- `mvn -q -DskipTests compile` 通过
- `npx eslint ...` 通过
- `npm run -s build` 通过

## 3. 覆盖点
- async batch download 可完成：
  - start
  - status poll
  - list
  - cancel
  - completed artifact download
- `FileBrowser` 可看到批量下载任务面板、轮询进度、取消任务、下载成品 ZIP
- `DocumentPreview` 可展开 comments 面板并加载 `CommentSection`
- `StartWorkflowDialog` 会通过 people directory 加载真实 approver 列表
- `TasksPage` 仍保持上一阶段 workflow detail / definition metadata / XML / history 的可视化查看
