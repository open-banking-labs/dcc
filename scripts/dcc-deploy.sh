#!/bin/bash
# ============================================
# DCC deployment script
# Supports dev / prod environments
# Dynamically generates environment configuration without relying on local files
# ============================================

set -e

# Prefer the Docker Compose v2 plugin, fall back to the standalone v1 binary.
if docker compose version >/dev/null 2>&1; then
    COMPOSE="docker compose"
else
    COMPOSE="docker-compose"
fi

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

# ============================================
# Help information
# ============================================
show_help() {
    cat << EOF
${CYAN}DCC deployment script${NC}

${GREEN}Usage:${NC}
  ./scripts/dcc-deploy.sh <environment> [component options]

${GREEN}Environments:${NC}
  dev            Development environment
  prod           Production environment

${GREEN}Component options:${NC}
  --redis        Include Redis
  --rabbitmq     Include RabbitMQ
  --minio        Include MinIO
  --app          Build and include the application (dcc-app)
  --mcp          Build and include the MCP layer (dcc-mcp; implies --app)
  --full         Include all components

${GREEN}Environment variables:${NC}
  Development: passwords are generated automatically when not provided.
  Production:  all four passwords are required and have no default.

${GREEN}Examples:${NC}
  # Development environment (database only, passwords auto-generated)
  ./scripts/dcc-deploy.sh dev

  # Development environment with the application container
  ./scripts/dcc-deploy.sh dev --app

  # Development environment with the REST app and the MCP layer
  ./scripts/dcc-deploy.sh dev --mcp

  # Development environment (with Redis, explicit passwords)
  POSTGRES_PASSWORD=dev123 REDIS_PASSWORD=dev_redis ./scripts/dcc-deploy.sh dev --redis

  # Production environment (all components, all passwords required)
  POSTGRES_PASSWORD=prod_secure \
  REDIS_PASSWORD=prod_redis \
  RABBITMQ_PASSWORD=prod_rabbit \
  MINIO_ROOT_PASSWORD=prod_minio \
  ./scripts/dcc-deploy.sh prod --full

EOF
}

# ============================================
# Generate a random password
# ============================================
gen_password() {
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -hex 16
    else
        LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32
    fi
}

# ============================================
# Generate environment configuration
# ============================================
generate_env() {
    local env=$1
    local target="env/${env}.env"
    local db_pw redis_pw rabbit_pw minio_pw

    if [ "$env" = "dev" ]; then
        db_pw="${POSTGRES_PASSWORD:-$(gen_password)}"
        redis_pw="${REDIS_PASSWORD:-$(gen_password)}"
        rabbit_pw="${RABBITMQ_PASSWORD:-$(gen_password)}"
        minio_pw="${MINIO_ROOT_PASSWORD:-$(gen_password)}"
    else
        db_pw="$POSTGRES_PASSWORD"
        redis_pw="$REDIS_PASSWORD"
        rabbit_pw="$RABBITMQ_PASSWORD"
        minio_pw="$MINIO_ROOT_PASSWORD"
    fi

    # If the file already exists, ask whether to overwrite it
    if [ -f "$target" ]; then
        echo -e "${YELLOW}⚠️  Configuration file already exists: $target${NC}"
        read -p "Overwrite? (y/n): " overwrite
        if [ "$overwrite" != "y" ]; then
            echo -e "${YELLOW}Skipping generation and using the existing configuration${NC}"
            return 0
        fi
    fi

    echo -e "${YELLOW}📄 Generating ${env}.env configuration...${NC}"

    case $env in
        dev)
            cat > "$target" << EOF
