
# DCC - DevOps Guide

DevOps documentation for DCC project - Docker Compose setup, multi-environment deployment, and configuration management.

---

## 📋 Table of Contents

- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Local Development](#local-development)
- [Multi-Environment Deployment](#multi-environment-deployment)
- [Project Structure](#project-structure)
- [Application Docker Deployment](#application-docker-deployment)
- [Docker Compose Services](#docker-compose-services)
- [Configuration Management](#configuration-management)
- [Common Commands](#common-commands)
- [Troubleshooting](#troubleshooting)

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                       DCC Project                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │  dcc-starter │  │   dcc-api    │  │  dcc-core    │    │
│  │  (Spring Boot)│  │   (REST API) │  │   (Core)    │    │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘    │
│         │                  │                  │            │
│         └──────────────────┼──────────────────┘            │
│                            │                               │
│                     ┌──────▼───────┐                      │
│                     │  Docker      │                      │
│                     │  Compose     │                      │
│                     └──────┬───────┘                      │
│                            │                               │
│         ┌──────────────────┼──────────────────┐           │
│         │                  │                  │           │
│  ┌──────▼──────┐  ┌───────▼──────┐  ┌───────▼──────┐   │
│  │ PostgreSQL  │  │    Redis     │  │  RabbitMQ    │   │
│  │   (DB)      │  │   (Cache)    │  │   (MQ)       │   │
│  └─────────────┘  └──────────────┘  └──────────────┘   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Module Dependencies

```
dcc-core          # Foundation: persistence and shared primitives
    ↑
    │
dcc-api           # Main business logic (protocol-agnostic)
    ↑
    ├── dcc-starter   # REST exposure layer + executable application (web UI)
    └── dcc-mcp       # MCP exposure layer + executable application (Streamable HTTP)
```

The dependencies are wired: `dcc-starter → dcc-api → dcc-core` and
`dcc-mcp → dcc-api → dcc-core`. Business logic lives in `dcc-api` and stays free
of transport concerns, so each exposure layer (REST in `dcc-starter`, MCP in
`dcc-mcp`) reuses it unchanged by depending on `dcc-api`.

---

## Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Docker | 20.10+ | Container runtime |
| Docker Compose | 2.0+ | Multi-container orchestration |
| Java | 17+ | Spring Boot runtime |
| Maven | 3.6+ | Build tool |
| Git | 2.0+ | Version control |

### Verify Installation

```bash
docker --version
docker-compose --version
java -version
mvn --version
git --version
```

---

## Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/your-org/dcc.git
cd dcc
```

### 2. Initialize Configuration

The first time you run the project, it will automatically generate a local configuration file from the template:

```bash
./scripts/dcc-start.sh
```

This will:
- Create `env/dev.env` from `env/.env.example`
- Generate random passwords for development
- Start PostgreSQL container

### 3. Start Development Environment

**Only Database (Default)**:
```bash
./scripts/dcc-start.sh
```

**Database + Redis**:
```bash
./scripts/dcc-start.sh --redis
```

**Database + Redis + RabbitMQ**:
```bash
./scripts/dcc-start.sh --redis --rabbitmq
```

**All Services**:
```bash
./scripts/dcc-start.sh --full
```

### 4. Run Spring Boot Application

**Option 1: Using IDE**
- Open project in IDE
- Run `DccApplication.java` main class
- Set active profile: `dev`

**Option 2: Using Maven**
```bash
# Build the project
./mvnw clean package -pl dcc-starter

# Run the application
./mvnw spring-boot:run -pl dcc-starter -Dspring-boot.run.profiles=dev
```

**Option 3: Using JAR file**
```bash
java -jar dcc-starter/target/dcc-starter-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

**Option 4: Using Docker** (builds the image and runs it alongside PostgreSQL)
```bash
./scripts/dcc-deploy.sh dev --app
```

### 5. Verify Services

```bash
# Check Docker services
docker-compose ps

# Check Spring Boot application
curl http://localhost:8080/actuator/health
```

---

## Local Development

### Development Configuration

**Edit `env/dev.env`**:
```bash
vim env/dev.env
```

Key configuration items:
```bash
POSTGRES_PASSWORD=your_password      # Auto-generated; set a value to override
REDIS_PASSWORD=your_redis_password   # If using Redis
RABBITMQ_PASSWORD=your_rabbit_pass   # If using RabbitMQ
MINIO_ROOT_PASSWORD=your_minio_pass  # If using MinIO
```

> Leave a password empty and `dcc-start.sh` fills it with a random value on
> first run. `env/dev.env` is git-ignored, so generated values stay local.

### Service Access URLs

Passwords are randomly generated per machine — read the current values from
`env/dev.env`.

| Service | URL | Credentials |
|---------|-----|-------------|
| PostgreSQL | `localhost:5432` | `POSTGRES_USER` / `POSTGRES_PASSWORD` |
| Redis | `localhost:6379` | Password: `REDIS_PASSWORD` |
| RabbitMQ Management | `http://localhost:15672` | `RABBITMQ_USER` / `RABBITMQ_PASSWORD` |
| MinIO Console | `http://localhost:9090` | `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` |
| Adminer (DB Management) | `http://localhost:8081` | Server: `postgres` |

### Spring Boot Application Profiles

| Profile | Description | Config File |
|---------|-------------|-------------|
| `dev` | Local development | `application-dev.yml` |
| `prod` | Production | `application-prod.yml` |

### Debug Mode

```bash
# Enable remote debugging (port 5005)
./mvnw spring-boot:run -pl dcc-starter -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

---

## Multi-Environment Deployment

### Environment Matrix

| Environment | Code | Database Port | Description | Usage |
|-------------|------|--------------|-------------|-------|
| Development | `dev` | 5432 | Local development | Developers |
| Production | `prod` | 5437 | Live environment | End Users |

### Deploy to Production

```bash
POSTGRES_PASSWORD=prod_super_secure_123 \
REDIS_PASSWORD=prod_redis_456 \
RABBITMQ_PASSWORD=prod_rabbit_789 \
MINIO_ROOT_PASSWORD=prod_minio_101 \
./scripts/dcc-deploy.sh prod --full
```

**⚠️ Production Deployment Security:**
- All passwords must be strong (12+ characters with special chars)
- The script will ask for confirmation before deployment
- Passwords are not stored in Git
- Audit logs are automatically generated

### Deployment Process Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    Deployment Flow                          │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. Developer commits code                                 │
│     git push origin main                                   │
│                         │                                   │
│                         ▼                                   │
│  2. CI/CD Pipeline runs                                    │
│     - Build JAR                                           │
│     - Run unit tests                                       │
│     - Run integration tests                                │
│                         │                                   │
│                         ▼                                   │
│  3. Deploy to PROD (Manual approval + confirmation)        │
│    POSTGRES_PASSWORD=xxx ./scripts/dcc-deploy.sh prod --full│
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## Project Structure

```
dcc/
├── docker-compose.yml                  # Docker orchestration
├── .gitignore                          # Git ignore rules
├── docs/
│   ├── DEVOPS.md                       # This file
│   └── flyway/                         # Migration conventions and recovery
│       ├── script-naming-convention.md
│       └── rollback-operations-manual.md
├── pom.xml                             # Parent Maven POM
│
├── init-scripts/                       # Postgres bootstrap SQL (first start only)
├── rabbitmq-definitions/               # RabbitMQ definitions.json (see README)
│
├── env/                                # Environment configurations
│   ├── .env.example                    # Configuration template (committed)
│   ├── dev.env                         # Local development (ignored)
│   └── prod.env                        # Production (ignored)
│
├── scripts/                            # Shell scripts
│   ├── dcc-start.sh                    # Local development starter
│   └── dcc-deploy.sh                   # Multi-environment deployment
│
├── dcc-starter/                        # Spring Boot starter module
│   ├── pom.xml
│   └── src/
│       └── main/
│           ├── java/
│           │   └── cn/org/openbanking/dcc/
│           │       ├── DccApplication.java
│           │       └── flyway/
│           │           ├── FlywayConfig.java
│           │           └── FlywayCallbackHandler.java
│           └── resources/
│               ├── application.yml
│               ├── application-dev.yml
│               ├── application-prod.yml
│               └── db/migration/
│                   ├── ddl/
│                   ├── dml/
│                   ├── function/
│                   └── index/
│
├── dcc-mcp/                            # MCP exposure module (Streamable HTTP)
│   ├── pom.xml
│   └── src/
│
├── dcc-core/                           # Core utilities module
│   ├── pom.xml
│   └── src/
│
└── dcc-api/                            # REST API module
    ├── pom.xml
    └── src/
```

---

## Application Docker Deployment

The runnable service (`dcc-starter`) is built from the repository-root
`Dockerfile` and runs as the `dcc-app` compose service. It sits behind the `app`
profile, so the default `up -d` still starts PostgreSQL only.

`dcc-mcp` is a second runnable application served by the same `Dockerfile` (the
`MODULE` / `SERVER_PORT` build args select it, so it ships on `8081` with MCP
endpoint `/mcp`). It runs as the `dcc-mcp` compose service, reuses the same
database and never runs Flyway - it depends on `dcc-app` being healthy so the
schema is already migrated. Deploy it with `--mcp` (builds and starts both apps).

### Build and run

```bash
# Start PostgreSQL + the application (builds the image first)
./scripts/dcc-deploy.sh dev --app

# Also build and start the MCP layer (implies --app)
./scripts/dcc-deploy.sh dev --mcp

# Or drive Compose directly
docker compose --env-file env/dev.env --profile app up -d --build
```

The app listens on `http://localhost:8080` (`/actuator/health` backs the
container health check). The MCP layer listens on `http://localhost:8082/mcp`
(container port `8081`). Inside the compose network they reach PostgreSQL as
`postgres:5432` - not the host-mapped port.

### Configuration

The container activates the Spring profile named by `ENV` (`dev` or `prod`) and
gets its datasource coordinates from the service's `environment:` block.
`.dockerignore` excludes `env/` and `application-local.yml`, so no secrets or
local overrides are baked into the image - supply them as container environment
variables instead.

### Rebuild after a code change

```bash
docker compose --env-file env/dev.env --profile app build dcc-app
docker compose --env-file env/dev.env --profile app up -d
```

## Docker Compose Services

### Service Overview

| Service | Image | Port (External) | Profiles | Required |
|---------|-------|-----------------|----------|----------|
| PostgreSQL | `postgres:18-alpine` | 5432 | `default` | **Yes** |
| dcc-app | built from `Dockerfile` (`MODULE=dcc-starter`) | 8080 | `app` | Optional |
| dcc-mcp | built from `Dockerfile` (`MODULE=dcc-mcp`) | 8082 | `mcp` | Optional |
| Redis | `redis:7-alpine` | 6379 | `redis` | Optional |
| RabbitMQ | `rabbitmq:4-management-alpine` | 5672, 15672 | `rabbitmq` | Optional |
| MinIO | `minio/minio:latest` | 9000, 9090 | `minio` | Optional |
| Adminer | `adminer:latest` | 8081 | `adminer` | Optional |

### Docker Compose Commands

```bash
# Start with default profile (PostgreSQL only)
docker-compose up -d

# Start with Redis
docker-compose --profile redis up -d

# Start with multiple profiles
docker-compose --profile redis --profile rabbitmq up -d

# Start all services
docker-compose --profile full up -d

# View running services
docker-compose ps

# View logs
docker-compose logs -f [service_name]

# Stop all services
docker-compose down

# Stop and remove volumes (clean data)
docker-compose down -v
```

---

## Configuration Management

### File Types

| File | Git | Purpose |
|------|-----|---------|
| `env/.env.example` | ✅ Committed | Configuration template with placeholders |
| `env/dev.env` | ❌ Ignored | Local development (generated from template) |
| `env/prod.env` | ❌ Ignored | Production (generated by deploy script) |

### Environment Variables Reference

> **Naming convention.** Every variable is `SCREAMING_SNAKE_CASE` and prefixed
> with the service it configures: `POSTGRES_*`, `REDIS_*`, `RABBITMQ_*`,
> `MINIO_*`, `ADMINER_*`. Ports use `<SERVICE>_HOST_PORT`, or
> `<SERVICE>_<ROLE>_HOST_PORT` when a service exposes more than one
> (RabbitMQ AMQP/management, MinIO API/console). Passwords use
> `<SERVICE>_PASSWORD` — MinIO is the sole exception, where
> `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` are MinIO's own container
> contract and are passed through verbatim.
>
> Leave every password **empty** in `env/.env.example`. `dcc-start.sh` fills
> dev values with random ones, and `dcc-deploy.sh` requires them for prod.

#### Core Variables

| Variable | Default | Description | Required |
|----------|---------|-------------|----------|
| `ENV` | `dev` | Environment name | Yes |
| `PROJECT_NAME` | `dcc` | Project identifier | Yes |
| `TZ` | `Asia/Shanghai` | Timezone | Yes |
| `RESTART_POLICY` | `unless-stopped` | Docker restart policy | Yes |

#### PostgreSQL Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_VERSION` | `18-alpine` | PostgreSQL image version |
| `POSTGRES_HOST` | `localhost` | Host used to build the JDBC URL |
| `POSTGRES_HOST_PORT` | `5432` | External port mapping |
| `POSTGRES_DB` | `dcc_dev` | Database name |
| `POSTGRES_USER` | `dcc_user` | Database user |
| `POSTGRES_PASSWORD` | *(empty)* | Password — generated (dev) / required (prod) |
| `POSTGRES_MEMORY_LIMIT` | `512M` | Container memory limit |
| `POSTGRES_MEMORY_RESERVATION` | `256M` | Container memory reservation |

#### Redis Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `REDIS_VERSION` | `7-alpine` | Redis image version |
| `REDIS_HOST_PORT` | `6379` | External port |
| `REDIS_PASSWORD` | *(empty)* | Redis password |
| `REDIS_MAX_MEMORY` | `256mb` | Max memory usage |
| `REDIS_MEMORY_LIMIT` | `256M` | Container memory limit |

#### RabbitMQ Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `RABBITMQ_VERSION` | `4-management-alpine` | RabbitMQ image version |
| `RABBITMQ_AMQP_HOST_PORT` | `5672` | AMQP port |
| `RABBITMQ_MANAGEMENT_HOST_PORT` | `15672` | Management UI port |
| `RABBITMQ_USER` | `admin` | Admin username |
| `RABBITMQ_PASSWORD` | *(empty)* | Admin password |
| `RABBITMQ_VHOST` | `/dcc` | Virtual host |
| `RABBITMQ_MEMORY_LIMIT` | `512M` | Container memory limit |

#### MinIO Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `MINIO_VERSION` | `latest` | MinIO image version |
| `MINIO_API_HOST_PORT` | `9000` | API port |
| `MINIO_CONSOLE_HOST_PORT` | `9090` | Console port |
| `MINIO_ROOT_USER` | `minioadmin` | Root username |
| `MINIO_ROOT_PASSWORD` | *(empty)* | Root password |
| `MINIO_CONSOLE_URL` | `http://localhost:9090` | URL MinIO redirects the browser to |
| `MINIO_MEMORY_LIMIT` | `1G` | Container memory limit |

#### Adminer Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `ADMINER_VERSION` | `latest` | Adminer image version |
| `ADMINER_HOST_PORT` | `8081` | External port (the app owns 8080) |
| `ADMINER_THEME` | `dracula` | Adminer UI theme |

#### Application Variables

Used by the `dcc-app` service.

| Variable | Default | Description |
|----------|---------|-------------|
| `APP_HOST_PORT` | `8080` | Host port mapped to the app's `8080` |
| `APP_MEMORY_LIMIT` | `512M` | Container memory limit (JVM heap scales with it) |
| `APP_MEMORY_RESERVATION` | `256M` | Container memory reservation |

#### MCP Variables

Used by the `dcc-mcp` service.

| Variable | Default | Description |
|----------|---------|-------------|
| `MCP_HOST_PORT` | `8082` | Host port mapped to the MCP app's `8081` (8080/8081 are dcc-app/Adminer) |
| `MCP_MEMORY_LIMIT` | `512M` | Container memory limit (JVM heap scales with it) |
| `MCP_MEMORY_RESERVATION` | `256M` | Container memory reservation |

#### Health Check Variables

Applied to every service that declares a health check. MinIO overrides these
with `30s` / `20s` / `3`.

| Variable | Default | Description |
|----------|---------|-------------|
| `HEALTHCHECK_INTERVAL` | `10s` | Time between checks |
| `HEALTHCHECK_TIMEOUT` | `5s` | Per-check timeout |
| `HEALTHCHECK_RETRIES` | `5` | Failures before a service is marked unhealthy |

### Spring Boot Configuration Mapping

Only the datasource is wired into Spring today; Redis, RabbitMQ and MinIO exist
in `docker-compose.yml` but have no Spring configuration yet.

```yaml
# application-dev.yml — defaults are dev-only
spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_HOST_PORT:5432}/${POSTGRES_DB:dcc_dev}
    username: ${POSTGRES_USER:dcc_user}
    password: ${POSTGRES_PASSWORD}
```

```yaml
# application-prod.yml — no defaults, so a missing variable fails startup
spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_HOST_PORT}/${POSTGRES_DB}
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
```

`application-prod.yml` intentionally omits every default: a misconfigured
production box fails fast instead of silently connecting to `localhost`.

---

## Common Commands

### Docker Management

```bash
# Start services
docker-compose up -d

# Stop services
docker-compose down

# Restart services
docker-compose restart

# View logs
docker-compose logs -f

# View specific service logs
docker-compose logs -f postgres

# Check service status
docker-compose ps

# Clean everything (including volumes)
docker-compose down -v

# Rebuild and start
docker-compose up -d --build
```

### Script Commands

```bash
# Help
./scripts/dcc-start.sh --help
./scripts/dcc-deploy.sh --help

# Start with different components
./scripts/dcc-start.sh --redis --rabbitmq
./scripts/dcc-start.sh --full

# Deploy to production
POSTGRES_PASSWORD=xxx REDIS_PASSWORD=xxx RABBITMQ_PASSWORD=xxx MINIO_ROOT_PASSWORD=xxx ./scripts/dcc-deploy.sh prod --full
```

### Maven Commands

```bash
# Clean and compile
./mvnw clean compile

# Build all modules
./mvnw clean package

# Build specific module
./mvnw clean package -pl dcc-starter

# Run tests
./mvnw test

# Run specific module
./mvnw spring-boot:run -pl dcc-starter

# Skip tests
./mvnw clean package -DskipTests
```

### Database Commands

```bash
# Connect to PostgreSQL
docker-compose exec postgres psql -U dcc_user -d dcc_dev

# Execute SQL file
docker-compose exec -T postgres psql -U dcc_user -d dcc_dev < script.sql

# Backup database
docker-compose exec -T postgres pg_dump -U dcc_user dcc_dev > backup.sql

# Restore database
docker-compose exec -T postgres psql -U dcc_user dcc_dev < backup.sql
```

### Redis Commands

```bash
# Connect to Redis
docker-compose exec redis redis-cli --pass "$REDIS_PASSWORD"

# Monitor Redis
docker-compose exec redis redis-cli --pass "$REDIS_PASSWORD" MONITOR

# Flush all data (development only)
docker-compose exec redis redis-cli --pass "$REDIS_PASSWORD" FLUSHALL
```

### RabbitMQ Commands

```bash
# List exchanges
docker-compose exec rabbitmq rabbitmqctl list_exchanges

# List queues
docker-compose exec rabbitmq rabbitmqctl list_queues

# List bindings
docker-compose exec rabbitmq rabbitmqctl list_bindings

# Purge queue
docker-compose exec rabbitmq rabbitmqctl purge_queue queue_name
```

---

## Troubleshooting

### Common Issues

#### 1. Port Already in Use

```bash
Error: port 5432 already in use
```

**Solution:**
```bash
# Find process using port
lsof -i :5432

# Kill process
kill -9 <PID>

# Or change port in env/dev.env
POSTGRES_HOST_PORT=5433
```

#### 2. Docker Container Not Starting

```bash
Error: Container exited with code 1
```

**Solution:**
```bash
# View logs
docker-compose logs postgres

# Check if volume is corrupted
docker-compose down -v
docker-compose up -d
```

#### 3. Spring Boot Cannot Connect to Database

```bash
Error: Connection refused
```

**Solution:**
```bash
# Check if PostgreSQL is running
docker-compose ps

# Check PostgreSQL health
docker-compose exec postgres pg_isready -U dcc_user

# Restart PostgreSQL
docker-compose restart postgres
```

#### 4. Memory Issues

```bash
Error: Container exceeded memory limit
```

**Solution:**
```bash
# Increase memory limit in env/dev.env
POSTGRES_MEMORY_LIMIT=1G
POSTGRES_MEMORY_RESERVATION=512M

# Restart service
docker-compose up -d
```

#### 5. Permission Denied on Scripts

```bash
Error: Permission denied: ./scripts/dcc-start.sh
```

**Solution:**
```bash
chmod +x scripts/*.sh
./scripts/dcc-start.sh
```

### Health Check Commands

```bash
# Check all services
docker-compose ps

# Check PostgreSQL health
docker-compose exec postgres pg_isready -U dcc_user

# Check Redis health
docker-compose exec redis redis-cli --pass "$REDIS_PASSWORD" ping

# Check RabbitMQ health
docker-compose exec rabbitmq rabbitmq-diagnostics ping

# Check MinIO health
curl http://localhost:9000/minio/health/live
```

### Log Locations

| Service | Log Command |
|---------|-------------|
| All services | `docker-compose logs -f` |
| PostgreSQL | `docker-compose logs -f postgres` |
| Redis | `docker-compose logs -f redis` |
| RabbitMQ | `docker-compose logs -f rabbitmq` |
| MinIO | `docker-compose logs -f minio` |
| Adminer | `docker-compose logs -f adminer` |

### Debug Mode

```bash
# Docker Compose
export COMPOSE_DEBUG=1
docker-compose up

# Spring Boot
./mvnw spring-boot:run -pl dcc-starter -Ddebug

# PostgreSQL
docker-compose exec postgres psql -U dcc_user -d dcc_dev -c "SET log_statement = 'all';"
```

---

## Quick Reference Card

```bash
# Local Development
./scripts/dcc-start.sh [--redis] [--rabbitmq] [--minio] [--adminer] [--full]

# Production Deployment
POSTGRES_PASSWORD=xxx REDIS_PASSWORD=xxx RABBITMQ_PASSWORD=xxx MINIO_ROOT_PASSWORD=xxx ./scripts/dcc-deploy.sh prod --full

# Docker Commands
docker-compose up -d              # Start services
docker-compose down               # Stop services
docker-compose ps                 # List services
docker-compose logs -f            # View logs

# Maven Commands
./mvnw clean package              # Build project
./mvnw spring-boot:run -pl dcc-starter  # Run application

# Database
docker-compose exec postgres psql -U dcc_user -d dcc_dev  # Connect to DB
```

---

## Module Quick Reference

| Module | Responsibility | Depends On |
|--------|---------------|------------|
| **dcc-core** | Persistence and shared primitives | None |
| **dcc-api** | Main business logic (protocol-agnostic) | dcc-core |
| **dcc-starter** | REST exposure layer + application entry point (web UI) | dcc-api |
| **dcc-mcp** | MCP exposure layer + application entry point (Streamable HTTP) | dcc-api |

