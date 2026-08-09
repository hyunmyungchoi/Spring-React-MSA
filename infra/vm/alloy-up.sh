#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
: "${ALLOY_INSTANCE:?Set ALLOY_INSTANCE to the VM name}"

export ALLOY_INSTANCE
docker compose -f "${SCRIPT_DIR}/alloy-compose.yml" up -d