ENV=dev
PROJECT_NAME=dcc
TZ=Asia/Shanghai
RESTART_POLICY=unless-stopped
POSTGRES_VERSION=18-alpine
POSTGRES_HOST=localhost
POSTGRES_HOST_PORT=5432
POSTGRES_DB=dcc_dev
POSTGRES_USER=dcc_user
POSTGRES_PASSWORD=${db_pw}
POSTGRES_MEMORY_LIMIT=512M
POSTGRES_MEMORY_RESERVATION=256M
REDIS_VERSION=7-alpine
REDIS_HOST_PORT=6379
REDIS_PASSWORD=${redis_pw}
REDIS_MAX_MEMORY=256mb
REDIS_MEMORY_LIMIT=256M
RABBITMQ_VERSION=4-management-alpine
RABBITMQ_AMQP_HOST_PORT=5672
RABBITMQ_MANAGEMENT_HOST_PORT=15672
RABBITMQ_USER=admin
RABBITMQ_PASSWORD=${rabbit_pw}
RABBITMQ_VHOST=/dcc
RABBITMQ_MEMORY_LIMIT=512M
MINIO_VERSION=latest
MINIO_API_HOST_PORT=9000
MINIO_CONSOLE_HOST_PORT=9090
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=${minio_pw}
MINIO_CONSOLE_URL=http://localhost:9090
MINIO_MEMORY_LIMIT=1G
ADMINER_VERSION=latest
ADMINER_HOST_PORT=8081
ADMINER_THEME=dracula
GATEWAY_HOST_PORT=8080
TRAEFIK_VERSION=v3.1
GATEWAY_RATE_AVERAGE=20
GATEWAY_RATE_BURST=40
APP_MEMORY_LIMIT=512M
APP_MEMORY_RESERVATION=256M
MCP_MEMORY_LIMIT=512M
MCP_MEMORY_RESERVATION=256M
HEALTHCHECK_INTERVAL=10s
HEALTHCHECK_TIMEOUT=5s
HEALTHCHECK_RETRIES=5
EOF
            ;;
        prod)
            cat > "$target" << EOF
ENV=prod
PROJECT_NAME=dcc
TZ=Asia/Shanghai
RESTART_POLICY=always
POSTGRES_VERSION=18-alpine
POSTGRES_HOST=localhost
POSTGRES_HOST_PORT=5437
POSTGRES_DB=dcc_prod
POSTGRES_USER=dcc_user
POSTGRES_PASSWORD=${db_pw}
POSTGRES_MEMORY_LIMIT=8G
POSTGRES_MEMORY_RESERVATION=4G
REDIS_VERSION=7-alpine
REDIS_HOST_PORT=6384
REDIS_PASSWORD=${redis_pw}
REDIS_MAX_MEMORY=2gb
REDIS_MEMORY_LIMIT=2G
RABBITMQ_VERSION=4-management-alpine
RABBITMQ_AMQP_HOST_PORT=5677
RABBITMQ_MANAGEMENT_HOST_PORT=15677
RABBITMQ_USER=admin
RABBITMQ_PASSWORD=${rabbit_pw}
RABBITMQ_VHOST=/dcc
RABBITMQ_MEMORY_LIMIT=4G
MINIO_VERSION=latest
MINIO_API_HOST_PORT=9005
MINIO_CONSOLE_HOST_PORT=9095
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=${minio_pw}
MINIO_CONSOLE_URL=http://localhost:9095
MINIO_MEMORY_LIMIT=8G
ADMINER_VERSION=latest
ADMINER_HOST_PORT=8085
ADMINER_THEME=default
GATEWAY_HOST_PORT=8080
TRAEFIK_VERSION=v3.1
GATEWAY_RATE_AVERAGE=100
GATEWAY_RATE_BURST=200
APP_MEMORY_LIMIT=2G
APP_MEMORY_RESERVATION=1G
MCP_MEMORY_LIMIT=2G
MCP_MEMORY_RESERVATION=1G
HEALTHCHECK_INTERVAL=30s
HEALTHCHECK_TIMEOUT=10s
HEALTHCHECK_RETRIES=3
EOF
            ;;
        *)
            echo -e "${RED}❌ Unknown environment: $env${NC}"
            exit 1
            ;;
    esac

    echo -e "${GREEN}✅ Configuration file generated: $target${NC}"
}

