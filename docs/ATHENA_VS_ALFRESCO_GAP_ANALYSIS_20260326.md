# Athena vs Alfresco Community Repository：功能差距分析与开发清单

> 基于对 Athena ECM 全量代码和 Alfresco Community Repository (`reference-projects/alfresco-community-repo`) 的深度对比分析。
>
> 日期：2026-03-26

---

## 概览

| 维度 | Alfresco | Athena | 覆盖率 |
|------|---------|--------|--------|
| 核心服务接口 | 164+ | ~35 | ~21% |
| REST 端点 | 100+ | ~150 | 超越（含大量诊断/运维端点） |
| 核心业务功能 | 52 大类 | ~30 大类 | ~58% |
| 企业级功能 | 全覆盖 | 部分覆盖 | ~40% |

---

## Alfresco 核心服务全景（Athena 对标基线）

| # | Alfresco 服务 | 方法数 | 描述 |
|---|-------------|-------|------|
| 1 | NodeService | 40+ | 节点 CRUD、层级管理、属性、切面、关联 |
| 2 | ContentService | 12+ | 内容读写、MIME 检测、预签名 URL、归档/恢复 |
| 3 | VersionService | 15+ | 版本创建、历史、回退、删除、自定义标签策略 |
| 4 | PermissionService | 15+ | ACL 设置/查询/继承、细粒度权限（35种） |
| 5 | SearchService | 10+ | Lucene/Solr/FTS/CMIS/XPath 多语言查询 |
| 6 | LockService | 15+ | 写锁/只读锁、超时、持久/临时、批量、挂起 |
| 7 | CheckOutCheckInService | 10+ | 工作副本、签出到指定位置、签入保持签出 |
| 8 | ActionService | 20+ | 动作定义/执行/条件/组合/持久化 |
| 9 | RuleService | 12+ | 文件夹规则、规则类型、继承、启停控制 |
| 10 | AuditService | 12+ | 审计应用、路径级启停、时间/ID 范围清理 |
| 11 | TaggingService | 10+ | 标签 CRUD、使用计数、分页、模式过滤 |
| 12 | RatingService | 8+ | 多评分方案（点赞/五星）、用户评分、汇总 |
| 13 | CategoryService | 5+ | 分类层级、成员/子类模式、深度控制 |
| 14 | CopyService | 4+ | 深拷贝/浅拷贝、跨仓库、重命名冲突 |
| 15 | FileFolderService | 10+ | 高级列表（分页/排序/过滤/模式匹配） |
| 16 | AuthenticationService | 8+ | 认证链、票据管理、访客登录 |
| 17 | AuthorityService | 20+ | 权限域、组层级、管理员/访客判断 |
| 18 | PersonService | 10+ | 人员 CRUD、头像、配额 |
| 19 | SiteService | 20+ | 协作空间、成员角色、容器、可见性 |
| 20 | ActivityService | 8+ | 活动发布、Feed 过滤、聚合、订阅控制 |
| 21 | SubscriptionService | 6+ | 关注用户、订阅节点、隐私设置 |
| 22 | PreferenceService | 4+ | 命名空间偏好、CRUD、模式查询 |
| 23 | AttributeService | 6+ | 全局键值存储、复合键、回调查询 |
| 24 | InvitationService | 6+ | 指名邀请、审批加入、工作流集成 |
| 25 | RenditionService2 | 8+ | 异步渲染、模板路径、管线转换、失败链 |
| 26 | ThumbnailService | 6+ | 缩略图生成、注册、更新 |
| 27 | QuickShareService | 5+ | 共享链接、过期、邮件发送 |
| 28 | DownloadService | 4+ | 异步 ZIP、进度追踪、清理 |
| 29 | ImporterService | 5+ | ACP 导入、参数绑定、进度追踪 |
| 30 | ExporterService | 5+ | ACP 导出、全量/增量 |
| 31 | ReplicationService | 6+ | 定义/调度/过滤/推送 |
| 32 | TransferService2 | 8+ | 同步/异步传输、回调、报告 |
| 33 | TenantAdminService | 10+ | 租户创建/启用/禁用、按租户部署 |
| 34 | DictionaryService | 15+ | 类型/切面/属性定义查询 |
| 35 | PolicyComponent | 10+ | 类/属性/关联策略注册与调用 |
| 36 | ScheduledActionService | 4+ | Quartz 调度、持久化任务 |
| 37 | UsageService | 4+ | 用量追踪、配额检查 |
| 38 | TemplateService | 5+ | FreeMarker 模板处理 |
| 39 | ScriptService | 4+ | Rhino JavaScript 执行 |
| 40 | DiscussionService | 6+ | 讨论帖/回复/分页 |
| 41 | BlogService | 6+ | 博客发布/草稿/分页 |
| 42 | CalendarService | 6+ | 日程创建/查询/更新 |
| 43 | LinksService | 4+ | 链接收藏/分页 |
| 44 | EmailService | 3+ | MIME 邮件发送 |
| 45 | FormService | 5+ | 动态表单生成/处理 |
| 46 | ModuleService | 3+ | 模块安装/查询 |
| 47 | DescriptorService | 3+ | 仓库信息/版本/许可 |
| 48 | BulkImportService | 5+ | 流式导入/进度/错误恢复 |
| 49 | WebDavService | 3+ | WebDAV 协议暴露 |
| 50 | CMIS Service | 20+ | CMIS 1.0/1.1 完整合规 |
| 51 | IMAP Service | 5+ | IMAP 服务端暴露仓库 |
| 52 | RemoteConnectorService | 4+ | HTTP 远程调用/凭据管理 |

