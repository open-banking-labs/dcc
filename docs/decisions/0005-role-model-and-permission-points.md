# ADR 0005: 三层租户与权限点模型（六角色）

- Status: Accepted
- Date: 2026-10-20
- Revised: 2026-10-19 — 删除全部旧权限体系，不保留兼容层（`DCC_ADMIN` / `DCC_USER`、
  `role-authorities`、`roles-passthrough`、`DccAuthorities` 均移除）
- Supersedes: 无（扩展 ADR 0003 / 0004）
- Depends on: ADR 0002（保留期与审计）、ADR 0003（授权目录）、ADR 0004（scoped grants）

## Context

### 现状：三顶帽子 + 写操作默认放行

`dcc-security` 定义了三个逻辑 authority（`admin` / `approver` / `user`），名字由配置持有、
代码中通过 `@dccAuthorities.*` 引用。全仓只有 17 处 `@PreAuthorize`，全部位于 `dcc-web`；
鉴权链的兜底是 `anyRequest().authenticated()`。也就是说**登录即获得写权限**，只有
"发布 / 删除 / 恢复 / 目录 / 审计"少数端点是显式受限的。

由此产生六类结构性缺陷：

| # | 缺陷 | 证据 |
|---|------|------|
| 1 | 写是默认值：任何登录用户可建租户、改表结构、执行迁移 | `anyRequest().authenticated()`；`TenantController#create`、`DataStandardController#create/update/import/rollback`、`MigrationController#submit/execute/rollback` 均无注解 |
| 2 | 没有读者角色：`DCC_USER` 既是基线身份又事实等于写手 | 同上；`DccAuthorities#getUser` 只用于 `/my-memberships` |
| 3 | `admin` 横跨两个面：既管平台底座又管业务记录生命周期 | `AuditController` 类级 admin；`DataStandardController#delete/restore` admin；`DirectoryController` 类级 admin |
| 4 | 同一份数据三扇门两把锁：审计/恢复在 `/api` 锁了，`/cli` 与 MCP 没锁 | `/api/audit` admin；`/cli/v1/audit/*` 无注解；MCP `dcc-audit-*` 无注解 |
| 5 | 无职责分离：批准只推进状态机，不比对提交人 | `MigrationService#approve` 仅 `transitionTo`；全仓无 SoD 实现 |
| 6 | 平台层无作用域概念：`/api/tenants` 无鉴权且返回全部租户 | `TenantController#list` + `TenantService#list` (`tenants.findAll()`) |

此外：`role_assignment` / `tenant_membership` 的写入目标租户来自**请求体**
（`CreateCommand.tenantCode`），而 `DirectoryController` 明确是平台级、不按租户隔离——
一旦引入"租户内管理员"，这就是最好用的一条越权路径。

### 决策驱动

1. 银行视角需要四类职责分离的自然人：写内容的人、批变更的人、管平台的人、看数据的人。
2. 平台层与租户层的破坏半径不同：环境/应用里装着该租户的业务数据，其生命周期必须留在
   **数据所有者**的边界内。
3. 端点数量已近百，"给哪个端点挂哪个角色"的调整会持续发生，角色名硬编码不可持续。

## Decision

### D1 三层作用域：平台层 / 租户层 / 数据面

| 层 | 作用域 | 治理者 | 治理对象 |
|----|--------|--------|----------|
| 平台层 | 无租户维度 | 系统管理员 | 租户准入、租户初始化、指导性案例 |
| 租户层 | 单租户 | 租户管理员 | 环境、应用、成员与角色、租户配置、业务执行 |
| 数据面 | 环境 × 应用 | 维护者 / 审批者 / 读者 | 数标、表结构、接口、模板、迁移单 |

**相邻层只允许"下级引用上级"，不允许"上级操作下级"**：系统管理员完成准入与初始化后即退出
该租户，无法读写该租户的业务数据、环境与应用。

