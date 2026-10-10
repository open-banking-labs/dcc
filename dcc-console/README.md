# DCC 管理端（dcc-console）

对接 DCC 的 `/api` REST 接口的后台管理界面。

## 技术栈

React + TypeScript + Vite · Tailwind CSS + shadcn/ui · react-router · axios · @tanstack/react-table · lucide-react。
认证：OIDC（`react-oidc-context`）或本地 Token（未配置 OIDC 时）。

## 目录

```
src/
├── api/        各资源接口封装（standards / tables / interfaces / templates / migrations / tenancy / versioning / generation）
├── auth/       会话抽象（session.tsx）、守卫、登录/回调页
├── components/ layout（外壳/侧边栏/面包屑/环境应用切换）· ui（shadcn 原子）· data（DataTable/DiffView）· form（字段/编辑器）· feedback（Toast/状态）
├── hooks/      useAsync · useResourceContext
├── lib/        apiClient（axios 封装）· oidc · jwt · labels · format · permissions
├── pages/      各模块页面
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

- **未配置 OIDC（默认）**：进入 `/login` 的「本地开发登录」，粘贴开发 Token：
  ```bash
  node scripts/dev-token.mjs        # 打印一个 HS256 JWT（含 tenant / roles）
  ```
  Token 存入 `localStorage`（键 `dcc.devToken`）。退出登录即清除。
- **配置了 OIDC**：在 `.env` 填 `VITE_OIDC_ISSUER`、`VITE_OIDC_CLIENT_ID` 等，即走统一身份登录。

> 后端默认密钥为 `DCC_JWT_SECRET`（未设置时用 dev 默认值），`dev-token.mjs` 与之对应。生产请通过 `dcc.security.jwk-set-uri` 对接 IdP。

## 构建

```bash
npm run build        # tsc --noEmit && vite build
npm run typecheck    # 仅类型检查
```

## 约定

- 列表接口返回裸数组，分页/排序在前端（TanStack Table）。
- 写操作的环境/应用上下文来自顶栏 `EnvAppSwitcher`，作为 query 参数带上。
- 删除需 `DCC_ADMIN`、生命周期流转需 `DCC_APPROVER`；`PermissionGate` 按 token 的 `roles` claim 显隐。
