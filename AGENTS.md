# AGENTS.md

## 项目概览

DCC（Data Contract Center）是一个基于 Java 17 和 Spring Boot 4.1.1 的多模块 Maven 项目，用于管理数据模型元数据并生成相关代码和 Flyway SQL。

仓库根目录是 Maven 聚合项目，当前模块职责如下：

- `dcc-core/`：核心领域与持久化能力，包含 JPA、PostgreSQL 相关代码。
- `dcc-application/`：主要业务逻辑（应用/用例层），与协议无关（不依赖 HTTP/RPC 等传输类型），便于被多种暴露层复用。
- `dcc-bootstrap/`：共享运行时装配（Flyway 启动集成、warm-up 框架、基础设施约定），被各可执行应用复用。
- `dcc-security/`：共享安全（校验调用方 JWT、租户上下文、方法鉴权、操作级限流），被各暴露层复用。
- `dcc-web/`：REST 暴露层 + 可执行应用，调用 `dcc-application`，包含 HTTP/OpenAPI、Actuator、Flyway 配置与环境配置。
- `dcc-mcp/`：MCP 暴露层 + 可执行应用（Streamable HTTP），复用 `dcc-application`。
- 边缘网关（`docker-compose.yml` 中的 Traefik 服务）：唯一对外入口，路由 `/api`、`/mcp` 并做粗粒度限流。
- `docs/`：开发、运维及 Flyway 文档。
- `scripts/`：启动和部署脚本。
- `init-scripts/`、`rabbitmq-definitions/`：基础设施初始化文件和说明。
- `env/`：环境变量示例；不要提交真实凭据。

## 开发环境

- Java 17
- Maven Wrapper：`./mvnw`（优先使用，不依赖本机 Maven 版本）
- Spring Boot 4.1.1
- PostgreSQL、Redis、RabbitMQ 等外部服务按 `docker-compose.yml` 和 `env/.env.example` 配置。

## 常用命令

在仓库根目录执行：

```bash
# 编译全部模块
./mvnw clean compile

# 运行全部测试
./mvnw test

# 打包（跳过测试仅用于快速构建，不替代正常验证）
./mvnw clean package

# 运行指定模块测试
./mvnw -pl dcc-core test
./mvnw -pl dcc-application test
./mvnw -pl dcc-web test

# 启动基础设施（按需执行）
docker compose up -d
```

修改模块间依赖或公共配置后，优先运行根项目的完整测试：
`./mvnw clean verify`。

## 代码约定

- 遵循现有 Spring Boot、Maven 和 Java 17 风格，避免无关重构。
- 包名使用现有的 `cn.org.openbanking...` 命名空间。
- 新增功能应放在职责对应的模块中，不要把业务逻辑塞入 `dcc-web` / `dcc-mcp` 暴露层模块。
- 优先复用现有配置、组件和测试模式；使用 Lombok 时保持与相邻代码一致。
- 配置按环境放在 `application.yml`、`application-dev.yml`、`application-prod.yml` 等文件中。不要把密码、Token、密钥或本地地址凭据写入源码或提交到仓库。
- 修改 API 时同步更新相关测试和文档；保持向后兼容，除非任务明确要求破坏性变更。

## 数据库与 Flyway

- 数据库结构变更必须通过 Flyway 迁移完成，并遵守 `docs/flyway/script-naming-convention.md`。
- 不要直接修改已经发布或执行过的迁移脚本；需要新增迁移来修正问题。
- 涉及回滚或生产操作时，先阅读 `docs/flyway/rollback-operations-manual.md`。
- 迁移应在本地测试环境验证，并检查初始化顺序、索引和兼容性。

## 测试与验证

- 为新增或修改的行为补充单元测试或 Spring 集成测试，测试放在对应模块的 `src/test/java` 下。
- 提交前至少运行受影响模块的测试；跨模块改动运行 `./mvnw clean verify`。
- `dcc-web` / `dcc-mcp` 的上下文冒烟测试需要运行中的 PostgreSQL 和 `dev` profile（数据源配置在 `application-dev.yml`）；未准备时它会因缺少数据源而失败，先执行 `./scripts/dcc-start.sh` 并激活 profile。`dcc-core`、`dcc-application`、`dcc-bootstrap`、`dcc-security` 为库模块；`dcc-security` 有单元测试（无需基础设施）。
- 测试失败时优先确认 Java/Maven 版本、外部服务和环境变量是否正确，再判断是否为代码回归。
- 不要为了让测试通过而删除、跳过或弱化已有断言。

## Git 与提交

- 保持变更聚焦，不要提交构建产物、IDE 文件、日志或本地环境文件。
- 提交前检查 `git diff` 和 `git status`，确认没有误改用户已有内容。
- 提交信息应简洁描述行为变化；如项目已有约定，遵循现有格式。

## 修改完成检查清单

- [ ] 代码放在正确模块，未引入无关重构
- [ ] 已补充或更新相关测试
- [ ] 已运行受影响测试；必要时运行完整 `verify`
- [ ] Flyway 变更遵循命名和版本规则
- [ ] 未提交真实凭据、生成文件或本地配置
- [ ] 已检查 `git diff`、`git status` 及文档影响