平台层不引入第三种身份模式（不改 `BearerJwtAuthenticationFilter` 的 tenant 语义），而是用
**只发给系统管理员的权限点**来表达：没有 `tenant.admit` 就进不了平台端点，tenant claim 仍然
是每个请求的上下文。这样平台隔离由权限收敛获得，而不是由新的作用域机制获得。

### D2 六个角色

| 角色 | authority（默认值，可配置） | 作用域 |
|------|------------------------------|--------|
| 读者 | `DCC_READER` | 租户内 |
| 维护者 | `DCC_MAINTAINER` | 租户内 |
| 审批者 | `DCC_APPROVER` | 租户内 |
| 环境管理员 | `DCC_ENV_ADMIN` | 单个环境 / 应用（必须带 scope） |
| 租户管理员 | `DCC_TENANT_ADMIN` | 单租户 |
| 系统管理员 | `DCC_PLATFORM_ADMIN` | 平台全局 |

角色是**权限包**，端点判定的是**权限点**（D3）。六个角色之间非层叠：系统管理员不是任何角色
的超集，租户管理员不持有维护/审批权限。

### D3 端点判权限点，角色是权限包

- 权限点是可配置的 authority 字符串，由 `DccPermissions`（bean 名 `@perms`）暴露，
  代码中写作 `@PreAuthorize("hasAuthority(@perms.artifactWrite)")`。
- 角色 → 权限包的展开复用既有接缝 `RolePermissionResolver`：新增
  `dcc.security.role-bundles.<role>: [perm, ...]`。没有 bundle 的角色**不赋予任何权限**。
- 旧 authority 词表与兼容入口全部删除：不存在 `DccAuthorities`、`role-authorities` 或
  `roles-passthrough`。旧 token 中的 `DCC_ADMIN` / `DCC_USER` 只作为普通未知字符串被忽略。

### D4 职责分离（四眼）

- 四眼按**身份**判定，不按角色判定："提交人 ≠ 批准人"始终成立。
- 提交人来源：
  - 迁移单：新增 `migration_order.submitted_by`（迁移单没有版本行，必须落列）。
  - 业务产物：取**当前版本的 author**（不可变版本历史已记录，避免 4 张业务表各加列）。
    该取值比真实提交人**偏严**（若 A 编辑、B 提交，则 A 不能批准），属于 fail-closed。
- 允许同一自然人同时持有维护者与审批者（小团队必须可行），但"批自己的提交"仍被拒绝。

### D5 租户强制（tenant-scope forcing）

目录写入分两套端点，权限点不同，落到服务层的目标租户来源也不同：

| 端点 | 权限点 | 目标租户来源 |
|------|--------|--------------|
| `POST /api/directory/memberships`、`/role-assignments` | `directory.write.platform` | 请求体（仅初始化用） |
| `POST /api/memberships`、`/api/role-assignments` | `directory.write` | **强制 = 调用者的 tenant**（请求体不接受 tenantCode） |

系统管理员只能授 `DCC_TENANT_ADMIN`（初始化唯一入口），不能直授维护者/审批者。

### D6 平台审计

`Tenant` / `TenantMembership` / `RoleAssignment` 是平台级行（无 tenant_id），而 `/api/audit`
按调用者租户过滤，因此平台动作目前**没有任何可读面**。新增平台作用域只读端点
`GET /api/platform/audit`（权限点 `audit.platform`），只返回 `tenant_id IS NULL` 的行；
`/api/audit` 收归 `audit.business`（租户管理员）。

### D7 指导性案例（reference case）

系统管理员维护各功能的参考实现；租户管理员可以采纳（生成草稿）或忽略后从零维护。

- 新增平台级表 `reference_case`（`BaseEntity`，无 tenant_id），字段：`feature`
  (`STANDARD|TABLE|INTERFACE|TEMPLATE|SKELETON`)、`code`、`name`、`description`、`content`、
  `case_version`、`enabled`。
- `SKELETON` 承载"环境/应用骨架范例"（如 DEV/TEST/UAT/PROD 的推荐形态），使新租户有起步路径，
  而平台不碰实例。