---

## Alfresco REST API 端点全景

### Nodes API (`/nodes`)
| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/nodes/{nodeId}` | 获取节点信息 |
| GET | `/nodes/{nodeId}/children` | 列出子节点 |
| POST | `/nodes/{nodeId}/children` | 创建子节点 |
| DELETE | `/nodes/{nodeId}` | 删除节点 |
| PUT | `/nodes/{nodeId}` | 更新节点元数据 |
| GET | `/nodes/{nodeId}/content` | 下载文件内容 |
| PUT | `/nodes/{nodeId}/content` | 上传/更新文件内容 |
| POST | `/nodes/{nodeId}/copy` | 复制节点 |
| POST | `/nodes/{nodeId}/move` | 移动节点 |
| POST | `/nodes/{nodeId}/lock` | 锁定节点 |
| DELETE | `/nodes/{nodeId}/lock` | 解锁节点 |
| POST | `/nodes/{nodeId}/direct-access-url` | 请求预签名 URL |
| GET | `/nodes/{nodeId}/versions` | 版本历史 |
| GET | `/nodes/{nodeId}/versions/{versionId}` | 获取特定版本 |
| PUT | `/nodes/{nodeId}/versions/{versionId}/revert` | 回退到版本 |
| GET | `/nodes/{nodeId}/versions/{versionId}/content` | 下载版本内容 |
| GET | `/nodes/{nodeId}/renditions` | 列出渲染 |
| POST | `/nodes/{nodeId}/renditions` | 请求创建渲染 |
| GET | `/nodes/{nodeId}/renditions/{renditionId}` | 获取渲染信息 |
| GET | `/nodes/{nodeId}/renditions/{renditionId}/content` | 下载渲染 |
| DELETE | `/nodes/{nodeId}/renditions/{renditionId}` | 删除渲染 |
| GET | `/nodes/{nodeId}/comments` | 列出评论 |
| POST | `/nodes/{nodeId}/comments` | 创建评论 |
| PUT | `/nodes/{nodeId}/comments/{commentId}` | 更新评论 |
| DELETE | `/nodes/{nodeId}/comments/{commentId}` | 删除评论 |
| GET | `/nodes/{nodeId}/ratings` | 获取评分 |
| POST | `/nodes/{nodeId}/ratings` | 添加评分 |
| DELETE | `/nodes/{nodeId}/ratings/{ratingSchemeId}` | 删除评分 |
| GET | `/nodes/{nodeId}/tags` | 获取标签 |
| POST | `/nodes/{nodeId}/tags` | 添加标签 |
| DELETE | `/nodes/{nodeId}/tags/{tagId}` | 删除标签 |
| GET | `/nodes/{nodeId}/targets` | 目标关联 |
| POST | `/nodes/{nodeId}/targets` | 添加目标关联 |
| GET | `/nodes/{nodeId}/sources` | 源关联 |
| POST | `/nodes/{nodeId}/secondary-children` | 添加二级子节点 |
| GET | `/nodes/{nodeId}/rules` | 获取文件夹规则 |
| POST | `/nodes/{nodeId}/rules` | 创建规则 |
| PUT | `/nodes/{nodeId}/rules/{ruleId}` | 更新规则 |
| DELETE | `/nodes/{nodeId}/rules/{ruleId}` | 删除规则 |
| POST | `/nodes/{nodeId}/rule-executions` | 执行规则 |
| GET | `/nodes/{nodeId}/audit-entries` | 节点审计条目 |
| GET | `/nodes/{nodeId}/category-links` | 节点分类链接 |
| POST | `/nodes/{nodeId}/category-links` | 添加分类链接 |
| DELETE | `/nodes/{nodeId}/category-links/{categoryId}` | 删除分类链接 |

### People API (`/people`)
| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/people` | 列出所有人员 |
| POST | `/people` | 创建人员 |
| GET | `/people/{personId}` | 获取人员详情 |
| PUT | `/people/{personId}` | 更新人员 |
| GET | `/people/{personId}/avatar` | 下载头像 |
| POST | `/people/{personId}/avatar` | 上传头像 |
| DELETE | `/people/{personId}/avatar` | 删除头像 |
| GET | `/people/{personId}/preferences` | 获取偏好 |
| PUT | `/people/{personId}/preferences/{name}` | 更新偏好 |
| GET | `/people/{personId}/activities` | 获取活动流 |
| GET | `/people/{personId}/favorites` | 获取收藏 |
| POST | `/people/{personId}/favorites` | 添加收藏 |
| DELETE | `/people/{personId}/favorites/{favoriteId}` | 删除收藏 |
| GET | `/people/{personId}/sites` | 获取站点 |
| GET | `/people/{personId}/groups` | 获取所属组 |
| GET | `/people/{personId}/site-membership-requests` | 成员请求 |
| POST | `/people/{personId}/site-membership-requests` | 创建请求 |
| DELETE | `/people/{personId}/site-membership-requests/{siteId}` | 取消请求 |

