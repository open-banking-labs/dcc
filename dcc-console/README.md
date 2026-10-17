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
├── components/ layout（外壳/侧边栏/面包屑/环境应用切换）· ui（shadcn 原子）· data（DataTable/DiffView）· form（字段/编辑器）· feedback（Toast/状态）
├── hooks/      useAsync · useResourceContext
├── lib/        apiClient（axios 封装）· oidc · jwt · devToken（本地开发签发）· labels · format · permissions
├── pages/      各模块页面（含 audit 审计日志）
├── router.tsx  路由（页面按需加载）
└── main.tsx    入口
```

## 运行

### 1. 后端（host，JDK 17 + 运行中的 Postgres）

```bash
# 只启动 Postgres（若未运行）
docker compose --env-file env/dev.env up -d postgres

# 构建依赖模块并安装到本地仓库（一次即可）
env JAVA_HOME=$(/usr/libexec/java_home -v 17) ./mvnw -q -pl dcc-web -am -DskipTests install

# 运行 dcc-web（dev,local：local 提供本机数据库密码）
env JAVA_HOME=$(/usr/libexec/java_home -v 17) ./mvnw -pl dcc-web spring-boot:run -Dspring-boot.run.profiles=dev,local
```

后端起在 http://localhost:8080（Flyway 自动建表）。

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

## 构建

```bash
npm run build        # tsc --noEmit && vite build
npm run typecheck    # 仅类型检查
```

## 约定

- 列表接口返回裸数组，分页/排序在前端（TanStack Table）。
- 写操作的环境/应用上下文来自顶栏 `EnvAppSwitcher`，作为 query 参数带上。
- 删除需 `DCC_ADMIN`、生命周期流转需 `DCC_APPROVER`；`PermissionGate` 按 token 的 `roles` claim 显隐。