- 采纳：`POST /api/cases/{id}/adopt?environmentId&applicationId` 在租户内创建**草稿**产物，
  溯源写入不可变版本记录（`ChangeContext` message = `adopted from case <code> v<n>`）。
- 案例更新**不自动同步**已采纳对象（自动同步等于平台间接修改租户内容）。
- 案例维护权是独立权限点 `case.write`，与租户内的 `artifact.write` 无关——不得用"保留系统租户
  + 平台管理员在该租户里是维护者"的方式实现，否则"管理员不参与维护"的底线当场破。

### D8 权限点目录

| 权限点 | 含义 |
|--------|------|
| `artifact.read` | 读、list、history、diff、verify、references、export |
| `artifact.write` | 建草稿、编辑、导入、版本回滚 |
| `artifact.delete` | 逻辑删除（保留期，非物理） |
| `artifact.restore` | 从保留期恢复已删除对象 |
| `artifact.transition` | 状态流转：发布 / 退回 |
| `migration.write` | 建迁移单、提交 |
| `migration.approve` | 批准 / 拒绝迁移单 |
| `migration.execute` | 执行 / 回滚已批准迁移 |
| `generation.read` | 校验类、DTO、OpenAPI 生成 |
| `generation.package` | JAR 打包、Flyway SQL 导出 |
| `topology.read` | 环境 / 应用 读取 |
| `topology.write` | 环境 / 应用 CRUD |
| `directory.read` | 成员 / 角色 查看 |
| `directory.write` | 本租户成员 / 角色授予 |
| `directory.write.platform` | 跨租户初始化授权 |
| `audit.business` | 本租户业务审计 |
| `audit.platform` | 平台审计（准入 / 授权 / 案例） |
| `case.read` / `case.adopt` | 案例读取 / 采纳 |
| `case.write` | 案例维护 |
| `tenant.admit` | 租户创建 / 启停 / 注销 / 全量枚举 |

### D9 角色 → 权限包默认值

| 角色 | 权限包 |
|------|--------|
| 读者 | `artifact.read` `generation.read` `topology.read` |
| 维护者 | 读者 + `artifact.write` `artifact.delete` `migration.write` `generation.package` |
| 审批者 | 读者 + `artifact.transition` `migration.approve` |
| 环境管理员 | 读者 + `artifact.write` `artifact.delete` `migration.write` `migration.execute` `generation.package` |
| 租户管理员 | 读者 + `artifact.restore` `migration.execute` `topology.write` `directory.read` `directory.write` `audit.business` `case.read` `case.adopt` |
| 系统管理员 | `tenant.admit` `directory.read` `directory.write.platform` `audit.platform` `case.write` `case.read` |

系统管理员**不含** `artifact.read`：平台管理员看不到任何租户业务内容。

### D10 升级：旧体系完全删除

本版本**不提供兼容层**。`DCC_ADMIN`、`DCC_USER`、`role-authorities`、
`roles-passthrough` 与 `DccAuthorities` 已从代码和默认配置中删除；携带旧角色的 token
或目录行不产生任何权限。升级时必须把 IdP 组和存量目录授权迁移到下列角色名：

| 角色 | 说明 |
|------|------|
| `DCC_READER` / `DCC_MAINTAINER` / `DCC_APPROVER` | 数据面读写与审批 |
| `DCC_ENV_ADMIN` | 单环境 / 应用的数据面管理，必须带 scope |
| `DCC_TENANT_ADMIN` | 本租户的环境、应用、目录、审计与执行 |
| `DCC_PLATFORM_ADMIN` | 平台准入、平台审计、案例维护；无业务数据权限 |

IdP 组应显式映射到角色（未知角色不会自动成为权限）：

