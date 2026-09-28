# Local Setup

## Prerequisites

- Java 17+
- Docker and Docker Compose
- Git

Use the Maven Wrapper (`./mvnw`) so the build does not depend on a globally installed Maven version.

## Build and Test

```bash
./mvnw clean verify
```

The `dcc-web` module loads a full Spring context in its `contextLoads` smoke test, which needs a running PostgreSQL instance and the `dev` profile (the datasource URL lives in `application-dev.yml`). Without it the test fails with `Failed to determine a suitable driver class`. Start the infrastructure and activate the profile first:

```bash
./scripts/dcc-start.sh                  # generates env/dev.env and starts PostgreSQL
set -a; source env/dev.env; set +a      # export generated credentials
./mvnw test -Dspring.profiles.active=dev
```

Alternatively, instead of exporting environment variables, keep the machine-specific value in a git-ignored `dcc-web/src/main/resources/application-local.yml` and activate both profiles:

```bash
./mvnw test -Dspring.profiles.active=dev,local
```

That is the layout IDEs expect: set the active profiles to `dev,local`, then run the application or the tests — no environment variables to wire up. The file is listed in `.gitignore`; keep its `spring.datasource.password` in sync with `env/dev.env`.

`dcc-core`, `dcc-application`, `dcc-bootstrap` and `dcc-security` are library modules; `dcc-core` ships no tests and the others are unit-tested without infrastructure.

The runnable modules (`dcc-web`, `dcc-mcp`) each load a full Spring context in their `contextLoads` smoke test, which needs the same PostgreSQL instance and `dev` profile as above.

## Infrastructure

Copy `env/.env.example` to a local environment file as described by `scripts/dcc-start.sh`, then start the required services:

```bash
docker compose up -d
```

Do not commit the local environment file or real credentials. For more deployment details, see [DEVOPS.md](DEVOPS.md).
