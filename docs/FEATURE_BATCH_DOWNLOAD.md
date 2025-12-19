# 功能开发报告：批量下载 (Batch Download)

> **版本**: 1.0
> **日期**: 2025-12-10
> **状态**: ✅ 已完成

## 1. 概述

批量下载是企业内容管理系统中的基础但高频需求。本功能允许用户选择多个文件或文件夹，将它们打包成一个 ZIP 归档文件进行下载，解决了逐个下载效率低下的问题。

## 2. 核心功能实现

### 2.1 服务层 (`BatchDownloadService`)

*   **流式处理**: 采用 `StreamingResponseBody` 和 `ZipOutputStream`，边读取边压缩边传输。这意味着服务器不需要在内存中构建整个 ZIP 文件，也不需要产生巨大的临时文件，极大地降低了内存消耗和磁盘 I/O 压力，支持 GB 级大文件的打包下载。
*   **递归打包**: 支持文件夹选择。如果选择了文件夹，服务会递归遍历其所有子节点，并在 ZIP 中重建相同的目录结构。
*   **权限校验**: 在打包过程中，系统会逐个检查用户对文件的读取权限 (`READ`)。无权访问的文件会被自动跳过，确保数据安全。
*   **命名冲突处理**: 自动检测 ZIP 内的路径冲突（例如不同文件夹下有同名文件被选中），并通过追加序号 `(1)` 来解决冲突。

### 2.2 API 接口

| 方法 | 路径 | 参数 | 描述 |
|------|------|------|------|
| GET | `/api/v1/nodes/download/batch` | `ids` (UUID列表), `name` (可选文件名) | 下载 ZIP 文件流 |

### 2.3 前端集成（File Browser）

- 列表多选后，在顶部工具栏提供 **Download selected**（批量下载）按钮。
- 前端调用 `GET /api/v1/nodes/download/batch?ids=...&name=...`，并以 `name.zip` 的文件名下载。
- 代码：
  - `ecm-frontend/src/pages/FileBrowser.tsx`
  - `ecm-frontend/src/services/nodeService.ts`

## 3. 验证方法

```bash
# 假设有两个文档 ID: uuid1, uuid2
# 下载为 my-files_20251210.zip
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/nodes/download/batch?ids=uuid1,uuid2&name=my-files" \
  --output my-files.zip
```

## 4. 价值

填补了 `ECM_FEATURE_COMPARISON.md` 中 **P1 级 (批量下载)** 的空白。结合之前的批量操作（移动/复制/删除），Athena ECM 现在拥有了完整的批量处理能力。
