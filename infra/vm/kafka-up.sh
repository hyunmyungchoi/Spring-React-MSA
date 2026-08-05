#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${SCRIPT_DIR}/kafka-compose.yml"
CONTAINER_NAME="springmsa-kafka"

docker compose -f "${COMPOSE_FILE}" up -d

printf 'Waiting for Kafka'
until docker exec "${CONTAINER_NAME}" /opt/kafka/bin/kafka-broker-api-versions.sh \
  --bootstrap-server localhost:9092 >/dev/null 2>&1; do
  printf '.'
  sleep 2
done
printf ' ready\n'

topics=(
  spring.chat.message.created
  spring.chat.message.created.DLT
  springmsa.community.post-created.v1
  springmsa.community.post-created.v1.DLT
  springmsa.user.registered.v1
  springmsa.user.registered.v1.DLT
  springmsa.stock.watchlist-item-added.v1
  springmsa.stock.watchlist-item-added.v1.DLT
)

for topic in "${topics[@]}"; do
  docker exec "${CONTAINER_NAME}" /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server localhost:9092 \
    --create \
    --if-not-exists \
    --topic "${topic}" \
    --partitions 3 \
    --replication-factor 1
done

docker exec "${CONTAINER_NAME}" /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --list