# ============================================
# Check required environment variables
# ============================================
check_required_vars() {
    local env=$1
    local missing=()

    case $env in
        prod)
            [ -z "$POSTGRES_PASSWORD" ] && missing+=("POSTGRES_PASSWORD")
            [ -z "$REDIS_PASSWORD" ] && missing+=("REDIS_PASSWORD")
            [ -z "$RABBITMQ_PASSWORD" ] && missing+=("RABBITMQ_PASSWORD")
            [ -z "$MINIO_ROOT_PASSWORD" ] && missing+=("MINIO_ROOT_PASSWORD")
            ;;
    esac

    if [ ${#missing[@]} -gt 0 ]; then
        echo -e "${RED}❌ Missing required environment variables:${NC}"
        for var in "${missing[@]}"; do
            echo -e "  ${RED}$var${NC}"
        done
        echo ""
        echo -e "${YELLOW}Example:${NC}"
          echo -e "  POSTGRES_PASSWORD=xxx ./scripts/dcc-deploy.sh ${env}"
        exit 1
    fi
}

# ============================================
# Parse arguments
# ============================================
if [[ $# -lt 1 ]]; then
    show_help
    exit 1
fi

ENV=$1
shift

PROFILES=""
COMPONENTS="Database"
BUILD_SERVICES=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --redis)
            PROFILES="$PROFILES --profile redis"
            COMPONENTS="$COMPONENTS, Redis"
            shift
            ;;
        --rabbitmq)
            PROFILES="$PROFILES --profile rabbitmq"
            COMPONENTS="$COMPONENTS, RabbitMQ"
            shift
            ;;
        --minio)
            PROFILES="$PROFILES --profile minio"
            COMPONENTS="$COMPONENTS, MinIO"
            shift
            ;;
        --full)
            PROFILES="--profile full"
            COMPONENTS="Database, Application, MCP, Redis, RabbitMQ, MinIO"
            BUILD_SERVICES="dcc-app dcc-mcp"
            shift
            ;;
        --app)
            PROFILES="$PROFILES --profile app"
            COMPONENTS="$COMPONENTS, Application"
            BUILD_SERVICES="dcc-app"
            shift
            ;;
        --mcp)
            # dcc-mcp only validates the schema, so it needs dcc-app (the schema
            # owner) in the same run; include both profiles and build both images.
            PROFILES="$PROFILES --profile app --profile mcp"
            COMPONENTS="$COMPONENTS, Application, MCP"
            BUILD_SERVICES="dcc-app dcc-mcp"
            shift
            ;;
        --help|-h)
            show_help
            exit 0
            ;;
        *)
            echo -e "${RED}❌ Unknown option: $1${NC}"
            show_help
            exit 1
            ;;
    esac
done

# ============================================
# Main flow
# ============================================
echo ""
echo -e "${CYAN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${CYAN}║          🚀 DCC deployment - ${ENV^^} environment             ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check required environment variables
check_required_vars "$ENV"

# Generate configuration file
generate_env "$ENV"

echo -e "${GREEN}✅ Configuration generation completed${NC}"
echo -e "  Environment: ${ENV^^}"
echo -e "  Components: ${COMPONENTS}"
echo ""

# Show config preview (hide passwords)
echo -e "${CYAN}📋 Configuration preview:${NC}"
grep -v "PASSWORD" "env/${ENV}.env" | head -15
echo "  ..."
echo ""

# Confirm production deployment
if [[ "$ENV" == "prod" ]]; then
    echo -e "${RED}⚠️  Warning: about to deploy to the production environment!${NC}"
    read -p "Confirm to continue? (yes/no): " confirm
    if [ "$confirm" != "yes" ]; then
        echo -e "${YELLOW}Deployment canceled${NC}"
        exit 0
    fi
fi

# Build the application image when requested
if [ -n "$BUILD_SERVICES" ]; then
    echo ""
    echo -e "${YELLOW}🔨 Building application image(s)...${NC}"
    $COMPOSE --env-file "env/${ENV}.env" $PROFILES build $BUILD_SERVICES
fi

# Start services
echo ""
echo -e "${YELLOW}⏳ Starting services...${NC}"
$COMPOSE --env-file "env/${ENV}.env" $PROFILES up -d

# Show status
echo ""
echo -e "${GREEN}✅ ${ENV^^} deployment completed!${NC}"
echo ""
$COMPOSE --env-file "env/${ENV}.env" $PROFILES ps

# Show access information
echo ""
echo -e "${CYAN}📌 Access information:${NC}"
if [[ "$ENV" == "dev" ]]; then
    echo -e "  PostgreSQL: localhost:5432"
    echo -e "  Adminer: http://localhost:8081"
else
    echo -e "  PostgreSQL: localhost:5437"
fi
if [[ "$BUILD_SERVICES" == *dcc-app* || "$BUILD_SERVICES" == *dcc-mcp* ]]; then
    echo -e "  Gateway (only public entry): http://localhost:8080"
    echo -e "    REST: http://localhost:8080/api"
    echo -e "    MCP:  http://localhost:8080/mcp"
fi
echo ""

echo -e "${BLUE}📝 Commands:${NC}"
echo -e "  View logs: $COMPOSE --env-file env/${ENV}.env $PROFILES logs -f"
echo -e "  Stop services: $COMPOSE --env-file env/${ENV}.env $PROFILES down"
echo ""