# 功能开发报告：商业授权许可 (Licensing)

> **版本**: 1.0
> **日期**: 2025-12-10
> **状态**: ✅ 已完成 (基础框架)

## 1. 概述

为了支持 Athena ECM 的商业化运作（如提供企业版 Enterprise Edition），我们需要一个基础的授权许可管理模块。该模块负责验证系统的使用权限，并根据 License 限制系统资源的使用。

## 2. 核心功能实现

### 2.1 License Service (`LicenseService`)

*   **启动检查**: 系统启动时自动读取配置文件中的 `ecm.license.key`。
*   **验证逻辑**:
    *   如果没有 Key，自动降级为 **Community Edition**（限制 5 用户，10GB 存储）。
    *   如果有 Key，解析并验证（模拟验证逻辑，生产环境应使用 RSA 签名校验）。
*   **限制执行**:
    *   `checkUserLimit()`: 在创建新用户时调用，超过限制则抛出异常。
    *   `isFeatureEnabled(feature)`: 用于代码中判断是否开启高级功能（如多租户、高级审计）。

### 2.2 API 接口

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/v1/system/license` | 获取当前 License 信息（仅限管理员） |

## 3. 验证方法

1.  **默认状态**: 不配置 License Key，启动系统。调用接口应返回 "Community" 版本，且 `maxUsers` 为 5。
2.  **配置 License**: 在 `application.yml` 或环境变量中设置 `ECM_LICENSE_KEY=valid`。重启系统，调用接口应返回 "Enterprise" 版本，且限制放宽。

## 4. 后续计划

*   **RSA 签名**: 实现完整的公私钥签发和验证机制，防止 License 被篡改。
*   **前端展示**: 在 Admin Dashboard 中显示 "Powered by Athena Enterprise" 或 "Community Edition" 标识。
