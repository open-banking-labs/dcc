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

## Infrastructure

Copy `env/.env.example` to a local environment file as described by `scripts/dcc-start.sh`, then start the required services:

```bash
docker compose up -d
```

Do not commit the local environment file or real credentials. For more deployment details, see [DEVOPS.md](DEVOPS.md).
