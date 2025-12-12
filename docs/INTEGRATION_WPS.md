# 集成设计：在线编辑 (WPS & Alfresco 模式)

> 版本: 1.0
> 日期: 2025-12-10

## 1. 设计理念

参考 **Alfresco** 的集成模式，Athena ECM 采用**存储与编辑分离**的架构。ECM 负责文档的存储、版本控制、权限管理和锁定；外部编辑器（如 WPS Web Office, Microsoft Office Online）负责文档内容的渲染和修改。

### 核心流程 (WPS Web Office)

1.  **前端请求**: 用户点击 "使用 WPS 编辑"。
2.  **生成 URL**: 后端生成带签名的 WPS 加载 URL (含 `access_token`)。
3.  **加载编辑器**: 前端 Iframe 加载该 URL。
4.  **回调 (Callback)**: WPS 云服务请求 Athena 后端接口：
    *   `GET .../file/info`: 获取文件元数据和下载地址。
    *   `GET .../file/download`: 下载文件流。
    *   `POST .../file/save`: 用户保存时，WPS 将新文件流回传给 Athena。
5.  **版本控制**: Athena 接收到回传流，自动创建新版本 (Minor Version)。

## 2. API 设计

### 2.1 客户端接口 (Client API)

供前端 React 应用调用。

*   `GET /api/v1/integration/wps/url/{documentId}`
    *   参数: `permission` (read/write)
    *   返回: `{ wpsUrl: "...", token: "...", expiresAt: ... }`

### 2.2 回调接口 (Callback API)

供 WPS 服务端调用 (需配置在 WPS 开放平台)。

*   `GET /api/v1/integration/wps/v1/3rd/file/info`
    *   获取文件信息、用户权限、水印设置。
*   `POST /api/v1/integration/wps/v1/3rd/file/save`
    *   接收文件流，创建新版本。

## 3. 安全性

*   **Token 验证**: 生成的 URL 包含一次性或短期 Token，回调接口需验证此 Token。
*   **权限映射**: Athena 的 READ/WRITE 权限映射到 WPS 的 `user_acl` (history, copy, print, export)。
*   **签名验证**: 生产环境应校验 WPS 请求头中的签名 (`x-wps-signature`)，防止伪造请求。

## 4. 后续扩展

*   **WebDAV 支持**: 未来可引入 Milton 库支持 WebDAV 协议，允许本地 Office 直接打开编辑 (Alfresco AOS 模式)。
*   **WOPI 支持**: 实现标准 WOPI 协议接口，以兼容 Microsoft Office Online 和 Collabora Online。