### Groups API (`/groups`)
| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/groups` | 列出所有组 |
| POST | `/groups` | 创建组 |
| GET | `/groups/{groupId}` | 获取组详情 |
| PUT | `/groups/{groupId}` | 更新组 |
| DELETE | `/groups/{groupId}` | 删除组 |
| GET | `/groups/{groupId}/members` | 列出成员 |
| POST | `/groups/{groupId}/members` | 添加成员 |
| DELETE | `/groups/{groupId}/members/{memberId}` | 移除成员 |

### Sites API (`/sites`)
| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/sites` | 列出所有站点 |
| POST | `/sites` | 创建站点 |
| GET | `/sites/{siteId}` | 获取站点详情 |
| PUT | `/sites/{siteId}` | 更新站点 |
| DELETE | `/sites/{siteId}` | 删除站点 |
| GET | `/sites/{siteId}/members` | 列出成员 |
| POST | `/sites/{siteId}/members` | 添加成员 |
| PUT | `/sites/{siteId}/members/{personId}` | 更新成员角色 |
| DELETE | `/sites/{siteId}/members/{personId}` | 移除成员 |
| GET | `/sites/{siteId}/containers` | 列出容器 |
| GET | `/sites/{siteId}/groups` | 获取站点组 |
| POST | `/sites/{siteId}/site-membership-requests/{personId}/approve` | 批准加入 |
| POST | `/sites/{siteId}/site-membership-requests/{personId}/reject` | 拒绝加入 |