```yaml
dcc:
  security:
    role-bundles:
      DCC_OPS_ADMINS: [DCC_PLATFORM_ADMIN]   # IdP 组 → 角色
      DCC_PLATFORM_ADMINS: [DCC_PLATFORM_ADMIN]
      DCC_TENANT_ADMINS: [DCC_TENANT_ADMIN]
      DCC_MAINTAINERS: [DCC_MAINTAINER]
      DCC_APPROVERS: [DCC_APPROVER]
      DCC_READERS: [DCC_READER]
```

### D11 端点矩阵（现状 → 目标）

作用域标记：**P** = 平台（权限点只发平台管理员），**T** = 租户内。

| 端点 | 旧实现 | 目标权限点 | 作用域 |
|------|------|-----------|--------|
| `POST /api/tenants` | 无 | `tenant.admit` | P |
| `GET /api/tenants`（全量） | 无 | `tenant.admit` | P |
| `GET /api/tenants/{code}` | 无 | `tenant.admit` **或** code 等于本租户 | P/T |
| `PUT / DELETE / POST restore /api/tenants/{code}` | 无 | `tenant.admit` | P |
| `POST/PUT/DELETE/restore /api/environments` | 无 | `topology.write` | T |
| `GET /api/environments*` | 无 | `topology.read` | T |
| `/api/applications` 写 / 读 | 无 | `topology.write` / `topology.read` | T |
| `POST/PUT/rollback/import /api/{standards,tables,interfaces,templates}` | 无 | `artifact.write` | T |
| `DELETE` 同上 | 旧 admin authority | `artifact.delete` | T |
| `POST /restore` 同上 | 旧 admin authority | `artifact.restore` | T |
| `POST /status` 同上 | 旧 approver authority | `artifact.transition` | T |
| `GET` 同上（含 history/diff/verify/references/export） | 无 | `artifact.read` | T |
| `POST /api/migrations`、`/submit` | 无 | `migration.write` | T |
| `/approve`、`/reject` | 旧 approver authority | `migration.approve` | T |
| `/execute`、`/rollback` | 无 | `migration.execute` | T |
| `GET /api/migrations*` | 无 | `artifact.read` | T |
| `GET /api/audit*` | 旧 admin authority | `audit.business` | T |
| `GET /api/platform/audit`（新增） | — | `audit.platform` | P |
| `/api/directory/*` 读写（既有） | 旧 admin authority | `directory.read` / `directory.write.platform` | P |
| `/api/memberships`、`/api/role-assignments`（新增） | — | `directory.write`（租户强制） | T |
| `/api/directory/my-memberships` | 旧 user authority | `directory.read` | T |
| `/api/cases` 读写（新增） | — | `case.read` / `case.write` | P/T |
| `POST /api/cases/{id}/adopt`（新增） | — | `case.adopt` | T |
| `/api/generation/{manifest,openapi}` | 无 | `generation.read` | T |
| `/api/generation/release` | 无 | `generation.package` | T |
| `/api/versioning/*` | 无 | `artifact.read` | T |
| `/cli/v1/{hash,diff,bump,validate,drift}` | 无 | `artifact.read` | T |
| `/cli/v1/export/*` | 无 | `generation.package` | T |
| `/cli/v1/audit/*` | 无 | `audit.business` | T |
| `/cli/v1/restore` | 旧 admin authority | `artifact.restore` | T |
| MCP 读工具（含 `dcc-audit-*`） | 无 | `artifact.read` / `audit.business` | T |
| MCP `generate-*` | 无 | `generation.read` | T |
| `GET /api/me`（新增） | — | 认证即可 | T |

## 自我评审与修正

初稿与终稿的差异（每条都是被自己推翻的设计）：

1. **环境/应用从平台层移回租户层。** 初稿把环境、应用当"平台拓扑"归系统管理员。错误：
   环境里装着该租户的业务数据，那等于给了"能删掉任意租户环境"的权限。终稿把破坏半径限制在
   数据所有者边界内，平台只保留准入与初始化。
2. **案例库从"保留系统租户"改为独立表。** 保留租户方案能复用版本/审计机制，但会让平台管理员
   在该租户里成为维护者——直接违反"管理员不参与维护"。改用独立表，并只保留内容+版本+说明，
   不做字段级 diff（省掉 80% 工作量）。
