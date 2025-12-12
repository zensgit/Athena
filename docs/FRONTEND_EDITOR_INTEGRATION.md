# 前端开发：在线编辑器集成

> 版本: 1.0
> 日期: 2025-12-10

## 1. 新增页面

### EditorPage (`/editor/:documentId`)
这是一个全屏页面，专门用于承载在线编辑器的 Iframe。

*   **路由参数**:
    *   `documentId`: 要编辑的文档 ID。
    *   `provider` (Query Param): 编辑器提供商，默认为 `wps`。可选值: `wps`, `wopi`。
    *   `permission` (Query Param): 权限模式，`read` (默认) 或 `write`。

*   **逻辑流程**:
    1.  组件加载时，根据 `documentId` 和 `provider` 调用后端 API。
    2.  后端返回带有签名和 Token 的编辑器 URL (例如 WPS Web Office 的链接)。
    3.  页面渲染全屏 Iframe 加载该 URL。

## 2. 集成点

### 调用后端
*   `GET /api/v1/integration/wps/url/{id}?permission={p}`

### 下一步建议
*   在 `FileBrowser` 或 `DocumentPreview` 组件中添加 "编辑" 按钮。
*   按钮点击后跳转至 `/editor/{id}?provider=wps&permission=write`。
