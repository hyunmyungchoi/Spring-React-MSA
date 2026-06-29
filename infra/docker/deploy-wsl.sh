#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

echo "[1/4] Validate compose config"
docker compose \
  --env-file .env.prod \
  -f docker-compose.prod.yml \
  -f docker-compose.wsl.yml \
  config > /dev/null

echo "[2/4] Pull images from GHCR"
docker compose \
  --env-file .env.prod \
  -f docker-compose.prod.yml \
  -f docker-compose.wsl.yml \
  pull

echo "[3/4] Start containers"
docker compose \
  --env-file .env.prod \
  -f docker-compose.prod.yml \
  -f docker-compose.wsl.yml \
  up -d

echo "[4/4] Show status"
docker compose \
  --env-file .env.prod \
  -f docker-compose.prod.yml \
  -f docker-compose.wsl.yml \
  ps