3. **案例溯源从"新增 `derived_from` 列"改为写进不可变版本 message。** 前者要动 4 张业务表
   （4 个 Flyway 脚本 + 4 个实体 + 4 个视图），后者零 schema 变更。代价是会失去"某案例被谁
   采纳"的反向查询，列入遗留项。
4. **SoD 提交人从"4 张业务表各加 `submitted_by`"改为"取当前版本 author"。** 只有
   `migration_order` 加列（它没有版本行）。取值偏严即 fail-closed，可接受。
5. **平台作用域不引入新机制。** 初稿想在 filter 里区分"平台身份/租户身份"（不要求 tenant
   claim）。放弃：新增第二种身份语义会污染 `TenantContext` 与所有 `AbstractTenantController`
   子类；改用"平台权限点只发给系统管理员"，隔离效果等同、改动面小一个数量级。
6. **旧权限 API 最终删除（修订）。** 本轮明确不保留兼容层：`DccAuthorities`、
   `role-authorities`、`roles-passthrough` 全部删除；业务注解只使用 `@perms`。
7. **`DCC_USER` / `DCC_ADMIN` 最终删除（修订）。** 两者不再出现在默认 bundle 中，旧 token
   携带它们也不会获得任何权限。
8. **`/api/tenants/{code}` 放宽为"平台 or 本租户"。** 初稿要求 `tenant.admit`，会让租户管理员
   读不到自己租户的名称/描述，前端会缺数据。
9. **MCP 的 `@PreAuthorize` 先验证再依赖。** MCP 工具由 Spring AI 反射调用，是否经过 AOP
   代理未验证。计划先写测试确认拦截生效，不生效则退化为显式 `McpAuthorizer.require(perm)`，
   而不是赌 AOP。
10. **审批者不给 `audit.business`。** 审批者通过对象 `/history` 已能看变更谱系；全量审计（含
   他人 before/after 镜像）留在租户管理员。

## 落地记录（实现阶段与初稿的差异）

落地时又修正了四处，均已体现在代码与本文档矩阵中：

1. **平台审计挂在 `/api/platform/audit`**，而不是 `/api/audit/platform`：后者会被
   `/api/audit/{entityType}/{entityId}` 的路由模式干扰，分开挂载后路由语义无歧义。
2. **`role-bundles` 的条目可以是另一个角色**（IdP 组 → 角色的组合），递归展开并带环检测；
   否则「IdP 组名 → DCC_APPROVER」这种最常见的配置无法表达。
3. **`/api/directory/my-memberships` 改为 `isAuthenticated()`**，不用 `directory.read`：
   它只返回调用者自己的成员关系，给读者角色保留自助查询能力不构成信息泄露。
4. **四眼同时作用于「批准迁移单」与「产物流转到 PUBLISHED」**（提交→待审、发布→废弃仍可
   由单人完成，因为它们是同一侧的动作）。
5. 另新增一个**矩阵守卫测试**（`EndpointAuthorizationMatrixTest`）：反射扫描 `dcc-web` 的
   所有 handler，要求每个端点都声明权限点，且写端点不得只要求 `isAuthenticated()`——
   这正是本 ADR 要永久防止的缺陷类型。

## Consequences

- 端点从"判 3 个角色"变成"判 21 个权限点"，新增角色只改配置，不改控制器。
- 平台与租户的破坏半径分离：系统管理员看不到任何业务数据，租户管理员出不了自己的租户。
- 升级必须配套"角色映射表 + 存量重授权"；旧角色不会自动迁移，也不会再获得任何权限。
- 生产环境至少需要两个自然人持有审批权，否则四眼会导致死锁（部署前置条件）。
- 端点数 × 表面数（web/cli/mcp）的判定面显著增加，必须用参数化 allow/deny 矩阵测试兜底，
  否则下一次新增端点会重新引入第 1 类缺陷。

