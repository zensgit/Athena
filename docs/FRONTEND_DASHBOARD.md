# 开发总结：前端监控仪表盘 (Admin Dashboard)

> 版本: 1.0
> 日期: 2025-12-10

## 1. 概述

为了使系统管理员能够直观地掌握系统运行状态，我们基于后端 Sprint 5 (Analytics) 的 API，实现了一个现代化的监控仪表盘。

## 2. 功能实现

`AdminDashboard.tsx` 页面集成了以下可视化模块：

### 2.1 核心指标卡 (Metrics Cards)
位于页面顶部，展示最关键的实时数据：
*   **Total Documents**: 仓库中的有效文档总数。
*   **Storage Used**: 系统当前占用的存储空间（自动格式化为 GB/MB）。
*   **Active Users**: 近期活跃的用户数量。
*   **Today's Activity**: 今日发生的系统事件总数。

### 2.2 存储分布 (Storage Distribution)
*   以列表+进度条的形式，展示了不同文件类型（PDF, Image, Word 等）的数量和占用空间。
*   帮助管理员识别主要的空间占用者。

### 2.3 用户排行 (Top Users)
*   展示系统中最活跃的用户列表及其操作次数，便于识别关键用户或潜在的异常行为。

### 2.4 实时审计流 (Live Audit Log)
*   展示最近 10 条系统关键操作（上传、删除、审批等）。
*   包含操作人、时间、事件类型和详细描述，是安全审计的第一道窗口。

## 3. 技术细节

*   **数据源**:
    *   `/api/v1/analytics/dashboard`: 获取聚合统计数据。
    *   `/api/v1/analytics/audit/recent`: 获取最新日志流。
*   **UI 组件**: 使用 Material UI (`Grid`, `Card`, `LinearProgress`) 构建响应式布局。
*   **交互**: 提供手动 "Refresh" 按钮以获取最新数据。

## 4. 价值

这个仪表盘将后端深埋在数据库中的日志数据转化为了**可操作的洞察 (Actionable Insights)**，标志着 Athena ECM 不仅是一个存储工具，更是一个可管理、可监控的企业级平台。
