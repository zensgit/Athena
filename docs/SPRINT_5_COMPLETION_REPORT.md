# Sprint 5 完成报告：分析与监控 (Analytics & Monitoring)

> **版本**: 1.0
> **日期**: 2025-12-10
> **状态**: ✅ 已完成

## 1. 概述

本次迭代（Sprint 5）实现了系统的**分析与监控**能力。通过统一的审计日志（Audit Log）和分析服务，管理员现在可以实时掌握系统的存储使用情况、用户活跃度以及安全审计记录。

## 2. 核心功能实现

### 2.1 统一审计日志 (Unified Audit Log)

将原有的基于 JDBC 的简单日志升级为完整的 JPA 实体模型，支持更丰富的查询和分析。

*   **AuditLog Entity**: 记录事件类型、节点信息、用户、时间、IP、User-Agent 及扩展元数据 (JSONB)。
*   **AuditLogRepository**: 提供了基于时间范围、用户、事件类型的查询能力。
*   **AuditService**: 重构为使用 Repository 模式，确保数据一致性。

### 2.2 分析服务 (Analytics Service)

实现了多维度的系统数据聚合。

*   **系统概览 (System Summary)**: 实时统计文档总数、文件夹总数、总存储占用。
*   **存储分析 (Storage Analysis)**: 按 MIME 类型 (PDF, Word, Images) 统计文件数量和大小占用。
*   **活跃度分析 (Activity Trends)**: 提供过去 30 天的每日活动趋势图数据。
*   **用户排行 (Top Users)**: 统计最活跃的用户。

### 2.3 监控仪表盘 API (Dashboard API)

提供了聚合的 REST API，方便前端构建监控大屏。

*   `GET /api/v1/analytics/dashboard`: 一次性获取所有核心指标。
*   `GET /api/v1/analytics/audit/recent`: 获取实时审计流。

## 3. API 接口清单

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/v1/analytics/dashboard` | 获取仪表盘聚合数据 |
| GET | `/api/v1/analytics/summary` | 获取系统概览 |
| GET | `/api/v1/analytics/storage/mimetype` | 获取存储分布 |
| GET | `/api/v1/analytics/activity/daily` | 获取每日活动趋势 |
| GET | `/api/v1/analytics/users/top` | 获取活跃用户排行 |
| GET | `/api/v1/analytics/audit/recent` | 获取最近审计日志 |

## 4. 架构变更

*   **NodeRepository**: 增加了原生 SQL 查询以支持高效的聚合统计 (count by mimeType, sum size)。
*   **Security**: Analytics API 默认仅限 `ROLE_ADMIN` 访问。

## 5. 验证方法

验证分析功能：

```bash
# 获取管理员 Token
export ADMIN_TOKEN=...

# 1. 查看系统概览
curl -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/v1/analytics/summary

# 2. 查看存储分布
curl -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/v1/analytics/storage/mimetype

# 3. 查看最近活动
curl -H "Authorization: Bearer $ADMIN_TOKEN" http://localhost:8080/api/v1/analytics/audit/recent
```

## 6. 后续计划

*   **前端实现**: 基于现有 API 开发 Admin Dashboard 页面 (Chart.js / Recharts)。
*   **高级报表**: 导出 CSV/Excel 格式的审计报告。
*   **集成**: 下一阶段 (Sprint 6) 将重点关注 Office 365 和 Odoo 的深度集成。
