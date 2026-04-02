# Phase 345 - Alfresco People Site Launcher Row Actions

## Goal

把 People Directory 中与站点相关的入口继续前移到行级动作，让用户从 people workspace 直接进入 workspace 或继续协作，而不是先读列表再找下一跳。

## Scope

- `Sites` 列表补 `Open` 行级入口
- `Favorite Sites` 列表补 `Open` 行级入口
- 保留并前移已有协作动作：
  - `Add favorite`
  - `Remove favorite`
  - `Request access`
  - `Edit request`
  - `Withdraw`

## Frontend Design

`PeopleDirectoryPage` 新增统一的 site open helper，并把动作收敛到列表项本身：

- `Sites`
  - `Open`
  - `Add favorite` / `Remove favorite`
- `Favorite Sites`
  - `Open`
  - `Request access`
  - `Edit request`
  - `Withdraw`
  - `Remove`

这比只展示站点元数据更接近真正的 user workspace / collaboration launcher。

## Files

- `ecm-frontend/src/pages/PeopleDirectoryPage.tsx`

## Result

Athena 的 People Directory 现在已经能从 comment、activity、favorite、site 四条路径直接回到协作面，站点入口明显比基础对标实现更直接。
