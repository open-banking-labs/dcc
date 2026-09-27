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

The `dcc-starter` module loads a full Spring context in its `contextLoads` smoke test, which needs a running PostgreSQL instance and the `dev` profile (the datasource URL lives in `application-dev.yml`). Without it the test fails with `Failed to determine a suitable driver class`. Start the infrastructure and activate the profile first:

```bash
./scripts/dcc-start.sh                  # generates env/dev.env and starts PostgreSQL
set -a; source env/dev.env; set +a      # export generated credentials
./mvnw test -Dspring.profiles.active=dev
```

`dcc-core` and `dcc-api` are library modules and currently ship no tests, so they do not need infrastructure.

## Infrastructure

Copy `env/.env.example` to a local environment file as described by `scripts/dcc-start.sh`, then start the required services:

```bash
docker compose up -d
```

Do not commit the local environment file or real credentials. For more deployment details, see [DEVOPS.md](DEVOPS.md).
