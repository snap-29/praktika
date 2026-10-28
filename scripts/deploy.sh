#!/usr/bin/env bash
set -euo pipefail
: "${1:?Usage: bash scripts/deploy.sh registry/image:commit}"
cd "$(dirname "$0")/.."
# A dedicated self-hosted runner works in a persistent checkout and Compose project.
export COMPOSE_PROJECT_NAME=support-training
previous="$(cat .previous-current-image 2>/dev/null || true)"
export APP_IMAGE="$1"
docker compose pull app
if ! { docker compose up -d --no-build --wait --wait-timeout 180 && bash scripts/smoke.sh; }; then
  if [ -n "$previous" ]; then
    export APP_IMAGE="$previous"
    docker compose up -d --no-build --wait --wait-timeout 180
    bash scripts/smoke.sh
  fi
  exit 1
fi
printf '%s\n' "$previous" > .rollback-image
printf '%s\n' "$1" > .previous-current-image
