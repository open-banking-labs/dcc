# DCC 管理端（dcc-console）

对接 DCC 的 `/api` REST 接口的后台管理界面。

## 技术栈

React + TypeScript + Vite · Tailwind CSS + shadcn/ui · react-router · axios · @tanstack/react-table · lucide-react。
认证：OIDC（`react-oidc-context`）或本地 Token（未配置 OIDC 时）。

## 目录

```
src/
├── api/        各资源接口封装（standards / tables / interfaces / templates / migrations / tenancy / versioning / generation / audit）
├── auth/       会话抽象（session.tsx）、守卫、登录/回调页
├── components/ layout（外壳/侧边栏/面包屑/环境应用切换）· ui（shadcn 原子）· data（DataTable/DiffView）· standards（数标表格/导入导出弹窗）· form（字段/编辑器）· feedback（Toast/状态）
├── hooks/      useAsync · useResourceContext · useListRestoration
├── lib/        apiClient（axios 封装）· oidc · jwt · devToken（本地开发签发）· labels · format · permissions
├── pages/      各模块页面（含 audit 审计日志、directory 授权目录）
├── router.tsx  路由（页面按需加载）
└── main.tsx    入口
```

## 运行

### 1. 后端（host，JDK 17 + 运行中的 Postgres）

```bash
# 只启动 Postgres + MinIO（Excel 导出用；若未运行 MinIO，导出任务会显示失败）
./scripts/dcc-start.sh --minio

# 让后端读到 MinIO 凭据（DCC_EXPORT_S3_* 缺省回落到 MINIO_ROOT_*）
set -a; source env/dev.env; set +a

# 构建依赖模块并安装到本地仓库（一次即可）
env JAVA_HOME=$(/usr/libexec/java_home -v 17) ./mvnw -q -pl dcc-web -am -DskipTests install

# 运行 dcc-web（dev,local：local 提供本机数据库密码）
env JAVA_HOME=$(/usr/libexec/java_home -v 17) ./mvnw -pl dcc-web spring-boot:run -Dspring-boot.run.profiles=dev,local
```

后端起在 http://localhost:8080（Flyway 自动建表）。Excel 导出的文件放在
`dcc-exports` 桶里，前端拿到的是 10 分钟有效的预签名链接，下载不经过应用服务器。

### 2. 前端

```bash
cd dcc-console
npm install
npm run dev          # http://localhost:5173
```

`vite.config.ts` 已把 `/api` 代理到 `localhost:8080`。

### 3. 登录

- **未配置 OIDC（默认）**：进入 `/login`。
  - **身份登录（推荐）**：直接在页面上选操作者 / 租户 / 角色 / 有效期，前端用 WebCrypto
    按开发密钥（`DCC_JWT_SECRET` 默认值，可用 `VITE_DEV_JWT_SECRET` 覆盖）签发 HS256 JWT，
    等价于 `scripts/dev-token.mjs`，并记住上次登录身份。
  - **使用已生成的 Token**：粘贴 `node scripts/dev-token.mjs` 的输出（会校验格式与过期）。
  二者都存入 `localStorage`（键 `dcc.devToken`），退出登录即清除。
- **配置了 OIDC**：在 `.env` 填 `VITE_OIDC_ISSUER`、`VITE_OIDC_CLIENT_ID` 等，即走统一身份登录。

> 后端默认密钥为 `DCC_JWT_SECRET`（未设置时用 dev 默认值），本页签发与 `dev-token.mjs` 皆与之对应。生产请通过 `dcc.security.jwk-set-uri` 对接 IdP（届时本地签发入口自动隐藏）。

> 审计与恢复能力对齐后端 retention 特性：「平台管理 → 审计日志」（管理员）提供检索（对象类型/id/执行者/操作/时间窗，服务端分页）、单行链路（完整历史 + 哈希链校验）与已删除对象的恢复；删除均为逻辑删除（保留行、版本与引用），恢复若与新对象编码冲突由数据库部分唯一索引仲裁。见 `docs/decisions/0002-retention-and-audit-trail.md`。
>
> 「平台管理 → 授权目录」按有效权限选择接口：平台管理使用 `/api/directory/*`，可指定租户；租户内管理使用 `/api/memberships`、`/api/role-assignments`，租户由服务端从当前身份确定，不发送租户参数。只读用户不显示写操作；特权授权显示待确认状态，授予人不能确认自己的授权。
>
> 「平台管理 → 平台审计」使用 `/api/platform/audit`，展示租户准入、授权目录与指导案例记录；该接口仅提供检索与记录详情。业务审计继续使用 `/api/audit`，提供链路和哈希校验；恢复按钮按对象类型对应的权限显示。

> **工作台（登录后首页）** 使用 `GET /api/dashboard/summary` 一次取回全部卡片：四类产物的总数与待审数、迁移单按状态分组计数、最近 5 条迁移单、近 24 小时审计条数、租户注册表。环境/应用取自顶栏切换器；其中迁移单按**目标环境**过滤（与 `/api/migrations?targetEnvironmentId` 一致），产物按所在环境过滤。接口对任何已认证用户开放，但每个分区由服务端按权限点裁剪，无权的分区返回 `null`（前端据此不渲染卡片）——「没有数据」与「无权查看」在界面上必须是两种样子。零权限账号仍落到 `/no-access`。

> **公告** 由平台管理员（`announcement.write.platform`，发布给所有租户）与租户管理员（`announcement.write`，只发本租户）发布，登录后显示在顶栏下方的轮播条上。读取横幅不设权限点：任何已认证用户都能看到「平台公告 + 本租户公告」中启用且在生效窗口内的条目。轮播每 8 秒切换一条；`prefers-reduced-motion` 下改为静态堆叠；单条可在本次会话内关闭（记在 `sessionStorage`，刷新后仍隐藏、下次打开仍显示）。两套端点（`/api/announcements` 与 `/api/platform/announcements`）在查询层互不可达，越权访问表现为 404。

## 构建

```bash
npm run build        # tsc --noEmit && vite build
npm run typecheck    # 仅类型检查
npm test             # API 契约和权限会话隔离检查，无需后端
```

## 约定

- 其他资产列表返回裸数组，分页/排序在前端（TanStack Table）；数标查询/维护、目录和审计接口使用服务端分页。
- 数标查询页与维护页把筛选、页码、页大小写入 URL；`useListRestoration` 记录列表地址与滚动位置，详情页据此返回原现场。
- 数标查询页只读：检索、引用分析（左栏按分类分组，点击才加载引用，5 分钟/50 条前端缓存）与历史版本；批量入口在表格上方，无操作列。维护页负责新建、导入、编辑、删除与状态流转。
- Excel 导出是异步任务：前端轮询 `/api/standards/exports/{id}`，完成后直接打开预签名链接下载；导入先下载模板，整表校验通过才写入。
- 写操作的环境/应用上下文来自顶栏 `EnvAppSwitcher`，作为 query 参数带上。
- 菜单及操作显隐以 `/api/me` 的有效权限点为准，不根据 JWT 角色推导权限。权限请求显式携带当前 Token；切换身份、请求失败或尚未返回时不沿用旧权限。
- 前端权限判断仅用于界面展示；资源真实环境/应用范围与职责分离规则仍由服务端校验（ADR 0005）。
