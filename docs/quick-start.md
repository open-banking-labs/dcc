# Quick start

Get DCC running locally in about 30 minutes.

## Prerequisites

- Java 17 (`java -version`)
- Docker + Docker Compose
- Git

Use the Maven Wrapper (`./mvnw`) — no global Maven install needed.

## 1. Start dependencies

Only PostgreSQL and Redis are needed to develop; the application runs on the host.

```bash
docker compose -f docker-compose.dev.yml up -d
docker compose -f docker-compose.dev.yml ps     # wait for "healthy"
```

This starts PostgreSQL on `localhost:5432` (db `dcc_dev`, user `dcc_user`) and Redis
on `localhost:6379`, both with the dev-only password `dcc_dev_password`.

## 2. Provide the datasource for the `dev` profile

The `dev` profile reads the datasource from environment variables (see
`env/.env.example`). Either export them, or keep a git-ignored
`dcc-web/src/main/resources/application-local.yml`:

```bash
set -a; source env/.env.example; set +a   # adjust values to the dev compose above
```

```yaml
# application-local.yml (git-ignored)
spring:
  datasource:
    password: dcc_dev_password
```

## 3. Run the application

```bash
# schema is owned by dcc-web (runs Flyway on startup)
./mvnw -pl dcc-web -am spring-boot:run -Dspring-boot.run.profiles=dev,local
```

Then:

- API base: `http://localhost:8080/api`
- Health: `http://localhost:8080/actuator/health`
- OpenAPI UI: `http://localhost:8080/swagger-ui.html`

Requests need a bearer token. For local use, mint an HS256 JWT signed with
`DCC_JWT_SECRET` (see `dcc-security/src/test/.../BearerJwtAuthenticationFilterTest`
for a signing example) carrying `tenant` and `roles` claims.

## 4. Run the MCP server (optional)

```bash
./mvnw -pl dcc-mcp -am spring-boot:run -Dspring-boot.run.profiles=dev,local
```

Serves MCP on `http://localhost:8081/mcp`.

## 5. Build and test

```bash
./mvnw -pl dcc-generator,dcc-security -am test   # fast, no infrastructure needed
./mvnw clean verify                              # full build (integration tests need PostgreSQL)
```

## Troubleshooting

- `Failed to determine a suitable driver class` — the datasource env/profile isn't
  active; re-check step 2.
- Port already in use — change `POSTGRES_HOST_PORT`/`REDIS_HOST_PORT` or stop the
  conflicting service.

## Next steps

- [Concepts](concepts.md) — the domain model.
- [Configuration](configuration.md) — every `dcc.*` property.
- [Templating](templating.md) — customise generated code/SQL and MCP prompts.
- [API reference](api.md) and [CLI](cli.md).
