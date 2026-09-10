#!/bin/bash
# ============================================
# DCC local development startup script
# Uses dev.env configuration
# ============================================

set -e

# Colors
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
${CYAN}DCC local development startup script${NC}

${GREEN}Usage:${NC}
  ./scripts/dcc-start.sh [component options]

${GREEN}Component options:${NC}
  --redis        Start Redis
  --rabbitmq     Start RabbitMQ
  --minio        Start MinIO
  --adminer      Start Adminer
  --full         Start all components
  (PostgreSQL starts by default)

${GREEN}Examples:${NC}
  ./scripts/dcc-start.sh              # Database only
  ./scripts/dcc-start.sh --redis      # Database + Redis
  ./scripts/dcc-start.sh --full       # All components

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
# Fill empty/placeholder passwords in dev.env with random values
# ============================================
fill_dev_passwords() {
    local tmp="env/dev.env.tmp"
    local key val

    : > "$tmp"
    while IFS= read -r line; do
        key="${line%%=*}"
        val="${line#*=}"
        case "$key" in
            POSTGRES_PASSWORD|REDIS_PASSWORD|RABBITMQ_PASSWORD|MINIO_ROOT_PASSWORD)
                if [ -z "$val" ] || [ "$val" = "CHANGE_ME" ]; then
                    echo "${key}=$(gen_password)" >> "$tmp"
                else
                    echo "$line" >> "$tmp"
                fi
                ;;
            *)
                echo "$line" >> "$tmp"
                ;;
        esac
    done < env/dev.env
    mv "$tmp" env/dev.env
}

# ============================================
# Check dev.env
# ============================================
check_dev_env() {
    if [ ! -f "env/dev.env" ]; then
        echo -e "${YELLOW}⚠️  dev.env does not exist, creating from template...${NC}"
        cp env/.env.example env/dev.env
        fill_dev_passwords
        echo -e "${GREEN}✅ Created env/dev.env with generated passwords${NC}"
    fi
}

# ============================================
# Parse arguments
# ============================================
PROFILES=""
COMPONENTS="Database"

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
        --adminer)
            PROFILES="$PROFILES --profile adminer"
            COMPONENTS="$COMPONENTS, Adminer"
            shift
            ;;
        --full)
            PROFILES="--profile full"
            COMPONENTS="Database, Redis, RabbitMQ, MinIO, Adminer"
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
echo -e "${CYAN}║          🚀 DCC local development                              ║${NC}"
echo -e "${CYAN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check configuration
check_dev_env

# Load configuration
set -a
source env/dev.env
set +a

# Display information
echo -e "${GREEN}📋 Startup information:${NC}"
echo -e "  Components: ${COMPONENTS}"
echo ""

# Start services
echo -e "${YELLOW}⏳ Starting services...${NC}"
docker-compose --env-file env/dev.env $PROFILES up -d

# Show status
echo ""
echo -e "${GREEN}✅ Services started successfully!${NC}"
echo ""
docker-compose --env-file env/dev.env $PROFILES ps

# Access information
echo ""
echo -e "${CYAN}🔗 Access URLs:${NC}"
echo -e "  PostgreSQL: localhost:${POSTGRES_HOST_PORT:-5432}"
echo -e "  Username: ${POSTGRES_USER:-dcc_user}"
echo -e "  Password: ${POSTGRES_PASSWORD}"
echo ""
echo -e "${BLUE}📝 Common commands:${NC}"
echo -e "  View logs: docker-compose --env-file env/dev.env $PROFILES logs -f"
echo -e "  Stop services: docker-compose --env-file env/dev.env $PROFILES down"
echo ""