## 后续增量（已交付）

0. **按目标环境的审批权（第二轮遗留项 1，已解决）**：新增 `ResourceScopeResolver`
   （dcc-application：从库里读出资源所在的环境/应用及其 code）与 `@scopeGuard`
   （dcc-web：唯一的判定点）。审批类端点不再"只判函数级权限"，而是判"**这个资源**上是否
   有该权限"：

   | 端点 | 判定 |
   | --- | --- |
   | `POST /api/{standards,tables,interfaces,templates}/{id}/status` | `@scopeGuard.mayTransition(kind, #id, #target)` |
   | `POST /api/migrations/{id}/approve`、`/reject` | `@scopeGuard.mayApproveMigration(#id)` |
   | `POST /api/migrations/{id}/execute`、`/rollback` | `@scopeGuard.mayExecuteMigration(#id)` |

   判定规则：调用者持有该权限点（租户全域，行为不变）**或**持有作用于该资源真实表面的
   环境/应用范围授权。表面取自数据库而非请求 —— 伪造 `X-DCC-Scope` 或传别人的 id 都不会激活
   范围授权；资源不属于本租户时按"无范围授权"处理。

   附带修正了一处语义不一致：`/status?target=PENDING_REVIEW`（提交待审）是维护者的动作
   （`artifact.write`），其余目标（发布/退回/废弃）才是审批者的动作（`artifact.transition`）。
   此前该端点一律要求审批权，维护者无法自己提交待审。

   回归：`ScopedApprovalIntegrationTest`（DEV/UAT 双环境 + 三种授权形态：仅 DEV 范围、仅 UAT
   范围、租户全域；含"迁移审批按**目标**环境判定"与"范围授权不绕过四眼"）。

1. **案例采纳反向索引**（原遗留项 1，已解决）：新增平台级表 `reference_case_adoption`（显式
   `tenant_id`、不走租户过滤器，与 `role_assignment` 同款），记录 `case_id / case_code /
   case_version / artifact_type / artifact_id / artifact_code / adopted_by`。两个读口分开授权：
   - `GET /api/case-adoptions`（`case.read`，租户侧）：我的副本，`stale` 标记案例已升级。
   - `GET /api/platform/case-adoptions/{caseId}`（`case.write`，平台侧）：哪些租户采纳了哪个版本，
     **不含**租户产物标识——平台看"影响面"，不看租户内容。
   采纳仍然只写草稿、不自动同步，`stale` 只是提示。
2. **创建时的变更原因不再丢失**：四个产物的 `create` 之前把版本消息硬编码为 `initial version`，
   忽略 `X-DCC-Change-Reason`；现在与 `update`/`rollback` 一致走
   `changeContext.messageOr(() -> "initial version")`。这也是案例溯源能落在不可变版本消息里的前提。
3. **端到端测试**：`RoleModelIntegrationTest`（/api/me、平台/租户权限分层、租户强制、四眼
   自批 409 与换人 200）与 `ReferenceCaseIntegrationTest`（采纳生成草稿、溯源、`stale`、
   两个受众看到不同字段）。

## 评审修正（第二轮：独立评审后的加固）

第一轮落地后做了一次独立安全评审，修掉 5 个问题，并记录 3 处有意为之的取舍：

| 编号 | 问题 | 处理 |
|------|------|------|
| B1 | `/api/directory/memberships`、`/role-assignments` 的**跨租户搜索**只要求 `directory.read`，而该权限点属于租户管理员 → 租户管理员可翻遍所有租户的成员与授权 | 两个搜索端点改要求 `directory.write.platform`（平台专属）；租户侧只能走 `/api/memberships`、`/api/role-assignments`（租户强制）。回归测试已加 |
| S1 | 产物的 `environmentId`/`applicationId` 来自请求，实体与租户无外键约束 → 可以写入"指向别人租户环境"的对象；案例采纳同样继承该缺陷 | 新增 `ScopeValidation`，四个 `create` 在写入前校验环境/应用属于本租户；报"unknown"而非"forbidden"，避免变成存在性探针 |
| S4 | `DCC_PLATFORM_ADMIN` 缺 `case.read` → 能写案例却不能列案例 | 平台权限包补上 `case.read` |
| S5 | `role-bundles` 里写错一个权限点会静默不授权 | 启动时校验每个 bundle 条目（要么是已知角色、要么是已声明权限点），拼错即 `IllegalStateException` |
| S7 | `McpAuthorizationFilter` 在拿不到认证主体时放行 | 改为拒绝（fail-closed），不再依赖过滤器顺序 |

