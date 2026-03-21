#!/usr/bin/env bash
# ============================================================
# Medoq — EC2 User-Data Bootstrap Script
# Amazon Linux 2023 / Ubuntu 22.04
# Run once on first boot to install Docker, AWS CLI, and set up
# the application directory.
# ============================================================
set -euo pipefail
exec > >(tee /var/log/medoq-init.log) 2>&1

echo "=== Medoq EC2 init started at $(date) ==="

# ── System update ─────────────────────────────────────────────
dnf update -y 2>/dev/null || apt-get update -y

# ── Docker ───────────────────────────────────────────────────
if ! command -v docker &>/dev/null; then
  # Amazon Linux 2023
  if command -v dnf &>/dev/null; then
    dnf install -y docker
    systemctl enable --now docker
    usermod -aG docker ec2-user
  else
    # Ubuntu fallback
    curl -fsSL https://get.docker.com | sh
    usermod -aG docker ubuntu
  fi
fi

# Docker Compose plugin
COMPOSE_VERSION="2.27.1"
mkdir -p /usr/local/lib/docker/cli-plugins
curl -SL "https://github.com/docker/compose/releases/download/v${COMPOSE_VERSION}/docker-compose-linux-x86_64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# ── AWS CLI v2 ───────────────────────────────────────────────
if ! command -v aws &>/dev/null; then
  curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
  unzip -q /tmp/awscliv2.zip -d /tmp
  /tmp/aws/install
  rm -rf /tmp/aws /tmp/awscliv2.zip
fi

# ── Application directory ─────────────────────────────────────
APP_DIR="/opt/medoq"
mkdir -p "$APP_DIR"

# docker-compose.prod.yml — references ECR image instead of build context
cat > "$APP_DIR/docker-compose.yml" << 'COMPOSE'
version: '3.9'

services:
  backend:
    image: medoq-backend:latest
    restart: unless-stopped
    env_file: .env
    ports:
      - "8080:8080"
    networks:
      - medoq_net
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/api/v1/actuator/health | grep -q UP || exit 1"]
      interval: 30s
      timeout: 5s
      start_period: 60s
      retries: 3

networks:
  medoq_net:
    driver: bridge
COMPOSE

echo "=== Copy your .env file to $APP_DIR/.env before starting ==="
echo "=== EC2 init complete at $(date) ==="
