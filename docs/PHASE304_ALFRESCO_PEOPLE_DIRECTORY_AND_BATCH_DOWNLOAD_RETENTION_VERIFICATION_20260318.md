# Phase304 Alfresco People Directory and Batch Download Retention（验证记录）

## 1. 验证命令
```bash
cd ecm-core
mvn -q -Dtest=BatchDownloadAsyncTaskRegistryTest,BatchDownloadControllerTest test
```

```bash
cd ecm-core
mvn -q -DskipTests compile
```

```bash
cd ecm-frontend
npx eslint src/pages/PeopleDirectoryPage.tsx src/App.tsx src/components/layout/MainLayout.tsx src/components/layout/MainLayout.menu.test.tsx
```

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/components/layout/MainLayout.menu.test.tsx --watchAll=false
```

```bash
cd ecm-frontend
npm run -s build
```

## 2. 结果
- `mvn -q -Dtest=BatchDownloadAsyncTaskRegistryTest,BatchDownloadControllerTest test` 通过
- `mvn -q -DskipTests compile` 通过
- `npx eslint ...` 通过
- `CI=true npm test -- --runTestsByPath src/components/layout/MainLayout.menu.test.tsx --watchAll=false` 通过
- `npm run -s build` 通过

## 3. 覆盖点
- batch download terminal task 会被 retention cleanup 正常清理并删除临时 ZIP
- active batch download task 不会被 cleanup 误删
- People Directory 路由可访问，账号菜单入口存在
- People Directory 页面可完成 search/profile/groups/favorites 工作流