有意保留的取舍（已在代码注释与本节说明）：

- **`submitted_by` 不回填**：升级时已在 `PENDING_APPROVAL` 的历史迁移单没有提交人记录，四眼对它无法生效。
  重新提交一次即补齐；新单不受影响。迁移脚本已注明。
- **四眼在"无绑定调用者"时放行**：系统作业/种子数据没有第二个自然人可比。真实请求一定经过
  `ChangeContextFilter`（dcc-web / dcc-mcp 均已注册），因此这条只对程序化流程生效。
- **`role-bundles` 是"配置或取默认"**：任何显式 `role-bundles` 块都会整体替换出厂默认，
  避免 Spring Boot 的 Map 合并语义留下未预期的角色。

## 遗留项（不在本 ADR 内解决）

1. 租户内可委派的"环境管理员"（租户管理员的降维版本）。
2. 数据库驱动的 role/permission/role_permission 解析器（`RolePermissionResolver` 已有接缝）。
3. 平台级紧急通道（break-glass）与特权授予双人确认。
4. 删除/恢复（`artifact.delete` / `artifact.restore`）仍按租户全域判定，未接入 `@scopeGuard`。

## 第三轮增量（原遗留项全部收敛）

第三轮把第二轮列出的 4 项遗留（删除/恢复的范围判定、环境管理员、break-glass + 双人确认、
数据库驱动解析器）一并做完，其中"恢复仍租户全域"是按设计保留：

1. **编辑 / 版本回滚 / 删除接入 `@scopeGuard`**：`PUT /{id}`、`POST /{id}/rollback` 判
   `artifact.write`，`DELETE /{id}` 判 `artifact.delete`，都可以由"作用于该对象所在环境"的
   范围授权满足。**恢复（restore）刻意保持租户全域**：行已被逻辑删除，表面查询看不到它，
   而且"把删掉的东西复活"是保留期动作，属租户管理员，且每一步都进审计。
2. **环境管理员 `DCC_ENV_ADMIN`**：数据面的一半（写/删/提交/执行迁移/产物打包 + 只读），
   不含审批、不含 `topology.write`、不含目录/审计/案例。新增
   `dcc.security.scope-required-roles: [DCC_ENV_ADMIN]`——该角色**必须**按环境或应用授予，
   租户全域授予直接 422（否则等于悄悄发了一整片数据面）。
3. **break-glass 紧急通道**：`dcc.security.break-glass.subjects` + `.permissions` +
   `.reason-header`（默认 `X-DCC-Change-Reason`）。三个约束：默认关闭（subjects 为空）；
   必须有非空理由，且理由会随 `ChangeContext` 进入不可变审计 trail；**永远不绕过目录成员闸门**
   ——它扩大"已在租户内的成员"的能力，不会把外部人放进租户。每次使用打 `BREAK-GLASS` WARN。
4. **特权授予双人确认**：`dcc.security.dual-control-roles`（默认 `DCC_PLATFORM_ADMIN`、
   `DCC_TENANT_ADMIN`）。命中该列表的授权行立即写入并审计，但**保持待确认**——
   目录策略忽略待确认行，所以它此刻不赋予任何权限；必须由**不同主体**通过
   `POST /api/directory/role-assignments/{id}/confirm`（或租户侧 `/api/role-assignments/{id}/confirm`）
   确认后才生效。授予人自己确认 → 409。`confirmation_required` 落在行上，因此策略无需读配置。
   **部署前置条件**：至少两个平台管理员（否则无法引导出任何租户管理员）。
