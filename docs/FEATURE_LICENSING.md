# 功能开发报告：商业授权许可 (Licensing)

> **版本**: 1.1
> **日期**: 2025-12-10（2026-06-23 状态纠偏）
> **状态**: ⚠️ **Placeholder / 占位实现** —— 校验逻辑为 mock（硬编码），限制执行（`checkUserLimit` / `isFeatureEnabled`）**未接入、为死代码（NOT enforced yet）**。read-only 复核见 `docs/GAP_AUDIT_LICENSE_ENTITLEMENT_ADMIN_20260623.md`。

## 1. 概述

为支持 Athena ECM 的商业化（如企业版 Enterprise Edition），原计划提供一个授权许可模块，验证使用权限并按 License 限制资源使用。**当前实现仅为占位**：校验是 mock，限制执行尚未接入任何代码路径，`/api/v1/system/license` 仅供 Admin Dashboard 只读展示。

## 2. 现状（2026-06-23 实查）

### 2.1 License Service (`LicenseService`)

*   **启动检查**: 系统启动时读取 `ecm.license.key`。
*   **验证逻辑（⚠️ mock，非真验签）**:
    *   没有 Key → 降级为 **Community Edition**（5 用户 / 10GB / 永不过期 / `[BASIC]`）。
    *   有 Key → **当前并不真正解析或验签**：任何非空且非 `"invalid"` 的 Key 一律硬编码为 **Enterprise**（100 用户 / 1000GB / +1 年 / `[WORKFLOW,OCR,AUDIT]`）；`"invalid"` 或异常一律回退 Community。代码导入了 JWT/RSA 却未使用。`valid` 对外**恒为 `true`**。
*   **限制执行（⚠️ 当前未接入 / NOT ENFORCED YET —— 死代码）**:
    *   `checkUserLimit()`: 已定义，但**全仓无生产调用者**——用户上限当前并不执行。
    *   `isFeatureEnabled(feature)`: 已定义，但**全仓无生产调用者**——没有任何功能受 License 开关控制；feature 名仅为 `LicenseService` 内的字符串字面量，无枚举 / 注册表。
    *   （`rg` 复核 2026-06-23：两方法仅在 `LicenseService.java:89 / :104` 定义，无 caller。）

### 2.2 API 接口

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/v1/system/license` | 获取当前 License 信息（仅限管理员，只读展示） |

## 3. 验证方法（当前行为）

1.  **默认状态**: 不配置 License Key → 接口返回 "Community"、`maxUsers=5`。
2.  **配置 License**: 设置任意非空且非 `invalid` 的 `ECM_LICENSE_KEY`（如 `valid`）→ 接口返回硬编码的 "Enterprise" 信息。**注意：这是 mock 数据，限制并未真正执行**（见 §2.1）。

## 4. 后续计划（若 licensing 成为真需求）

*   **RSA 签名**: 实现真正的公私钥签发与验签，防止 License 被篡改。
*   **限制执行接入**: 将 `checkUserLimit` / `isFeatureEnabled` 接进 create-user / feature 路径（当前为死代码、无 caller）。
*   ✅ **前端展示已完成**: Admin Dashboard 已有 License 区块（展示 Edition / 有效性 / 限制 / 到期 / Features）——注意它展示的是上述 **mock 数据**。
