# 集成设计：WOPI 协议支持

> 版本: 1.0
> 日期: 2025-12-10

## 1. 概述

WOPI (Web Application Open Platform Interface) 是微软推出的标准化协议，用于将 Office Online Server (OOS) 集成到任何内容管理系统中。通过实现 WOPI Host，Athena ECM 可以支持：

*   **Microsoft Office Online** (Word, Excel, PowerPoint)
*   **Collabora Online** (基于 LibreOffice)
*   **OnlyOffice** (支持 WOPI 模式)

这体现了 Alfresco 的架构理念：**不内置编辑器，而是提供强大的标准集成接口**。

> 本地 Docker 开发建议：将 Collabora 通过前端 Nginx 反代到同源（例如 `http://localhost:5500/browser/...`），
> 并在 `.env` 中设置 `ECM_WOPI_PUBLIC_URL=http://localhost:5500`，可避免跨域 iframe 带来的限制与控制台噪声。

### 1.1 快速自检（推荐）

后端提供 WOPI 自检接口（需登录）：

- `GET /api/v1/integration/wopi/health`
  - 返回：WOPI 是否启用、WOPI Host URL、Collabora discovery/capabilities 是否可达、支持的扩展名 sample 等。

Collabora 本身也提供两个公开接口（可直接在浏览器打开查看）：

- `GET <publicUrl>/hosting/capabilities`（返回 productName/productVersion 等信息）
- `GET <publicUrl>/hosting/discovery`（WOPI discovery XML）

> 如果 `publicUrl` 配置为前端同源代理（例如 `http://localhost:5500`），那么上面的 capabilities/discovery 也会通过前端 Nginx 反代到 Collabora 容器。

### 1.2 关于 “Development Edition” 提示/弹窗（并发/限制）

本项目默认使用 `collabora/code`（Collabora Online Development Edition, CODE），它是面向开发/评估的镜像：

- 页面上出现 “Collabora Online Development Edition” 字样/提示属于编辑器自身行为（非 Athena 前端渲染）。
- 并发/使用限制由 Collabora CODE 控制，Athena 无法在不改动编辑器的前提下完全屏蔽。
- 需要 “无提示/白标/生产级支持” 时，请使用商业版 Collabora Online 或替换为 OnlyOffice（WOPI 模式）等方案。

> Athena 已在前端 Nginx 中做了 **同源反代** + **cool.html 注入 overrides（`collabora-overrides.*`）**，可减少部分噪声/链接，但不保证能去除所有品牌与限制提示（取决于 Collabora 版本与页面结构）。

### 1.3 配置项（Docker / `.env`）

后端（Spring）主要配置项与默认值：

- `ECM_WOPI_ENABLED`（默认：`true`）
- `ECM_WOPI_PUBLIC_URL`（默认：`http://localhost:${ECM_FRONTEND_PORT}`，推荐同源 `http://localhost:5500`）
- `ECM_WOPI_DISCOVERY_URL`（默认：`http://collabora:9980/hosting/discovery`，容器内访问）
- `ECM_WOPI_CAPABILITIES_URL`（默认：`http://collabora:9980/hosting/capabilities`，容器内访问）
- `ECM_WOPI_HOST_URL`（默认：`http://ecm-core:8080`，必须被 Collabora 容器访问到）

## 2. API 接口 (Standard WOPI)

所有接口均位于 `/wopi/files/{id}` 路径下。

### 2.1 CheckFileInfo (`GET /wopi/files/{id}`)
返回文件的元数据和用户权限。

*   **关键字段**:
    *   `BaseFileName`: 文件名
    *   `OwnerId`: 所有者
    *   `UserCanWrite`: 是否有写权限 (映射自 Athena WRITE 权限)
    *   `Version`: 当前版本号
    *   `SupportsLocks`: 是否支持锁定 (必须支持)

### 2.2 GetFile (`GET /wopi/files/{id}/contents`)
获取文件流。WOPI Client (如 Office Online) 会调用此接口加载文件内容。

### 2.3 PutFile (`POST /wopi/files/{id}/contents`)
保存文件。
*   接收二进制流。
*   Athena 接收到流后，自动触发 **VersionService** 创建新版本。
*   支持 `X-WOPI-Lock` 头进行并发控制。

## 3. 发现机制 (Discovery)

为了让前端知道如何打开文件，通常需要实现 WOPI Discovery 处理：
1.  解析 Office Online Server 提供的 `discovery.xml`。
2.  根据文件扩展名 (docx, xlsx) 匹配对应的 `urlsrc`。
3.  生成最终的 iframe URL: `https://<office-server>/.../word.aspx?WOPISrc=...&access_token=...`

## 4. 安全性

*   **Access Token**: WOPI URL 必须包含 `access_token`。Athena 需验证此 Token 的有效性和对应的文件/用户权限。
*   **Proof Keys**: (高级) 验证请求确实来自合法的 WOPI Client。

## 5. 对比 WPS 集成

| 特性 | WPS 集成 (Sprint 6) | WOPI 集成 (本次新增) |
|------|-------------------|-------------------|
| **协议** | 私有回调协议 | 国际标准协议 |
| **支持编辑器** | WPS Web Office | MS Office, Collabora, OnlyOffice |
| **复杂度** | 低 | 中 (涉及 XML Discovery) |
| **适用场景** | 国内企业, WPS 生态 | 跨国企业, 通用 Office 生态 |

通过同时支持这两套接口，Athena ECM 具备了极强的适应性。