5. **数据库驱动解析器**：`dcc.security.resolver-mode: config|database`（默认 `config`，行为不变）。
   `database` 时由平台级表 `role_permission(role, permission)` 提供映射，
   `ConfigRolePermissionResolver` 不再注册——表成为唯一权威，未映射的角色一个权限都不给
   （未映射的角色名一个权限都不给，这正是"闭合词表"的部署想要的）。表的改动同样进审计与保留期。

回归测试：`ScopedApprovalIntegrationTest`（7 个，含范围写/删与环境管理员边界）、
`BearerJwtAuthenticationFilterTest`（break-glass 三条）、`DualControlIntegrationTest`（2 个）、
`DatabaseRoleResolverIntegrationTest`（2 个）。

## 第四轮：评审修正（第三轮落地后的加固）

第三轮后做了第二轮独立安全评审，修掉 4 个阻断级 + 4 个应修问题：

| 编号 | 问题 | 处理 |
|------|------|------|
| B1 | 旧 `DCC_ADMIN` 曾绕过双人确认 | 该角色与整套 legacy 兼容层已删除；双人确认只覆盖 `DCC_PLATFORM_ADMIN` / `DCC_TENANT_ADMIN` |
| B2 | 双人确认是**控制器传参**决定的（`create(command, boolean)`），任何新表面（CLI/MCP/种子）走单参重载都会铸出特权授权 | 判定下沉到 `RoleAssignmentService`：它自己读 `dcc.security.dual-control-roles`，单参重载删除；双人确认成为"授予"这条用例的不变式而非端点选择 |
| B3 | 平台授权路径没有角色白名单，曾允许任意字符串直接成为 authority | `GrantPolicy.requirePlatformGrant` 现在要求角色名必须定义在 `role-bundles` 中（`resolver-mode=database` 时不检查：表才是权威，未映射名不赋权） |
| B4 | `role-bundles` 的默认值是预填 Map，而 Spring Boot 的 Map 绑定是**合并**语义 → 部署显式配置 `role-bundles` 后 legacy 帽子仍然存活，"database 模式无 legacy 帽子"的承诺不成立 | 改为**配置或取默认**：字段默认空，`effectiveRoleBundles()` 在空时回退到出厂默认；因此任何配置块整体替换默认（并有单测锁定） |
| S1 | break-glass 权限名拼错会静默不赋权，只有出事时才发现 | `DccPermissions` 构造时校验，未知名称直接 `IllegalStateException`；启用时打一行"break-glass 已开启"的 WARN |
| S2 | 四眼在"提交人未知"时静默放行 | 改为打 WARN 说明原因（两类：无绑定调用者、提交人未知），不再无声 |
| S3 | `McpAuthorizationFilter` 硬编码 `/mcp`，改了 `spring.ai.mcp.server.mcp-endpoint` 就能绕过基线闸门；且 `/mcpanything` 会误命中 | 端点改为可配置项注入，并加路径边界（`equals` 或 `startsWith(endpoint + "/")`） |
| S4 | dcc-web 与 dcc-mcp 各有一个同名 `rolePermissionResolver` bean，未来若两者同处一个上下文会冲突 | 两处都加 `@ConditionalOnMissingBean(RolePermissionResolver.class)` |

**明确保留（非缺陷）**：break-glass 的只读使用（如 `GET /api/audit`）不产生审计行——审计 trail 由实体变更写入；
只读越权走的是 `BREAK-GLASS` WARN 日志与网关访问日志。写变更则一定带 actor + 理由进审计。

## 遗留项

无。后续若还要收紧，方向是"按产物类型的角色"与"scoped grant 过期时间"（ADR 0004 的 future work），
不属于本 ADR 范围。
5. 平台级紧急通道（break-glass）与特权授予双人确认。