### 其他 API
| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/tags` | 列出所有标签 |
| POST | `/tags` | 创建标签 |
| PUT | `/tags/{tagId}` | 更新标签 |
| DELETE | `/tags/{tagId}` | 删除标签 |
| GET | `/categories/{categoryId}` | 获取分类 |
| POST | `/categories/{parentId}` | 创建子分类 |
| GET | `/categories/{categoryId}/children` | 列出子分类 |
| PUT | `/categories/{categoryId}` | 更新分类 |
| DELETE | `/categories/{categoryId}` | 删除分类 |
| GET | `/shared-links` | 列出共享链接 |
| POST | `/shared-links` | 创建共享链接 |
| GET | `/shared-links/{sharedId}` | 获取共享详情 |
| GET | `/shared-links/{sharedId}/content` | 通过共享下载 |
| DELETE | `/shared-links/{sharedId}` | 删除共享 |
| POST | `/shared-links/{sharedId}/email` | 邮件发送共享 |
| POST | `/downloads` | 创建批量下载 |
| GET | `/downloads/{downloadNodeId}` | 获取下载状态 |
| DELETE | `/downloads/{downloadNodeId}` | 取消下载 |
| GET | `/deleted-nodes` | 列出已删除节点 |
| POST | `/deleted-nodes/{archivedId}/restore` | 恢复节点 |
| DELETE | `/deleted-nodes/{archivedId}` | 永久删除 |
| GET | `/queries/nodes` | 搜索节点 |
| GET | `/queries/people` | 搜索人员 |
| GET | `/queries/sites` | 搜索站点 |
| GET | `/audit-applications` | 列出审计应用 |
| GET | `/audit-applications/{appId}/audit-entries` | 审计条目 |
| GET | `/action-definitions` | 列出动作定义 |
| POST | `/actions` | 执行动作 |
| POST | `/authentications` | 获取认证票据 |
| GET | `/authentications/me` | 验证当前票据 |
| DELETE | `/authentications/me` | 登出 |
| GET | `/types` | 列出类型 |
| GET | `/aspects` | 列出切面 |
| GET | `/cmm` | 列出自定义模型 |
| POST | `/cmm` | 创建自定义模型 |
| GET | `/probes/readiness` | 就绪探针 |
| GET | `/probes/liveness` | 存活探针 |

---

## 功能逐项对比矩阵

### 核心文档管理

| 功能 | Alfresco | Athena | 差距 |
|------|:--------:|:------:|------|
| 节点 CRUD (文档/文件夹) | ✅ | ✅ | — |
| 节点移动/复制 | ✅ | ✅ | — |
| 内容上传/下载 | ✅ | ✅ | — |
| 内容 SHA 去重 | ✅ | ✅ | — |
| MIME 类型检测 | ✅ | ✅ | — |
| 文件元数据提取 (Tika) | ✅ | ✅ | — |
| 路径导航 | ✅ | ✅ | — |
| 回收站 (软删除/恢复) | ✅ | ✅ | — |
| 文档锁定 — 锁类型/超时/持久/批量 | ✅ 完整 | ⚠️ 基础 | **需增强** |
| Check-Out / Check-In 工作副本 | ✅ 完整 | ⚠️ 基础 | **需增强** |
| 二级子节点 (多父级) | ✅ | ❌ | **缺失** |
| 预签名直接访问 URL | ✅ | ❌ | **缺失** |
| 内容归档到冷存储/恢复 | ✅ | ❌ | **缺失** |

### 版本管理

| 功能 | Alfresco | Athena | 差距 |
|------|:--------:|:------:|------|
| 版本创建 (主版本/次版本) | ✅ | ✅ | — |
| 版本历史查询 | ✅ | ✅ | — |
| 回退到历史版本 | ✅ | ✅ | — |
| 版本内容下载 | ✅ | ✅ | — |
| 版本比较 (文本 Diff) | ❌ | ✅ | **Athena 超越** |
| 删除特定版本 | ✅ | ✅ | — |
| 分页版本历史 | ✅ | ✅ | — |
| 自定义版本标签策略 | ✅ | ❌ | 缺失 (低优先级) |
| 版本子节点联动 | ✅ | ❌ | 缺失 (低优先级) |

### 内容模型与数据字典

| 功能 | Alfresco | Athena | 差距 |
|------|:--------:|:------:|------|
| 类型定义系统 (Type Definition) | ✅ | ❌ | **缺失** |
| 切面系统 (Aspect) — 动态附加/移除 | ✅ | ❌ | **缺失** |
| 属性定义与约束 (Constraint) | ✅ | ❌ | **缺失** |
| 强制属性 (Mandatory Properties) | ✅ | ❌ | **缺失** |
| 多语言文本属性 (MLText) | ✅ | ❌ | **缺失** |
| 运行时自定义模型 API (CMM) | ✅ | ❌ | **缺失** |
| 16 种属性数据类型 | ✅ | ⚠️ JSONB 灵活 | 无强类型校验 |
| 灵活 JSONB 属性 (无需预定义) | ❌ | ✅ | **Athena 优势** |

### 权限与安全

| 功能 | Alfresco | Athena | 差距 |
|------|:--------:|:------:|------|
| ACL 权限设置/查询 | ✅ | ✅ | — |
| 权限继承开关 | ✅ | ✅ | — |
| 角色 (USER/GROUP/ROLE/EVERYONE) | ✅ | ✅ | — |
| 权限过期自动清理 | ❌ | ✅ | **Athena 超越** |
| 权限决策诊断 (explainPermission) | ❌ | ✅ | **Athena 超越** |
| 权限集模板 | ✅ | ✅ | — |
| 细粒度权限 (35 种: READ_CONTENT, WRITE_PROPERTIES 等) | ✅ | ⚠️ 6 种 | **需增强** |
| 所有权转让 (takeOwnership) | ✅ | ✅ | — |
| 权限域 (Authority Zones) | ✅ | ❌ | 缺失 |

### 搜索

| 功能 | Alfresco | Athena | 差距 |
|------|:--------:|:------:|------|
| 全文搜索 | ✅ Solr | ✅ Elasticsearch | — |
| 分面搜索 (Faceted Search) | ✅ | ✅ | — |
| 高亮显示 | ✅ | ✅ | — |
| 多查询语言 (Lucene/FTS/CMIS/XPath) | ✅ | ❌ 仅 ES DSL | **缺失** |
| 搜索驱动的批量操作 | ❌ | ✅ | **Athena 超越** |
| DryRun 分析 / 原因分解 | ❌ | ✅ | **Athena 超越** |
| 搜索统计 / Pivot 统计 | ❌ | ✅ | **Athena 超越** |
| 异步 CSV 导出 | ❌ | ✅ | **Athena 超越** |
| 保存搜索 | ✅ | ✅ | — |

### 预览与渲染

| 功能 | Alfresco | Athena | 差距 |
|------|:--------:|:------:|------|
| 多格式预览 (Office/PDF/Image) | ✅ | ✅ | — |
| CAD 文件预览 | ⚠️ 需插件 | ✅ 内置 | **Athena 超越** |
| 渲染管线 (Pipeline Transform) | ✅ | ✅ | — |
| 失败诊断 / 死信队列 / 自动重试 | ❌ | ✅ | **Athena 超越** |
| CAD Failover 链 + 熔断器 | ❌ | ✅ | **Athena 超越** |
| 预览策略 Profile | ❌ | ✅ | **Athena 超越** |
| Rendition 渲染管理 API | ✅ 完整 | ⚠️ 基础 | 需增强 |
| 缩略图服务 | ✅ | ✅ | — |

### 工作流

| 功能 | Alfresco | Athena | 差距 |
|------|:--------:|:------:|------|
| BPMN 2.0 引擎 | ✅ Activiti | ✅ Flowable | — |
| 流程定义部署 | ✅ | ✅ | — |
| 任务管理 (认领/完成/委派) | ✅ | ✅ | — |
| 流程变量 | ✅ | ✅ | — |
| 流程图渲染 | ✅ | ✅ | — |
| 表单模型 | ✅ | ✅ | — |
| 流程历史 / 活动时间线 | ✅ | ✅ | — |
| 升级/降级任务 | ✅ | ✅ | — |
| 流程包 (Workflow Package) | ✅ | ❌ | 缺失 (低优先级) |
| 任务计时器查询 | ✅ | ❌ | 缺失 (低优先级) |

### 规则与自动化

| 功能 | Alfresco | Athena | 差距 |
|------|:--------:|:------:|------|
| 文件夹规则 | ✅ | ✅ | — |
| 规则类型 (Inbound/Update/Outbound) | ✅ | ✅ 按事件类型 | — |
| 规则继承 | ✅ | ❌ | **缺失** |
| 条件组合 (AND/OR/NOT) | ✅ | ✅ | — |
| 规则启停 (全局/单条/文件夹) | ✅ | ⚠️ 单条 | 需增强 |
| 规则执行历史 / 统计 | ⚠️ 基础 | ✅ 完整 | **Athena 超越** |
| DryRun 测试 | ❌ | ✅ | **Athena 超越** |
| 计划执行 (Cron) | ✅ | ✅ | — |
| 幂等性账本 | ❌ | ✅ | **Athena 超越** |

### 协作功能

| 功能 | Alfresco | Athena | 差距 |
|------|:--------:|:------:|------|
| 评论 | ✅ 扁平 | ✅ 嵌套+@提及+Reaction | **Athena 超越** |
| 标签 (Tags) | ✅ | ✅ | — |
| 分类 (Categories) | ✅ | ✅ | — |
| 收藏 (Favorites) | ✅ | ✅ | — |
| 评分/点赞 (Ratings/Likes) | ✅ | ❌ | **缺失** |
| 共享链接 | ✅ + 过期 + 邮件 | ✅ 基础 | **需增强** |
| 站点/协作空间 (Sites) | ✅ | ❌ | **缺失** |
| 活动流 (Activity Feed) | ✅ | ❌ | **缺失** |
| 关注/订阅 (Follow/Subscribe) | ✅ | ❌ | **缺失** |
| 站点邀请 (Invitations) | ✅ | ❌ | **缺失** |
| 讨论/论坛 | ✅ | ❌ | **缺失** |
| 博客 | ✅ | ❌ | **缺失** |
| 日历/事件 | ✅ | ❌ | **缺失** |
| 链接收藏 | ✅ | ❌ | **缺失** |
| PDF 页面注解 | ❌ | ✅ | **Athena 超越** |

### 用户与组管理

| 功能 | Alfresco | Athena | 差距 |
|------|:--------:|:------:|------|
| 用户 CRUD | ✅ | ✅ | — |
| 组管理 (嵌套组) | ✅ | ✅ | — |
| 用户头像 | ✅ | ❌ | **缺失** |
| 用户偏好 (服务端) | ✅ | ❌ | **缺失** |
| 用户配额 (存储限额) | ✅ | ❌ | **缺失** |
| MFA/TOTP | ⚠️ 需插件 | ✅ 内置 | **Athena 超越** |
| 人员目录 | ✅ | ✅ | — |

### 审计与合规

| 功能 | Alfresco | Athena | 差距 |
|------|:--------:|:------:|------|
| 审计日志 | ✅ | ✅ | — |
| 审计应用 (多应用/多路径) | ✅ | ⚠️ 单应用 | 需增强 |
| 按时间/ID 范围清理 | ✅ | ⚠️ 按分类 | 部分 |
| 审计导出 | ✅ | ✅ 异步 CSV | — |
| 可配置分类启停 | ✅ | ✅ | — |
| 节点级审计查询 | ✅ | ❌ | **缺失** |

### 企业级功能

| 功能 | Alfresco | Athena | 差距 |
|------|:--------:|:------:|------|
| 多租户 (Multi-Tenancy) | ✅ | ❌ | **缺失** |
| 批量导入 (Bulk Import) | ✅ | ❌ | **缺失** |
| 内容复制 (Replication) | ✅ | ❌ | **缺失** |
| 内容传输 (Transfer) | ✅ | ❌ | **缺失** |
| 导入/导出 (ACP Package) | ✅ | ❌ | **缺失** |
| CMIS 1.0/1.1 协议 | ✅ | ❌ | **缺失** |
| WebDAV 协议 | ✅ | ✅ | — |
| IMAP 服务端 | ✅ | ❌ | **缺失** |
| 模板引擎 (FreeMarker) | ✅ | ❌ | **缺失** |
| 脚本引擎 (JavaScript) | ✅ | ❌ | **缺失** |
| 全局属性存储 (Attribute Service) | ✅ | ❌ | **缺失** |
| 策略/行为框架 (Policy/Behavior) | ✅ | ❌ | **缺失** |
| 模块管理 (Module Service) | ✅ | ❌ | **缺失** |
| 发现 API (Discovery) | ✅ | ❌ | **缺失** |

### 集成与协议

| 功能 | Alfresco | Athena | 差距 |
|------|:--------:|:------:|------|
| Keycloak/OIDC | ✅ | ✅ | — |
| Elasticsearch/Solr | ✅ Solr | ✅ ES | — |
| RabbitMQ | ❌ | ✅ | **Athena 超越** |
| Redis 缓存/队列 | ❌ | ✅ | **Athena 超越** |
| Odoo ERP | ❌ | ✅ | **Athena 超越** |
| 邮件自动化 (IMAP/OAuth) | ⚠️ 仅 IMAP 服务端 | ✅ 客户端收件+规则 | **Athena 超越** |
| Webhook | ❌ | ✅ | **Athena 超越** |
| WOPI/Collabora 在线编辑 | ❌ | ✅ | **Athena 超越** |
| OCR (Tesseract) | ❌ | ✅ | **Athena 超越** |
| ML 服务 | ❌ | ✅ | **Athena 超越** |
| ClamAV 病毒扫描 | ❌ | ✅ | **Athena 超越** |

---

## Athena 已超越 Alfresco 的能力汇总

| 领域 | Athena 优势 | Alfresco 对应 |
|------|-----------|-------------|
| Preview 诊断与治理 | 失败分析、死信队列、策略管理、CAD Failover、熔断器 | 基础 Rendition 错误 |
| 异步任务中心 | 统一跨域任务生命周期、风险评分、治理 | 无统一任务中心 |
| 运维恢复控制面板 | DryRun、策略回滚、历史审计、批量重试 | 无 |
| 搜索驱动批量操作 | 搜索→DryRun→批量执行→原因分析→CSV 导出 | 无 |
| 评论系统 | 嵌套 5 层回复、@提及通知、Emoji Reaction、统计 | 扁平评论 |
| PDF 注解 | 页面级坐标注解持久化 | 无 |
| 版本比较 | 行级文本 Diff (支持 JSON/XML/YAML) | 无 |
| 邮件自动化 | IMAP/OAuth 收件 + 规则引擎匹配 | 仅 IMAP 服务端 |
| 规则引擎 | DryRun 测试、幂等性账本、执行统计 | 基础规则执行 |
| 权限诊断 | explainPermission 决策链可视化 | 无 |
| 权限过期 | 自动清理过期权限 | 无 |
| ERP 集成 | Odoo XML-RPC 双向同步 | 无 |
| 实时协作 | WOPI/Collabora 在线编辑 | 无 (社区版) |
| AI/OCR | Tesseract OCR + ML 服务 | 无 (社区版) |
| 病毒扫描 | ClamAV 集成 | 无 (社区版) |
| 消息队列 | RabbitMQ 事件总线 | 无 |
| 缓存/队列 | Redis 缓存 + 调度队列 | 无 |
| Webhook | 事件订阅外推 | 无 |

---

## 缺失功能开发清单

### 第一梯队：核心 ECM 必备（缺失 = 产品短板）

#### 1. 文档锁定服务增强 (LockService)

**Alfresco 能力：** WRITE_LOCK / READ_ONLY_LOCK 两种锁类型、超时自动解锁、持久锁/临时锁 (Persistent/Ephemeral)、批量锁、锁挂起/恢复、锁冲突检测、锁继承（锁文件夹连带子节点）

**Athena 现状：** Node 实体有 `locked/lockedBy/lockedDate` 字段，NodeService 有 `lockNode()/unlockNode()`，但缺乏锁类型、超时、批量、继承等高级能力。

**开发量：** ~3 天

```
新建: service/LockService.java (~300行)
新建: entity/LockType.java 枚举 (WRITE_LOCK, READ_ONLY_LOCK, NODE_LOCK)
新建: entity/LockLifetime.java 枚举 (PERSISTENT, EPHEMERAL)
修改: entity/Node.java (增加 lockType, lockExpiry, lockLifetime 字段)
修改: controller/NodeController.java (lock/unlock 端点增加参数)
新建: db/changelog/changelog-032-lock-service.xml
测试: LockServiceTest.java
```

---

#### 2. Check-Out/Check-In 工作副本机制

**Alfresco 能力：** checkout 创建工作副本 (Working Copy)、签出到指定位置、checkin 自动创建版本、checkin 后保持 checkout 状态 (keepCheckedOut)、cancel checkout 丢弃修改、查询工作副本/原文档关联。

**Athena 现状：** Document 有 `checkoutUser/checkoutDate`，有基础 checkout/checkin 端点，但无独立工作副本节点、无签出到不同位置、无 keepCheckedOut。

**开发量：** ~4 天

```
新建: service/CheckOutCheckInService.java (~400行)
修改: entity/Document.java (增加 workingCopyOf UUID, isWorkingCopy boolean)
修改: controller/DocumentController.java (增强 checkout/checkin 逻辑)
新建: db/changelog/changelog-033-checkout-working-copy.xml
测试: CheckOutCheckInServiceTest.java
```

---

#### 3. 内容模型 / 数据字典 (Content Model / Data Dictionary)

**Alfresco 能力：** 完整的类型/切面/属性/约束定义系统 (XML Model)、16 种属性数据类型、属性约束 (正则/列表/范围/长度)、强制属性、属性继承、运行时自定义模型 CRUD API (`/cmm`)、多语言文本 (MLText)。

**Athena 现状：** 使用 JSONB 存储灵活属性，有基础 ContentType 管理，但无正式类型/切面/约束系统。

**开发量：** ~10 天

```
新建: entity/ContentModelDefinition.java (~200行)
新建: entity/TypeDefinition.java (~150行)
新建: entity/AspectDefinition.java (~150行)
新建: entity/PropertyDefinition.java (~200行)
新建: entity/ConstraintDefinition.java (~100行)
新建: service/ContentModelService.java (~600行)
新建: service/DictionaryService.java (~400行)
新建: controller/ContentModelController.java (~300行)
新建: validation/PropertyConstraintValidator.java (~300行)
新建: db/changelog/changelog-034-content-model.xml (4-5 tables)
测试: ContentModelServiceTest.java, DictionaryServiceTest.java
```

---

#### 4. 节点切面系统 (Aspect System)

**Alfresco 能力：** `addAspect()`, `removeAspect()`, `hasAspect()`, `getAspects()` — 动态为节点附加额外属性集和行为。

**Athena 现状：** 前端类型定义有 `aspects?: string[]` 但后端实体无 aspects 字段、无 addAspect/removeAspect 方法。

**开发量：** ~3 天 (可与内容模型合并)

```
修改: entity/Node.java (增加 aspects ElementCollection<String>)
修改: service/NodeService.java (addAspect, removeAspect, hasAspect, getAspects)
修改: controller/NodeController.java (增加切面操作端点)
新建: db/changelog/changelog-035-node-aspects.xml (node_aspects join table)
测试: AspectServiceTest.java
```

---

#### 5. 评分/点赞服务 (Rating Service)

**Alfresco 能力：** 多评分方案 (likes / fiveStar)、用户评分追踪、评分汇总 (平均/总数/计数)、删除评分。

**Athena 现状：** 评论有 Reaction 系统，但节点级别无 Rating/Like。

**开发量：** ~2 天

```
新建: entity/Rating.java (~80行)
新建: service/RatingService.java (~200行)
新建: controller/RatingController.java (~150行)
新建: repository/RatingRepository.java
新建: db/changelog/changelog-036-ratings.xml
测试: RatingServiceTest.java
```

---

#### 6. 用户偏好服务 (Preference Service)

**Alfresco 能力：** 服务端持久化的命名空间用户偏好 (如 `org.alfresco.share.sites.favourites`)、CRUD、模式匹配查询。

**Athena 现状：** 前端用 localStorage 存偏好，服务端无偏好存储。

**开发量：** ~1.5 天

```
新建: entity/UserPreference.java (~60行)
新建: service/PreferenceService.java (~150行)
新建: controller/PreferenceController.java (~100行)
新建: repository/UserPreferenceRepository.java
新建: db/changelog/changelog-037-user-preferences.xml
测试: PreferenceServiceTest.java
```

---

#### 7. 节点关联增强 (Target/Source Associations)

**Alfresco 能力：** 一级公民 peer-to-peer 关联 (`createAssociation`, `getTargetAssocs`, `getSourceAssocs`)、带类型的关联 (QName)、二级子节点 (Secondary Children，多父级)。

**Athena 现状：** 有 DocumentRelation 实体和基础 CRUD，但缺乏关联类型系统、二级子节点/多父级、方向查询。

**开发量：** ~3 天

```
修改: entity/DocumentRelation.java (增加 relationType 枚举, 方向字段)
新建: service/AssociationService.java (~300行)
修改: controller/NodeController.java (增加 /targets, /sources, /secondary-children)
新建: db/changelog/changelog-038-associations.xml
测试: AssociationServiceTest.java
```

---

#### 8. 共享链接增强 (Shared Links)

**Alfresco 能力：** 过期时间、邮件发送共享链接、通过共享链接访问 Rendition、共享链接列表。

**Athena 现状：** 有 ShareLinkService/Controller，但缺乏过期时间、邮件发送、Rendition 访问。

**开发量：** ~2 天

```
修改: ShareLink entity (增加 expiryDate, accessCount)
修改: ShareLinkService.java (过期逻辑, 发邮件)
修改: ShareLinkController.java (增加 /email, /renditions 端点)
新建: scheduled cleanup for expired links
新建: db/changelog/changelog-039-share-link-expiry.xml
测试更新
```

---

### 第二梯队：协作与社交功能

#### 9. 站点/协作空间 (Sites)

**Alfresco 能力：** 协作空间创建/管理、成员角色 (Manager/Collaborator/Contributor/Consumer)、站点容器 (documentLibrary/wiki/blog)、可见性 (PUBLIC/MODERATED/PRIVATE)、成员申请/审批。

**开发量：** ~7 天

```
新建: entity/Site.java, SiteMember.java, SiteContainer.java
新建: service/SiteService.java (~500行)
新建: controller/SiteController.java (~400行)
新建: repository/SiteRepository.java, SiteMemberRepository.java
新建: db migration (sites, site_members, site_containers)
修改: 前端 SitesPage.tsx, siteService.ts
测试: SiteServiceTest.java
```

---

#### 10. 用户活动流 (Activity Feed)

**Alfresco 能力：** 活动发布、按用户/站点/类型过滤、活动聚合、按站点/工具的订阅控制。

**开发量：** ~5 天

```
新建: entity/Activity.java, ActivityFeedControl.java
新建: service/ActivityService.java (~400行), ActivityPostService.java (~200行)
新建: controller/ActivityController.java (~200行)
新建: db migration (activities, activity_feed_controls)
修改: 各 Service 增加活动发布
修改: 前端 ActivityFeedPage.tsx, activityService.ts
测试: ActivityServiceTest.java
```

---

#### 11. 关注/订阅 (Subscription/Following)

**Alfresco 能力：** 关注用户、订阅节点变更、关注者列表、隐私设置。

**开发量：** ~3 天

```
新建: entity/Subscription.java
新建: service/SubscriptionService.java (~250行)
新建: controller/SubscriptionController.java (~150行)
新建: db migration
修改: NotificationService.java (通知订阅者)
测试: SubscriptionServiceTest.java
```

---

#### 12. 站点邀请 (Invitation Service)

**Alfresco 能力：** 指名邀请、审批加入、邀请工作流、Ticket 追踪。

**开发量：** ~3 天 (依赖 Sites)

```
新建: entity/Invitation.java
新建: service/InvitationService.java (~300行)
新建: controller/InvitationController.java (~200行)
新建: db migration
测试: InvitationServiceTest.java
```

---

### 第三梯队：企业级功能

#### 13. 多租户 (Multi-Tenancy) — 架构级改造

**开发量：** ~10 天

```
新建: entity/Tenant.java, service/TenantService.java
新建: config/TenantFilterConfig.java (请求级路由)
修改: 所有 Repository (增加 @TenantId 过滤)
修改: SecurityService.java (租户感知)
修改: db migration (所有表增加 tenant_id)
新建: controller/TenantAdminController.java
```

---

#### 14. 批量导入 (Bulk Import)

**开发量：** ~5 天

```
新建: service/BulkImportService.java (~500行)
新建: controller/BulkImportController.java (~200行)
新建: entity/ImportJob.java
修改: 前端 BulkImportPage.tsx
测试: BulkImportServiceTest.java
```

---

#### 15. 内容归档/恢复 (Content Archive)

**开发量：** ~4 天

```
新建: service/ContentArchiveService.java (~300行)
修改: ContentService.java (archive/restore)
修改: Document.java (archiveStatus, archiveDate)
新建: controller 端点
新建: db migration
```

---

#### 16. 预签名 URL (Direct Access URL)

**开发量：** ~3 天

```
新建: service/DirectAccessUrlService.java (~200行)
修改: DocumentController.java (/direct-access-url 端点)
集成: MinIO presigned URL
```

---

#### 17. CMIS 协议支持

**开发量：** ~15 天

```
引入: Apache Chemistry OpenCMIS 库
新建: cmis/ package (~15-20 个类)
新建: CmisServiceFactory, CmisRepository, CmisTypeManager
新建: CMIS 绑定端点 (AtomPub + Browser)
新建: CMIS 查询转 ES 查询
```

---

#### 18. 策略/行为框架 (Policy/Behavior)

**开发量：** ~5 天

```
新建: policy/PolicyComponent.java (~300行)
新建: policy/ClassPolicy.java, PropertyPolicy.java
新建: policy/BehaviorFilter.java (~200行)
新建: policy/PolicyRegistry.java (~250行)
修改: NodeService.java (CRUD 触发策略)
```

---

#### 19. 配额服务 (Quota Service)

**开发量：** ~3 天

```
新建: entity/UserQuota.java
新建: service/QuotaService.java (~250行)
修改: ContentService.java (上传检查配额)
修改: User.java (quota, usedStorage)
新建: db migration
```

---

#### 20. 内容复制 (Replication Service)

**开发量：** ~5 天

```
新建: entity/ReplicationDefinition.java
新建: service/ReplicationService.java (~400行)
新建: controller/ReplicationController.java
新建: scheduler (Cron 触发复制)
```

---

#### 21. 导入/导出 (ACP Package)

**开发量：** ~5 天

```
新建: service/ImporterService.java (~400行)
新建: service/ExporterService.java (~400行)
新建: controller/ImportExportController.java
新建: ACP 格式定义 (XML + ZIP)
```

---

#### 22. 讨论/论坛/博客

**开发量：** ~7 天

```
新建: entity/DiscussionTopic.java, DiscussionReply.java
新建: entity/BlogPost.java
新建: service/DiscussionService.java, BlogService.java
新建: controller/DiscussionController.java, BlogController.java
修改: 前端 DiscussionPage.tsx, BlogPage.tsx
```

---

#### 23. 日历/事件 (Calendar)

**开发量：** ~4 天

```
新建: entity/CalendarEvent.java
新建: service/CalendarService.java (~300行)
新建: controller/CalendarController.java (~200行)
修改: 前端 CalendarPage.tsx
```

---

#### 24. 模板/脚本引擎

**开发量：** ~5 天

```
新建: service/TemplateService.java (FreeMarker 集成)
新建: service/ScriptService.java (Nashorn/GraalJS)
新建: controller/TemplateController.java
新建: 模板变量绑定框架
```

---

#### 25. 全局属性存储 (Attribute Service)

**开发量：** ~2 天

```
新建: entity/GlobalAttribute.java
新建: service/AttributeService.java (~200行)
新建: controller/AttributeController.java
新建: db migration
```

---

#### 26. 发现 API (Discovery)

**开发量：** ~1 天

```
新建: controller/DiscoveryController.java (~100行)
返回: 仓库版本、已安装模块、支持能力列表
```

---

## 开发量汇总

| 梯队 | # | 功能 | 工日 | 优先级 |
|:----:|:-:|------|:----:|:------:|
| **一** | 1 | 文档锁定服务增强 | 3 | P0 |
| **一** | 2 | Check-Out/Check-In 工作副本 | 4 | P0 |
| **一** | 3 | 内容模型/数据字典 | 10 | P0 |
| **一** | 4 | 节点切面系统 | 3 | P0 |
| **一** | 5 | 评分/点赞服务 | 2 | P1 |
| **一** | 6 | 用户偏好服务 | 1.5 | P1 |
| **一** | 7 | 节点关联增强 | 3 | P1 |
| **一** | 8 | 共享链接增强 | 2 | P1 |
| | | **第一梯队小计** | **28.5** | |
| **二** | 9 | 站点/协作空间 | 7 | P1 |
| **二** | 10 | 用户活动流 | 5 | P1 |
| **二** | 11 | 关注/订阅 | 3 | P2 |
| **二** | 12 | 站点邀请 | 3 | P2 |
| | | **第二梯队小计** | **18** | |
| **三** | 13 | 多租户 | 10 | P2 |
| **三** | 14 | 批量导入 | 5 | P1 |
| **三** | 15 | 内容归档/恢复 | 4 | P2 |
| **三** | 16 | 预签名 URL | 3 | P1 |
| **三** | 17 | CMIS 协议 | 15 | P3 |
| **三** | 18 | 策略/行为框架 | 5 | P2 |
| **三** | 19 | 配额服务 | 3 | P2 |
| **三** | 20 | 内容复制 | 5 | P3 |
| **三** | 21 | 导入/导出 (ACP) | 5 | P3 |
| **三** | 22 | 讨论/论坛/博客 | 7 | P3 |
| **三** | 23 | 日历/事件 | 4 | P3 |
| **三** | 24 | 模板/脚本引擎 | 5 | P3 |
| **三** | 25 | 全局属性存储 | 2 | P2 |
| **三** | 26 | 发现 API | 1 | P1 |
| | | **第三梯队小计** | **74** | |
| | | | | |
| | | **总计** | **120.5 工日** | **26 项** |

---

## 推荐开发顺序

### Phase 1：核心 ECM 补齐（~4 周）
1. 内容模型/数据字典 + 切面系统 → 13 天
2. LockService 增强 → 3 天
3. Check-Out/Check-In 工作副本 → 4 天

### Phase 2：协作能力补齐（~3 周）
4. 评分/点赞 → 2 天
5. 用户偏好 → 1.5 天
6. 节点关联增强 → 3 天
7. 活动流 → 5 天
8. 站点/协作空间 → 7 天

### Phase 3：企业级功能（~4 周）
9. 批量导入 → 5 天
10. 预签名 URL → 3 天
11. 发现 API → 1 天
12. 配额服务 → 3 天
13. 策略/行为框架 → 5 天
14. 多租户 → 10 天

### Phase 4：协议兼容与高级功能（按需）
15. CMIS 协议 → 15 天
16. 讨论/论坛/博客 → 7 天
17. 日历/事件 → 4 天
18. 其他 → 按需

---

> **结论：** Athena 在运维诊断、搜索批量操作、评论协作、邮件自动化、AI/OCR 等方面已显著超越 Alfresco 社区版。核心差距集中在**内容模型/数据字典**、**锁定/签出机制**、**协作空间**和**活动流**等传统 ECM 基础能力上。补齐第一梯队（~28.5 工日）即可在核心 ECM 功能上达到 Alfresco 同等水平；补齐全部三个梯队（~120.5 工日）即可全面超越。
