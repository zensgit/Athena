# 功能开发报告：WebDAV 协议支持

> **版本**: 1.0
> **日期**: 2025-12-10
> **状态**: ✅ 原型已就绪 (MVP)

## 1. 概述

为了让 Athena ECM 能够像本地磁盘一样被操作系统（Windows Explorer, macOS Finder）挂载，或者被 Microsoft Office 直接打开编辑（类似 Alfresco AOS），我们引入了 **WebDAV (Web Distributed Authoring and Versioning)** 协议支持。

## 2. 核心功能实现

### 2.1 技术栈
*   **Milton (io.milton)**: Java 生态中事实标准的 WebDAV 服务端库。

### 2.2 架构设计
*   **WebDavConfig**: 配置 Spring Boot Filter，将 `/webdav/*` 请求拦截并转交给 Milton 处理。
*   **WebDavResourceFactory**: Milton 的入口，负责将 URL 路径解析为资源对象。
*   **资源映射**:
    *   `WebDavRootResource` -> 虚拟根目录
    *   `WebDavFolderResource` -> `Folder` 实体
    *   `WebDavFileResource` -> `Document` 实体

### 2.3 支持的操作
*   **浏览**: `PROPFIND` - 列出文件和文件夹结构。
*   **读取**: `GET` - 下载文件内容。
*   **写入**: `PUT` - 上传新版本（待完善）。
*   **创建**: `MKCOL` - 创建文件夹（待完善）。

## 3. 验证方法

### 3.1 macOS Finder
1.  Finder -> 前往 -> 连接服务器 (Cmd+K)。
2.  输入地址: `http://localhost:8080/webdav`
3.  输入用户名/密码 (Basic Auth)。
4.  应能看到 ECM 中的根文件夹列表。

### 3.2 Windows Explorer
1.  此电脑 -> 映射网络驱动器。
2.  输入地址: `http://localhost:8080/webdav`

## 4. 后续计划

*   **完善写操作**: 实现 `replaceContent` (PUT) 逻辑，对接 `VersionService`。
*   **锁定支持**: 实现 WebDAV Locking 协议，防止并发编辑冲突。
*   **性能优化**: 优化大文件夹的 `PROPFIND` 性能。
